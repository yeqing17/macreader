# MAC地址读取器

Android 应用，用于获取设备所有网卡的 MAC 地址。

## 功能

- 列出所有网络接口（wlan0, eth0, rmnet 等）
- 显示每个接口的 MAC 地址
- 显示接口类型、MTU、启用状态
- 支持复制所有信息到剪贴板

## 编译

项目使用 GitHub Actions 自动编译，推送到 main/master 分支即可触发构建。

## 本地编译

```bash
./gradlew assembleDebug
```

## 注意事项

Android 6.0+ 对 MAC 地址有隐私限制，普通应用可能无法获取真实 MAC 地址。