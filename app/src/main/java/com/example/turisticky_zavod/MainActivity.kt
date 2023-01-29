package com.example.turisticky_zavod

import android.content.DialogInterface
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
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.turisticky_zavod.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException
import kotlin.collections.ArrayList


//class Person(var id: String, var name: String, var team: String)

class MainActivity : AppCompatActivity(), ReaderCallback {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var peopleList = ArrayList<Person>()
    private lateinit var rvAdapter: RvAdapter

    var nfcAdapter: NfcAdapter? = null
    var nfcHelper = NFCHelper()

    lateinit var db: TZDatabase

    lateinit var view: View

    private lateinit var builder: AlertDialog
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

        db = Room.databaseBuilder(this@MainActivity, TZDatabase::class.java, "tz.db").build()
        loadPeople()

        binding.fabAdd.setOnClickListener {
//            if (nfcAdapter != null) {
//                if (nfcHelper.state == NfcState.READ) {
//                    val options = Bundle()
//                    options.putInt(EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
//
//                    nfcAdapter!!.enableReaderMode(this@MainActivity, this, FLAG_READER_NFC_A, options)
//                }
//                else
//                    nfcAdapter!!.disableReaderMode(this@MainActivity)
//            }
            handleAddingNewPerson()
        }

        view = layoutInflater.inflate(R.layout.dialog_add, null)
        builder = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Nový záznam")
            .setView(view)
            .setCancelable(true)
            .setOnDismissListener {
                nfcAdapter!!.disableReaderMode(this@MainActivity)
                view.findViewById<TextView>(R.id.textView_chipScanPrompt).visibility = View.VISIBLE
                view.findViewById<ImageView>(R.id.imageView_scanChip).visibility = View.VISIBLE
                view.findViewById<LinearLayout>(R.id.linearLayout_addDialogContent).visibility = View.GONE
                view.findViewById<LinearLayout>(R.id.linearLayout).visibility = View.GONE
                builder.setCancelable(true)
            }
            .create()
        cancelDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Zrušit")
            .setMessage("Opravdu chcete odejít? Provedené změny nebudou uloženy")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface, _: Int ->
                view.findViewById<TextInputEditText>(R.id.editText_RunnerId).setText("")
                view.findViewById<TextInputEditText>(R.id.editText_RunnerName).setText("")
                view.findViewById<TextInputEditText>(R.id.editText_RunnerTeam).setText("")
                builder.dismiss()
            }
            .create()
    }

    override fun onTagDiscovered(tag: Tag?) {
        runOnUiThread {
            view.findViewById<TextView>(R.id.textView_chipScanPrompt).visibility = View.GONE
            view.findViewById<ImageView>(R.id.imageView_scanChip).visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.linearLayout_addDialogContent).visibility = View.VISIBLE
            view.findViewById<LinearLayout>(R.id.linearLayout).visibility = View.VISIBLE
            builder.setCancelable(false)
        }
        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()

                val person = nfcHelper.readPerson(ndef)

                runOnUiThread {
                    try {
                        view.findViewById<TextInputEditText>(R.id.editText_RunnerId).setText(person.id.toString())
                        view.findViewById<TextInputEditText>(R.id.editText_RunnerName).setText(person.name)
                        view.findViewById<TextInputEditText>(R.id.editText_RunnerTeam).setText(person.team)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_LONG).show()
                        Log.d("NFC", e.stackTraceToString())
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_LONG).show()
                    Log.d("NFC", e.stackTraceToString())
                }
            } finally {
                try {
                    ndef.close()
                } catch (e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Nepodporovaný typ čipu", Toast.LENGTH_LONG).show()
            }
        }
        runOnUiThread {
            nfcAdapter!!.disableReaderMode(this@MainActivity)
        }
    }

    private fun handleAddingNewPerson() {
        val options = Bundle()
        options.putInt(EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        nfcAdapter!!.enableReaderMode(this@MainActivity, this, FLAG_READER_NFC_A, options)

        view.findViewById<Button>(R.id.button_save).setOnClickListener {
            val id = view.findViewById<TextInputEditText>(R.id.editText_RunnerId)
            val name = view.findViewById<TextInputEditText>(R.id.editText_RunnerName)
            val team = view.findViewById<TextInputEditText>(R.id.editText_RunnerTeam)

            val person = Person(id.text.toString().takeWhile { c -> c.isDigit() }.toInt(), name.text.toString(), team.text.toString())

            peopleList.add(0, person)
            rvAdapter.notifyItemInserted(0)
            rvAdapter.notifyItemRangeChanged(0, peopleList.size)

            id.setText("")
            name.setText("")
            team.setText("")

            if (peopleList.size == 1)
                binding.textViewNoData.visibility = View.GONE

            Thread { db.personDao().insert(person) }

            builder.dismiss()
        }
        view.findViewById<Button>(R.id.button_cancel).setOnClickListener { cancelDialog.show() }

        builder.show()
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

                            val tempItem = peopleList[position]
                            peopleList.remove(tempItem)

                            if (peopleList.size == 0)
                                binding.textViewNoData.visibility = View.VISIBLE

                            rvAdapter.notifyItemRemoved(position)
                            rvAdapter.notifyItemRangeChanged(0, peopleList.size)
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
                Toast.makeText(this@MainActivity, "ne", Toast.LENGTH_SHORT).show()

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
                }
                builder.create().show()

                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadPeople() {
        Thread {
            val people = db.personDao().getAll()
            if (people.isNotEmpty()) {
                for (person in people) {
                    peopleList.add(0, person)
                }
                rvAdapter.notifyItemRangeInserted(0, people.size)
//            rvAdapter.notifyItemRangeChanged(0, people.size)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
