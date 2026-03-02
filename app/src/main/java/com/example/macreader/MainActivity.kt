package com.example.macreader

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.ClipboardManager
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var tvMacInfo: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnCopy: Button
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMacInfo = findViewById(R.id.tvMacInfo)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCopy = findViewById(R.id.btnCopy)
        scrollView = findViewById(R.id.scrollView)

        tvMacInfo.movementMethod = ScrollingMovementMethod()

        btnRefresh.setOnClickListener {
            refreshMacInfo()
        }

        btnCopy.setOnClickListener {
            copyToClipboard()
        }

        // 首次加载
        refreshMacInfo()
    }

    @SuppressLint("MissingPermission")
    private fun refreshMacInfo() {
        val info = StringBuilder()
        info.append("=== 网卡MAC地址信息 ===\n")
        info.append("设备: ${Build.MODEL}\n")
        info.append("Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        info.append("厂商: ${Build.MANUFACTURER}\n")
        info.append("\n")

        // 方法1: 通过NetworkInterface获取
        info.append("─".repeat(40)).append("\n")
        info.append("【方法1: NetworkInterface】\n")
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            if (networkInterfaces != null) {
                val interfaces = Collections.list(networkInterfaces)
                info.append("发现 ${interfaces.size} 个网络接口:\n\n")

                for (netInterface in interfaces) {
                    info.append("【${netInterface.name}】\n")
                    info.append("  显示名: ${netInterface.displayName}\n")

                    val macBytes = netInterface.hardwareAddress
                    if (macBytes != null && macBytes.isNotEmpty()) {
                        val macAddress = macBytes.joinToString(":") {
                            String.format("%02X", it)
                        }
                        info.append("  MAC: $macAddress\n")
                    } else {
                        info.append("  MAC: 无法获取\n")
                    }

                    info.append("  MTU: ${netInterface.mtu}\n")
                    info.append("  状态: ${if (netInterface.isUp) "启用" else "禁用"}\n")
                    info.append("  类型: ${getInterfaceType(netInterface.name)}\n")
                    info.append("\n")
                }
            } else {
                info.append("NetworkInterface.getNetworkInterfaces() 返回 null\n")
                info.append("(Android 11+ 普通应用无法访问)\n")
            }
        } catch (e: Exception) {
            info.append("获取失败: ${e.message}\n")
        }

        // 方法2: 读取 /sys/class/net 文件
        info.append("─".repeat(40)).append("\n")
        info.append("【方法2: /sys/class/net】\n")
        try {
            val netDir = File("/sys/class/net")
            if (netDir.exists() && netDir.isDirectory) {
                val interfaces = netDir.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
                info.append("发现 ${interfaces.size} 个接口:\n\n")

                for (iface in interfaces) {
                    info.append("【${iface.name}】\n")
                    val macFile = File(iface, "address")
                    if (macFile.exists()) {
                        val mac = macFile.readText().trim()
                        if (mac.isNotEmpty() && mac != "00:00:00:00:00:00") {
                            info.append("  MAC: $mac\n")
                        } else {
                            info.append("  MAC: 空或全零\n")
                        }
                    } else {
                        info.append("  MAC: 文件不存在\n")
                    }
                    info.append("\n")
                }
            } else {
                info.append("/sys/class/net 目录不存在或无法访问\n")
            }
        } catch (e: Exception) {
            info.append("读取失败: ${e.message}\n")
        }

        // 方法3: 通过ConnectivityManager获取 (Android M+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            info.append("─".repeat(40)).append("\n")
            info.append("【方法3: ConnectivityManager】\n")
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val networks = cm.allNetworks

                for (network in networks) {
                    val nc = cm.getNetworkCapabilities(network)
                    val lp = cm.getLinkProperties(network)

                    if (lp != null) {
                        info.append("【${lp.interfaceName ?: "未知接口"}】\n")

                        if (nc != null) {
                            val type = when {
                                nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                                nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网"
                                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                                nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                                else -> "其他"
                            }
                            info.append("  类型: $type\n")
                            info.append("  状态: ${if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "已连接" else "未连接"}\n")
                        }

                        // 获取MAC - LinkProperties不直接提供MAC
                        val macAddr = getMacFromInterface(lp.interfaceName)
                        if (macAddr != null) {
                            info.append("  MAC: $macAddr\n")
                        } else {
                            info.append("  MAC: 无法获取\n")
                        }
                        info.append("\n")
                    }
                }
            } catch (e: Exception) {
                info.append("获取失败: ${e.message}\n")
            }
        }

        // 方法4: WiFi MAC (传统方式)
        info.append("─".repeat(40)).append("\n")
        info.append("【方法4: WifiManager】\n")
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val wifiMac = wifiInfo.macAddress
            info.append("  MAC: $wifiMac\n")
            if (wifiMac == "02:00:00:00:00:00") {
                info.append("  (Android 6.0+ 返回假MAC)\n")
            }
        } catch (e: Exception) {
            info.append("  获取失败: ${e.message}\n")
        }

        // 执行shell命令获取
        info.append("─".repeat(40)).append("\n")
        info.append("【方法5: Shell命令】\n")
        try {
            val commands = listOf(
                "cat /sys/class/net/wlan0/address",
                "cat /sys/class/net/eth0/address",
                "ip link show",
                "ifconfig"
            )

            for (cmd in commands) {
                info.append("\n\$ $cmd\n")
                try {
                    val process = Runtime.getRuntime().exec(cmd)
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = reader.readText().trim()
                    reader.close()
                    process.waitFor()

                    if (output.isNotEmpty()) {
                        // 只显示前几行
                        val lines = output.lines().take(5)
                        for (line in lines) {
                            info.append("  $line\n")
                        }
                        if (output.lines().size > 5) {
                            info.append("  ...\n")
                        }
                    } else {
                        info.append("  (无输出)\n")
                    }
                } catch (e: Exception) {
                    info.append("  错误: ${e.message}\n")
                }
            }
        } catch (e: Exception) {
            info.append("执行失败: ${e.message}\n")
        }

        // 说明
        info.append("\n")
        info.append("─".repeat(40)).append("\n")
        info.append("【说明】\n")
        info.append("• Android 10+ 普通应用无法获取真实MAC\n")
        info.append("• 需要系统签名或root权限才能获取\n")
        info.append("• /sys/class/net 在部分设备可读取\n")
        info.append("• 此APP仅用于测试权限限制\n")

        tvMacInfo.text = info.toString()
        scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun getMacFromInterface(interfaceName: String?): String? {
        if (interfaceName.isNullOrBlank()) return null
        return try {
            val macFile = File("/sys/class/net/$interfaceName/address")
            if (macFile.exists()) {
                macFile.readText().trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getInterfaceType(name: String): String {
        return when {
            name.startsWith("wlan") -> "WiFi无线网卡"
            name.startsWith("eth") -> "以太网网卡"
            name.startsWith("rmnet") -> "移动数据"
            name.startsWith("lo") -> "本地回环"
            name.startsWith("bt") -> "蓝牙"
            name.startsWith("tun") -> "VPN隧道"
            name.startsWith("dummy") -> "虚拟接口"
            name.startsWith("sit") -> "IPv6隧道"
            else -> "其他接口"
        }
    }

    private fun copyToClipboard() {
        val text = tvMacInfo.text.toString()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.text = text
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}