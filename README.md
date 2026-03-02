# MAC地址读取器

Android 应用，用于获取设备所有网卡的 MAC 地址。

## 功能

- 5种方法尝试获取MAC地址
- 双列卡片布局，适配 PAD/TV 大屏
- 支持复制所有信息到剪贴板
- MAC地址统一大写格式，自动标注假MAC

## 获取方式

| 方法 | 原理 | 适用版本 | 说明 |
|------|------|----------|------|
| 1. NetworkInterface | Java API | Android ≤9 | 部分设备返回null |
| 2. /sys/class/net | 读取系统文件 | 取决于SELinux | 机顶盒可能可读 |
| 3. ConnectivityManager | Android API | Android 6+ | 不直接提供MAC |
| 4. WifiManager | WiFi信息 | Android ≤5 | 6+返回假MAC |
| 5. Shell命令 | ip link/ifconfig | 需root | 普通应用受限 |

## 实测情况

| 设备 | Android | 方法1 | 方法2 | 方法3 | 方法4 | 方法5 |
|------|---------|-------|-------|-------|-------|-------|
| 普通手机 | 13 | ❌ | ❌ | ✅状态 | ❌假MAC | ❌ |
| 机顶盒 | 9 | ❌ | ✅ | ✅状态 | ❌假MAC | ⚠️ |

**结论**: 机顶盒设备推荐使用**方法2**读取 `/sys/class/net/eth0/address`

## 更新日志

### v1.7 (2026-03-02)
- 修复: ClipboardManager 使用已废弃API
- 修复: Shell命令(方法5)资源泄露，新增3秒超时防ANR
- 修复: 方法5移至子线程执行，避免UI卡顿
- 优化: 新增 `formatMac()` 函数，所有MAC统一大写格式
- 优化: 自动识别并标注假MAC (`02:00:00:00:00:00`)
- 优化: 错误信息更明确，权限拒绝标注SELinux

### v1.6
- 修复构建错误

### v1.4
- 简化标题

## 权限

- `ACCESS_NETWORK_STATE` - 获取网络状态
- `ACCESS_WIFI_STATE` - 获取WiFi信息

## 编译

```bash
git clone https://github.com/yeqing17/macreader.git
cd macreader
gradle assembleDebug
```

## License

MIT