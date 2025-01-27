# ByPlayer - Android 音乐播放器

本仓库绝大部分代码是由 AI 编写开发。

ByPlayer 是一个现代化的 Android 音乐播放器应用，使用 Kotlin 和 Jetpack Compose 开发。它提供了流畅的用户界面和丰富的功能，让您享受本地音乐播放的乐趣。

[English Version](README.md)

## 主要功能

- 🎵 本地音乐扫描和播放
  - 支持 MP3、M4A 等常见音频格式
  - 自动扫描设备中的音乐文件
  - 显示歌曲标题、艺术家、专辑信息

- 📝 LRC 歌词支持
  - 自动加载和显示 LRC 格式歌词
  - 实时同步歌词显示
  - 支持歌词界面手势控制

- 🎮 丰富的播放控制
  - 播放/暂停、上一曲/下一曲
  - 进度条拖动控制
  - 随机播放模式
  - 单曲/列表循环模式

- 📱 通知栏控制
  - 媒体通知栏显示当前播放信息
  - 通知栏快捷控制播放
  - 后台播放支持

- 🚗 车载系统支持
  - 支持蓝牙连接显示歌词
  - 通过 AVRCP 协议与车载系统集成
  - 支持方向盘控制

## 技术特点

- 使用 Kotlin 语言开发
- 采用 Jetpack Compose 构建现代化 UI
- 使用 Media3 ExoPlayer 实现音频播放
- 支持 Android 12+ 的新权限系统
- 遵循 Material Design 3 设计规范

## 系统要求

- Android 6.0 (API 23) 或更高版本
- 需要存储权限以访问本地音乐文件
- 需要通知权限以显示媒体通知
- 需要蓝牙权限以支持车载功能

## 开发环境设置

1. 克隆项目：
```bash
git clone https://github.com/yourusername/ByPlayer.git
```

2. 使用 Android Studio Hedgehog 或更高版本打开项目

3. 同步 Gradle 依赖

4. 运行应用

## 使用的主要依赖

- androidx.compose:compose-bom:2023.10.01
- androidx.media3:media3-exoplayer:1.2.0
- androidx.media3:media3-session:1.2.0
- androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0
- com.google.accompanist:accompanist-permissions:0.32.0

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 致谢

感谢所有为本项目提供帮助和建议的贡献者。
