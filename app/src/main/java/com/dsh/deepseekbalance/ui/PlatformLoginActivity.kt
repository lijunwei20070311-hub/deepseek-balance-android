package com.dsh.deepseekbalance.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.dsh.deepseekbalance.DsbApp
import com.dsh.deepseekbalance.R
import com.dsh.deepseekbalance.api.PlatformApi
import com.dsh.deepseekbalance.databinding.ActivityPlatformLoginBinding
import com.dsh.deepseekbalance.prefs.DsbPrefs
import com.dsh.deepseekbalance.widget.WidgetUpdater
import kotlinx.coroutines.launch

/**
 * 用平台账号登录（platform.deepseek.com），拿到登录态 userToken。
 *
 * 为什么需要它：API Key 只能读到余额；「账号下全部 tokens 用量」只有平台网页
 * 后台的私有接口能给出，而它认的是登录态。登录页里已登录时 localStorage.userToken
 * 就是需要的凭据，所以这里用 WebView 完成登录后自动取出并校验。
 */
class PlatformLoginActivity : BaseActivity() {

    private lateinit var binding: ActivityPlatformLoginBinding
    private var captured = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlatformLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = DsbPrefs.get(this)
        binding.tvStatus.text = getString(R.string.platform_login_help)
        if (prefs.platformToken.isNotBlank()) {
            binding.etManual.setText("")
            binding.tvStatus.text = getString(R.string.platform_already_logged)
        }

        with(binding.web) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = PlatformApi.USER_AGENT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    binding.progress.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.progress.visibility = View.GONE
                    binding.tvUrl.text = url?.take(80).orEmpty()
                    tryCapture(view)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false
            }
            loadUrl("${PlatformApi.BASE}/sign_in")
        }

        binding.btnRetry.setOnClickListener { tryCapture(binding.web) }
        binding.btnManual.setOnClickListener { submitManual() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    /** 从页面 localStorage 里取 userToken（登录页登录成功后即存在）。 */
    private fun tryCapture(view: WebView?) {
        if (captured || view == null) return
        view.evaluateJavascript(
            """
            (function(){
              try {
                var keys = ['userToken','user_token','token','accessToken','access_token','ds_user_token'];
                for (var i=0;i<keys.length;i++){
                  var v = window.localStorage.getItem(keys[i]);
                  if (!v) continue;
                  if (v.charAt(0) === '{') { try { v = JSON.parse(v).value || ''; } catch(e){} }
                  if (v && v.length > 8) return v;
                }
                return '';
              } catch (e) { return ''; }
            })()
            """.trimIndent()
        ) { value ->
            val token = value?.trim('"')?.trim().orEmpty()
            if (token.isNotBlank() && token.length > 8 && !captured) {
                verifyAndSave(token, fromManual = false)
            }
        }
    }

    private fun submitManual() {
        val token = binding.etManual.text?.toString()?.trim().orEmpty()
        if (token.isBlank()) {
            toast(getString(R.string.platform_token_required))
            return
        }
        verifyAndSave(token, fromManual = true)
    }

    private fun verifyAndSave(token: String, fromManual: Boolean) {
        captured = true
        binding.progress.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.platform_verifying)
        lifecycleScope.launch {
            val ok = DsbApp.repo().savePlatformToken(token)
            binding.progress.visibility = View.GONE
            if (ok) {
                binding.tvStatus.text = getString(R.string.platform_verified)
                toast(getString(R.string.platform_verified))
                DsbApp.repo().refresh(syncPlatform = true, syncBalance = false)
                WidgetUpdater.updateAll(this@PlatformLoginActivity)
                finish()
            } else {
                captured = false
                binding.tvStatus.text = getString(R.string.platform_verify_failed)
                if (fromManual) toast(getString(R.string.platform_verify_failed))
            }
        }
    }
}
