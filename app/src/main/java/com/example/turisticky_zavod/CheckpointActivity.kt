package com.example.turisticky_zavod

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.example.turisticky_zavod.databinding.ActivityCheckpointBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CheckpointActivity: AppCompatActivity() {

    private lateinit var binding: ActivityCheckpointBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCheckpointBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarCheckpoint)

        onBackPressedDispatcher.addCallback { }
        binding.constraintLayoutCheckpoint.setOnClickListener { view: View -> loseFocus(view) }
        binding.toolbarCheckpoint.setOnClickListener { view: View -> loseFocus(view) }
        binding.autoCompleteTextViewMenuCheckpoints.setOnClickListener { view: View -> hideKeyboard(view) }
        binding.buttonSaveCheckpoint.setOnClickListener { save() }

        val insetsWithKeyboardCallback = InsetsWithKeyboardCallback(window)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, insetsWithKeyboardCallback)
        ViewCompat.setWindowInsetsAnimationCallback(binding.root, insetsWithKeyboardCallback)

        val button = findViewById<Button>(R.id.button_save_checkpoint)
        val insetsWithKeyboardAnimationCallback = InsetsWithKeyboardAnimationCallback(button)
        ViewCompat.setWindowInsetsAnimationCallback(button, insetsWithKeyboardAnimationCallback)

        Thread {
            val checkpoints = TZDatabase.getInstance(this@CheckpointActivity).checkpointDao().getNames()
            runOnUiThread {
                (binding.autoCompleteTextViewMenuCheckpoints as? MaterialAutoCompleteTextView)?.setSimpleItems(checkpoints)
            }
        }.start()
    }

    private fun loseFocus(view: View) {
        for (child in binding.constraintLayoutCheckpoint.children)
            child.clearFocus()
        hideKeyboard(view)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun save() {
        val checkpointName = binding.autoCompleteTextViewMenuCheckpoints.text
        val refereeName = binding.editTextRefereeName.text!!.trim()

        if (validateTextFields(checkpointName, refereeName)) {
            val intent = Intent()
            intent.putExtra("name", refereeName)
            intent.putExtra("checkpoint", checkpointName)
            setResult(RESULT_OK, intent)

            getSharedPreferences("TZ", MODE_PRIVATE).edit().putString("referee", refereeName.toString()).apply()
            lifecycleScope.launch(Dispatchers.IO) {
                TZDatabase.getInstance(this@CheckpointActivity).checkpointDao().setActive(binding.autoCompleteTextViewMenuCheckpoints.text.toString())
            }

            finish()
        }
    }

    private fun validateTextFields(checkpointName: Editable, refereeName: CharSequence): Boolean {
        var validated = true

        if (refereeName.isEmpty()) {
            validated = false
            binding.editTextRefereeName.error = "Jméno je povinné"
        } else if (!refereeName.contains(' ')) {
            binding.editTextRefereeName.error = "Text musí obsahovat jméno i příjmení"
            validated = false
        } else if (!refereeName.all { ch -> ch.isLetter() || ch == ' ' }) {
            binding.editTextRefereeName.error = "Jméno obsahuje nepovolené znaky"
            validated = false
        }

        if (checkpointName.isEmpty()) {
            validated = false
            binding.autoCompleteTextViewMenuCheckpoints.error = "Stanoviště je povinné"
        }

        return validated
    }
}
