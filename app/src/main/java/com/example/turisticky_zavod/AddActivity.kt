package com.example.turisticky_zavod

import android.content.DialogInterface
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.room.Room
import com.example.turisticky_zavod.databinding.ActivityAddBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.net.URI
import java.time.temporal.ValueRange

class AddActivity : AppCompatActivity(), ReaderCallback {

    private var _binding: ActivityAddBinding? = null
    private val binding get() = _binding!!

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var db: TZDatabase

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarAdd)

        nfcAdapter = getDefaultAdapter(this@AddActivity)

        onBackPressedDispatcher.addCallback(this@AddActivity) { handleBackButtonPressed() }
        binding.toolbarAdd.setNavigationOnClickListener { handleBackButtonPressed() }
        binding.buttonCancel.setOnClickListener { handleBackButtonPressed() }

        binding.editTextRunnerId.setText(intent.getStringExtra("id"))
        binding.editTextRunnerName.setText(intent.getStringExtra("name"))
        binding.editTextRunnerTeam.setText(intent.getStringExtra("team"))

        binding.buttonSave.setOnClickListener { save() }
        binding.coordinatorLayoutAdd.setOnClickListener { view: View -> loseFocus(view) }
        binding.toolbarAdd.setOnClickListener { view: View -> loseFocus(view) }

        db = Room.databaseBuilder(this@AddActivity, TZDatabase::class.java, "tz.db").build()

        val insetsWithKeyboardCallback = InsetsWithKeyboardCallback(window)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, insetsWithKeyboardCallback)
        ViewCompat.setWindowInsetsAnimationCallback(binding.root, insetsWithKeyboardCallback)

        val buttons = findViewById<LinearLayout>(R.id.linearLayout_addButtons)
        val insetsWithKeyboardAnimationCallback = InsetsWithKeyboardAnimationCallback(buttons)
        ViewCompat.setWindowInsetsAnimationCallback(buttons, insetsWithKeyboardAnimationCallback)

        val seconds = ArrayList<String>()
        for (i in 0 until 60 step 5)
            seconds.add(i.toString())
        val formatter = NumberPicker.Formatter { (it * 5).toString() }
        with (binding.numberPickerMinute) {
            minValue = 0
            maxValue = 60
            wrapSelectorWheel = true
        }
        with (binding.numberPickerSecond) {
            wrapSelectorWheel = true
            minValue = 0
            maxValue = 11
            setFormatter(formatter)
        }

        binding.cardViewDisqualified.setOnClickListener {
            binding.switchDisqualified.isChecked = !binding.switchDisqualified.isChecked
        }
    }

    private fun loseFocus(view: View) {
        for (child in binding.linearLayoutScrollViewContent.children)
            child.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun handleBackButtonPressed() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Máte neuložené změny")
        builder.setMessage("Opravdu chcete jít zpět? Provedené změny nebudou uloženy")
        builder.setCancelable(false)
        builder.setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
        builder.setPositiveButton("Ano") { _: DialogInterface?, _: Int -> finish() }
        builder.create().show()
    }

    override fun onTagDiscovered(tag: Tag?) {
        runOnUiThread {
            Toast.makeText(this@AddActivity, "lmao", Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        val id = binding.editTextRunnerId.text.toString().takeWhile { c -> c.isDigit() }.toInt()
        val name = binding.editTextRunnerName.text.toString()
        val team = binding.editTextRunnerTeam.text.toString()

        val penaltyMinutes = binding.numberPickerMinute.value
        val penaltySeconds = binding.numberPickerSecond.value
        val penalty = penaltyMinutes * 60 + penaltySeconds * 5

        val person = Person(id, name, team, penalty,false, null)

        Thread {
            db.personDao().insert(person)
        }.start()

        setResult(RESULT_OK)
        finish()
    }

    private fun startScanningNFC() {
        val options = Bundle()
        options.putInt(EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        nfcAdapter!!.enableReaderMode(this@AddActivity, this, FLAG_READER_NFC_A, options)
    }

    private fun stopScanningNFC() {
        nfcAdapter!!.disableReaderMode(this@AddActivity)
    }
}
