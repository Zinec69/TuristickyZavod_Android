package com.example.turisticky_zavod

import android.content.DialogInterface
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.*
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.turisticky_zavod.databinding.ActivityMainBinding
import com.example.turisticky_zavod.databinding.ListRunnersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import kotlin.collections.ArrayList


class Person(var id: Int, var name: String, var team: String)

class MainActivity : AppCompatActivity(), ReaderCallback {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var itemList = ArrayList<Person>()
    private lateinit var rvAdapter: RvAdapter

    var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        rvAdapter = RvAdapter(itemList, object: RvAdapter.OptionsMenuLongClickListener {
            override fun onOptionsMenuLongClicked(position: Int): Boolean {
                performOptionsMenuClick(position)
                return true
            }
        })
        binding.recyclerView.adapter = rvAdapter

        binding.fabAdd.setOnClickListener {
            val i = itemList.size + 1

            itemList.add(0, Person(i, "Jméno Příjmení $i", "Oddíl $i"))
            rvAdapter.notifyItemInserted(0)
            rvAdapter.notifyItemRangeChanged(0, itemList.size)

            if (i == 1)
                binding.textViewNoData.visibility = View.GONE
        }

        nfcAdapter = getDefaultAdapter(this@MainActivity)
    }

    override fun onResume() {
        super.onResume()

        if (nfcAdapter != null) {
            val options = Bundle()
            options.putInt(EXTRA_READER_PRESENCE_CHECK_DELAY, 250)

            nfcAdapter!!.enableReaderMode(this@MainActivity, this, FLAG_READER_NFC_A, options)
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter != null)
            nfcAdapter!!.disableReaderMode(this@MainActivity)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val ndef = MifareClassic.get(tag)
        if (ndef != null) {
            val record = NdefRecord.createTextRecord("en", "English string")
            val msg = NdefMessage(record)
            try {
                ndef.connect()
                ndef.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT)
//                val block0 = ndef.readBlock(0)
//                val str = ByteArray(MifareClassic.BLOCK_SIZE)
//                str.fill(0.toByte(), 0, MifareClassic.BLOCK_SIZE - 1)
//                val toCopy = "xd".toByteArray()
//                System.arraycopy(toCopy, 0, str, 0, toCopy.size)
//                ndef.writeBlock(1, str)
                val block1 = ndef.readBlock(1)
                val block2 = ndef.readBlock(2)
                ndef.authenticateSectorWithKeyA(1, MifareClassic.KEY_DEFAULT)
                val block4 = ndef.readBlock(4)

                runOnUiThread {
                    try {
                        val idk = block1.takeWhile { b -> b != 0.toByte() }.toByteArray().decodeToString()
                        itemList.add(0, Person(idk.toInt(), block2.decodeToString(), block4.decodeToString()))
                        rvAdapter.notifyItemInserted(0)
                        rvAdapter.notifyItemRangeChanged(0, itemList.size)
                        if (itemList.size == 1)
                            binding.textViewNoData.visibility = View.GONE
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_LONG).show()
                    }
                }

//                try {
//                    val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
//                    val r = RingtoneManager.getRingtone(this@MainActivity, notification)
//                    r.play()
//                } catch (e: Exception) {
//                    val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
//                    val r = RingtoneManager.getRingtone(this@MainActivity, alarm)
//                    r.play()
//                }
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
        }
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

                            val tempItem = itemList[position]
                            itemList.remove(tempItem)

                            if (itemList.size == 0)
                                binding.textViewNoData.visibility = View.VISIBLE

                            else if (position == itemList.size)
                                findViewById<RecyclerView>(R.id.recyclerView)
                                    .getChildAt(position - 1)
                                    .findViewById<View>(R.id.divider_horizontal)
                                    .visibility = View.GONE

                            rvAdapter.notifyItemRemoved(position)
                            rvAdapter.notifyItemRangeChanged(0, itemList.size)
                        }
                        builder.create().show()

                        return true
                    }
                    R.id.menuItem_listItemEdit -> {
                        Toast.makeText(this@MainActivity , "Position: $position\nSize: ${itemList.size}" , Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "lmao", Toast.LENGTH_SHORT).show()
                return true
            }
            R.id.menuItem_deleteAllData -> {
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle("Vymazat všechna data")
                builder.setMessage("Opravdu chcete vymazat všechna data? Změny nelze vrátit")
                builder.setCancelable(false)
                builder.setNegativeButton("Ne") { dialog: DialogInterface, _: Int -> dialog.cancel() }
                builder.setPositiveButton("Ano") { _: DialogInterface?, _: Int ->
                    val size = itemList.size
                    itemList.clear()
                    rvAdapter.notifyItemRangeRemoved(0, size)
                    binding.textViewNoData.visibility = View.VISIBLE
                }
                builder.create().show()

                return true
            }
            R.id.menuItem_nfcCheck -> {
                if (nfcAdapter == null)
                    Toast.makeText(this@MainActivity, "NFC not supported", Toast.LENGTH_SHORT).show()
                else {
                    if (nfcAdapter!!.isEnabled)
                        Toast.makeText(this@MainActivity, "NFC is supported and enabled", Toast.LENGTH_SHORT).show()
                    else
                        Toast.makeText(this@MainActivity, "NFC is supported, but not enabled", Toast.LENGTH_SHORT).show()
                }
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

class RvAdapter(
    private var itemList: List<Person>,
    private var optionsMenuLongClickListener: OptionsMenuLongClickListener
) : RecyclerView.Adapter<RvAdapter.ViewHolder>() {

    interface OptionsMenuLongClickListener {
        fun onOptionsMenuLongClicked(position: Int): Boolean
    }

    inner class ViewHolder(val binding: ListRunnersBinding, val parent: ViewGroup) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListRunnersBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with (holder) {
            with (itemList[position]) {
                binding.textViewRunnerId.text = this.id.toString()
                binding.textViewRunnerName.text = this.name
                binding.textViewRunnerTeam.text = this.team

                if (position == itemCount - 1)
                    binding.dividerHorizontal.visibility = View.GONE

                binding.root.setOnLongClickListener {
                    optionsMenuLongClickListener.onOptionsMenuLongClicked(position)
                }
            }
            val count = parent.childCount
            Toast.makeText(parent.context, "pos: $position, child: $count, item: ${itemList.size}", Toast.LENGTH_SHORT).show()
            for (i in 0 until count - 1) {
                parent.getChildAt(i).findViewById<View>(R.id.divider_horizontal).visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}
