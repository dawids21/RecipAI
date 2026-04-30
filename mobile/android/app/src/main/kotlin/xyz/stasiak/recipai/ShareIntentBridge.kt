package xyz.stasiak.recipai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlin.random.Random

object ShareIntentBridge {
    private const val METHOD_CHANNEL = "recipai/share"
    private const val EVENT_CHANNEL = "recipai/share/events"

    private var stagedPayload: Map<String, Any>? = null
    private var eventSink: EventChannel.EventSink? = null
    private var appContext: Context? = null

    fun attach(messenger: BinaryMessenger, context: Context) {
        appContext = context.applicationContext
        MethodChannel(messenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "consumeInitialShare") {
                result.success(stagedPayload)
                stagedPayload = null
            } else {
                result.notImplemented()
            }
        }

        EventChannel(messenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    eventSink = sink
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            }
        )
    }

    fun stageInitialShare(intent: Intent) {
        wipeShareCacheDir()
        val payload = extractTextPayload(intent) ?: extractImagePayload(intent) ?: return
        stagedPayload = payload
    }

    fun handleNewIntent(intent: Intent) {
        wipeShareCacheDir()
        val payload = extractTextPayload(intent) ?: extractImagePayload(intent) ?: return
        eventSink?.success(payload)
    }

    private fun extractTextPayload(intent: Intent): Map<String, Any>? {
        if (intent.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return mapOf("type" to "text", "text" to text)
    }

    private fun extractImagePayload(intent: Intent): Map<String, Any>? {
        if (intent.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null

        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri ?: return null

        val ctx = appContext ?: return null
        val mime = ctx.contentResolver.getType(uri) ?: intent.type ?: "image/jpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"

        val dir = java.io.File(ctx.cacheDir, "share_intent").apply { mkdirs() }
        val name = "${System.currentTimeMillis()}-${Random.nextInt(0, Int.MAX_VALUE).toString(16)}.$ext"
        val outFile = java.io.File(dir, name)

        ctx.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        return mapOf("type" to "image", "imagePath" to outFile.absolutePath)
    }

    private fun wipeShareCacheDir() {
        val ctx = appContext ?: return
        val dir = java.io.File(ctx.cacheDir, "share_intent")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
    }
}
