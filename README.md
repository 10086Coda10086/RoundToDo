# RoundToDo (for Microsoft To Do)

<p align="center">
  <img width="110" height="110" alt="Generated Image November 23, 2025 - 10_08AM" src="https://github.com/user-attachments/assets/4b8f2b29-d63f-44f6-9199-f83ead5ab742" />

</p>

<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Language-Kotlin-purple" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-green" alt="Compose"></a>
  <a href="https://graph.microsoft.com/"><img src="https://img.shields.io/badge/API-Microsoft%20Graph-blue" alt="MS Graph"></a>
</p>

**RoundToDo** 是一个专为**圆形屏幕安卓设备**（如智能手表、特殊形态手机）设计的轻量级 Microsoft To Do (微软待办) 第三方客户端。（非wear os）

它摒弃了繁杂的传统界面，采用极简设计，完美适配圆形表盘，支持手势操作与离线缓存。

## ✨ 主要功能 (Features)

*   **🎯 圆形屏幕适配**：UI 布局专为圆形显示优化，边缘不遮挡，阅读更舒适。
*   **☁️ 微软账号同步**：基于 OAuth 2.0 登录，通过 Microsoft Graph API 实时同步你的清单与任务。
*   **⚡️ 极速离线缓存**：支持无网查看，本地秒开，后台静默同步。
*   **👆 手势操作**：全局支持屏幕**向右滑动返回**，单手操作更顺畅。
*   **🛠 任务管理**：
    *   查看所有清单文件夹。
    *   创建任务（支持设置截止日期、重复规则）。
    *   完成/取消完成任务。
    *   长按删除任务。
*   **⚙️ 旋钮优化**：针对带有旋转表冠/滚轮的设备进行了阻尼滚动优化。

## 📸 截图 (Screenshots)

<!-- 在这里上传你的截图，然后把链接填在下面，或者直接在 GitHub 编辑器里把图片拖进去 -->
| 清单列表 | 添加任务 | 手表演示 |
|:---:|:---:|:---:|
| ![Screenshot_20251122_234528](https://github.com/user-attachments/assets/54a4f907-4c1c-4998-8220-0600cf1f6f2c) | ![Screenshot_20251122_234556](https://github.com/user-attachments/assets/6eb741d1-bf23-4f2a-bd6b-56a04644569f) | ![IMG_20251122_234643](https://github.com/user-attachments/assets/e36ddcaf-bf15-4713-89af-e85d3b13b595) |

## 🛠️ 技术栈 (Tech Stack)

*   **语言**: Kotlin
*   **UI 框架**: Jetpack Compose (Material3)
*   **网络请求**: Retrofit2 + OkHttp
*   **身份验证**: MSAL (Microsoft Authentication Library) for Android
*   **数据解析**: Gson
*   **架构**: 单 Activity + Navigation Compose

## 🚀 开始使用 (Getting Started)

如果你是开发者并希望自行构建项目，请遵循以下步骤：

1.  **克隆仓库**：
    ```bash
    git clone https://github.com/你的用户名/RoundToDo.git
    ```
2.  **配置 Azure 应用**：
    *   前往 [Azure Portal](https://portal.azure.com) 注册一个新应用。
    *   获取 `Client ID` 和 `Tenant ID`。
    *   添加 Android 平台并配置包名 `com.round.todo` 及对应的签名哈希。
3.  **添加配置文件**：
    *   在 `app/src/main/res/raw/` 目录下创建 `auth_config.json` 文件，填入你的 Azure 配置信息：
    ```json
    {
      "client_id" : "YOUR_CLIENT_ID",
      "authorization_user_agent" : "WEBVIEW",
      "redirect_uri" : "msauth://com.round.todo/YOUR_SIGNATURE_HASH",
      "account_mode" : "SINGLE",
      "authorities" : ...
    }
    ```
4.  **构建运行**：
    *   使用 Android Studio 打开项目并运行即可。


## 📄 许可证 (License)

本项目采用 MIT 许可证开源。

---
*Disclaimer: This app is an unofficial client and is not affiliated with Microsoft Corporation.*
