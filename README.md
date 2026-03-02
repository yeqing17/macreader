# MAC地址读取器

Android 应用，用于获取设备所有网卡的 MAC 地址。

## 功能

- 列出所有网络接口（wlan0, eth0, rmnet 等）
- 显示每个接口的 MAC 地址
- 支持复制所有信息到剪贴板
- 适配 Android 13 (API 33)

## 获取方式

本应用使用以下5种方法尝试获取MAC地址：

### 方法1: NetworkInterface (Java API)
```java
NetworkInterface.getNetworkInterfaces()
netInterface.getHardwareAddress()
```
- **适用**: Android 9 及以下
- **限制**: Android 10+ 返回 null 或假MAC

### 方法2: /sys/class/net 文件系统
```
/sys/class/net/wlan0/address
/sys/class/net/eth0/address
```
- **适用**: 部分设备（未启用SELinux严格模式）
- **限制**: Android 10+ 普通应用无读取权限

### 方法3: ConnectivityManager (Android M+)
```java
ConnectivityManager.getLinkProperties(network)
```
- **适用**: 获取网络状态
- **限制**: 不直接提供MAC地址

### 方法4: WifiManager
```java
WifiManager.getConnectionInfo().getMacAddress()
```
- **适用**: Android 5 及以下
- **限制**: Android 6+ 返回 `02:00:00:00:00:00`

### 方法5: Shell 命令
```bash
cat /sys/class/net/wlan0/address
ip link show
ifconfig
```
- **适用**: 需要 root 或系统权限
- **限制**: 普通应用执行受限

## 权限要求

| 权限 | 用途 |
|------|------|
| `ACCESS_NETWORK_STATE` | 获取网络状态 |
| `ACCESS_WIFI_STATE` | 获取WiFi信息 |

## Android 版本限制

| Android版本 | MAC获取情况 |
|-------------|-------------|
| 5.x 及以下 | ✅ 可获取真实MAC |
| 6.0 - 9.0 | ⚠️ 部分方法可获取 |
| 10+ | ❌ 普通应用无法获取 |

## 解决方案

如需在 Android 10+ 获取真实MAC，需要：
1. 系统签名应用
2. 设备Root后授予应用特权
3. 使用设备厂商提供的私有API

## 编译

```bash
# 克隆项目
git clone https://github.com/yeqing17/macreader.git
cd macreader

# 编译
gradle assembleDebug
```

## GitHub Actions 自动构建

- 推送 tag 时自动触发构建
- 构建产物自动发布到 Releases

```bash
git tag v1.1
git push origin v1.1
```

## License

MIT