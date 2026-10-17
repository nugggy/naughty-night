package au.naughtynight;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    // Releases on this GitHub repo are the app's update channel.
    static final String REPO = "nugggy/naughty-night";
    static final String ACTION_INSTALL = "au.naughtynight.INSTALL_STATUS";

    private WebView web;
    private volatile boolean busy;
    private String pendingUrl;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF14001F);
        getWindow().setNavigationBarColor(0xFF14001F);
        web = new WebView(this);
        web.setBackgroundColor(0xFF14001F);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "NN");
        setContentView(web);
        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");

        IntentFilter f = new IntentFilter(ACTION_INSTALL);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(installReceiver, f);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(installReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the "install unknown apps" settings screen.
        if (pendingUrl != null && canInstall()) {
            final String url = pendingUrl;
            pendingUrl = null;
            new Thread(() -> doInstall(url)).start();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    private void handleBack() {
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    // ---------- updater ----------

    class Bridge {
        @JavascriptInterface public String version() { return currentVersion(); }
        @JavascriptInterface public void check(final String channel) { new Thread(() -> doCheck(channel)).start(); }
        @JavascriptInterface public void install(final String url) { new Thread(() -> doInstall(url)).start(); }
        @JavascriptInterface public void openReleases() {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + REPO + "/releases")));
        }
    }

    private String currentVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "0"; }
    }

    private boolean canInstall() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    private void emit(String state, Object... kv) {
        try {
            JSONObject o = new JSONObject();
            o.put("state", state);
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
            final String js = "window.nnUpdate&&window.nnUpdate(" + o + ")";
            runOnUiThread(() -> web.evaluateJavascript(js, null));
        } catch (Exception ignored) { }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "NaughtyNight-Updater");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("Server returned " + code);
        return c;
    }

    private void doCheck(String channel) {
        try {
            HttpURLConnection c = open("https://api.github.com/repos/" + REPO + "/releases?per_page=20");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) { copy(in, buf, -1, false); }
            JSONArray rels = new JSONArray(buf.toString("UTF-8"));
            boolean beta = "beta".equals(channel);
            String current = currentVersion();
            for (int i = 0; i < rels.length(); i++) {
                JSONObject r = rels.getJSONObject(i);
                if (r.optBoolean("draft")) continue;
                if (!beta && r.optBoolean("prerelease")) continue;
                String apk = null;
                JSONArray assets = r.optJSONArray("assets");
                for (int j = 0; assets != null && j < assets.length(); j++) {
                    JSONObject a = assets.getJSONObject(j);
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break; }
                }
                if (apk == null) continue;
                String ver = r.optString("tag_name").replaceFirst("^[vV]", "");
                emit(isNewer(ver, current) ? "available" : "current",
                    "version", ver, "current", current, "url", apk,
                    "notes", r.optString("body"), "prerelease", r.optBoolean("prerelease"));
                return;
            }
            emit("current", "version", current, "current", current);
        } catch (Exception e) {
            emit("error", "message", "Couldn't check for updates: " + e.getMessage());
        }
    }

    private void doInstall(String url) {
        if (busy) return;
        if (!canInstall()) {
            pendingUrl = url;
            emit("permission");
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()))));
            return;
        }
        busy = true;
        PackageInstaller.Session session = null;
        try {
            emit("downloading", "progress", 0);
            HttpURLConnection c = open(url);
            long total = c.getContentLengthLong();
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(getPackageName());
            if (Build.VERSION.SDK_INT >= 31) {
                p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            session = pi.openSession(pi.createSession(p));
            try (InputStream in = c.getInputStream();
                 OutputStream out = session.openWrite("base.apk", 0, total)) {
                copy(in, out, total, true);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            Intent status = new Intent(ACTION_INSTALL).setPackage(getPackageName());
            PendingIntent pend = PendingIntent.getBroadcast(this, 0, status, flags);
            emit("installing");
            session.commit(pend.getIntentSender());
            session.close();
            session = null;
        } catch (Exception e) {
            if (session != null) session.abandon();
            emit("error", "message", "Update failed: " + e.getMessage());
        } finally {
            busy = false;
        }
    }

    private void copy(InputStream in, OutputStream out, long total, boolean report) throws IOException {
        byte[] b = new byte[16384];
        long done = 0;
        int n, last = -1;
        while ((n = in.read(b)) > 0) {
            out.write(b, 0, n);
            done += n;
            if (report && total > 0) {
                int pct = (int) (done * 100 / total);
                if (pct / 5 != last / 5) { last = pct; emit("downloading", "progress", pct); }
            }
        }
    }

    static boolean isNewer(String a, String b) {
        String[] x = a.split("[.-]"), y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    static int num(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        try { return i == 0 ? 0 : Integer.parseInt(s.substring(0, i)); } catch (Exception e) { return 0; }
    }

    private final BroadcastReceiver installReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirm);
                }
                emit("confirm");
            } else if (st == PackageInstaller.STATUS_SUCCESS) {
                emit("installed");
            } else {
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                emit("error", "message", st == PackageInstaller.STATUS_FAILURE_ABORTED
                    ? "Install cancelled." : "Install failed: " + msg);
            }
        }
    };
}
