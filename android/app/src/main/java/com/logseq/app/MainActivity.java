package com.logseq.app;

import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.EdgeToEdge;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.PluginCall;
import com.getcapacitor.JSObject;
import com.getcapacitor.BridgeActivity;
import androidx.activity.OnBackPressedDispatcher;
import android.util.Log;
import android.view.View;
import kotlinx.coroutines.CompletableDeferred;

public class MainActivity extends BridgeActivity {
    private NavigationCoordinator navigationCoordinator = new NavigationCoordinator();
    private BroadcastReceiver routeChangeReceiver;
    private boolean webViewLoaded = false;
    private CompletableDeferred<String> folderPickerDeferred = null;

    public void pickFolder(CompletableDeferred<String> deferred) {
        this.folderPickerDeferred = deferred;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(intent, 9999);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9999 && folderPickerDeferred != null) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                android.net.Uri treeUri = data.getData();
                android.net.Uri docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri,
                        android.provider.DocumentsContract.getTreeDocumentId(treeUri));
                String path = FileUtil.getPath(this, docUri);
                folderPickerDeferred.complete(path);
            } else {
                folderPickerDeferred.complete(null);
            }
            folderPickerDeferred = null;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FolderPicker.class);
        registerPlugin(UILocal.class);
        registerPlugin(NativeTopBarPlugin.class);
        registerPlugin(NativeBottomSheetPlugin.class);
        registerPlugin(NativeEditorToolbarPlugin.class);
        registerPlugin(NativeSelectionActionBarPlugin.class);
        registerPlugin(LiquidTabsPlugin.class);
        registerPlugin(Utils.class);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        WebView webView = getBridge().getWebView();
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);

        applyLogseqTheme();

        // Let Compose host the WebView with system bar padding for safe areas and handle back.
        ComposeHost.INSTANCE.renderWithSystemInsets(this, webView, () -> {
            sendJsBack(webView);
            return null;
        }, () -> {
            finish();
            return null;
        });

        routeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!UILocal.ACTION_ROUTE_CHANGED.equals(intent.getAction())) return;
                String stack = intent.getStringExtra("stack");
                String navigationType = intent.getStringExtra("navigationType");
                String path = intent.getStringExtra("path");
                navigationCoordinator.onRouteChange(stack, navigationType, path);
            }
        };
        IntentFilter filter = new IntentFilter(UILocal.ACTION_ROUTE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(routeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(routeChangeReceiver, filter);
        }

        // initNavigationBarBgColor();
    }

    private void dispatchSendIntentEvent() {
        bridge.eval("window.dispatchEvent(new Event('sendIntentReceived'))", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                //
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLogseqTheme();
        dispatchSystemThemeToWeb();
    }

    private void applyLogseqTheme() {
        LogseqTheme.INSTANCE.update(this);
        LogseqThemeColors colors = LogseqTheme.INSTANCE.current();

        int bg = colors.getBackground();
        boolean isDark = colors.isDark();

        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(bg);
        }

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            webView.setBackgroundColor(bg);
        }

        getWindow().getDecorView().setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!isDark);
        controller.setAppearanceLightNavigationBars(!isDark);

        WebViewSnapshotManager.INSTANCE.setSnapshotBackgroundColor(bg);
    }

    public void applyLogseqThemeNow() {
        applyLogseqTheme();
    }

    private void dispatchSystemThemeToWeb() {
        try {
            if (bridge == null) return;
            boolean isDark = LogseqTheme.INSTANCE.isDark(this);
            String js = "window.dispatchEvent(new CustomEvent('logseq:native-system-theme-changed', { detail: { isDark: "
                + (isDark ? "true" : "false")
                + " } }));";
            bridge.eval(js, null);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onPause() {
        overridePendingTransition(0, R.anim.byebye);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        Log.d("onBackPressed", "Debug");
        super.onBackPressed();
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            bridge.getActivity().setIntent(intent);
            dispatchSendIntentEvent();
        }
    }

    @Override
    public void onDestroy() {
        if (routeChangeReceiver != null) {
            unregisterReceiver(routeChangeReceiver);
            routeChangeReceiver = null;
        }
        super.onDestroy();
    }

    private void sendJsBack(WebView webView) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
            "window.LogseqNative && window.LogseqNative.onNativePop && window.LogseqNative.onNativePop();",
            null
        ));
    }
}
 }
        super.onDestroy();
    }

    private void sendJsBack(WebView webView) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(
            "window.LogseqNative && window.LogseqNative.onNativePop && window.LogseqNative.onNativePop();",
            null
        ));
    }
}
