package com.example.test2

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class SavedSessionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listView = ListView(this)
        setContentView(listView)

        val baseDir = getExternalFilesDir(null)
        val folders = baseDir?.listFiles { file -> file.isDirectory }?.map { it.name } ?: emptyList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, folders)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val folderName = folders[position]
            val intent = Intent(this, SessionDetailActivity::class.java)
            intent.putExtra("folderName", folderName)
            startActivity(intent)
        }
    }
}
