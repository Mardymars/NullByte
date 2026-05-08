package com.nullbyte.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class MainActivity : ComponentActivity() {
    private val sharedUris = MutableStateFlow<List<Uri>>(emptyList())
    private val tutorialRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            NullByteRoot(
                sharedUris = sharedUris,
                tutorialRequests = tutorialRequests,
                onSharedUrisConsumed = { sharedUris.value = emptyList() },
                onTutorialRequestConsumed = { tutorialRequests.update { count -> (count - 1).coerceAtLeast(0) } },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(EXTRA_OPEN_TUTORIAL, false)) {
            tutorialRequests.update { it + 1 }
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.extractSharedUri()?.let { uri ->
                    sharedUris.value = listOf(uri)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val incoming = intent.extractSharedUris()
                if (incoming.isNotEmpty()) {
                    sharedUris.value = incoming.distinct()
                }
            }
        }
    }

    private fun Intent.extractSharedUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun Intent.extractSharedUris(): List<Uri> {
        val uris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }

        return uris.orEmpty()
    }

    companion object {
        const val EXTRA_OPEN_TUTORIAL = "open_tutorial"
    }
}
