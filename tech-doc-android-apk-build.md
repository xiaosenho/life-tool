# LifeTool Android APK 构建技术总结

## 背景

本次目标是在本地为 LifeTool Expo 移动端打出可安装的 Android APK，当前用户环境提供：

```bash
export ANDROID_HOME=~/android-sdk
export PATH=~/gradle-8.13/bin:$PATH
```

网络可通过本地代理 `127.0.0.1:7897` 加速依赖下载。

## 最终结果

APK 构建成功：

```text
frontend/android/app/build/outputs/apk/release/app-release.apk
```

构建结果：

- `BUILD SUCCESSFUL`
- 构建耗时约 3 分 42 秒
- APK 大小约 84 MB
- 应用包名：`com.anonymous.lifetool`
- 版本号：`0.1.0`

## 关键处理

1. 项目原本没有 `frontend/android/` 原生工程，因此先通过 Expo prebuild 生成 Android 工程。
2. Gradle 首次找不到 Android SDK，补充了 `frontend/android/local.properties`：

```properties
sdk.dir=/Users/zhouxiaojie/android-sdk
```

3. React Native Codegen 报 `VirtualViewNativeComponent.js`，根因是 Expo SDK 55 与 React Native 版本不匹配。已将前端依赖对齐到 Expo SDK 55 推荐版本。
4. TypeScript 5.9 不接受 `ignoreDeprecations: "6.0"`，已调整为 `"5.0"`。
5. 构建过程中缺少 `expo-linking`，已按 Expo SDK 55 增加 `expo-linking@~55.0.15`。

## 可复用命令

```bash
export HTTP_PROXY=http://127.0.0.1:7897
export HTTPS_PROXY=http://127.0.0.1:7897
export ALL_PROXY=http://127.0.0.1:7897
export ANDROID_HOME=~/android-sdk
export ANDROID_SDK_ROOT=~/android-sdk
export PATH=~/gradle-8.13/bin:$PATH

cd frontend
npm install --legacy-peer-deps
npx expo install --check
npm run typecheck

CI=1 npx expo prebuild --platform android
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > android/local.properties

cd android
export NODE_ENV=production
./gradlew assembleRelease
```

更完整的日常构建手册见 `docs/ANDROID_BUILD.md`。

## 后续建议

- 正式发布前配置 Android release keystore，避免继续使用默认本地签名。
- 建立版本号策略，同步维护 `app.json`、Android `versionCode` 和发布记录。
- 如果决定提交 `frontend/android/`，需要先确认其中没有 `local.properties`、本机路径、缓存和签名密钥。

