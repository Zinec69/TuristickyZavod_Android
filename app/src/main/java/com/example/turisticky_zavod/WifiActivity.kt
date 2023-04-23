package com.example.turisticky_zavod

import android.Manifest.permission.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.example.turisticky_zavod.databinding.ActivityWifiBinding


class WifiActivity : AppCompatActivity() {

    lateinit var binding: ActivityWifiBinding

    private lateinit var mStatusLabel: TextView

    private lateinit var wifiManager: WifiManager
    private lateinit var p2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var broadcastReceiver: WiFiDirectBroadcastReceiver

    private lateinit var intentFilter: IntentFilter

    private val peers = ArrayList<WifiP2pDevice>()
    private val discoveredDeviceNames = ArrayList<String>()
    private lateinit var listAdapter: ArrayAdapter<String>
    private var connectedDevice: WifiP2pDevice? = null

//    private var socket: Socket? = null
//
//    private lateinit var client: MyClient
//    private lateinit var server: MyServer
//
//    private var isHost by Delegates.notNull<Boolean>()

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PackageManager.PERMISSION_GRANTED -> {
                if (grantResults.isNotEmpty() &&
                            grantResults.all { x -> x == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this@WifiActivity, "Oprávnění bylo uděleno", Toast.LENGTH_SHORT).show()
                    discoverPeers()
                } else {
                    Toast.makeText(this@WifiActivity, "Oprávnění nebylo uděleno", Toast.LENGTH_SHORT).show()
                }
                return
            }
            else -> {
                Toast.makeText(this@WifiActivity, "Oprávnění nebylo uděleno", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Thread {
            if (wifiManager.isWifiEnabled) {
                runOnUiThread {
                    requestPermissionsAndDiscoverPeers()
                }
            } else {
                Thread.sleep(1000)
                if (wifiManager.isWifiEnabled) {
                    runOnUiThread {
                        requestPermissionsAndDiscoverPeers()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@WifiActivity, "Tak tohle teda ne", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWifiBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setSupportActionBar(binding.toolbarWifi)

        binding.toolbarWifi.setNavigationOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this@WifiActivity) { finish() }

        mStatusLabel = findViewById(R.id.textView_status)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        p2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = p2pManager.initialize(this, mainLooper, null)
        broadcastReceiver = WiFiDirectBroadcastReceiver(p2pManager, channel, this@WifiActivity)

        intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        binding.buttonConnect.setOnClickListener {
            clearPeers()
            if (!wifiManager.isWifiEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val intent = Intent(Settings.Panel.ACTION_WIFI)
                    Toast.makeText(this@WifiActivity, "Zapněte prosím WiFi", Toast.LENGTH_SHORT).show()
                    activityResultLauncher.launch(intent)
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                    requestPermissionsAndDiscoverPeers()
                }
            } else {
//                registerReceiver(broadcastReceiver, intentFilter)
                requestPermissionsAndDiscoverPeers()
            }
        }

        binding.listViewWifi.setOnItemClickListener { _, _, position, _ ->
            val device = peers[position]
            val config = WifiP2pConfig()
            config.deviceAddress = device.deviceAddress
            config.wps.setup = WpsInfo.PBC
            config.groupOwnerIntent = 0
            if (ActivityCompat.checkSelfPermission(
                    this,
                    ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this,
                    NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val permissions = arrayOf(ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.plus(NEARBY_WIFI_DEVICES)
                }
                requestPermissions(permissions, PackageManager.PERMISSION_GRANTED)
            }
            p2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    binding.textViewStatus.text = "Connecting to '${device.deviceName}'..."
                    connectedDevice = device
                }

                override fun onFailure(reason: Int) {
                    binding.textViewStatus.text = "Failed connecting to '${device.deviceName}' [$reason]"
                    connectedDevice = null
                }

            })
        }

//        binding.buttonWifiSendMessage.setOnClickListener {
//            val executor = Executors.newSingleThreadExecutor()
//            val msg = binding.editTextWifiMessage.text.toString()
//            executor.execute {
//                if (msg.isNotEmpty() && isHost) {
//                    server.write(msg.toByteArray(Charset.forName("Windows-1250")))
//                } else if (msg.isNotEmpty() && !isHost) {
//                    client.write(msg.toByteArray(Charset.forName("Windows-1250")))
//                }
//            }
//        }
    }

    var connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        if (info.groupFormed && info.isGroupOwner) {
            binding.textViewStatus.text = "Successfully connected to '${connectedDevice?.deviceName}' as host"
//            isHost = true
//            server = MyServer()
//            server.run()
        } else if (info.groupFormed) {
            binding.textViewStatus.text = "Successfully connected to '${connectedDevice?.deviceName}' as client"
//            isHost = false
//            client = MyClient(info.groupOwnerAddress)
//            client.run()
        } else {
            binding.textViewStatus.text = "Failed connecting to '${connectedDevice?.deviceName}'"
        }
//        unregisterReceiver(broadcastReceiver)
        clearPeers()
    }

    val peerListListener = WifiP2pManager.PeerListListener { deviceList ->
        discoveredDeviceNames.clear()

        if (deviceList.deviceList.isNotEmpty()) {
            peers.addAll(deviceList.deviceList)
            deviceList.deviceList.forEach { d -> discoveredDeviceNames.add(d.deviceName) }
        }

        listAdapter = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, discoveredDeviceNames)
        binding.listViewWifi.adapter = listAdapter
    }

    private fun clearPeers() {
        peers.clear()
        discoveredDeviceNames.clear()
        connectedDevice = null
        listAdapter = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, discoveredDeviceNames)
        binding.listViewWifi.adapter = listAdapter
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(broadcastReceiver)
    }

    private fun discoverPeers() {
        if (ActivityCompat.checkSelfPermission(
                this,
                ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.plus(NEARBY_WIFI_DEVICES)
            }
            requestPermissions(permissions, PackageManager.PERMISSION_GRANTED)
            return
        }
        p2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                mStatusLabel.text = "Searching for peers..."
            }

            override fun onFailure(reason: Int) {
                mStatusLabel.text = "Failed to discover peers: $reason"
            }
        })
    }

    private fun requestPermissionsAndDiscoverPeers() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                INTERNET,
                ACCESS_WIFI_STATE,
                CHANGE_WIFI_STATE,
                CHANGE_NETWORK_STATE,
                ACCESS_NETWORK_STATE,
                NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                INTERNET,
                ACCESS_WIFI_STATE,
                CHANGE_WIFI_STATE,
                CHANGE_NETWORK_STATE,
                ACCESS_NETWORK_STATE,
                ACCESS_COARSE_LOCATION,
                ACCESS_FINE_LOCATION
            )
        }
        when {
            permissions.all {
                ContextCompat.checkSelfPermission(this@WifiActivity, it) == PackageManager.PERMISSION_GRANTED
            } -> {
                discoverPeers()
            }
            shouldShowRequestPermissionRationale(CHANGE_WIFI_STATE) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
                Toast.makeText(this@WifiActivity, "Grant me the permission or else 💪😠", Toast.LENGTH_SHORT).show()
            }
            else -> {
                requestPermissions(permissions, PackageManager.PERMISSION_GRANTED)
            }
        }
    }

//    inner class MyClient(private val hostAddress: InetAddress) : Runnable {
//
//        private lateinit var inputStream: InputStream
//        private lateinit var outputStream: OutputStream
//
//        fun write(bytes: ByteArray) {
//            try {
//                outputStream.write(bytes)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//
//        override fun run() {
//            socket = Socket()
//            try {
//                socket!!.connect(InetSocketAddress(hostAddress.hostAddress, 8888), 500)
//                inputStream = socket!!.getInputStream()
//                outputStream = socket!!.getOutputStream()
//
//                val executor = Executors.newSingleThreadExecutor()
//                val handler = Handler(Looper.getMainLooper())
//                executor.execute {
//                    val buffer = ByteArray(1024)
//                    var bytesRead: Int
//
//                    while (socket != null) {
//                        try {
//                            bytesRead = inputStream.read(buffer)
//                            if (bytesRead > 0) {
//                                handler.post {
//                                    val msg = buffer.toString(Charset.forName("Windows-1250"))
//                                    binding.textViewWifiMessage.text = msg
//                                }
//                            }
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                Toast.makeText(this@WifiActivity, "Chyba při spojení: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//    }
//
//    inner class MyServer: Runnable {
//
//        private lateinit var serverSocket: ServerSocket
//        private lateinit var inputStream: InputStream
//        private lateinit var outputStream: OutputStream
//
//        fun write(bytes: ByteArray) {
//            try {
//                outputStream.write(bytes)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//
//        override fun run() {
//            try {
//                serverSocket = ServerSocket(8888)
//                socket = serverSocket.accept()
//                inputStream = socket!!.getInputStream()
//                outputStream = socket!!.getOutputStream()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//            val executor = Executors.newSingleThreadExecutor()
//            val handler = Handler(Looper.getMainLooper())
//
//            executor.execute {
//                val buffer = ByteArray(1024)
//                var bytesRead: Int
//
//                while (socket != null) {
//                    try {
//                        bytesRead = inputStream.read(buffer)
//                        if (bytesRead > 0) {
//                            handler.post {
//                                val msg = buffer.toString(Charset.forName("Windows-1250"))
//                                binding.textViewWifiMessage.text = msg
//                            }
//                        }
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                }
//            }
//        }
//
//    }

}

@Suppress("DEPRECATION")
class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: WifiActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {

            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                if (ActivityCompat.checkSelfPermission(
                        context!!,
                        ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                        context,
                        NEARBY_WIFI_DEVICES
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    val permissions = arrayOf(ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.plus(NEARBY_WIFI_DEVICES)
                    }
                    requestPermissions(activity, permissions, PackageManager.PERMISSION_GRANTED)
                    return
                }
                manager.requestPeers(channel, activity.peerListListener)
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)!!
                } else {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)!!
                }
                if (networkInfo.isConnected) {
                    manager.requestConnectionInfo(channel, activity.connectionInfoListener)
                }
//                else {
//                    activity.binding.textViewStatus.text = "Connecting failed"
//                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)!!
                } else {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)!!
                }
                activity.binding.textViewStatus.text = when (device.status) {
                    WifiP2pDevice.AVAILABLE -> "Ready"
                    WifiP2pDevice.FAILED -> "Failed"
                    WifiP2pDevice.CONNECTED -> "Connected"
                    WifiP2pDevice.INVITED -> "Invited"
                    WifiP2pDevice.UNAVAILABLE -> "Unavailable"
                    else -> """¯\_(ツ)_/¯"""
                }
            }
        }
    }

}
