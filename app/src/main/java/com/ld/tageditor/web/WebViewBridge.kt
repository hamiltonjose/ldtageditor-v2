package com.ld.tageditor.web

import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebView
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Simple JSON message bridge between WebView and native.
 * Messages are JSON objects with { id, method, params }
 * Responses: { id, result } or { id, error }
 */
class WebViewBridge(private val webView: WebView) {
    private val TAG = "WebViewBridge"

    fun sendResponse(id: String, result: Any?) {
        val json = JSONObject()
        json.put("id", id)
        json.put("result", result)
        postToWeb(json)
    }

    fun sendError(id: String, error: String) {
        val json = JSONObject()
        json.put("id", id)
        json.put("error", error)
        postToWeb(json)
    }

    private fun postToWeb(json: JSONObject) {
        val script = "window.__nativeCallback && window.__nativeCallback(${JSONObject.quote(json.toString())});"
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(script, null)
                } else {
                    webView.loadUrl("javascript:$script")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error posting to web", e)
            }
        }
    }

    // Fallback single entry point for older webviews
    @JavascriptInterface
    fun handleMessage(json: String) {
        // parse and emit to native. This method is intentionally a no-op here.
        Log.i(TAG, "Received message from JS (fallback): $json")
        // Native message handling happens in MainActivity which has a reference to NfcManager.
    }
}
