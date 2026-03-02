package com.example.macreader

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.ClipboardManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var tvVersion: TextView
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

        tvVersion = findViewById(R.id.tvVersion)
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
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        tvVersion.text = "v$versionName"
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
                sb.append("❌ getNetworkInterfaces() = null\n")
                sb.append("原因: 系统限制或无权限\n")
                // 尝试直接获取特定接口
                sb.append("\n尝试直接获取:\n")
                val names = listOf("wlan0", "eth0", "rmnet0", "p2p0")
                for (name in names) {
                    try {
                        val iface = NetworkInterface.getByName(name)
                        if (iface != null) {
                            val mac = iface.hardwareAddress
                            val macStr = if (mac != null && mac.isNotEmpty()) {
                                mac.joinToString(":") { String.format("%02X", it) }
                            } else {
                                "null"
                            }
                            sb.append("$name: $macStr\n")
                        }
                    } catch (e: Exception) {
                        sb.append("$name: ${e.message}\n")
                    }
                }
            } else {
                val list = Collections.list(interfaces)
                sb.append("发现${list.size}个接口:\n")
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
            sb.append("❌ 异常: ${e.javaClass.simpleName}\n")
            sb.append("${e.message}\n")
        }
        tvMethod1.text = sb.toString()
    }

    // 方法2: /sys/class/net
    private fun refreshMethod2() {
        val sb = StringBuilder()
        try {
            val netDir = File("/sys/class/net")
            if (!netDir.exists()) {
                sb.append("❌ /sys/class/net 不存在\n")
            } else {
                val interfaces = netDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                sb.append("发现${interfaces.size}个接口:\n")
                for (iface in interfaces) {
                    val macFile = File(iface, "address")
                    try {
                        val mac = macFile.readText().trim()
                        sb.append("${iface.name}: $mac\n")
                    } catch (e: SecurityException) {
                        sb.append("${iface.name}: 权限拒绝\n")
                    } catch (e: Exception) {
                        sb.append("${iface.name}: 读取失败\n")
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
                val networks = cm.allNetworks
                if (networks.isEmpty()) {
                    sb.append("无活动网络\n")
                } else {
                    for (network in networks) {
                        val nc = cm.getNetworkCapabilities(network)
                        val lp = cm.getLinkProperties(network) ?: continue
                        val type = when {
                            nc?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                            nc?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETH"
                            nc?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cell"
                            else -> "Other"
                        }
                        val mac = getMacFromSysfs(lp.interfaceName)
                        sb.append("[${lp.interfaceName}] $type\n")
                        sb.append("  MAC: $mac\n")
                    }
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

            val wifiMac = wifiManager.connectionInfo.macAddress
            sb.append("WiFi MAC: $wifiMac\n")
            if (wifiMac == "02:00:00:00:00:00") {
                sb.append("(Android 6+返回假MAC)\n")
            }

            // Android 13+ 新API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                sb.append("\nAndroid 13+:\n")
                try {
                    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val nc = connectivityManager.getNetworkCapabilities(network)
                    if (nc != null) {
                        sb.append("网络已连接\n")
                        val lp = connectivityManager.getLinkProperties(network)
                        lp?.linkAddresses?.forEach { la ->
                            sb.append("IP: ${la.address.hostAddress}\n")
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
            "ip link show wlan0 2>/dev/null | grep ether" to "ip link",
            "ifconfig wlan0 2>/dev/null | grep -i hw" to "ifconfig"
        )

        for ((cmd, label) in commands) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText().trim()
                reader.close()
                process.waitFor()

                val result = if (output.isNotEmpty()) {
                    output.lines().first().take(35)
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
        if (interfaceName.isNullOrBlank()) return "未知"
        return try {
            val macFile = File("/sys/class/net/$interfaceName/address")
            if (macFile.exists()) macFile.readText().trim() else "不存在"
        } catch (e: SecurityException) {
            "权限拒绝"
        } catch (e: Exception) {
            "错误"
        }
    }

    private fun copyToClipboard() {
        val sb = StringBuilder()
        sb.append("=== MAC地址信息 ===\n")
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