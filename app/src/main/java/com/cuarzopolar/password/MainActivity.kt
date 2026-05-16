package com.cuarzopolar.password

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.cuarzopolar.password.network.ConnectionState
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var ivCuarzito:    ImageView
    private lateinit var formPanel:     LinearLayout
    private lateinit var resultOverlay: FrameLayout
    private lateinit var etPassword:    EditText
    private lateinit var etConfirm:     EditText
    private lateinit var btnSend:       Button
    private lateinit var btnNext:       Button
    private lateinit var tvError:       TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultPass:  TextView

    private var service: PasswordService? = null
    private var serviceBound = false
    private var pulseAnimator: ObjectAnimator? = null
    private var baseScale = 1f

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as PasswordService.LocalBinder).getService()
            service = svc
            serviceBound = true
            svc.onVerdictReceived = { cracked, password ->
                runOnUiThread { showResult(cracked, password) }
            }
            observeConnectionState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service?.onVerdictReceived = null
            serviceBound = false
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()
        setContentView(R.layout.activity_main)

        ivCuarzito    = findViewById(R.id.ivCuarzito)
        formPanel     = findViewById(R.id.formPanel)
        resultOverlay = findViewById(R.id.resultOverlay)
        etPassword    = findViewById(R.id.etPassword)
        etConfirm     = findViewById(R.id.etConfirm)
        btnSend       = findViewById(R.id.btnSend)
        btnNext       = findViewById(R.id.btnNext)
        tvError       = findViewById(R.id.tvError)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultPass  = findViewById(R.id.tvResultPassword)

        btnSend.setOnClickListener { onSend() }
        btnNext.setOnClickListener { showForm() }
        ivCuarzito.setOnClickListener { onCuarzitoTapped() }

        ivCuarzito.post { applyFillScale(ivCuarzito, 0.85f) }

        val svcIntent = Intent(this, PasswordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }
        bindService(svcIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun observeConnectionState() {
        val svc = service ?: return
        lifecycleScope.launch {
            svc.connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.DISCONNECTED -> {
                        ivCuarzito.setImageResource(R.drawable.cuarzito_blue)
                        formPanel.visibility     = View.GONE
                        resultOverlay.visibility = View.GONE
                    }
                    ConnectionState.CONNECTING -> {
                        ivCuarzito.setImageResource(R.drawable.cuarzito_amber)
                        formPanel.visibility     = View.GONE
                        resultOverlay.visibility = View.GONE
                    }
                    ConnectionState.CONNECTED -> {
                        ivCuarzito.setImageResource(R.drawable.cuarzito_green)
                        showForm()
                    }
                }
            }
        }
    }

    // ── Navegación entre estados ────────────────────────────────

    private fun showForm() {
        etPassword.text.clear()
        etConfirm.text.clear()
        tvError.visibility       = View.GONE
        resultOverlay.visibility = View.GONE
        formPanel.visibility     = View.VISIBLE
    }

    private fun showWaiting() {
        formPanel.visibility     = View.GONE
        resultOverlay.visibility = View.GONE
        ivCuarzito.setImageResource(R.drawable.cuarzito_red)
    }

    private fun showResult(cracked: Boolean, password: String) {
        formPanel.visibility     = View.GONE
        resultOverlay.visibility = View.VISIBLE

        ivCuarzito.setImageResource(R.drawable.cuarzito_green)
        if (cracked) {
            tvResultTitle.text      = "CONTRASEÑA DETECTADA"
            tvResultTitle.setTextColor(0xFFFF4444.toInt())
            tvResultPass.text       = password
            tvResultPass.visibility = View.VISIBLE
        } else {
            tvResultTitle.text      = "CONTRASEÑA SEGURA ✓"
            tvResultTitle.setTextColor(0xFF00FF55.toInt())
            tvResultPass.visibility = View.GONE
        }
    }

    // ── Lógica de envío ─────────────────────────────────────────

    private fun onSend() {
        val pass    = etPassword.text.toString()
        val confirm = etConfirm.text.toString()

        if (pass.isEmpty()) {
            showError("Introduce una contraseña"); return
        }
        if (pass != confirm) {
            showError("Las contraseñas no coinciden"); return
        }

        val svc = service
        if (svc == null || svc.connectionState.value != ConnectionState.CONNECTED) {
            showError("Sin conexión con el sistema"); return
        }

        svc.sendPassword(pass)
        showWaiting()
    }

    private fun onCuarzitoTapped() {
        val svc = service ?: return
        if (svc.connectionState.value == ConnectionState.CONNECTED) return
        showConnectBottomSheet()
    }

    private fun showConnectBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_connect, null)
        sheet.setContentView(sheetView)

        val etIp = sheetView.findViewById<EditText>(R.id.etIp)
        val prefs = getSharedPreferences("password_prefs", Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("last_ip", ""))

        sheetView.findViewById<View>(R.id.btnConnect).setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                service?.connect(ip)
                sheet.dismiss()
            }
        }

        sheet.show()
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }

    // ── Escala y animación ───────────────────────────────────────

    private fun applyFillScale(iv: ImageView, targetHeightFraction: Float) {
        val vw = iv.width.toFloat()
        val vh = iv.height.toFloat()
        val d  = iv.drawable ?: return
        val imgW = d.intrinsicWidth.toFloat()
        val imgH = d.intrinsicHeight.toFloat()
        if (imgW <= 0f || imgH <= 0f) return
        val fitScale  = minOf(vw / imgW, vh / imgH)
        val renderedH = imgH * fitScale
        val scale     = (vh * targetHeightFraction) / renderedH
        baseScale     = scale
        iv.scaleX     = scale
        iv.scaleY     = scale
        iv.translationX = vw * 0.08f
        startPulseAnimation()
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            ivCuarzito,
            PropertyValuesHolder.ofFloat("scaleX", baseScale, baseScale * 1.06f),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.06f)
        ).apply {
            duration     = 3000
            repeatCount  = ObjectAnimator.INFINITE
            repeatMode   = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
        if (isFinishing) service?.onVerdictReceived = null
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
