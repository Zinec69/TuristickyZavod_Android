package com.example.turisticky_zavod

import android.content.DialogInterface
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.turisticky_zavod.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import java.nio.charset.Charset
import kotlin.collections.ArrayList


class Person(var id: Int, var name: String, var team: String)

class MainActivity : AppCompatActivity(), ReaderCallback {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var peopleList = ArrayList<Person>()
    private lateinit var rvAdapter: RvAdapter

    var nfcAdapter: NfcAdapter? = null
    var nfcHelper = MyNfcHelper()

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
                performOptionsMenuClick(position)
                return true
            }
        })
        binding.recyclerView.adapter = rvAdapter

        nfcAdapter = getDefaultAdapter(this@MainActivity)

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
            .setOnDismissListener { nfcAdapter!!.disableReaderMode(this@MainActivity) }
            .setOnCancelListener { Toast.makeText(this@MainActivity, "lmaoo", Toast.LENGTH_SHORT).show() }
            .create()
        cancelDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle("Zrušit")
            .setMessage("Opravdu chcete odejít? Změny nebudou uloženy")
            .setCancelable(false)
            .setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .setPositiveButton("Ano") { _: DialogInterface, _: Int -> builder.dismiss() }
            .create()
    }

    override fun onTagDiscovered(tag: Tag?) {
        runOnUiThread {
            view.findViewById<TextView>(R.id.textView_chipScanPrompt).visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.linearLayout_addDialogContent).visibility = View.VISIBLE
            view.findViewById<LinearLayout>(R.id.linearLayout).visibility = View.VISIBLE
        }
        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                ndef.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT)

//                val str = ByteArray(MifareClassic.BLOCK_SIZE)
//                str.fill(0.toByte(), 0, MifareClassic.BLOCK_SIZE - 1)
//                val toCopy = "Jakub Ostružka  ".toByteArray(Charset.forName("ISO-8859-2"))
//                System.arraycopy(toCopy, 0, str, 0, toCopy.size)
//                ndef.writeBlock(4, toCopy)

                val block1 = ndef.readBlock(1).toString(Charset.forName("ISO-8859-2"))
                val block2 = ndef.readBlock(2).toString(Charset.forName("ISO-8859-2"))
                ndef.authenticateSectorWithKeyA(1, MifareClassic.KEY_DEFAULT)
                val block4 = ndef.readBlock(4).toString(Charset.forName("ISO-8859-2"))

                runOnUiThread {
                    try {
                        view.findViewById<TextInputEditText>(R.id.editText_RunnerId).setText(block1)
                        view.findViewById<TextInputEditText>(R.id.editText_RunnerName).setText(block2)
                        view.findViewById<TextInputEditText>(R.id.editText_RunnerTeam).setText(block4)

//                        val idk = block1.takeWhile { b -> b != 0.toByte() }.toByteArray().decodeToString()
//                        peopleList.add(0, Person(idk.toInt(), block2.decodeToString(), block4.decodeToString()))
//                        rvAdapter.notifyItemInserted(0)
//                        rvAdapter.notifyItemRangeChanged(0, peopleList.size)
//                        if (peopleList.size == 1)
//                            binding.textViewNoData.visibility = View.GONE
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_LONG).show()
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

//        view.findViewById<Button>(R.id.button_save).setOnClickListener { builder.dismiss() }
        view.findViewById<Button>(R.id.button_cancel).setOnClickListener { cancelDialog.show() }

        builder.show()
    }

    private fun performOptionsMenuClick(position: Int) {
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

                            else if (position == peopleList.size)
                                findViewById<RecyclerView>(R.id.recyclerView)
                                    .getChildAt(position - 1)
                                    .findViewById<View>(R.id.divider_horizontal)
                                    .visibility = View.GONE

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
                val i = peopleList.size + 1

                peopleList.add(0, Person(i, "Jméno Příjmení $i", "Oddíl $i"))
                rvAdapter.notifyItemInserted(0)
                rvAdapter.notifyItemRangeChanged(0, peopleList.size)

                if (i == 1)
                    binding.textViewNoData.visibility = View.GONE

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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
