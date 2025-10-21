package com.ld.tageditor;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    MainActivity activity = this;
    private IntentFilter[] mFilters;
    private PendingIntent mPendingIntent;
    private String[][] mTechLists;
    NfcAdapter nfc;
    public volatile Tag tag;
    WebView webView;

    /* Access modifiers changed, original: protected */
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Use the symbolic Intent flag and explicitly provide PendingIntent flags (mutable)
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.mPendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        try {
            // 1. Filter for NDEF-formatted tags
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            ndef.addDataType("*/*");

            // 2. Filter for tags that match the mTechLists (e.g., NfcA)
            IntentFilter tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);

            // Use both filters
            mFilters = new IntentFilter[]{ndef, tech};

            String[][] strArr = new String[1][];
            strArr[0] = new String[]{NfcA.class.getName()};
            this.mTechLists = strArr;
            this.nfc = NfcAdapter.getDefaultAdapter(this);
            this.webView = findViewById(R.id.webView);
            this.webView.getSettings().setJavaScriptEnabled(true);
            this.webView.getSettings().setAllowFileAccess(true);
            this.webView.getSettings().setAllowFileAccessFromFileURLs(true);
            this.webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
            this.webView.setWebChromeClient(new WebChromeClient() {
                public void onProgressChanged(WebView view, int progress) {
                    MainActivity.this.activity.setProgress(progress * 100);
                }
            });
            this.webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    String host = Uri.parse(url).getHost();
                    ArrayList<String> allowedHosts = new ArrayList<>();
                    allowedHosts.add("android_asset");
                    allowedHosts.add("127.0.0.1");
                    ArrayList<String> allowedSubstring = new ArrayList<>();
                    allowedSubstring.add("192.168.");
                    if (host != null && allowedHosts.contains(host)) {
                        return false;
                    }
                    if (host != null) {
                        for (String s : allowedSubstring) {
                            if (host.startsWith(s)) {
                                return false;
                            }
                        }
                    }
                    view.evaluateJavascript("(function(){ var err = 'ACCESS TO URL " + url + " DENIED'; console.error(err); if(window.appErrorHandler) window.appErrorHandler(err); })();", null);
                    return true;
                }
            });
            WebView.setWebContentsDebuggingEnabled(true);
            this.webView.addJavascriptInterface(new JSAPI(this, this.webView), "AndroidApp");
            this.webView.loadUrl("file:///android_asset/index.html");
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.nfc != null) {
            this.nfc.enableForegroundDispatch(this, this.mPendingIntent, this.mFilters, this.mTechLists);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.nfc != null) {
            this.nfc.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String action = intent.getAction();

        // Only process the intent if it is an NFC discovery event
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            // CORRECTLY retrieves the Tag object
            this.tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            Log.i("Foreground dispatch", "Discovered tag");

            // Pass the tag id to JavaScript (base64) so the page doesn't need to rely on timing
            // The null check for this.tag is good practice here.
            byte[] id = (this.tag != null) ? this.tag.getId() : new byte[0];
            String idB64 = Base64.encodeToString(id, Base64.NO_WRAP);

            Log.i("Foreground dispatch", "TAG: " + idB64);

            // Call the JS callbacks namespace method that is defined in app.js
            callJavaScript("AndroidAppCallbacks.tagDetected", idB64);
        }
    }


    private void callJavaScript(String methodName, Object... params) {
        Log.i(TAG, "CallJS Building string...");
        StringBuilder stringBuilder = new StringBuilder();
        // Build a safe JavaScript snippet (we'll pass it to evaluateJavascript)
        stringBuilder.append("try{(window.");
        stringBuilder.append(methodName);
        stringBuilder.append("||console.warn.bind(console,'UNHANDLED','");
        stringBuilder.append(methodName);
        stringBuilder.append("'))(");
        boolean first = true;
        for (Object param : params) {
            if (!first) {
                stringBuilder.append(',');
            }
            first = false;
            String param2 = (param == null) ? "" : param.toString();
            // Use JSONObject.quote to properly escape the string literal for JS
            stringBuilder.append(JSONObject.quote(param2));
        }

        // ensure at least one argument (some callers expect an empty string sentinel)
        if (first) {
            stringBuilder.append(JSONObject.quote(""));
        }
        stringBuilder.append(")}catch(error){console.error('ANDROID APP ERROR',error);}{}");

        final String jsSnippet = stringBuilder.toString();
        // evaluateJavascript must run on the UI thread; post to the WebView's handler
        this.webView.post(() -> {
            try {
                webView.evaluateJavascript(jsSnippet, null);
            } catch (Exception e) {
                Log.e(TAG, "evaluateJavascript failed, falling back to loadUrl", e);
                webView.loadUrl("javascript:" + jsSnippet);
            }
        });

        Log.i(TAG, "CallJS javascript:" + jsSnippet);

    }
}
