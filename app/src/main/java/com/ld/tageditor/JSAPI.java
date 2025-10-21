package com.ld.tageditor;

import android.nfc.tech.NfcA;
import android.content.Intent;
import android.nfc.Tag;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.io.IOException;

public class JSAPI {
    MainActivity activity;
    WebView web;

    public JSAPI(MainActivity paramActivity, WebView paramWeb) {
        this.activity = paramActivity;
        this.web = paramWeb;
    }

    @JavascriptInterface
    public String readTag(byte page) {
        if (this.activity == null) {
            Log.w("JSAPI", "readTag called but activity is null");
            return "";
        }
        // If activity.tag is null, try to recover it from the current activity intent (onNewIntent sets it)
        if (this.activity.tag == null) {
            Log.w("JSAPI", "activity.tag is null; attempting to recover from activity.getIntent()...");
            Intent intent = this.activity.getIntent();
            if (intent != null) {
                Tag t = (Tag) intent.getParcelableExtra("android.nfc.extra.TAG");
                if (t != null) {
                    this.activity.tag = t;
                    Log.i("JSAPI", "Recovered activity.tag from intent");
                } else {
                    Log.w("JSAPI", "No tag found on activity intent");
                    return "";
                }
            } else {
                Log.w("JSAPI", "activity.getIntent() returned null");
                return "";
            }
        }
        NfcA nfcA = NfcA.get(this.activity.tag);
        if (nfcA == null) {
            Log.w("JSAPI", "NfcA.get returned null for current tag");
            return null;
        }
        try {
            Log.i("JSAPI", "Connecting");
            nfcA.connect();
            Log.i("JSAPI", "Connected");
            Log.i("JSAPI", "Read");

            byte[] message = new byte[] {
                    0x30,
                    (byte)(page & 0xFF)
            };

            byte[] payload = nfcA.transceive(message);
//            Log.i("JSAPI", String.format("Payload %02X%02X%02X%02X %02X%02X%02X%02X %02X%02X%02X%02X %02X%02X%02X%02X", new Object[]{Byte.valueOf(payload[0]), Byte.valueOf(payload[1]), Byte.valueOf(payload[2]), Byte.valueOf(payload[3]), Byte.valueOf(payload[4]), Byte.valueOf(payload[5]), Byte.valueOf(payload[6]), Byte.valueOf(payload[7]), Byte.valueOf(payload[8]), Byte.valueOf(payload[9]), Byte.valueOf(payload[10]), Byte.valueOf(payload[11]), Byte.valueOf(payload[12]), Byte.valueOf(payload[13]), Byte.valueOf(payload[14]), Byte.valueOf(mifare.readPages(page)[15])}));
            String encodeToString = Base64.encodeToString(payload, 0, 16, 0);
            Log.i("JSAPI", encodeToString);
            return encodeToString;
        } catch (IOException e) {
            Log.e("JSAPI", "IOException while transceiving with NfcA...", e);
            if (e.getMessage() != null) Log.e("JSAPI", e.getMessage());
            return "";
        } finally {
            if (nfcA!= null) {
                try {
                    nfcA.close();
                } catch (IOException e22) {
                    Log.e("JSAPI", "Error closing tag...", e22);
                }
            }
        }
    }

    @JavascriptInterface
    public boolean writeTag(byte page, String payload) {
        byte[] data = Base64.decode(payload, 0);
        if (this.activity == null) {
            Log.w("JSAPI", "writeTag called but activity is null");
            return false;
        }
        if (this.activity.tag == null) {
            Log.w("JSAPI", "writeTag called but no tag is available (activity.tag is null)");
            return false;
        }
        NfcA nfca = NfcA.get(this.activity.tag);
        if (nfca == null) {
            Log.w("JSAPI", "NfcA.get returned null for current tag (write)");
            return false;
        }
        try {
            Log.i("JSAPI", "Connecting");
            nfca.connect();
            Log.i("JSAPI", "Connected");
            Log.i("JSAPI", String.format("Writing %02X%02X%02X%02X", new Object[]{Byte.valueOf(data[0]), Byte.valueOf(data[1]), Byte.valueOf(data[2]), Byte.valueOf(data[3])}));
            byte[] message = new byte[] {
                    (byte) 0xA2,
                    (byte)(page & 0xFF),
                    data[0], data[1], data[2], data[3]
            };
            byte[] result = nfca.transceive(message);
            Log.i("JSAPI", "Writing Done");
            try {
                Log.i("JSAPI", "Closing");
                nfca.close();
                Log.i("JSAPI", "Closed");
                return true;
            } catch (IOException e) {
                Log.e("JSAPI", "IOException while closing MifareUltralight...", e);
                return false;
            }
        } catch (IOException e2) {
            Log.e("JSAPI", "IOException during write/transceive...", e2);
            try {
                if (nfca != null && nfca.isConnected()) {
                    Log.i("JSAPI", "Closing after IOException");
                    nfca.close();
                    Log.i("JSAPI", "Closed");
                }
            } catch (IOException closeEx) {
                Log.e("JSAPI", "IOException while closing after error...", closeEx);
            }
            return false;
        } catch (Throwable th) {
            Log.e("JSAPI", "Unexpected throwable in writeTag", th);
            try {
                if (nfca != null && nfca.isConnected()) {
                    nfca.close();
                }
            } catch (IOException e222) {
                Log.e("JSAPI", "IOException while closing after throwable...", e222);
            }
            return false;
        }
    }

    private void callJavaScript(String methodName, Object... params) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("javascript:try{(window.");
        stringBuilder.append(methodName);
        stringBuilder.append("||console.warn.bind(console,'UNHANDLED','");
        stringBuilder.append(methodName);
        stringBuilder.append("'))(");
        for (Object param : params) {
            // Always convert param to string so string params are passed through correctly
            String param2 = (param == null) ? "" : param.toString();
            stringBuilder.append("'");
            stringBuilder.append(param2);
            stringBuilder.append("'");
            stringBuilder.append(",");
        }
        stringBuilder.append("''");
        stringBuilder.append(")}catch(error){console.error('ANDROID APP ERROR',error);}");
        this.web.loadUrl(stringBuilder.toString());
        Log.i("CallJS", stringBuilder.toString());
    }
}
