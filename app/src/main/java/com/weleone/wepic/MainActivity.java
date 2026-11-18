package com.weleone.wepic;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_FILE_CHOOSER = 1001;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // 本地文件互访：GIF 导出用的 gif.worker.js 是 file:// 下的 Worker，
        // 默认 file:// 不许建 Worker，这两项不开 worker 加载不了
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // 关掉 WebView 的强制深色，页面自己的颜色不动
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            s.setAlgorithmicDarkeningAllowed(false);
        }
        s.setForceDark(WebSettings.FORCE_DARK_OFF);

        // 导出保存桥：页面把 blob 转 base64 传进来，原生存到下载目录
        webView.addJavascriptInterface(new WepicBridge(), "WepicBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                // 页面 input 带 multiple，允许多选图片
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{ data.getData() };
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private void toastOnUi(String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    // 存到公开下载目录：Android 10+ 走 MediaStore，不用申请权限；
    // 旧版走公开下载目录直写。分图是逐个调进来，不弹保存框
    private void saveToDownloads(byte[] data, String fileName, String mimeType) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    toastOnUi("保存失败");
                    return;
                }
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        toastOnUi("保存失败");
                        return;
                    }
                    out.write(data);
                }
            } else {
                java.io.File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, fileName);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                    out.write(data);
                }
            }
            toastOnUi("已保存到下载：" + fileName);
        } catch (Exception e) {
            toastOnUi("保存失败");
        }
    }

    private class WepicBridge {
        @JavascriptInterface
        public void saveFile(String base64, String fileName, String mimeType) {
            try {
                byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                saveToDownloads(data, fileName, mimeType);
            } catch (Exception e) {
                toastOnUi("保存失败");
            }
        }
    }
}
