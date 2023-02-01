package com.example.turisticky_zavod

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.airbnb.lottie.LottieAnimationView
import com.example.turisticky_zavod.NFCHelper.NfcAvailability
import com.example.turisticky_zavod.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), ReaderCallback {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var peopleList = ArrayList<Person>()
    private lateinit var rvAdapter: RvAdapter

    private var nfcAdapter: NfcAdapter? = null
    private var nfcHelper = NFCHelper()

    private lateinit var db: TZDatabase

    private lateinit var newPersonDialogView: View

    private lateinit var newPersonDialog: AlertDialog
    private lateinit var cancelDialog: AlertDialog

    private var activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            Thread {
                val person = db.personDao().getLast()
                runOnUiThread {
                    peopleList.add(0, person)
                        rvAdapter.notifyItemInserted(0)
                        rvAdapter.notifyItemRangeChanged(0, peopleList.size)
                    if (peopleList.size == 1)
                        binding.textViewNoData.visibility = View.GONE
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { Toast.makeText(this@MainActivity, "lmao", Toast.LENGTH_SHORT).show() }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        rvAdapter = RvAdapter(peopleList, object: RvAdapter.OptionsMenuLongClickListener {
            override fun onOptionsMenuLongClicked(position: Int): Boolean {
                handleRecyclerViewItemLongClicked(position)
                return true
            }
        })
        binding.recyclerView.adapter = rvAdapter

        nfcAdapter = getDefaultAdapter(this@MainActivity)
        if (nfcAdapter == null) {
            Toast.makeText(this@MainActivity, "Váš telefon nemá NFC", Toast.LENGTH_LONG).show()
            finish()
        }

        db = Room.databaseBuilder(this@MainActivity, TZDatabase::class.java, "tz.db")
            .fallbackToDestructiveMigration()
            .addCallback( object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    populateCheckpoints()
                }
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    populateCheckpoints()
                }
            })
            .build()

        binding.fabAdd.setOnClickListener {
            handleAddingNewPerson()
        }

        newPersonDialogView = layoutInflater.inflate(R.layout.dialog_add, null)
        initNewPersonDialog()
        initCancelDialog()

        loadPeople()
    }

    override fun onTagDiscovered(tag: Tag?) {
        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()

                val person = nfcHelper.readPerson(ndef)

                for (p in peopleList) {
                    if (p.runnerId == person.runnerId) {
                        runOnUiThread {
                            scanFail(null, "Tento člověk již byl přidán")
                        }
                        return
                    }
                }

                runOnUiThread {
                    scanSuccess(person)

//                    try {
//                        newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerId).setText(person.runnerId.toString())
//                        newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerName).setText(person.name)
//                        newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerTeam).setText(person.team)
//                    } catch (e: Exception) {
//                        Toast.makeText(this@MainActivity, "Chyba při vkládání informací do textových polí", Toast.LENGTH_LONG).show()
//                        Log.d("NFC", e.stackTraceToString())
//                    }
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
        newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
        val animation = newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcSuccess)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(700).withEndAction {
            val intent = Intent(this@MainActivity, AddActivity::class.java)
            intent.putExtra("id", person.runnerId.toString())
            intent.putExtra("name", person.name)
            intent.putExtra("team", person.team)
            activityResultLauncher.launch(intent)
//            startActivity(intent)
            newPersonDialog.dismiss()
            animation.visibility = View.GONE
        }.start()
    }

    private fun scanFail(e: Exception?, mess: String) {
        newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
        val animation = newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcFail)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(2000).withEndAction {
            animation.visibility = View.GONE
            newPersonDialog.dismiss()
        }.start()

        Toast.makeText(this@MainActivity, mess, Toast.LENGTH_LONG).show()
        e?.stackTraceToString()?.let { Log.d("NFC", it) }
    }

    private fun handleAddingNewPerson() {
        if (nfcHelper.checkNfcAvailability(nfcAdapter) == NfcAvailability.OFF) {
            newPersonDialogView.findViewById<Button>(R.id.button_turnOnNfc)
                .setOnClickListener {
                    startActivity(Intent("android.settings.NFC_SETTINGS"))
                    newPersonDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
                    newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
                    startScanningNFC()
                }
            newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
            newPersonDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.VISIBLE
        } else {
            newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
            newPersonDialogView.findViewById<ConstraintLayout>(R.id.constraintLayout_nfcOff).visibility = View.GONE
            startScanningNFC()
        }

        newPersonDialog.show()
    }

    @SuppressLint("RestrictedApi", "DiscouragedPrivateApi")
    private fun handleRecyclerViewItemLongClicked(position: Int) {
        val popupMenu = PopupMenu(this@MainActivity, binding.recyclerView[position].findViewById(R.id.textView_runnerName))
        popupMenu.inflate(R.menu.menu_list_item)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menuItem_listItemDelete -> {
                    val builder = MaterialAlertDialogBuilder(this@MainActivity)
                    builder.setTitle("Smazat položku")
                    builder.setMessage("Opravdu chcete smazat tuto položku? Tato akce nelze vrátit")
                    builder.setCancelable(false)
                    builder.setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
                    builder.setPositiveButton("Ano") { _: DialogInterface?, _: Int ->

                        val tmpPerson = peopleList[position]
                        peopleList.remove(tmpPerson)

                        if (peopleList.size == 0)
                            binding.textViewNoData.visibility = View.VISIBLE

                        rvAdapter.notifyItemRemoved(position)
                        rvAdapter.notifyItemRangeChanged(0, peopleList.size)

                        deletePerson(tmpPerson)
                    }
                    builder.create().show()

                    true
                }
                R.id.menuItem_listItemEdit -> {
                    Toast.makeText(this@MainActivity , "Position: $position\nSize: ${peopleList.size}" , Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@MainActivity, "ne", Toast.LENGTH_SHORT).show()

                true
            }
            R.id.menuItem_deleteAllData -> {
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle("Vymazat všechna data")
                builder.setMessage("Opravdu chcete vymazat všechna data? Změny nelze vrátit")
                builder.setCancelable(false)
                builder.setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
                builder.setPositiveButton("Ano") { _: DialogInterface?, _: Int ->
                    val size = peopleList.size
                    peopleList.clear()
                    rvAdapter.notifyItemRangeRemoved(0, size)
                    binding.textViewNoData.visibility = View.VISIBLE

                    deleteAllPeople()
                }
                builder.create().show()

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

    private fun loadPeople() {
        if (peopleList.isEmpty()) {
            Thread {
                val people = db.personDao().getAll()
                if (people.isNotEmpty()) {
                    runOnUiThread {
                        for (person in people) {
                            peopleList.add(0, person)
                        }
                        rvAdapter.notifyItemRangeInserted(0, people.size)
                        binding.textViewNoData.visibility = View.GONE
                    }
                }
            }.start()
        }
    }

    private fun addNewPerson(person: Person) {
        Thread {
            try {
                db.personDao().insert(person)
            } catch (e: Exception) {
                runOnUiThread {
                    Log.d("DB", e.stackTraceToString())
                    Toast.makeText(this@MainActivity, "Chyba při ukládání záznamu", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun deletePerson(person: Person) {
        Thread {
            try {
                db.personDao().delete(person)
            } catch (e: Exception) {
                runOnUiThread {
                    Log.d("DB", e.stackTraceToString())
                    Toast.makeText(this@MainActivity, "Chyba při mazání záznamu", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun deleteAllPeople() {
        Thread {
            try {
                db.personDao().deleteAll()
            } catch (e: Exception) {
                runOnUiThread {
                    Log.d("DB", e.stackTraceToString())
                    Toast.makeText(this@MainActivity, "Chyba při mazání záznamů", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun initNewPersonDialog() {
        newPersonDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setView(newPersonDialogView)
            .setCancelable(true)
            .setOnDismissListener {
                nfcAdapter!!.disableReaderMode(this@MainActivity)
                newPersonDialogView.findViewById<ImageView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
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
                newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_runnerId).setText("")
                newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_runnerName).setText("")
                newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_runnerTeam).setText("")
                newPersonDialog.dismiss()
            }
            .create()
    }

    private fun populateCheckpoints() {
        Thread {
            db.checkpointDao().apply {
                insert(Checkpoint("Stavba stanu", false, null))
                insert(Checkpoint("Orientace mapy", false, null))
                insert(Checkpoint("Lanová lávka", false, null))
                insert(Checkpoint("Uzly", false, null))
                insert(Checkpoint("Míček", false, null))
                insert(Checkpoint("Plížení", false, null))
                insert(Checkpoint("Turistické a topografické", false, null))
                insert(Checkpoint("Určování dřevin", false, null))
                insert(Checkpoint("Kulturně poznávací", false, null))
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
