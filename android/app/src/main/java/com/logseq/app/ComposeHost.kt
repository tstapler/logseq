package com.logseq.app

import android.graphics.Color
import android.app.Activity
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import com.logseq.kmp.ui.LogseqApp
import com.logseq.kmp.platform.PlatformFileSystem
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableSharedFlow

private const val ROOT_ROUTE = "web/{encodedPath}"

data class NavigationEvent(
    val navigationType: String,
    val path: String
)

/**
 * Hosts the existing WebView inside Compose and drives Compose Navigation
 * so we get back gestures/animations while delegating actual routing to the JS layer.
 */
object ComposeHost {
    private val navEvents = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 64)

    fun applyNavigation(navigationType: String?, path: String?) {
        val type = (navigationType ?: "push").lowercase()
        val safePath = path?.takeIf { it.isNotBlank() } ?: "/"
        navEvents.tryEmit(NavigationEvent(type, safePath))
    }

    fun renderWithSystemInsets(
        activity: Activity,
        webView: WebView,
        onBackRequested: () -> Unit,
        onExit: () -> Unit = { activity.finish() }
    ) {
        WebViewSnapshotManager.registerWindow(activity.window)
        val root = activity.findViewById<FrameLayout>(android.R.id.content)

        // WebView already created by BridgeActivity; just reparent it into Compose.
        (webView.parent as? ViewGroup)?.removeView(webView)

        val composeView = ComposeView(activity).apply {
            tag = "compose-host-webview"
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val fileSystem = remember { 
                    PlatformFileSystem().apply {
                        init(context)
                    }
                }
                
                // KMP Integration: Render the new KMP App instead of the WebView wrapper
                LogseqApp(
                    fileSystem = fileSystem,
                    graphPath = fileSystem.getDefaultGraphPath()
                )
            }
        }

        if (root.findViewWithTag<ComposeView>("compose-host-webview") == null) {
            root.addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }
}


