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
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.turisticky_zavod.NFCHelper.NfcAvailability
import com.example.turisticky_zavod.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException


class MainActivity : AppCompatActivity(), ReaderCallback {

    private lateinit var binding: ActivityMainBinding

    private var runnersList = ArrayList<Runner>()
    private lateinit var rvAdapter: RvAdapter

    private val runnerViewModel: RunnerViewModel by viewModels()

    private var nfcAdapter: NfcAdapter? = null
    private var nfcHelper = NFCHelper()

    private lateinit var newRunnerDialogView: View

    private lateinit var newRunnerDialog: AlertDialog
    private lateinit var cancelDialog: AlertDialog

    private var activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val name = result.data?.getCharSequenceExtra("name")
            val checkpoint = result.data?.getCharSequenceExtra("checkpoint")
            name?.let { binding.textViewRefereeNameVar.text = it }
            binding.textViewCheckpointNameVar.text = checkpoint
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        nfcAdapter = getDefaultAdapter(this@MainActivity)
        if (nfcAdapter == null) {
            Toast.makeText(this@MainActivity, "Tato aplikace vyžaduje telefon s NFC", Toast.LENGTH_LONG).show()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarMain)

        Thread {
            val checkpoint = TZDatabase.getInstance(this@MainActivity).checkpointDao().getActive()
            if (checkpoint == null) {
                runOnUiThread {
                    activityResultLauncher.launch(Intent(this@MainActivity, CheckpointActivity::class.java))
                }
            } else {
                val referee = TZDatabase.getInstance(this@MainActivity).refereeDao().get()
                runOnUiThread {
                    binding.textViewCheckpointNameVar.text = checkpoint.name
                    binding.textViewRefereeNameVar.text = referee?.name
                }
            }
        }.start()

        binding.toolbarMain.setNavigationOnClickListener {
            // TODO: Navigation panel
            Toast.makeText(this@MainActivity, "Not yet implemented", Toast.LENGTH_SHORT).show()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
        rvAdapter =
            RvAdapter(runnersList, object : RvAdapter.OptionsMenuLongClickListener {
                    override fun onOptionsMenuLongClicked(position: Int): Boolean {
                        handleRecyclerViewItemLongClicked(position)
                        return true
                    }
                })
        binding.recyclerView.adapter = rvAdapter

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
                    rvAdapter.notifyItemRangeChanged(0, runnersList.size)

                    if (runnersList.size == 1)
                        binding.textViewNoData.visibility = View.GONE
                } else {
                    for (runner in runners.takeLast(diff))
                        runnersList.add(0, runner)

                    rvAdapter.notifyItemRangeInserted(0, diff)
                    rvAdapter.notifyItemRangeChanged(0, runnersList.size)

                    if (runnersList.size > 1)
                        binding.textViewNoData.visibility = View.GONE
                }
            }
        }

        binding.fabAdd.setOnClickListener {
            if (nfcAdapter != null) {
                handleScanningNewRunner()
            } else {
                Toast.makeText(this@MainActivity, "Tato aplikace vyžaduje telefon s NFC", Toast.LENGTH_LONG).show()
            }
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

                val runner = nfcHelper.readRunner(ndef)

                if (runnersList.find { p -> p.runnerId == runner.runnerId } != null) {
                    runOnUiThread {
                        scanFail(null, "Tento člověk již byl přidán")
                    }
                    return
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
        newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE

        val animation = newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcSuccess)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(700).withEndAction {
            val intent = Intent(this@MainActivity, AddActivity::class.java)
                .putExtra("runner", runner)

//            activityResultLauncher.launch(intent)
            startActivity(intent)

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
                    startActivity(Intent("android.settings.NFC_SETTINGS"))
                    newRunnerDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
                    newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
                    startScanningNFC()
                }
            newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
            newRunnerDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.VISIBLE
        } else {
            newRunnerDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
            newRunnerDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
            startScanningNFC()
        }

        newRunnerDialog.show()
    }

    @SuppressLint("RestrictedApi", "DiscouragedPrivateApi")
    private fun handleRecyclerViewItemLongClicked(position: Int) {
        val popupMenu = PopupMenu(this@MainActivity, binding.recyclerView[position].findViewById(R.id.textView_runnerName))
        popupMenu.inflate(R.menu.menu_list_item)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menuItem_listItemDelete -> {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Smazat položku")
                        .setMessage("Opravdu chcete smazat tuto položku? Tato akce nelze vrátit")
                        .setCancelable(false)
                        .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
                        .setPositiveButton("Ano") { _: DialogInterface?, _: Int -> deleteRunner(position) }
                        .create()
                        .show()

                    true
                }
                R.id.menuItem_listItemEdit -> {
                    Toast.makeText(this@MainActivity , "Position: $position\nSize: ${runnersList.size}" , Toast.LENGTH_SHORT).show()

                    true
                }
                else -> false
            }
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

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (menu is MenuBuilder) menu.setOptionalIconsVisible(true)
        menuInflater.inflate(R.menu.menu_main, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuItem_sendDataToPc -> {
                // TODO: Communicate with desktop app
                Toast.makeText(this@MainActivity, "Not yet implemented", Toast.LENGTH_SHORT).show()

                true
            }
            R.id.menuItem_deleteAllData -> {
                if (runnersList.isNotEmpty()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Vymazat všechna data")
                        .setMessage("Opravdu chcete vymazat všechna data? Změny nelze vrátit")
                        .setCancelable(false)
                        .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
                        .setPositiveButton("Ano") { _: DialogInterface?, _: Int -> deleteAllRunners() }
                        .create()
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "Žádná data ke smazání", Toast.LENGTH_SHORT).show()
                }

                true
            }
            R.id.menuItem_reset -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Resetovat")
                    .setMessage("Opravdu chcete aplikaci resetovat? Tato akce nelze vrátit")
                    .setCancelable(false)
                    .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
                    .setPositiveButton("Ano") { _: DialogInterface?, _: Int -> reset() }
                    .create()
                    .show()

                true
            }
            else -> super.onOptionsItemSelected(item)
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
            rvAdapter.notifyItemRangeChanged(position, runnersList.size - position)

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

    private fun reset() {
        deleteAllRunners()
        Thread {
            TZDatabase.getInstance(this@MainActivity).refereeDao().reset()
            TZDatabase.getInstance(this@MainActivity).checkpointDao().reset()
        }.start()
        activityResultLauncher.launch(Intent(this@MainActivity, CheckpointActivity::class.java))
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
}
