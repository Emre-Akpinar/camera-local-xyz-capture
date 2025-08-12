package com.example.test2

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ArBackgroundRenderer {
    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var texMatHandle = 0
    var textureId = -1
        private set

    // Ekran kaplayacak dikdörtgen koordinatları (X,Y)
    private val quadCoords = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )
    private val quadTexCoords = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    // Transform edilmiş UV’ler için ayrı dizi
    private val transformedTexCoords: FloatBuffer = ByteBuffer
        .allocateDirect(quadTexCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()


    private val vb: FloatBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().put(quadCoords).apply { position(0) }

    private val tb: FloatBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().put(quadTexCoords).apply { position(0) }

    private val texMat = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    fun createOnGlThread(@Suppress("UNUSED_PARAMETER") context: Context) {
        // Kamera için external texture oluştur
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val vsh = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            uniform mat4 u_TexMatrix;
            void main() {
              gl_Position = a_Position;
              v_TexCoord = (u_TexMatrix * vec4(a_TexCoord, 0.0, 1.0)).xy;
            }
        """.trimIndent()

        val fsh = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            void main() {
              gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """.trimIndent()

        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vsh)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsh)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        texMatHandle = GLES20.glGetUniformLocation(program, "u_TexMatrix")
    }

    fun draw(frame: Frame) {
        // UV koordinatlarını doğru transform et
        tb.position(0)
        tb.put(quadTexCoords).position(0)

        transformedTexCoords.position(0)
        frame.transformDisplayUvCoords(tb, transformedTexCoords)
        transformedTexCoords.position(0)


        tb.position(0)
        tb.put(transformedTexCoords).position(0)

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vb)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, tb)
        GLES20.glUniformMatrix4fv(texMatHandle, 1, false, texMat, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }


    private fun loadShader(type: Int, code: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
        }
}
