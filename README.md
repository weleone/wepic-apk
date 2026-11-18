# WePic APK

WePic 小工具（拼图 / 分图 / GIF 动图导出）的 Android 版，**完全本地化，不用服务器**。

## 怎么做到的

- `app/src/main/assets/` 里就是 WePic 的网页版（`index.html` + `gif.js` + `gif.worker.js`），
  APK 里一个全屏 WebView 直接加载本地文件 `file:///android_asset/index.html`。
- 唯一的外网依赖是 Google 字体（Noto Sans SC / Noto Serif SC），纯装饰；
  离线时加载不到就回落到系统字体，功能不受影响。
- GIF 导出用的 Worker 是本地 `gif.worker.js`，WebView 开了
  `setAllowFileAccessFromFileURLs(true)` + `setAllowUniversalAccessFromFileURLs(true)` 才能加载。
- 选图走系统文件选择器（可多选）；导出走 `WepicBridge.saveFile` 原生桥，
  直接存到公开下载目录（Android 10+ 用 MediaStore，不用申请权限），不弹保存框，
  分图逐个自动存。

## 构建

打 tag 触发 GitHub Actions（`v*`），或手动 workflow_dispatch：

```bash
git tag v1.0.0
git push origin v1.0.0
```

产物在 Actions 的 Artifacts 里（`wepic-apk`）。

## 签名状态

**首版 v1.0.0 是 unsigned release**（`app-release-unsigned.apk`），没有配 keystore，
先用来验证编译和运行。要发正式签名版，需要在仓库 Secrets 里配 keystore
（参考 tagr-apk 的 `TAGR_KEYSTORE_*` 那套做法：`*_KEYSTORE_BASE64`、
`*_KEYSTORE_PASSWORD`、`*_KEY_ALIAS`、`*_KEY_PASSWORD`），
并在 `app/build.gradle` 里加上 `signingConfigs.release`。
