package com.example.turisticky_zavod

import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.airbnb.lottie.LottieAnimationView
import com.example.turisticky_zavod.databinding.ActivityAddBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException

class AddActivity : AppCompatActivity(), ReaderCallback {

    private lateinit var binding: ActivityAddBinding

    private var nfcAdapter: NfcAdapter? = null
    private var nfcHelper = NFCHelper()
    
    private lateinit var scanDialogView: View
    private lateinit var scanDialog: AlertDialog

    private var peopleQueue = ArrayList<Pair<Person, Long?>>()

    private val viewModel: PersonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarAdd)

        nfcAdapter = getDefaultAdapter(this@AddActivity)

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

        peopleQueue.add(Pair(person, null))

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

        binding.buttonAddToQueue.setOnClickListener { scanForNewPerson() }

        scanDialogView = layoutInflater.inflate(R.layout.dialog_add, null)
        initScanDialog()
    }

    private fun save() {
        val disqualified = binding.switchDisqualified.isChecked

        val penaltyMinutes = binding.numberPickerMinute.value
        val penaltySeconds = binding.numberPickerSecond.value
        val penalty = penaltyMinutes * 60 + penaltySeconds * 5

        val person = peopleQueue[0].first
        val holdup = peopleQueue[0].second
        person.disqualified = disqualified
        person.penaltySeconds += penalty
        if (holdup != null)
            person.timeWaited += (System.currentTimeMillis() - holdup).toInt()

        viewModel.addPerson(person)

        peopleQueue.removeAt(0)

        if (peopleQueue.isEmpty()) {
            setResult(RESULT_OK)
            finish()
        } else {
            handleNewPerson()
        }
    }

    private fun handleNewPerson() {
        val person = peopleQueue[0].first
        binding.editTextRunnerId.animate().alpha(0f).setDuration(200).withEndAction {
            binding.editTextRunnerId.setText(person.runnerId.toString())
            binding.editTextRunnerId.animate().alpha(1f).setDuration(200).start()
        }.start()
        binding.editTextRunnerName.animate().alpha(0f).setDuration(200).withEndAction {
            binding.editTextRunnerName.setText(person.name)
            binding.editTextRunnerName.animate().alpha(1f).setDuration(200).start()
        }.start()
        binding.editTextRunnerTeam.animate().alpha(0f).setDuration(200).withEndAction {
            binding.editTextRunnerTeam.setText(person.team)
            binding.editTextRunnerTeam.animate().alpha(1f).setDuration(200).start()
        }.start()
        binding.numberPickerMinute.animate().alpha(0f).setDuration(200).withEndAction {
            binding.numberPickerMinute.value = 0
            binding.numberPickerMinute.animate().alpha(1f).setDuration(200).start()
        }.start()
        binding.numberPickerSecond.animate().alpha(0f).setDuration(200).withEndAction {
            binding.numberPickerSecond.value = 0
            binding.numberPickerSecond.animate().alpha(1f).setDuration(200).start()
        }.start()
        binding.switchDisqualified.isChecked = person.disqualified
    }

    override fun onTagDiscovered(tag: Tag?) {
        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()

                val start = System.currentTimeMillis()

                val person = nfcHelper.readPerson(ndef)

                Log.d("NFC DEBUG READ", "${System.currentTimeMillis() - start}ms")

                if (viewModel.getPersonByID(person.runnerId) != null) {
                    runOnUiThread {
                        scanFail(null, "Tento člověk již byl přidán")
                    }
                    return
                }

                runOnUiThread {
                    scanSuccess(person)
                }
            } catch (e: TagLostException) {
                runOnUiThread {
                    scanFail(e, "Čip byl odebrán příliš rychle")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    scanFail(e, "Chyba při komunikaci s čipem")
                }
            } finally {
                runOnUiThread {
                    stopScanningNFC()
                }
                try {
                    ndef.close()
                } catch (e: IOException) {
                    runOnUiThread { scanFail(e, "Chyba při komunikaci s čipem") }
                }
            }
        } else {
            runOnUiThread { scanFail(null, "Nepodporovaný typ čipu") }
        }
    }

    private fun scanSuccess(person: Person) {
        scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE

        val animation = scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcSuccess)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(700).withEndAction {
            peopleQueue.add(Pair(person, System.currentTimeMillis()))
            Toast.makeText(this@AddActivity, "Běžec č. ${person.runnerId} přidán do fronty", Toast.LENGTH_SHORT).show()

            scanDialog.dismiss()
            animation.visibility = View.GONE
        }.start()
    }

    private fun scanFail(e: Exception?, mess: String) {
        scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE

        val animation = scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcFail)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(2000).withEndAction {
            animation.visibility = View.GONE
            scanDialog.dismiss()
        }.start()

        Toast.makeText(this@AddActivity, mess, Toast.LENGTH_LONG).show()
        e?.stackTraceToString()?.let { Log.d("NFC", it) }
    }

    private fun scanForNewPerson() {
        if (nfcHelper.checkNfcAvailability(nfcAdapter) == NFCHelper.NfcAvailability.OFF) {
            scanDialogView.findViewById<Button>(R.id.button_turnOnNfc)
                .setOnClickListener {
                    startActivity(Intent("android.settings.NFC_SETTINGS"))
                    scanDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
                    scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
                    startScanningNFC()
                }
            scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
            scanDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.VISIBLE
        } else {
            scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
            scanDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
            startScanningNFC()
        }

        scanDialog.show()
    }

    private fun initScanDialog() {
        scanDialog = MaterialAlertDialogBuilder(this@AddActivity)
            .setView(scanDialogView)
            .setCancelable(true)
            .setOnDismissListener {
                nfcAdapter!!.disableReaderMode(this@AddActivity)
                scanDialogView.findViewById<ImageView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
            }
            .create()
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
