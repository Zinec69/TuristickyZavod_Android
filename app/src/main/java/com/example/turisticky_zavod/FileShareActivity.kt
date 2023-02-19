package com.example.turisticky_zavod

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class FileShareActivity : AppCompatActivity() {

    private lateinit var fileName: String

    private var activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let {
                    it.data?.let { uri ->
                        val cacheFile = File(cacheDir.path).listFiles()?.single { f -> f.name == fileName }!!
                        val cacheUri = FileProvider.getUriForFile(
                            this@FileShareActivity,
                            "${BuildConfig.APPLICATION_ID}.provider",
                            cacheFile
                        )
                        contentResolver.openOutputStream(uri)?.let { os ->
                            contentResolver.openInputStream(cacheUri)?.copyTo(os)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this@FileShareActivity, "Nastala chyba při ukládání souboru", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            fileName = intent.getStringExtra("filename")!!

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
            }

            val file = File(getExternalFilesDir(null), fileName)
            val uri = FileProvider.getUriForFile(
                this@FileShareActivity,
                "${applicationContext.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }
            activityResultLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this@FileShareActivity, "Nastala chyba při ukládání souboru", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
