package com.example.turisticky_zavod

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.turisticky_zavod.databinding.ActivityAddBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddActivity : AppCompatActivity(), ReaderCallback {

    private var _binding: ActivityAddBinding? = null
    private val binding get() = _binding!!

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var db: TZDatabase

    private var peopleQueue = ArrayList<Person>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarAdd)

        nfcAdapter = getDefaultAdapter(this@AddActivity)

        db = Room.databaseBuilder(this@AddActivity, TZDatabase::class.java, "tz.db").build()

        onBackPressedDispatcher.addCallback(this@AddActivity) { handleBackButtonPressed() }
        binding.toolbarAdd.setNavigationOnClickListener { handleBackButtonPressed() }
        binding.buttonCancel.setOnClickListener { handleBackButtonPressed() }

        val person =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra("person")!!
            else
                intent.getParcelableExtra("person", Person::class.java)!!

        binding.editTextRunnerId.setText(person.runnerId.toString())
        binding.editTextRunnerName.setText(person.name)
        binding.editTextRunnerTeam.setText(person.team)
        binding.switchDisqualified.isChecked = person.disqualified

        peopleQueue.add(person)

//        binding.textViewPenaltyMinutes.alpha = 0f
//        binding.textViewPenaltyMinutes.x -= 60f

        binding.buttonSave.setOnClickListener {
            save()
//            binding.textViewPenaltyMinutes.animate().alpha(1f).translationXBy(60f).setDuration(700).withEndAction {
//                binding.textViewPenaltyMinutes.animate().alpha(0f).translationXBy(60f).setDuration(700).setStartDelay(3000).start()
//            }.start()
        }
        binding.coordinatorLayoutAdd.setOnClickListener { view: View -> loseFocus(view) }
        binding.toolbarAdd.setOnClickListener { view: View -> loseFocus(view) }

        val insetsWithKeyboardCallback = InsetsWithKeyboardCallback(window)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, insetsWithKeyboardCallback)
        ViewCompat.setWindowInsetsAnimationCallback(binding.root, insetsWithKeyboardCallback)

        val buttons = findViewById<LinearLayout>(R.id.linearLayout_addButtons)
        val insetsWithKeyboardAnimationCallback = InsetsWithKeyboardAnimationCallback(buttons)
        ViewCompat.setWindowInsetsAnimationCallback(buttons, insetsWithKeyboardAnimationCallback)

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

        val colorsLayout = arrayOf(ColorDrawable(getColor(R.color.background_layout_normal)), ColorDrawable(getColor(R.color.background_layout_disqualified)))
        val colorsToolbar = arrayOf(ColorDrawable(getColor(R.color.background_layout_normal)), ColorDrawable(getColor(R.color.background_layout_disqualified)))
        if (person.disqualified) {
            binding.root.background = ColorDrawable(getColor(R.color.background_layout_disqualified))
            binding.toolbarAdd.background = ColorDrawable(getColor(R.color.background_layout_disqualified))
            colorsLayout.reverse()
            colorsToolbar.reverse()
        }
        val transitionLayout = TransitionDrawable(colorsLayout)
        val transitionToolbar = TransitionDrawable(colorsToolbar)
        binding.switchDisqualified.setOnCheckedChangeListener { _, _ ->
            binding.root.background = transitionLayout
            binding.toolbarAdd.background = transitionToolbar
            transitionLayout.reverseTransition(500)
            transitionToolbar.reverseTransition(500)
        }
    }

    private fun save() {
        val disqualified = binding.switchDisqualified.isChecked

        val penaltyMinutes = binding.numberPickerMinute.value
        val penaltySeconds = binding.numberPickerSecond.value
        val penalty = penaltyMinutes * 60 + penaltySeconds * 5

        val person = peopleQueue[0]
        person.disqualified = disqualified
        person.penaltySeconds += penalty

        addNewPerson(person)

        setResult(RESULT_OK)
        finish()
    }

    override fun onTagDiscovered(tag: Tag?) {
        runOnUiThread {
            Toast.makeText(this@AddActivity, "lmao", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addNewPerson(person: Person) {
        Thread {
            try {
                db.personDao().insert(person)
            } catch (e: Exception) {
                runOnUiThread {
                    Log.d("DB", e.stackTraceToString())
                    Toast.makeText(this@AddActivity, "Chyba při ukládání záznamu", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startScanningNFC() {
        val options = Bundle()
        options.putInt(EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        nfcAdapter!!.enableReaderMode(this@AddActivity, this, FLAG_READER_NFC_A, options)
    }

    private fun stopScanningNFC() {
        nfcAdapter!!.disableReaderMode(this@AddActivity)
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

    private fun loseFocus(view: View) {
        for (child in binding.linearLayoutScrollViewContent.children)
            child.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
