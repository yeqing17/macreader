package com.example.macreader

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

    private fun refreshMacInfo() {
        val info = StringBuilder()
        info.append("=== 网卡MAC地址信息 ===\n")
        info.append("设备: ${Build.MODEL}\n")
        info.append("Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        info.append("\n")

        // 获取所有网络接口
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            info.append("发现 ${interfaces.size} 个网络接口:\n")
            info.append("─".repeat(40)).append("\n\n")

            for (netInterface in interfaces) {
                info.append("【${netInterface.name}】\n")

                val macBytes = netInterface.hardwareAddress
                if (macBytes != null && macBytes.isNotEmpty()) {
                    val macAddress = macBytes.joinToString(":") {
                        String.format("%02X", it)
                    }
                    info.append("  MAC: $macAddress\n")

                    // 判断接口类型
                    val type = getInterfaceType(netInterface.name)
                    info.append("  类型: $type\n")
                } else {
                    info.append("  MAC: 不可获取\n")
                    info.append("  (可能需要权限或接口未启用)\n")
                }

                info.append("  MTU: ${netInterface.mtu}\n")
                info.append("  启用: ${netInterface.isUp}\n")
                info.append("  回环: ${netInterface.isLoopback}\n")
                info.append("\n")
            }
        } catch (e: Exception) {
            info.append("获取网络接口失败: ${e.message}\n")
            e.printStackTrace()
        }

        // 尝试通过WifiManager获取WiFi MAC (传统方式)
        info.append("─".repeat(40)).append("\n")
        info.append("【WiFi MAC (传统方式)】\n")
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val wifiMac = wifiInfo.macAddress
            if (wifiMac != null && wifiMac != "02:00:00:00:00:00") {
                info.append("  MAC: $wifiMac\n")
            } else {
                info.append("  MAC: 不可获取 (Android 6.0+返回空MAC)\n")
            }
        } catch (e: Exception) {
            info.append("  获取失败: ${e.message}\n")
        }

        info.append("\n")
        info.append("─".repeat(40)).append("\n")
        info.append("【说明】\n")
        info.append("• eth0/wlan0: 有线/无线网卡\n")
        info.append("• Android 11+ 对MAC有访问限制\n")
        info.append("• 系统应用可获取真实MAC\n")

        tvMacInfo.text = info.toString()
        scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun getInterfaceType(name: String): String {
        return when {
            name.startsWith("wlan") -> "WiFi无线网卡"
            name.startsWith("eth") -> "以太网网卡"
            name.startsWith("rmnet") -> "移动数据"
            name.startsWith("lo") -> "本地回环"
            name.startsWith("bt") -> "蓝牙"
            name.startsWith("tun") -> "VPN隧道"
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