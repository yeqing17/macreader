package com.example.macreader

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Bundle
import android.text.ClipboardManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvMethod1: TextView
    private lateinit var tvMethod2: TextView
    private lateinit var tvMethod3: TextView
    private lateinit var tvMethod4: TextView
    private lateinit var tvMethod5: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        tvMethod1 = findViewById(R.id.tvMethod1)
        tvMethod2 = findViewById(R.id.tvMethod2)
        tvMethod3 = findViewById(R.id.tvMethod3)
        tvMethod4 = findViewById(R.id.tvMethod4)
        tvMethod5 = findViewById(R.id.tvMethod5)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCopy = findViewById(R.id.btnCopy)

        btnRefresh.setOnClickListener { refreshAll() }
        btnCopy.setOnClickListener { copyToClipboard() }

        refreshAll()
    }

    private fun refreshAll() {
        tvDeviceInfo.text = "${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        refreshMethod1()
        refreshMethod2()
        refreshMethod3()
        refreshMethod4()
        refreshMethod5()
    }

    // 方法1: NetworkInterface
    @SuppressLint("MissingPermission")
    private fun refreshMethod1() {
        val sb = StringBuilder()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces == null) {
                sb.append("❌ 返回null (Android 10+限制)\n")
            } else {
                val list = Collections.list(interfaces)
                for (iface in list.sortedBy { it.name }) {
                    val mac = iface.hardwareAddress
                    val macStr = if (mac != null && mac.isNotEmpty()) {
                        mac.joinToString(":") { String.format("%02X", it) }
                    } else {
                        "null"
                    }
                    sb.append("${iface.name}: $macStr\n")
                }
            }
        } catch (e: Exception) {
            sb.append("❌ ${e.message}\n")
        }
        tvMethod1.text = sb.toString()
    }

    // 方法2: /sys/class/net
    private fun refreshMethod2() {
        val sb = StringBuilder()
        try {
            val netDir = File("/sys/class/net")
            if (!netDir.exists()) {
                sb.append("❌ 目录不存在\n")
            } else {
                val interfaces = netDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                for (iface in interfaces) {
                    val macFile = File(iface, "address")
                    try {
                        val mac = macFile.readText().trim()
                        sb.append("${iface.name}: $mac\n")
                    } catch (e: SecurityException) {
                        sb.append("${iface.name}: 权限拒绝\n")
                    } catch (e: Exception) {
                        sb.append("${iface.name}: ${e.message}\n")
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("❌ ${e.message}\n")
        }
        tvMethod2.text = sb.toString()
    }

    // 方法3: ConnectivityManager
    @SuppressLint("MissingPermission", "NewApi")
    private fun refreshMethod3() {
        val sb = StringBuilder()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            sb.append("需要Android 6.0+\n")
        } else {
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                for (network in cm.allNetworks) {
                    val nc = cm.getNetworkCapabilities(network)
                    val lp = cm.getLinkProperties(network) ?: continue
                    val type = when {
                        nc?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                        nc?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETH"
                        nc?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cell"
                        else -> "Other"
                    }
                    val mac = getMacFromSysfs(lp.interfaceName)
                    sb.append("[${lp.interfaceName}] $type: $mac\n")
                }
            } catch (e: Exception) {
                sb.append("❌ ${e.message}\n")
            }
        }
        tvMethod3.text = sb.toString()
    }

    // 方法4: WifiManager
    @SuppressLint("MissingPermission", "NewApi")
    private fun refreshMethod4() {
        val sb = StringBuilder()
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

            // 传统方式
            val wifiMac = wifiManager.connectionInfo.macAddress
            sb.append("传统API: $wifiMac\n")
            if (wifiMac == "02:00:00:00:00:00") sb.append("(假MAC)\n")

            // Android 13+ 新API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sb.append("\nAndroid 13+:\n")

                try {
                    val wifiInfo = wifiManager.connectionInfo
                    sb.append("SSID: ${wifiInfo?.ssid ?: "null"}\n")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                        val network = connectivityManager.activeNetwork
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        if (capabilities != null) {
                            sb.append("网络: 已连接\n")
                            val lp = connectivityManager.getLinkProperties(network)
                            lp?.linkAddresses?.forEach { la ->
                                sb.append("IP: ${la.address.hostAddress}\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    sb.append("异常: ${e.message}\n")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sb.append("\n系统属性:\n")
                try {
                    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    for (net in cm.allNetworks) {
                        val nc = cm.getNetworkCapabilities(net)
                        if (nc?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                            val lp = cm.getLinkProperties(net)
                            sb.append("接口: ${lp?.interfaceName}\n")
                            val wifiMacProp = getSystemProperty("wifi.mac")
                            if (!wifiMacProp.isNullOrEmpty()) {
                                sb.append("wifi.mac: $wifiMacProp\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    sb.append("异常: ${e.message}\n")
                }
            }

        } catch (e: Exception) {
            sb.append("❌ ${e.message}\n")
        }
        tvMethod4.text = sb.toString()
    }

    // 方法5: Shell命令
    private fun refreshMethod5() {
        val sb = StringBuilder()
        val commands = listOf(
            "cat /sys/class/net/wlan0/address" to "wlan0",
            "cat /sys/class/net/eth0/address" to "eth0",
            "ip link show wlan0" to "ip wlan0",
            "ifconfig wlan0 2>/dev/null | head -2" to "ifconfig"
        )

        for ((cmd, label) in commands) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText().trim()
                reader.close()
                process.waitFor()

                val result = if (output.isNotEmpty()) {
                    output.lines().first().take(30)
                } else {
                    "(空)"
                }
                sb.append("$label: $result\n")
            } catch (e: Exception) {
                sb.append("$label: ❌\n")
            }
        }
        tvMethod5.text = sb.toString()
    }

    private fun getMacFromSysfs(interfaceName: String?): String {
        if (interfaceName.isNullOrBlank()) return "未知接口"
        return try {
            val macFile = File("/sys/class/net/$interfaceName/address")
            if (macFile.exists()) macFile.readText().trim() else "文件不存在"
        } catch (e: SecurityException) {
            "权限拒绝"
        } catch (e: Exception) {
            "错误"
        }
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val value = reader.readLine()
            reader.close()
            value
        } catch (e: Exception) {
            null
        }
    }

    private fun copyToClipboard() {
        val sb = StringBuilder()
        sb.append("=== MAC地址读取器 ===\n")
        sb.append("设备: ${tvDeviceInfo.text}\n\n")
        sb.append("【方法1 NetworkInterface】\n${tvMethod1.text}\n")
        sb.append("【方法2 /sys/class/net】\n${tvMethod2.text}\n")
        sb.append("【方法3 ConnectivityManager】\n${tvMethod3.text}\n")
        sb.append("【方法4 WifiManager】\n${tvMethod4.text}\n")
        sb.append("【方法5 Shell命令】\n${tvMethod5.text}")

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.text = sb.toString()
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }
}