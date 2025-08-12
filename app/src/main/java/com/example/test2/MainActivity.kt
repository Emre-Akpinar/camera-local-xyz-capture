package com.example.test2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.ar.core.*
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var glView: GLSurfaceView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    // ARCore
    private var session: Session? = null
    private val bg = ArBackgroundRenderer()

    // Capture (1 FPS)
    private var recording = false
    private var lastCaptureTimeNs = 0L
    private val captureIntervalNs = 1_000_000_000L // 1 second
    private var frameIndex = 0

    // Output
    private lateinit var sessionFolder: File
    private var csvStream: FileOutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // must match the Drawer + Toolbar + GLSurfaceView layout

        // --- Drawer + Toolbar (real hamburger) ---
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        // Use built-in strings to avoid adding new resources
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            android.R.string.ok, android.R.string.cancel
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

// Set hamburger icon color to white
        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, android.R.color.white)


        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_start -> startRecordingSession()
                R.id.nav_stop  -> stopRecordingSession()
                R.id.nav_saved -> startActivity(Intent(this, SavedSessionsActivity::class.java))
            }
            drawerLayout.closeDrawers()
            true
        }

        // --- Views ---
        glView = findViewById(R.id.glView)
        btnStart = findViewById(R.id.startButton)
        btnStop  = findViewById(R.id.stopButton)

        btnStart.setOnClickListener { startRecordingSession() }
        btnStop.setOnClickListener  { stopRecordingSession()  }

        // --- Permissions (CAMERA is required) ---
        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 11)
        }

        // --- GLSurfaceView Renderer for ARCore camera preview ---
        glView.preserveEGLContextOnPause = true
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                bg.createOnGlThread(this@MainActivity)
                session?.setCameraTextureName(bg.textureId)
            }
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {}
            override fun onDrawFrame(gl: GL10?) { drawFrame() }
        })
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    // --- Lifecycle / ARCore session ---
    override fun onResume() {
        super.onResume()
        if (session == null) {
            // Ensure device has ARCore (Google Play Services for AR installed)
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            if (!availability.isSupported) {
                Toast.makeText(this, "ARCore not supported on this device", Toast.LENGTH_LONG).show()
                return
            }
            session = Session(this)
            session!!.configure(Config(session).apply {
                planeFindingMode = Config.PlaneFindingMode.DISABLED
                depthMode = Config.DepthMode.DISABLED
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            })
        }
        try { session?.resume() } catch (e: Exception) {
            Toast.makeText(this, "Session resume failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        if (recording) stopRecordingSession()
        session?.pause()
    }

    // --- Recording sessions (1 FPS frames + single CSV) ---
    private fun startRecordingSession() {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val folderName = "recording_${sdf.format(Date())}"
        sessionFolder = File(getExternalFilesDir(null), folderName).apply { mkdirs() }

        val csvFile = File(sessionFolder, "coordinates.csv")
        csvStream = FileOutputStream(csvFile).apply {
            write("frame_index,posX,posY,posZ\n".toByteArray())
            flush()
        }

        frameIndex = 0
        lastCaptureTimeNs = 0L
        recording = true
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingSession() {
        recording = false
        csvStream?.flush()
        csvStream?.close()
        Toast.makeText(
            this,
            "Recording stopped.\nSaved in: ${sessionFolder.absolutePath}",
            Toast.LENGTH_LONG
        ).show()
    }

    // --- Per-frame render + 1 Hz capture ---
    private fun drawFrame() {
        val s = session ?: return
        try {
            // Some devices need this every frame
            if (bg.textureId != -1) s.setCameraTextureName(bg.textureId)

            val frame = s.update()
            val camera = frame.camera

            // Draw camera preview
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            bg.draw(frame)

            // Capture one frame per second when TRACKING
            if (recording) {
                val ts = frame.timestamp
                if (camera.trackingState == TrackingState.TRACKING &&
                    ts - lastCaptureTimeNs >= captureIntervalNs) {

                    frameIndex++
                    saveFrameAndCoords(frame, camera, frameIndex)
                    lastCaptureTimeNs = ts
                }
            }
        } catch (_: Exception) {
            // ignore occasional frame/update issues
        }
    }

    // --- Save JPEG frame + CSV row (index,x,y,z) ---
    private fun saveFrameAndCoords(frame: Frame, camera: Camera, index: Int) {
        try {
            val image = frame.acquireCameraImage()
            val jpegBytes = yuvToJpeg(image)
            image.close()

            // frame{index}.jpg
            FileOutputStream(File(sessionFolder, "frame$index.jpg")).use { it.write(jpegBytes) }

            // CSV row
            val t = camera.pose.translation
            csvStream?.apply {
                write("$index,${t[0]},${t[1]},${t[2]}\n".toByteArray())
                flush()
            }
        } catch (_: NotYetAvailableException) {
            // skip if image not ready this frame
        }
    }

    // --- YUV_420_888 -> JPEG (NV21) ---
    private fun yuvToJpeg(image: Image): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 90, out)
        return out.toByteArray()
    }

    // --- Permissions ---
    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
}
