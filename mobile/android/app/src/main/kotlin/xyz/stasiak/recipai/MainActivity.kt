package xyz.stasiak.recipai

import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        ShareIntentBridge.attach(flutterEngine.dartExecutor.binaryMessenger, applicationContext, this)
    }

    // super.onCreate first (conventional FlutterActivity order); stageInitialShare only writes
    // to a static field and consumeInitialShare is post-first-frame, so order is safe.
    // design.md suggests calling stageInitialShare before super.onCreate, but that risks
    // engine-init issues — deviating intentionally; noted in PR.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShareIntentBridge.stageInitialShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShareIntentBridge.handleNewIntent(intent)
    }
}
