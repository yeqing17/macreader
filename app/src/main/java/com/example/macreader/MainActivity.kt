package com.example.macreader

import android.annotation.SuppressLint
import android.net.ConnectivityManager
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

        refreshMacInfo()
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun refreshMacInfo() {
        val info = StringBuilder()

        // 标题
        info.append("═".repeat(40)).append("\n")
        info.append("       MAC地址读取器 v1.1\n")
        info.append("═".repeat(40)).append("\n\n")

        info.append("【设备信息】\n")
        info.append("  型号: ${Build.MODEL}\n")
        info.append("  厂商: ${Build.MANUFACTURER}\n")
        info.append("  Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        info.append("\n")

        // ═══════════════════════════════════════
        // 方法1: NetworkInterface
        // ═══════════════════════════════════════
        info.append("═".repeat(40)).append("\n")
        info.append("【方法1】NetworkInterface (Java API)\n")
        info.append("─".repeat(40)).append("\n")
        info.append("代码:\n")
        info.append("  NetworkInterface.getNetworkInterfaces()\n")
        info.append("  .getHardwareAddress()\n")
        info.append("─".repeat(40)).append("\n")
        info.append("结果:\n")
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            if (networkInterfaces != null) {
                val interfaces = Collections.list(networkInterfaces)
                info.append("  ✅ 获取到 ${interfaces.size} 个接口\n\n")

                for (netInterface in interfaces.sortedBy { it.name }) {
                    val macBytes = netInterface.hardwareAddress
                    val macStr = if (macBytes != null && macBytes.isNotEmpty()) {
                        macBytes.joinToString(":") { String.format("%02X", it) }
                    } else {
                        "null (无权限获取MAC)"
                    }

                    info.append("  [${netInterface.name}]\n")
                    info.append("    MAC: $macStr\n")
                }
            } else {
                info.append("  ❌ 返回 null\n")
                info.append("  原因: Android 10+ 普通应用无权访问\n")
            }
        } catch (e: Exception) {
            info.append("  ❌ 异常: ${e.message}\n")
        }
        info.append("\n")

        // ═══════════════════════════════════════
        // 方法2: /sys/class/net 文件系统
        // ═══════════════════════════════════════
        info.append("═".repeat(40)).append("\n")
        info.append("【方法2】/sys/class/net 文件系统\n")
        info.append("─".repeat(40)).append("\n")
        info.append("代码:\n")
        info.append("  File(\"/sys/class/net/wlan0/address\")\n")
        info.append("  .readText()\n")
        info.append("─".repeat(40)).append("\n")
        info.append("结果:\n")
        try {
            val netDir = File("/sys/class/net")
            if (netDir.exists() && netDir.isDirectory) {
                val interfaces = netDir.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
                info.append("  ✅ 目录存在，${interfaces.size} 个接口\n\n")

                for (iface in interfaces) {
                    val macFile = File(iface, "address")
                    info.append("  [${iface.name}]\n")
                    if (macFile.exists()) {
                        try {
                            val mac = macFile.readText().trim()
                            info.append("    MAC: $mac\n")
                        } catch (e: SecurityException) {
                            info.append("    MAC: ❌ 权限被拒绝\n")
                        } catch (e: Exception) {
                            info.append("    MAC: ❌ ${e.message}\n")
                        }
                    } else {
                        info.append("    MAC: 文件不存在\n")
                    }
                }
            } else {
                info.append("  ❌ 目录不存在或无权访问\n")
            }
        } catch (e: Exception) {
            info.append("  ❌ 异常: ${e.message}\n")
        }
        info.append("\n")

        // ═══════════════════════════════════════
        // 方法3: ConnectivityManager
        // ═══════════════════════════════════════
        info.append("═".repeat(40)).append("\n")
        info.append("【方法3】ConnectivityManager (Android M+)\n")
        info.append("─".repeat(40)).append("\n")
        info.append("代码:\n")
        info.append("  ConnectivityManager.getAllNetworks()\n")
        info.append("  .getLinkProperties()\n")
        info.append("─".repeat(40)).append("\n")
        info.append("结果:\n")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val networks = cm.allNetworks

                if (networks.isNotEmpty()) {
                    info.append("  ✅ 获取到 ${networks.size} 个网络\n\n")

                    for (network in networks) {
                        val nc = cm.getNetworkCapabilities(network)
                        val lp = cm.getLinkProperties(network)

                        if (lp != null) {
                            info.append("  [${lp.interfaceName ?: "未知"}]\n")

                            if (nc != null) {
                                val type = when {
                                    nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                                    nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                                    nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                                    nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                                    else -> "其他"
                                }
                                info.append("    类型: $type\n")
                            }

                            // 尝试从sysfs读取MAC
                            val mac = getMacFromSysfs(lp.interfaceName)
                            info.append("    MAC: $mac\n")
                        }
                    }
                } else {
                    info.append("  无活动网络\n")
                }
            } catch (e: Exception) {
                info.append("  ❌ 异常: ${e.message}\n")
            }
        } else {
            info.append("  ⚠️ 需要 Android 6.0+\n")
        }
        info.append("\n")

        // ═══════════════════════════════════════
        // 方法4: WifiManager
        // ═══════════════════════════════════════
        info.append("═".repeat(40)).append("\n")
        info.append("【方法4】WifiManager (传统方式)\n")
        info.append("─".repeat(40)).append("\n")
        info.append("代码:\n")
        info.append("  WifiManager.getConnectionInfo()\n")
        info.append("  .getMacAddress()\n")
        info.append("─".repeat(40)).append("\n")
        info.append("结果:\n")
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val wifiMac = wifiInfo.macAddress

            info.append("  MAC: $wifiMac\n")
            when {
                wifiMac == "02:00:00:00:00:00" -> info.append("  状态: ❌ Android 6.0+ 返回假MAC\n")
                wifiMac.isNullOrBlank() -> info.append("  状态: ❌ 返回空\n")
                else -> info.append("  状态: ✅ 可能是真实MAC\n")
            }
        } catch (e: Exception) {
            info.append("  ❌ 异常: ${e.message}\n")
        }
        info.append("\n")

        // ═══════════════════════════════════════
        // 方法5: Shell 命令
        // ═══════════════════════════════════════
        info.append("═".repeat(40)).append("\n")
        info.append("【方法5】Shell 命令执行\n")
        info.append("─".repeat(40)).append("\n")
        info.append("命令:\n")
        info.append("  cat /sys/class/net/wlan0/address\n")
        info.append("  ip link show\n")
        info.append("  ifconfig\n")
        info.append("─".repeat(40)).append("\n")
        info.append("结果:\n")

        val commands = listOf(
            "cat /sys/class/net/wlan0/address" to "wlan0 MAC",
            "cat /sys/class/net/eth0/address" to "eth0 MAC",
            "ip link show wlan0" to "ip link wlan0",
            "ifconfig wlan0" to "ifconfig wlan0"
        )

        for ((cmd, label) in commands) {
            info.append("\n  [$label]\n")
            info.append("  \$ $cmd\n")
            try {
                val process = Runtime.getRuntime().exec(cmd)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText().trim()
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val error = errorReader.readText().trim()
                reader.close()
                errorReader.close()
                process.waitFor()

                when {
                    output.isNotEmpty() -> {
                        val firstLine = output.lines().first()
                        info.append("  → $firstLine\n")
                    }
                    error.isNotEmpty() -> info.append("  → 错误: ${error.lines().first()}\n")
                    else -> info.append("  → 无输出\n")
                }
            } catch (e: Exception) {
                info.append("  → 异常: ${e.message}\n")
            }
        }
        info.append("\n")

        // ═══════════════════════════════════════
        // 总结
        // ═══════════════════════════════════════
        info.append("═".repeat(40)).append("\n")
        info.append("【总结】\n")
        info.append("─".repeat(40)).append("\n")
        info.append("Android 版本限制:\n")
        info.append("  • Android 5.x 及以下: ✅ 可获取真实MAC\n")
        info.append("  • Android 6.0-9.0:   ⚠️ 部分方法可用\n")
        info.append("  • Android 10+:       ❌ 普通应用无法获取\n")
        info.append("\n")
        info.append("如需获取真实MAC，需要:\n")
        info.append("  1. 系统签名应用\n")
        info.append("  2. 设备Root权限\n")
        info.append("  3. 厂商私有API\n")

        tvMacInfo.text = info.toString()
        scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun getMacFromSysfs(interfaceName: String?): String {
        if (interfaceName.isNullOrBlank()) return "接口名未知"
        return try {
            val macFile = File("/sys/class/net/$interfaceName/address")
            if (macFile.exists()) {
                val mac = macFile.readText().trim()
                if (mac.isNotEmpty()) mac else "读取失败"
            } else {
                "文件不存在"
            }
        } catch (e: SecurityException) {
            "权限被拒绝"
        } catch (e: Exception) {
            "错误: ${e.message}"
        }
    }

    private fun copyToClipboard() {
        val text = tvMacInfo.text.toString()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.text = text
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}