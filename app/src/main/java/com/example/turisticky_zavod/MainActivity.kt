package com.example.turisticky_zavod

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.turisticky_zavod.NFCHelper.NfcAvailability
import com.example.turisticky_zavod.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.text.Normalizer


class MainActivity : AppCompatActivity(), ReaderCallback {

    private lateinit var binding: ActivityMainBinding

    private var runnersList = ArrayList<Runner>()
    private lateinit var rvAdapter: RvAdapter

    private val runnerViewModel: RunnerViewModel by viewModels()

    private var activeCheckpoint: Checkpoint? = null

    private var nfcAdapter: NfcAdapter? = null
    private var nfcHelper = NFCHelper()

    private lateinit var newRunnerDialogView: View

    private lateinit var newRunnerDialog: AlertDialog
    private lateinit var cancelDialog: AlertDialog

    private var checkpointActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val name = result.data?.getCharSequenceExtra("name")
            Thread {
                activeCheckpoint = TZDatabase.getInstance(this@MainActivity).checkpointDao().getActive()
            }.start()
            name?.let { binding.textViewRefereeNameVar.text = it }
            binding.textViewCheckpointNameVar.text = result.data?.getCharSequenceExtra("checkpoint")
        }
    }

    private var nfcActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Thread {
            var i = 0
            while (nfcHelper.checkNfcAvailability(nfcAdapter) != NfcAvailability.READY && i++ < 50) {
                Thread.sleep(100)
            }
            if (nfcHelper.checkNfcAvailability(nfcAdapter) == NfcAvailability.READY) {
                runOnUiThread {
                    newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
                    newRunnerDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
                    newRunnerDialogView.findViewById<TextView>(R.id.textView_attachTag).text = getString(R.string.text_view_attach_tag)
                    startScanningNFC()
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarMain)

        nfcAdapter = getDefaultAdapter(this@MainActivity)
        if (nfcAdapter == null) {
            Toast.makeText(this@MainActivity, "Tato aplikace vyžaduje telefon s NFC", Toast.LENGTH_LONG).show()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
        rvAdapter = RvAdapter(runnersList, object : RvAdapter.OptionsMenuLongClickListener {
                        override fun onOptionsMenuLongClicked(position: Int): Boolean {
                            handleRecyclerViewItemLongClicked(position)
                            return true
                        }
                    })
        binding.recyclerView.adapter = rvAdapter

        val sp = getSharedPreferences("TZ", MODE_PRIVATE)
        if (!sp.contains("list_mode")) {
            sp.edit().putInt("list_mode", rvAdapter.BASIC).apply()
        } else {
            rvAdapter.state = sp.getInt("list_mode", rvAdapter.BASIC)
        }

        Thread {
            activeCheckpoint = TZDatabase.getInstance(this@MainActivity).checkpointDao().getActive()
            runOnUiThread {
                if (activeCheckpoint == null) {
                    checkpointActivityResultLauncher.launch(Intent(this@MainActivity, CheckpointActivity::class.java))
                } else {
                    val referee = sp.getString("referee", " - ")
                    binding.textViewCheckpointNameVar.text = activeCheckpoint!!.name
                    binding.textViewRefereeNameVar.text = referee
                }
            }
        }.start()

        binding.toolbarMain.setNavigationOnClickListener { binding.drawerLayout.open() }
        binding.navigationView.setCheckedItem(if (rvAdapter.state == rvAdapter.BASIC) R.id.menuItem_viewBasic else R.id.menuItem_viewDetailed)
        binding.navigationView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.close()

            when (item.itemId) {
                R.id.menuItem_viewBasic -> {
                    item.isChecked = true
                    if (rvAdapter.state != rvAdapter.BASIC) {
                        sp.edit().putInt("list_mode", rvAdapter.BASIC).apply()
                        rvAdapter.state = rvAdapter.BASIC
                        rvAdapter.notifyItemRangeChanged(0, runnersList.size)
                    }
                }
                R.id.menuItem_viewDetailed -> {
                    item.isChecked = true
                    if (rvAdapter.state != rvAdapter.DETAILED) {
                        sp.edit().putInt("list_mode", rvAdapter.DETAILED).apply()
                        rvAdapter.state = rvAdapter.DETAILED
                        rvAdapter.notifyItemRangeChanged(0, runnersList.size)
                    }
                }
                R.id.menuItem_actionExportData -> {
                    exportRunnersList()
                }
                R.id.menuItem_actionReset -> {
                    showResetDialog()
                }
                R.id.menuItem_actionDeleteRunners -> {
                    if (runnersList.isNotEmpty()) {
                        showDeleteAllRunnersDialog()
                    } else {
                        Toast.makeText(this@MainActivity, "Žádná data ke smazání", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            true
        }

        runnerViewModel.runners.observe(this@MainActivity) { runners ->
            val diff = runners.size - runnersList.size

            if (diff > 0) {
                if (runnersList.isEmpty()) {
                    for (runner in runners)
                        runnersList.add(0, runner)

                    rvAdapter.notifyItemRangeInserted(0, runnersList.size)
                    binding.textViewNoData.visibility = View.GONE
                } else if (diff == 1) {
                    runnersList.add(0, runners.last())

                    rvAdapter.notifyItemInserted(0)
                    binding.recyclerView.scrollToPosition(0)

                    if (runnersList.size == 1)
                        binding.textViewNoData.visibility = View.GONE
                } else {
                    for (runner in runners.takeLast(diff))
                        runnersList.add(0, runner)

                    rvAdapter.notifyItemRangeInserted(0, diff)
                    binding.recyclerView.scrollToPosition(0)

                    if (runnersList.size > 1)
                        binding.textViewNoData.visibility = View.GONE
                }
            }
        }

        binding.fabAdd.setOnClickListener {
            if (nfcAdapter != null) {
                Thread {
                    if (TZDatabase.getInstance(this@MainActivity).checkpointDao().getActive()!!.id == 1) {
                        runOnUiThread {
                            if (binding.fabStart.visibility == View.GONE) {
                                showStartFinishButtons()
                            } else {
                                hideStartFinishButtons()
                            }
                        }
                    } else {
                        runOnUiThread {
                            handleScanningNewRunner()
                        }
                    }
                }.start()
            } else {
                Toast.makeText(this@MainActivity, "Tato aplikace vyžaduje telefon s NFC", Toast.LENGTH_LONG).show()
            }
        }
        binding.fabStart.setOnClickListener {
            hideStartFinishButtons()
            val intent = Intent(this@MainActivity, AddActivity::class.java)
            startActivity(intent)
        }
        binding.fabFinish.setOnClickListener {
            handleScanningNewRunner()
            hideStartFinishButtons()
        }

        newRunnerDialogView = layoutInflater.inflate(R.layout.dialog_add, null)
        initNewRunnerDialog()
        initCancelDialog()
    }

    override fun onTagDiscovered(tag: Tag?) {
        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()

                runOnUiThread {
                    newRunnerDialog.findViewById<TextView>(R.id.textView_attachTag)?.text = getString(R.string.text_view_dont_remove_tag)
                }

                var runner = nfcHelper.readRunner(ndef)

                if (activeCheckpoint!!.id == 1) {
                    for (i in 0 until runnersList.size) {
                        if (runnersList[i].startNumber == runner.startNumber) {
                            if (runnersList[i].finishTime != null) {
                                runOnUiThread {
                                    scanFail(null, "Tento běžec již byl zpracován")
                                }
                                return
                            }
                            runner = runnersList[i]
                            runner.finishTime = System.currentTimeMillis()

                            runnersList.removeAt(i)
                            runnersList.add(0, runner)

                            nfcHelper.writeRunnerOnTag(ndef, runner)

                            runnerViewModel.update(runner)

                            runOnUiThread {
                                rvAdapter.notifyItemRemoved(i)
                                rvAdapter.notifyItemInserted(0)
                                binding.recyclerView.scrollToPosition(0)
                                scanSuccess(runner, false)
                            }
                            return
                        }
                    }
                    runOnUiThread {
                        scanFail(null, "Tento běžec nemá záznam o startu")
                    }
                    return
                }

                if (runnersList.find { r -> r.startNumber == runner.startNumber } != null) {
                    runOnUiThread {
                        scanFail(null, "Tento běžec již byl přidán")
                    }
                    return
                }

                runOnUiThread {
                    scanSuccess(runner, true)
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

    private fun scanSuccess(runner: Runner, startActivity: Boolean) {
        newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE

        val animation = newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcSuccess)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(700).withEndAction {
            if (startActivity) {
                val intent = Intent(this@MainActivity, AddActivity::class.java)
                    .putExtra("runner", runner)

                startActivity(intent)
            }

            newRunnerDialog.dismiss()
            animation.visibility = View.GONE
        }.start()
    }

    private fun scanFail(e: Exception?, mess: String) {
        newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE

        val animation = newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcFail)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(2000).withEndAction {
            animation.visibility = View.GONE
            newRunnerDialog.dismiss()
        }.start()

        Toast.makeText(this@MainActivity, mess, Toast.LENGTH_LONG).show()
        e?.stackTraceToString()?.let { Log.d("NFC", it) }
    }

    private fun handleScanningNewRunner() {
        if (nfcHelper.checkNfcAvailability(nfcAdapter) == NfcAvailability.OFF) {
            newRunnerDialogView.findViewById<Button>(R.id.button_turnOnNfc)
                .setOnClickListener {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Intent(Settings.Panel.ACTION_NFC)
                    } else {
                        Intent("android.settings.NFC_SETTINGS")
                    }
                    nfcActivityResultLauncher.launch(intent)
                }
            newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
            newRunnerDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.VISIBLE
            newRunnerDialogView.findViewById<TextView>(R.id.textView_attachTag).text = getString(R.string.text_view_nfc_off)
        } else {
            newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
            newRunnerDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
            newRunnerDialogView.findViewById<TextView>(R.id.textView_attachTag).text = getString(R.string.text_view_attach_tag)
            startScanningNFC()
        }

        newRunnerDialog.show()
    }

    @SuppressLint("RestrictedApi", "DiscouragedPrivateApi")
    private fun handleRecyclerViewItemLongClicked(position: Int) {
        val popupMenu = PopupMenu(this@MainActivity, binding.recyclerView[position].findViewById(R.id.textView_runnerName_rv))
        popupMenu.inflate(R.menu.menu_list_item)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menuItem_listItemDelete -> { showDeleteRunnerDialog(position) }
            }
            true
        }
        try {
            val popup = PopupMenu::class.java.getDeclaredField("mPopup")
            popup.isAccessible = true
            val menu = popup.get(popupMenu)
            menu.javaClass
                .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(menu, true)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
        } finally {
            popupMenu.show()
        }
    }

    private fun startScanningNFC() {
        val options = Bundle()
        options.putInt(EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        nfcAdapter!!.enableReaderMode(this@MainActivity, this, FLAG_READER_NFC_A, options)
    }

    private fun stopScanningNFC() {
        nfcAdapter!!.disableReaderMode(this@MainActivity)
    }

    private fun deleteRunner(position: Int) {
        try {
            runnerViewModel.delete(runnersList[position])

            runnersList.remove(runnersList[position])

            rvAdapter.notifyItemRemoved(position)

            if (runnersList.size == 0)
                binding.textViewNoData.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.d("DB", e.stackTraceToString())
            Toast.makeText(this@MainActivity, "Chyba při mazání záznamu", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteAllRunners() {
        try {
            runnerViewModel.deleteAll()

            val size = runnersList.size
            runnersList.clear()

            rvAdapter.notifyItemRangeRemoved(0, size)
            binding.textViewNoData.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.d("DB", e.stackTraceToString())
            Toast.makeText(this@MainActivity, "Chyba při mazání záznamů", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportRunnersList() {
        Thread {
            val json = runnerViewModel.exportToJson()

            val fileName = "${
                activeCheckpoint!!.name
                    .replace(' ', '-')
                    .replace('/', '-')
                    .removeNonSpacingMarks()
            }_${System.currentTimeMillis()}.json"

            try {
                val file = File(cacheDir.path, fileName)
                file.createNewFile()

                if (file.exists()) {
                    file.writeText(json, Charset.forName("Windows-1250"))
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${BuildConfig.APPLICATION_ID}.provider",
                        file
                    )
                    val intent = Intent.createChooser(Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        putExtra("filename", fileName)
                    }, null)

                    startActivity(intent)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Nepodařilo se vytvořit soubor", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Chyba při exportu dat", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun reset() {
        cacheDir.deleteRecursively()
        deleteAllRunners()
        getSharedPreferences("TZ", MODE_PRIVATE).edit().remove("referee").apply()
        lifecycleScope.launch(Dispatchers.IO) {
            TZDatabase.getInstance(this@MainActivity).checkpointDao().reset()
        }
        checkpointActivityResultLauncher.launch(Intent(this@MainActivity, CheckpointActivity::class.java))
    }

    private fun showDeleteRunnerDialog(position: Int) {
        MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Smazat položku")
            .setMessage("Opravdu chcete smazat tuto položku? Tato akce nelze vrátit")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface?, _: Int -> deleteRunner(position) }
            .create()
            .show()
    }

    private fun showDeleteAllRunnersDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Vymazat všechna data")
            .setMessage("Opravdu chcete vymazat všechna data? Změny nelze vrátit")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface?, _: Int -> deleteAllRunners() }
            .create()
            .show()
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Resetovat")
            .setMessage("Opravdu chcete aplikaci resetovat? Tato akce nelze vrátit")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface?, _: Int -> reset() }
            .create()
            .show()
    }

    private fun initNewRunnerDialog() {
        newRunnerDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setView(newRunnerDialogView)
            .setCancelable(true)
            .setOnDismissListener {
                nfcAdapter!!.disableReaderMode(this@MainActivity)
                newRunnerDialogView.findViewById<ImageView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
            }
            .create()
    }

    private fun initCancelDialog() {
        cancelDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Zrušit")
            .setMessage("Opravdu chcete odejít? Provedené změny nebudou uloženy")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface, _: Int ->
                newRunnerDialogView.findViewById<TextInputEditText>(R.id.editText_runnerId).setText("")
                newRunnerDialogView.findViewById<TextInputEditText>(R.id.editText_runnerName).setText("")
                newRunnerDialogView.findViewById<TextInputEditText>(R.id.editText_runnerTeam).setText("")
                newRunnerDialog.dismiss()
            }
            .create()
    }

    private fun showStartFinishButtons() {
        binding.fabStart.visibility = View.VISIBLE
        binding.fabStart.alpha = 0f
        binding.fabStart.translationY = binding.fabStart.height.toFloat()
        binding.fabStart.animate()
            .setDuration(200)
            .translationY(0f)
            .alpha(1f)
            .start()
        binding.fabFinish.visibility = View.VISIBLE
        binding.fabFinish.alpha = 0f
        binding.fabFinish.translationY = binding.fabFinish.height.toFloat()
        binding.fabFinish.animate()
            .setDuration(200)
            .translationY(0f)
            .alpha(1f)
            .start()
    }

    private fun hideStartFinishButtons() {
        binding.fabStart.alpha = 1f
        binding.fabStart.translationY = 0f
        binding.fabStart.animate()
            .setDuration(200)
            .translationY(binding.fabStart.height.toFloat())
            .alpha(0f)
            .withEndAction { binding.fabStart.visibility = View.GONE }
            .start()
        binding.fabFinish.alpha = 1f
        binding.fabFinish.translationY = 0f
        binding.fabFinish.animate()
            .setDuration(200)
            .translationY(binding.fabFinish.height.toFloat())
            .alpha(0f)
            .withEndAction { binding.fabFinish.visibility = View.GONE }
            .start()
    }
}

fun String.removeNonSpacingMarks() =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
