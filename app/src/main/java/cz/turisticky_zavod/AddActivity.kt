package cz.turisticky_zavod

import android.annotation.SuppressLint
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
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import cz.turisticky_zavod.databinding.ActivityAddBinding
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

    private var nfcActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Thread {
            var i = 0
            while (nfcHelper.checkNfcAvailability(nfcAdapter) != NFCHelper.NfcAvailability.READY && i++ < 50) {
                Thread.sleep(100)
            }
            if (nfcHelper.checkNfcAvailability(nfcAdapter) == NFCHelper.NfcAvailability.READY) {
                runOnUiThread {
                    scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
                    scanDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
                    scanDialogView.findViewById<TextView>(R.id.textView_attachTag).text = getString(R.string.text_view_attach_tag)
                    startScanningNFC()
                }
            }
        }.start()
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: možná vymazat azimuty, dřeviny, TT a KPČ a možná je zpracovávat v cíli

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
                @Suppress("DEPRECATION")
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
                    binding.editTextRunnerId.setText(runner.startNumber.toString())
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

                    runnersQueue.add(QueueInfo(runner))
                }

                val refereeName = getSharedPreferences("TZ", MODE_PRIVATE).getString("referee", "")!!
                checkpointInfo = CheckpointInfo(currentCheckpoint.id!!, refereeName, System.currentTimeMillis())

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
            if (validateTextFields()) {
                Thread {
                    if (runnerViewModel.getByStartNumber(binding.editTextRunnerId.text!!.toString().toInt()) != null) {
                        runOnUiThread {
                            Toast.makeText(this@AddActivity, "Běžec s tímto číslem již existuje", Toast.LENGTH_SHORT).show()
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

        checkpointInfo.penaltySeconds = penalty
        checkpointInfo.timeDeparted = System.currentTimeMillis()
        checkpointInfo.disqualified = disqualified
        runner.checkpointInfo.add(checkpointInfo)

        return runner
    }

    private fun handleNextRunnerInQueue() {
        val delaySeconds = (System.currentTimeMillis() - runnersQueue[0].timeArrived!!) / 1000

        checkpointInfo.timeWaitedSeconds = delaySeconds.toInt()
        checkpointInfo.timeArrived = runnersQueue[0].timeArrived!!

        val runner = runnersQueue[0].runner

        binding.editTextRunnerId.animate().alpha(0f).setDuration(200).withEndAction {
            binding.editTextRunnerId.setText(runner.startNumber.toString())
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
        runOnUiThread {
            scanDialog.findViewById<TextView>(R.id.textView_attachTag)?.text = getString(R.string.text_view_dont_remove_tag)
        }

        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()

                if (runnersQueue.isEmpty()) {
                    val runner = Runner(
                        binding.editTextRunnerId.text!!.toString().toInt(),
                        binding.editTextRunnerName.text!!.trim().toString(),
                        binding.editTextRunnerTeam.text!!.trim().toString(),
                        System.currentTimeMillis()
                    )
                    checkpointInfo.timeDeparted = runner.startTime
                    runner.checkpointInfo.add(checkpointInfo)
                    nfcHelper.writeRunnerOnTag(ndef, runner)
                    runnerViewModel.insert(runner)
                    runOnUiThread {
                        scanSuccess(runner)
                    }
                    return
                }

                var runner = nfcHelper.readRunner(ndef)

                if (stage == WRITING && runner.startNumber != runnersQueue[0].runner.startNumber) {
                    runOnUiThread {
                        scanFail(null, "Čip se neshoduje s právě zpracovávaným běžcem")
                    }
                    return
                }
                if (runnerViewModel.getByStartNumber(runner.startNumber) != null) {
                    runOnUiThread {
                        scanFail(null, "Tento běžec již byl přidán")
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

    private fun scanSuccess(runner: Runner) {
        scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE

        val animation = scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcSuccess)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(700).withEndAction {
            if (stage == READING) {
                runnersQueue.add(QueueInfo(runner, System.currentTimeMillis()))

                Toast.makeText(this@AddActivity, "Běžec č. ${runner.startNumber} přidán do fronty", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@AddActivity, "Běžec č. ${runner.startNumber} úspěšně uložen", Toast.LENGTH_SHORT).show()

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
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Intent(Settings.Panel.ACTION_NFC)
                    } else {
                        Intent("android.settings.NFC_SETTINGS")
                    }
                    nfcActivityResultLauncher.launch(intent)
                }
            scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
            scanDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.VISIBLE
            scanDialogView.findViewById<TextView>(R.id.textView_attachTag).text = getString(R.string.text_view_nfc_off)
        } else {
            scanDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
            scanDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
            scanDialogView.findViewById<TextView>(R.id.textView_attachTag).text = getString(R.string.text_view_attach_tag)
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

    private fun validateTextFields(): Boolean {
        var validated = true

        val id = binding.editTextRunnerId.text!!
        val name = binding.editTextRunnerName.text!!.trim()
        val team = binding.editTextRunnerTeam.text!!.trim()

        if (id.isEmpty()) {
            validated = false
            binding.editTextRunnerId.error = "Startovní číslo je povinné"
        }

        if (name.isEmpty()) {
            validated = false
            binding.editTextRunnerName.error = "Jméno je povinné"
        } else if (!name.contains(' ')) {
            validated = false
            binding.editTextRunnerName.error = "Text musí obsahovat jméno i příjmení"
        } else if (!name.all { ch -> ch.isLetter() || ch == ' ' }) {
            validated = false
            binding.editTextRunnerName.error = "Jméno obsahuje nepovolené znaky"
        }

        if (team.isEmpty()) {
            validated = false
            binding.editTextRunnerTeam.error = "Oddíl je povinný"
        } else if (!team.all { ch -> ch.isLetter() || ch == ' ' }) {
            validated = false
            binding.editTextRunnerTeam.error = "Název oddílu obsahuje nepovolené znaky"
        }

        return validated
    }

    private fun handleBackButtonPressed() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Máte neuložené změny")
            .setMessage("Opravdu chcete jít zpět? Provedené změny nebudou uloženy")
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
