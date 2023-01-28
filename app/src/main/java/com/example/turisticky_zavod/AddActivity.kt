package com.example.turisticky_zavod

import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
//import com.example.turisticky_zavod.databinding.ActivityAddBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddActivity : AppCompatActivity() {

//    private lateinit var binding: ActivityAddBinding
    private lateinit var editText_runnerId : EditText
    private lateinit var button_save_phone : Button
    private lateinit var layout_add : ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(R.layout.dialog_add)

//        setSupportActionBar(binding.toolbar)

        if (intent.getStringExtra("xd") != null)
            Toast.makeText(this, intent.getStringExtra("xd"), Toast.LENGTH_SHORT).show()

        editText_runnerId = findViewById(R.id.editText_RunnerId)
        button_save_phone = findViewById(R.id.button_save)
//        layout_add = findViewById<View>(R.id.layout_add) as ConstraintLayout

        button_save_phone.setOnClickListener { finish() }
        layout_add.setOnClickListener { view: View -> loseFocus(view) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun loseFocus(view: View) {
        editText_runnerId.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Máte neuložené změny")
        builder.setMessage("Opravdu chcete jít zpět? Provedené změny nebudou uloženy")
        builder.setCancelable(false)
        builder.setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
        builder.setPositiveButton("Ano") { _: DialogInterface?, _: Int -> finish() }
        builder.create().show()
    }
}
