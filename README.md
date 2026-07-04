# sctrl

基于 Shizuku 的 Android 屏幕控制应用，提供 HTTP API 接口实现远程屏幕控制功能。

## 核心功能

- **屏幕控制** — 通过 Shizuku 调用系统 API 控制屏幕开关
- **HTTP Server** — 内嵌 Ktor 服务器，提供 RESTful API
- **Shizuku 集成** — 免 Root 调用隐藏 API，安全高效
- **Material 3 UI** — 基于 Jetpack Compose 的现代化界面
- **快速设置磁贴** — 系统级快捷开关，一键控制屏幕

## HTTP API

应用启动后会在本地提供 HTTP 服务，支持以下接口：

### 获取屏幕状态
```http
GET /api/screen/state
```
返回当前屏幕状态（开/关）

### 关闭屏幕
```http
POST /api/screen/off
```
关闭设备屏幕

### 打开屏幕
```http
POST /api/screen/on
```
打开设备屏幕

## 使用说明

1. **安装 Shizuku**：在设备上安装 [Shizuku](https://github.com/Rikkaapps/Shizuku)
2. **授权应用**：首次运行时点击提示卡片完成 Shizuku 授权
3. **启动服务**：应用会自动启动 HTTP 服务
4. **调用 API**：通过 HTTP 接口控制屏幕状态

## 技术栈

- Kotlin 2.4.0 + Jetpack Compose 1.11.2
- Material 3 + Navigation 3
- Shizuku 13.1.5
- Ktor 3 (Server + Client)

## 免责声明

**本项目遵循 [GPL-3.0](/LICENSE) 开源，项目仅供学习交流，禁止用于商业或非法用途**
