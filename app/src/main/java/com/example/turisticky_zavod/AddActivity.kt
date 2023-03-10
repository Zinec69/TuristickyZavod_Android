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
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.turisticky_zavod.databinding.ActivityAddBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class QueueInfo(
    val runner: Runner,
    val timeArrived: Long? = null
)

class AddActivity : AppCompatActivity(), ReaderCallback {

    private val READING = 0
    private val WRITING = 1

    private var stage = 1

    private lateinit var binding: ActivityAddBinding

    private var nfcAdapter: NfcAdapter? = null
    private var nfcHelper = NFCHelper()
    
    private lateinit var scanDialogView: View
    private lateinit var scanDialog: AlertDialog

    private var runnersQueue = ArrayList<QueueInfo>()

    private val runnerViewModel: RunnerViewModel by viewModels()

    private lateinit var currentCheckpoint: Checkpoint
    private lateinit var checkpointInfo: CheckpointInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: mo??n?? p??idat mo??nost ??ek??n?? pro v??echny
        // TODO: mo??n?? vymazat azimuty, d??eviny, TT a KP?? a mo??n?? je zpracov??vat v c??li

        binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarAdd)

        nfcAdapter = getDefaultAdapter(this@AddActivity)

        onBackPressedDispatcher.addCallback(this@AddActivity) { handleBackButtonPressed() }
        binding.toolbarAdd.setNavigationOnClickListener { handleBackButtonPressed() }
        binding.buttonCancel.setOnClickListener { handleBackButtonPressed() }

        val checkpointJob = lifecycleScope.launch(Dispatchers.IO) {
            currentCheckpoint = TZDatabase.getInstance(this@AddActivity).checkpointDao().getActive()!!
        }
        checkpointJob.invokeOnCompletion {
            runOnUiThread {
                val runner =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra("runner")
                    else
                        intent.getParcelableExtra("runner", Runner::class.java)

                if (runner == null) {
                    binding.editTextRunnerId.isEnabled = true
                    binding.editTextRunnerName.isEnabled = true
                    binding.editTextRunnerTeam.isEnabled = true
                    binding.cardViewDisqualified.visibility = View.GONE
                    binding.cardViewPenaltyMinutes.visibility = View.GONE
                    binding.buttonAddToQueue.visibility = View.GONE
                    binding.editTextRunnerId.setTextColor(getColor(R.color.edit_text_default))
                    binding.editTextRunnerName.setTextColor(getColor(R.color.edit_text_default))
                    binding.editTextRunnerTeam.setTextColor(getColor(R.color.edit_text_default))
                    showKeyboard(binding.editTextRunnerId)
                } else {
                    binding.editTextRunnerId.setText(runner.runnerId.toString())
                    binding.editTextRunnerName.setText(runner.name)
                    binding.editTextRunnerTeam.setText(runner.team)
                    when (currentCheckpoint.id) {
                        4, 7, 9 -> {
                            binding.cardViewDisqualified.isEnabled = false
                            binding.switchDisqualified.isEnabled = false
                            binding.textViewDisqualified.isEnabled = false
                        }
                        5 -> {
                            binding.buttonAddToQueue.visibility = View.GONE
                        }
                        2, 3, 6 -> {
                            binding.cardViewPenaltyMinutes.visibility = View.GONE
                        }
                    }
                    binding.switchDisqualified.isChecked = runner.disqualified

                    if (currentCheckpoint.id != 1) {
                        val refereeName = getSharedPreferences("TZ", MODE_PRIVATE).getString("referee", "")!!
                        checkpointInfo = CheckpointInfo(currentCheckpoint.id!!, refereeName, System.currentTimeMillis())
                    }

                    runnersQueue.add(QueueInfo(runner))
                }

                val colorsLayout = arrayOf(
                    ColorDrawable(getColor(R.color.background_layout_normal)),
                    ColorDrawable(getColor(R.color.background_layout_disqualified))
                )
                val colorsToolbar = arrayOf(
                    ColorDrawable(getColor(R.color.background_layout_normal)),
                    ColorDrawable(getColor(R.color.background_layout_disqualified))
                )
                if (runner?.disqualified == true) {
                    binding.root.background =
                        ColorDrawable(getColor(R.color.background_layout_disqualified))
                    binding.toolbarAdd.background =
                        ColorDrawable(getColor(R.color.background_layout_disqualified))
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
        }
        checkpointJob.start()

        binding.buttonSave.setOnClickListener {
            val id = binding.editTextRunnerId.text!!
            if (id.isEmpty() ||
                binding.editTextRunnerName.text!!.isEmpty() ||
                binding.editTextRunnerTeam.text!!.isEmpty()) {
                Toast.makeText(this@AddActivity, "V??echna pole jsou povinn??", Toast.LENGTH_SHORT).show()
            } else {
                Thread {
                    if (runnerViewModel.getByID(id.toString().toInt()) != null) {
                        runOnUiThread {
                            Toast.makeText(this@AddActivity, "B????ec s t??mto ????slem ji?? existuje", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            loseFocus(it)
                            stage = WRITING
                            scanForRunner()
                        }
                    }
                }.start()
            }
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

        binding.buttonAddToQueue.setOnClickListener {
            stage = READING
            scanForRunner()
        }

        scanDialogView = layoutInflater.inflate(R.layout.dialog_add, null)
        initScanDialog()
    }

    private fun getUpdatedRunner(): Runner {
        val disqualified = binding.switchDisqualified.isChecked

        val penaltyMinutes = binding.numberPickerMinute.value
        val penaltySeconds = binding.numberPickerSecond.value
        val penalty = penaltyMinutes * 60 + penaltySeconds * 5

        val runner = runnersQueue[0].runner
        runner.disqualified = disqualified

        if (currentCheckpoint.id != 1) {
            checkpointInfo.penaltySeconds = penalty
            checkpointInfo.timeDeparted = System.currentTimeMillis()
            runner.checkpointInfo.add(checkpointInfo)
        }

        return runner
    }

    private fun handleNextRunnerInQueue() {
        val delaySeconds = (System.currentTimeMillis() - runnersQueue[0].timeArrived!!) / 1000

        checkpointInfo.timeWaitedSeconds = delaySeconds.toInt()
        checkpointInfo.timeArrived = runnersQueue[0].timeArrived!!

        val runner = runnersQueue[0].runner

        binding.editTextRunnerId.animate().alpha(0f).setDuration(200).withEndAction {
            binding.editTextRunnerId.setText(runner.runnerId.toString())
            binding.editTextRunnerId.animate().alpha(1f).setDuration(200).start()
        }.start()
        binding.editTextRunnerName.animate().alpha(0f).setDuration(200).withEndAction {
            binding.editTextRunnerName.setText(runner.name)
            binding.editTextRunnerName.animate().alpha(1f).setDuration(200).start()
        }.start()
        binding.editTextRunnerTeam.animate().alpha(0f).setDuration(200).withEndAction {
            binding.editTextRunnerTeam.setText(runner.team)
            binding.editTextRunnerTeam.animate().alpha(1f).setDuration(200).start()
        }.start()
        if (binding.numberPickerMinute.value > 0 || binding.numberPickerSecond.value > 0) {
            binding.numberPickerMinute.animate().alpha(0f).setDuration(200).withEndAction {
                binding.numberPickerMinute.value = 0
                binding.numberPickerMinute.animate().alpha(1f).setDuration(200).start()
            }.start()
            binding.numberPickerSecond.animate().alpha(0f).setDuration(200).withEndAction {
                binding.numberPickerSecond.value = 0
                binding.numberPickerSecond.animate().alpha(1f).setDuration(200).start()
            }.start()
        }
        binding.switchDisqualified.isChecked = runner.disqualified
        binding.linearLayoutRunnerDelay.visibility = View.VISIBLE
        binding.textViewRunnerDelayVar.text = SimpleDateFormat("mm:ss", Locale("cze")).format(delaySeconds * 1000)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()

                if (runnersQueue.isEmpty()) {
                    val runner = Runner(
                        binding.editTextRunnerId.text!!.toString().toInt(),
                        binding.editTextRunnerName.text!!.dropLastWhile { c -> c.isWhitespace() }.toString(),
                        binding.editTextRunnerTeam.text!!.dropLastWhile { c -> c.isWhitespace() }.toString(),
                        System.currentTimeMillis()
                    )
                    nfcHelper.writeRunnerOnTag(ndef, runner)
                    runnerViewModel.insert(runner)
                    runOnUiThread {
                        scanSuccess(runner)
                    }
                    return
                }

                var runner = nfcHelper.readRunner(ndef)

                if (stage == WRITING && runner.runnerId != runnersQueue[0].runner.runnerId) {
                    runOnUiThread {
                        scanFail(null, "??ip se neshoduje s pr??v?? zpracov??van??m b????cem")
                    }
                    return
                }
                if (runnerViewModel.getByID(runner.runnerId) != null) {
                    runOnUiThread {
                        scanFail(null, "Tento b????ec ji?? byl p??id??n")
                    }
                    return
                }
                if (stage == WRITING) {
                    runner = getUpdatedRunner()

                    nfcHelper.writeRunnerOnTag(ndef, runner)
                    runnerViewModel.insert(runner)
                    runnersQueue.removeAt(0)
                }

                runOnUiThread {
                    scanSuccess(runner)
                }
            } catch (e: NFCException) {
                runOnUiThread {
                    scanFail(e, e.message!!)
                }
            } catch (e: TagLostException) {
                runOnUiThread {
                    scanFail(e, "??ip byl odebr??n p????li?? rychle")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    scanFail(e, "Chyba p??i komunikaci s ??ipem")
                }
            } finally {
                runOnUiThread {
                    stopScanningNFC()
                }
                try {
                    ndef.close()
                } catch (e: IOException) {
                    runOnUiThread { scanFail(e, "Chyba p??i komunikaci s ??ipem") }
                }
            }
        } else {
            runOnUiThread { scanFail(null, "Nepodporovan?? typ ??ipu") }
        }
    }

    private fun scanSuccess(runner: Runner) {
        scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE

        val animation = scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcSuccess)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(700).withEndAction {
            if (stage == READING) {
                runnersQueue.add(QueueInfo(runner, System.currentTimeMillis()))

                Toast.makeText(this@AddActivity, "B????ec ??. ${runner.runnerId} p??id??n do fronty", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@AddActivity, "B????ec ??. ${runner.runnerId} ??sp????n?? ulo??en", Toast.LENGTH_SHORT).show()

                if (runnersQueue.isEmpty()) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    handleNextRunnerInQueue()
                }
            }

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

    private fun scanForRunner() {
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
                stopScanningNFC()
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
        MaterialAlertDialogBuilder(this)
            .setTitle("M??te neulo??en?? zm??ny")
            .setMessage("Opravdu chcete j??t zp??t? Proveden?? zm??ny nebudou ulo??eny")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface?, _: Int ->
                if (runnersQueue.isNotEmpty())
                    runnersQueue.removeAt(0)

                if (runnersQueue.isEmpty()) {
                    finish()
                } else {
                    handleNextRunnerInQueue()
                }
            }
            .create()
            .show()
    }

    private fun loseFocus(view: View) {
        for (child in binding.linearLayoutScrollViewContent.children)
            child.clearFocus()
        hideKeyboard(view)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showKeyboard(view: View) {
        if (view.requestFocus()) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
