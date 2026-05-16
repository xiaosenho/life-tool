# Android APK 构建手册

本文档记录 LifeTool 当前 Expo Android APK 的可复用构建流程。适用于本地调试包、下一版本 APK 验证，以及后续交给 Agent 或 CI 自动化。

## 1. 当前构建结果

- 构建类型：Android release APK
- 应用包名：`com.anonymous.lifetool`
- 版本号：`0.1.0`
- 成功产物：`frontend/android/app/build/outputs/apk/release/app-release.apk`
- 当前构建环境：
  - macOS 本地
  - Node.js + npm
  - Expo SDK 55
  - Gradle 8.13
  - Android SDK：`~/android-sdk`

> 当前 release APK 仍使用本地默认签名流程，后续正式发布应用商店前需要补充生产 keystore、版本号策略和签名密钥保管方案。

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

## 8. 下次版本构建 Checklist

1. 确认 `git status`，不要把无关临时文件混入发布。
2. 执行 `cd frontend && npm install --legacy-peer-deps`。
3. 执行 `npx expo install --check`。
4. 执行 `npm run typecheck`。
5. 如果 `android/` 不存在，执行 `CI=1 npx expo prebuild --platform android`。
6. 确认 `frontend/android/local.properties` 指向本机 `ANDROID_HOME`。
7. 执行 `cd frontend/android && NODE_ENV=production ./gradlew assembleRelease`。
8. 验证 `app-release.apk` 存在，并记录版本号和构建时间。

