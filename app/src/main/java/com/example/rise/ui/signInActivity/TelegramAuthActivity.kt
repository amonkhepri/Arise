package com.example.rise.ui.signInActivity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.example.rise.BuildConfig
import com.example.rise.R
import com.example.rise.baseclasses.BaseActivity
import com.example.rise.data.auth.TelegramAuthData

class TelegramAuthActivity : BaseActivity() {

    companion object {
        const val EXTRA_AUTH_DATA = "com.example.rise.telegram.AUTH_DATA"
        const val EXTRA_ERROR_MESSAGE = "com.example.rise.telegram.ERROR"
        private const val TELEGRAM_AUTH_BASE_URL = "https://oauth.telegram.org/auth"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isConfigValid()) {
            cancelWithMessage(getString(R.string.sign_in_telegram_not_configured))
            return
        }

        setContent {
            MaterialTheme {
                TelegramAuthContent(
                    title = getString(R.string.sign_in_continue_telegram),
                    url = buildTelegramUrl().toString(),
                    onClose = { cancelWithMessage(getString(R.string.sign_in_telegram_cancelled)) },
                    onAuthComplete = { data ->
                        val intent = Intent().putExtra(EXTRA_AUTH_DATA, data)
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onMissingData = { cancelWithMessage(getString(R.string.sign_in_telegram_missing_data)) }
                )
            }
        }
    }

    private fun isConfigValid(): Boolean {
        return BuildConfig.TELEGRAM_BOT_ID.isNotBlank() &&
            BuildConfig.TELEGRAM_BOT_ID != "000000000" &&
            BuildConfig.TELEGRAM_REDIRECT_URI.isNotBlank() &&
            BuildConfig.TELEGRAM_LOGIN_DOMAIN.isNotBlank()
    }

    private fun buildTelegramUrl(): Uri {
        return Uri.parse(TELEGRAM_AUTH_BASE_URL).buildUpon()
            .appendQueryParameter("bot_id", BuildConfig.TELEGRAM_BOT_ID)
            .appendQueryParameter("origin", BuildConfig.TELEGRAM_LOGIN_DOMAIN)
            .appendQueryParameter("request_access", "write")
            .appendQueryParameter("embed", "1")
            .appendQueryParameter("redirect_uri", BuildConfig.TELEGRAM_REDIRECT_URI)
            .build()
    }

    private fun cancelWithMessage(message: String) {
        val intent = Intent().putExtra(EXTRA_ERROR_MESSAGE, message)
        setResult(RESULT_CANCELED, intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramAuthContent(
    title: String,
    url: String,
    onClose: () -> Unit,
    onAuthComplete: (TelegramAuthData) -> Unit,
    onMissingData: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    val webView = rememberWebView(onLoadingChanged = { loading -> isLoading = loading }, onRedirect = { uri ->
        val redirect = BuildConfig.TELEGRAM_REDIRECT_URI
        if (redirect.isNotBlank() && uri.toString().startsWith(redirect)) {
            parseAuthData(uri)?.let { onAuthComplete(it) } ?: onMissingData()
            true
        } else {
            false
        }
    })

    LaunchedEffect(url) {
        webView.loadUrl(url)
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = title)
                    }
                }
            )
        }
    ) { padding ->
        Surface(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun rememberWebView(
    onLoadingChanged: (Boolean) -> Unit,
    onRedirect: (Uri) -> Boolean
): WebView {
    val context = LocalContext.current
    val loadingCallback by rememberUpdatedState(newValue = onLoadingChanged)
    val redirectCallback by rememberUpdatedState(newValue = onRedirect)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadingCallback(newProgress < 100)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    return redirectCallback(uri)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val uri = url?.let(Uri::parse) ?: return false
                    return redirectCallback(uri)
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    return webView
}

private fun parseAuthData(uri: Uri): TelegramAuthData? {
    val params = mutableMapOf<String, String>()
    for (name in uri.queryParameterNames) {
        uri.getQueryParameter(name)?.let { params[name] = it }
    }
    if (params.isEmpty() && !uri.fragment.isNullOrBlank()) {
        val fragment = Uri.parse("https://placeholder?${uri.fragment}")
        for (name in fragment.queryParameterNames) {
            fragment.getQueryParameter(name)?.let { params[name] = it }
        }
    }
    val id = params["id"]?.toLongOrNull() ?: return null
    val firstName = params["first_name"] ?: return null
    val authDate = params["auth_date"]?.toLongOrNull() ?: return null
    val hash = params["hash"] ?: return null
    return TelegramAuthData(
        id = id,
        firstName = firstName,
        lastName = params["last_name"],
        username = params["username"],
        photoUrl = params["photo_url"],
        authDate = authDate,
        hash = hash,
    )
}
