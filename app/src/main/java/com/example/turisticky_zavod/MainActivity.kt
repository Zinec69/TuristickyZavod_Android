package com.example.turisticky_zavod

import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.airbnb.lottie.LottieAnimationView
import com.example.turisticky_zavod.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException
import kotlin.collections.ArrayList
import com.example.turisticky_zavod.NFCHelper.NfcAvailability


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

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        rvAdapter = RvAdapter(peopleList, object: RvAdapter.OptionsMenuLongClickListener {
            override fun onOptionsMenuLongClicked(position: Int): Boolean {
                handleListItemClicked(position)
                return true
            }
        })
        binding.recyclerView.adapter = rvAdapter

        nfcAdapter = getDefaultAdapter(this@MainActivity)

        db = Room.databaseBuilder(this@MainActivity, TZDatabase::class.java, "tz.db")
            .addCallback( object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    this@MainActivity.db.checkpointDao().apply {
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
                }
            })
            .fallbackToDestructiveMigration()
            .build()

        binding.fabAdd.setOnClickListener {
            handleAddingNewPerson()
        }

        newPersonDialogView = layoutInflater.inflate(R.layout.dialog_add, null)
        newPersonDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setView(newPersonDialogView)
            .setCancelable(true)
            .setOnDismissListener {
                nfcAdapter!!.disableReaderMode(this@MainActivity)
                newPersonDialogView.findViewById<ImageView>(R.id.animation_nfcScanning).visibility = View.VISIBLE
                newPersonDialogView.findViewById<LinearLayout>(R.id.linearLayout_addDialogContent).visibility = View.GONE
                newPersonDialogView.findViewById<LinearLayout>(R.id.linearLayout).visibility = View.GONE
                newPersonDialog.setCancelable(true)
            }
            .create()
        cancelDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Zrušit")
            .setMessage("Opravdu chcete odejít? Provedené změny nebudou uloženy")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface, _: Int ->
                newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerId).setText("")
                newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerName).setText("")
                newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerTeam).setText("")
                newPersonDialog.dismiss()
            }
            .create()

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
                    scanSuccess()

                    try {
                        newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerId).setText(person.runnerId.toString())
                        newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerName).setText(person.name)
                        newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerTeam).setText(person.team)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Chyba při vkládání informací do textových polí", Toast.LENGTH_LONG).show()
                        Log.d("NFC", e.stackTraceToString())
                    }
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

    private fun scanSuccess() {
        newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcScanning).visibility = View.GONE
        val animation = newPersonDialogView.findViewById<LottieAnimationView>(R.id.animation_nfcSuccess)
        animation.visibility = View.VISIBLE
        animation.animate().setDuration(500).withEndAction {
            animation.visibility = View.GONE
            newPersonDialogView.findViewById<LinearLayout>(R.id.linearLayout_addDialogContent).visibility = View.VISIBLE
            newPersonDialogView.findViewById<LinearLayout>(R.id.linearLayout).visibility = View.VISIBLE
            newPersonDialog.setCancelable(false)
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
        newPersonDialogView.findViewById<Button>(R.id.button_save).setOnClickListener {
            val id = newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerId)
            val name = newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerName)
            val team = newPersonDialogView.findViewById<TextInputEditText>(R.id.editText_RunnerTeam)

            val person = Person(id.text.toString().takeWhile { c -> c.isDigit() }.toInt(), name.text.toString(), team.text.toString(), null)

            peopleList.add(0, person)
            rvAdapter.notifyItemInserted(0)
            rvAdapter.notifyItemRangeChanged(0, peopleList.size)

            id.setText("")
            name.setText("")
            team.setText("")

            if (peopleList.size == 1)
                binding.textViewNoData.visibility = View.GONE

            addNewPerson(person)

            newPersonDialog.dismiss()
        }
        newPersonDialogView.findViewById<Button>(R.id.button_cancel).setOnClickListener { cancelDialog.show() }

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

    private fun handleListItemClicked(position: Int) {
        val popupMenu = PopupMenu(this , binding.recyclerView[position].findViewById(R.id.textView_runnerName))
        popupMenu.inflate(R.menu.menu_list_item)
        popupMenu.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener{
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                when (item?.itemId) {
                    R.id.menuItem_listItemDelete -> {
                        val builder = MaterialAlertDialogBuilder(this@MainActivity)
                        builder.setTitle("Smazat položku")
                        builder.setMessage("Opravdu chcete smazat tuto položku? Změny nelze vrátit")
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

                        return true
                    }
                    R.id.menuItem_listItemEdit -> {
                        Toast.makeText(this@MainActivity , "Position: $position\nSize: ${peopleList.size}" , Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
                return false
            }
        })
        popupMenu.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.menuItem_sendDataToPc -> {
                Toast.makeText(this@MainActivity, if (nfcHelper.checkNfcAvailability(nfcAdapter) == NfcAvailability.OFF) "ne" else "ano", Toast.LENGTH_SHORT).show()

                return true
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

                return true
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
                    for (person in people) {
                        peopleList.add(0, person)
                    }
                    rvAdapter.notifyItemRangeInserted(0, people.size)
                    binding.textViewNoData.visibility = View.GONE
                }
            }.start()
        }
    }

    private fun addNewPerson(person: Person) {
        Thread {
            try {
                db.personDao().insert(person)
            } catch (e: SQLiteConstraintException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "exception caught like a boss", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun deletePerson(person: Person) {
        Thread { db.personDao().delete(person) }.start()
    }

    private fun deleteAllPeople() {
        Thread { db.personDao().deleteAll() }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
