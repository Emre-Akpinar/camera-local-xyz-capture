package com.example.test2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import androidx.core.content.FileProvider


class SessionDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listView = ListView(this)
        setContentView(listView)

        val folderName = intent.getStringExtra("folderName") ?: return
        val folder = File(getExternalFilesDir(null), folderName)
        val files = folder.listFiles()?.map { it.name } ?: emptyList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val file = File(folder, files[position])
            if (file.extension == "jpg") {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "image/*")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)

            } else if (file.extension == "csv") {
                val intent = Intent(this, TextViewerActivity::class.java)
                intent.putExtra("filePath", file.absolutePath)
                startActivity(intent)
            }
        }
    }
}
