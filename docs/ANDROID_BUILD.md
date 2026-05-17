# Android APK 构建手册

本文档记录 LifeTool 当前 Expo Android APK 的可复用构建流程。适用于本地调试包、下一版本 APK 验证，以及后续交给 Agent 或 CI 自动化。

## 1. 当前构建结果

- 构建类型：Android release APK
- 应用包名：`com.anonymous.lifetool`
- 版本号：`1.0.0`
- 成功产物：`frontend/android/app/build/outputs/apk/release/app-release.apk`
- 当前构建环境：
  - macOS 本地
  - Node.js + npm
  - Expo SDK 55
  - Gradle 8.13
  - Android SDK：`~/android-sdk`

> 当前 release APK 仍使用本地默认签名流程，后续正式发布应用商店前需要补充生产 keystore、版本号策略和签名密钥保管方案。

## 1.1 当前图标资源

- Expo 通用图标：`frontend/assets/app-icon.png`
- Android adaptive icon 前景图：`frontend/assets/adaptive-icon-foreground.png`
- `frontend/app.json` 已接入上述资源并随 `1.0.0` 版本生效

## 2. 依赖版本基线

`frontend/package.json` 当前已按 Expo SDK 55 对齐：

| 依赖 | 当前要求 |
| --- | --- |
| `expo` | `~55.0.24` |
| `react` | `^19.2.0` |
| `react-native` | `^0.83.6` |
| `expo-sqlite` | `~55.0.16` |
| `expo-linking` | `~55.0.15` |
| `react-native-safe-area-context` | `~5.6.2` |
| `react-native-screens` | `~4.23.0` |
| `typescript` | `~5.9.2` |

构建前建议执行：

```bash
cd frontend
npx expo install --check
npm run typecheck
```

期望结果：

- `npx expo install --check` 提示依赖已对齐。
- `npm run typecheck` 通过。

## 3. 环境变量

如果本机网络需要代理，先设置代理。没有代理时可以跳过这几行。

```bash
export HTTP_PROXY=http://127.0.0.1:7897
export HTTPS_PROXY=http://127.0.0.1:7897
export ALL_PROXY=http://127.0.0.1:7897
```

设置 Android 和 Gradle：

```bash
export ANDROID_HOME=~/android-sdk
export ANDROID_SDK_ROOT=~/android-sdk
export PATH=~/gradle-8.13/bin:$PATH
```

如果要构建带阿里云推送能力的 Android 包，还需要额外注入：

```bash
export LIFETOOL_ALIYUN_PUSH_APP_KEY=<阿里云移动推送 AppKey>
export LIFETOOL_ALIYUN_PUSH_APP_SECRET=<阿里云移动推送 AppSecret>
```

当前 Android 包名（也是阿里云移动推送里要填写的 Package Name）是：

```text
com.anonymous.lifetool
```

检查环境：

```bash
java -version
gradle -v
ls "$ANDROID_HOME"
```

## 4. 首次生成 Android 工程

如果 `frontend/android/` 不存在，先执行 Expo prebuild：

```bash
cd frontend
CI=1 npx expo prebuild --platform android
```

生成后写入 Android SDK 路径：

```bash
cd frontend
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > android/local.properties
```

`android/local.properties` 只保存本机路径，不能提交 Git。

## 5. 安装依赖

```bash
cd frontend
npm install --legacy-peer-deps
npx expo install --check
npm run typecheck
```

如果 `expo-linking` 缺失：

```bash
cd frontend
npm install --legacy-peer-deps --save expo-linking@~55.0.15
```

## 6. 构建 release APK

```bash
cd frontend/android
export NODE_ENV=production
./gradlew assembleRelease
```

构建成功后产物位置：

```bash
ls -lh app/build/outputs/apk/release/app-release.apk
```

## 7. 常见问题

### 7.1 无法发起 HTTP 请求

Android 9+ 默认禁止明文 HTTP 流量，APK 安装后无法连接 `http://` 开头的 API。

已通过 `app.json` 配置 `expo-build-properties` 插件，在 `android.usesCleartextTraffic` 设为 `true` 来允许明文 HTTP。

如果后续需要更细粒度的控制（只允许特定域名），可以创建 `network_security_config.xml`。

### 7.2 SDK location not found

现象：

```text
SDK location not found. Define a valid SDK location with ANDROID_HOME or by setting the sdk.dir path in local.properties.
```

处理：

```bash
cd frontend
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > android/local.properties
```

### 7.3 React Native Codegen 报 VirtualViewNativeComponent

现象：

```text
VirtualViewNativeComponent.js: Unable to determine event arguments for "onModeChange"
```

原因通常是 Expo SDK 与 React Native 版本不匹配。当前 Expo SDK 55 应使用 `react-native ^0.83.6`，不要让 npm 自动升级到不匹配的 RN 版本。

处理：

```bash
cd frontend
npx expo install --fix
npm install --legacy-peer-deps
npx expo install --check
```

### 7.4 TypeScript ignoreDeprecations 报错

现象：

```text
TS5103: Invalid value for '--ignoreDeprecations'
```

当前 TypeScript 5.9 使用：

```json
{
  "compilerOptions": {
    "ignoreDeprecations": "5.0"
  }
}
```

不要写成 `"6.0"`，否则 TypeScript 5.x 会直接报错。

### 7.5 expo-linking 缺失

现象：

```text
Cannot find module 'expo-linking/package.json'
```

处理：

```bash
cd frontend
npm install --legacy-peer-deps --save expo-linking@~55.0.15
```

### 7.6 APK 图标显示为 Expo 默认占位图

现象：`app.json` 已配置 `icon` 和 `adaptiveIcon.foregroundImage`，`assets/` 下有 1024×1024 的 PNG 源文件，但安装后桌面图标仍显示蓝色 Expo "E" 图标。

原因：`expo prebuild` 生成的自适应图标（`ic_launcher_foreground.webp`）使用了自定义图片，但传统图标（`ic_launcher.webp` / `ic_launcher_round.webp`）仍使用 Expo 默认占位图。部分 Android 启动器（Launcher）和最近任务列表会优先使用传统图标。

处理：

1. 确认图标配置文件 `app.json`：

```json
{
  "expo": {
    "icon": "./assets/app-icon.png",
    "android": {
      "adaptiveIcon": {
        "foregroundImage": "./assets/adaptive-icon-foreground.png",
        "backgroundColor": "#0F172A"
      }
    }
  }
}
```

2. 源图标要求：1024×1024 PNG，RGBA。

3. 用 `sips`（macOS 内置）从源图标生成各密度传统图标并替换：

```bash
cd frontend

# 替换 ic_launcher.webp（传统图标）
sips -z 48 48   assets/app-icon.png --out android/app/src/main/res/mipmap-mdpi/ic_launcher.webp
sips -z 72 72   assets/app-icon.png --out android/app/src/main/res/mipmap-hdpi/ic_launcher.webp
sips -z 96 96   assets/app-icon.png --out android/app/src/main/res/mipmap-xhdpi/ic_launcher.webp
sips -z 144 144 assets/app-icon.png --out android/app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
sips -z 192 192 assets/app-icon.png --out android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp

# ic_launcher_round.webp 同传统图标
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  cp android/app/src/main/res/mipmap-${d}/ic_launcher.webp android/app/src/main/res/mipmap-${d}/ic_launcher_round.webp
done
```

> 说明：macOS 的 `sips` 输出 webp 后缀实际为 PNG 格式，Android 构建工具（AAPT2）可正常处理。如需严格 webp 格式，可用 `cwebp` 转换。

4. 重新构建 APK：

```bash
cd frontend/android
./gradlew assembleRelease
```

## 8. 下次版本构建 Checklist

1. 确认 `git status`，不要把无关临时文件混入发布。
2. 执行 `cd frontend && npm install --legacy-peer-deps`。
3. 执行 `npx expo install --check`。
4. 执行 `npm run typecheck`。
5. 如果 `android/` 不存在，执行 `CI=1 npx expo prebuild --platform android`。
6. 确认 `frontend/android/local.properties` 指向本机 `ANDROID_HOME`。
7. 执行 `cd frontend/android && NODE_ENV=production ./gradlew assembleRelease`。
8. 验证 APK 图标非 Expo 默认占位图（见 7.6）。
9. 记录版本号和构建时间。
