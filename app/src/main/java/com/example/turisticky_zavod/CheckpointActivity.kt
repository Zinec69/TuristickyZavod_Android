package com.example.turisticky_zavod

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.example.turisticky_zavod.databinding.ActivityCheckpointBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class CheckpointActivity: AppCompatActivity() {

    private lateinit var binding: ActivityCheckpointBinding

    private lateinit var checkpoints: Array<String>

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
            checkpoints = TZDatabase.getInstance(this@CheckpointActivity).checkpointDao().getNames()
            runOnUiThread {
                (binding.autoCompleteTextViewMenuCheckpoints as? MaterialAutoCompleteTextView)?.setSimpleItems(checkpoints)
            }
        }.start()
    }

    private fun loseFocus(view: View) {
        for (child in binding.constraintLayoutCheckpoint.children)
            child.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun save() {
        if (binding.autoCompleteTextViewMenuCheckpoints.text.isEmpty()) {
            Toast.makeText(this@CheckpointActivity, "Musíte zvolit stanoviště", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent()
            intent.putExtra("name", binding.editTextGuardName.text)
            intent.putExtra("checkpoint", binding.autoCompleteTextViewMenuCheckpoints.text)
            setResult(RESULT_OK, intent)

            Thread {
                TZDatabase.getInstance(this@CheckpointActivity).checkpointDao().setActive(binding.autoCompleteTextViewMenuCheckpoints.text.toString())
            }.start()

            finish()
        }
    }
}
