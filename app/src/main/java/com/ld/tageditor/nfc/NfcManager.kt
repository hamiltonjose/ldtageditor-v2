package com.ld.tageditor.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class NfcEvent {
    data class TagDiscovered(val tag: Tag): NfcEvent()
    object TagLost : NfcEvent()
}

class NfcManager(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    private val _events = MutableSharedFlow<NfcEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events = _events.asSharedFlow()

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        // emit tag discovered
        _events.tryEmit(NfcEvent.TagDiscovered(tag))
    }

    fun isNfcAvailable(): Boolean {
        return adapter != null
    }

    fun isNfcEnabled(): Boolean {
        return adapter?.isEnabled ?: false
    }

    fun enableReaderMode() {
        adapter?.enableReaderMode(activity, readerCallback,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    fun disableReaderMode() {
        adapter?.disableReaderMode(activity)
    }

    suspend fun readPage(tag: Tag, page: Byte): ByteArray = suspendCancellableCoroutine { cont: CancellableContinuation<ByteArray> ->
        val nfca = NfcA.get(tag)
        try {
            nfca.connect()
            val cmd = byteArrayOf(0x30, page)
            val resp = nfca.transceive(cmd)
            // resp should be 16 bytes for readPages
            cont.resume(resp.copyOfRange(0, Math.min(resp.size, 16)))
        } catch (e: IOException) {
            cont.resumeWithException(e)
        } finally {
            try { nfca.close() } catch (_: Exception) {}
        }
    }

    suspend fun writePage(tag: Tag, page: Byte, data4: ByteArray): Boolean = suspendCancellableCoroutine { cont: CancellableContinuation<Boolean> ->
        val nfca = NfcA.get(tag)
        try {
            nfca.connect()
            val cmd = ByteArray(6)
            cmd[0] = 0xA2.toByte()
            cmd[1] = page
            System.arraycopy(data4, 0, cmd, 2, 4.coerceAtMost(data4.size))
            nfca.transceive(cmd)
            // assume success if no exception
            cont.resume(true)
        } catch (e: IOException) {
            cont.resumeWithException(e)
        } finally {
            try { nfca.close() } catch (_: Exception) {}
        }
    }
}
