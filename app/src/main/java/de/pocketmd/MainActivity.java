package de.pocketmd;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_FILES = 41;
    private static final int SAVE_FILE = 42;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private WebView web;
    private boolean pageReady = false;
    private final List<String[]> pending = new ArrayList<>();
    private String pendingSaveText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                flushPending();
            }
        });

        web.loadUrl("file:///android_asset/index.html");
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            loadUri(intent.getData());
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) {
                loadUri(stream);
            } else {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null) deliver("Shared.md", text);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == SAVE_FILE) pendingSaveText = null;
            return;
        }
        if (requestCode == PICK_FILES) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) loadUri(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                loadUri(data.getData());
            }
        } else if (requestCode == SAVE_FILE && data.getData() != null && pendingSaveText != null) {
            Uri uri = data.getData();
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new Exception("no stream");
                out.write(pendingSaveText.getBytes(StandardCharsets.UTF_8));
                jsToast("Saved " + displayName(uri));
            } catch (Exception e) {
                jsToast("Couldn't save the file. Try another folder.");
            }
            pendingSaveText = null;
        }
    }

    private void loadUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("no stream");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) break;
            }
            deliver(displayName(uri), new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open that file", Toast.LENGTH_SHORT).show();
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
        }
        if (name == null) name = uri.getLastPathSegment();
        return name != null ? name : "Untitled.md";
    }

    private void deliver(String name, String text) {
        pending.add(new String[]{name, text});
        if (pageReady) flushPending();
    }

    private void flushPending() {
        for (String[] f : pending) {
            web.evaluateJavascript("window.openFromApp(" + JSONObject.quote(f[0]) + "," + JSONObject.quote(f[1]) + ")", null);
        }
        pending.clear();
    }

    private void jsToast(String msg) {
        runOnUiThread(() -> web.evaluateJavascript("window.appToast&&window.appToast(" + JSONObject.quote(msg) + ")", null));
    }

    @Override
    public void onBackPressed() {
        if (!pageReady) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("(window.handleBack&&window.handleBack())?'1':'0'", value -> {
            if (!"\"1\"".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    private class Bridge {
        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Markdown", text));
        }

        @JavascriptInterface
        public String paste() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm == null || !cm.hasPrimaryClip()) return "";
                ClipData clip = cm.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) return "";
                CharSequence t = clip.getItemAt(0).coerceToText(MainActivity.this);
                return t == null ? "" : t.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void pick() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "application/octet-stream"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(i, PICK_FILES);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No file picker found", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void save(String name, String text, String mime) {
            pendingSaveText = text;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, SAVE_FILE);
                } catch (Exception e) {
                    pendingSaveText = null;
                    Toast.makeText(MainActivity.this, "No file app found to save with", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void share(String name, String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, name);
                i.putExtra(Intent.EXTRA_TEXT, text);
                try {
                    startActivity(Intent.createChooser(i, "Share " + name));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "This file is too large to share as text", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void keepScreenOn(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void setBars(String color, boolean lightBackground) {
            runOnUiThread(() -> {
                try {
                    int c = Color.parseColor(color);
                    Window w = getWindow();
                    w.setStatusBarColor(c);
                    w.setNavigationBarColor(c);
                    View d = w.getDecorView();
                    int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    int flags = d.getSystemUiVisibility();
                    d.setSystemUiVisibility(lightBackground ? (flags | light) : (flags & ~light));
                } catch (Exception ignored) {
                }
            });
        }
    }
}
