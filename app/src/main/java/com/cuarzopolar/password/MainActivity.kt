package com.cuarzopolar.password

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var screenForm:    LinearLayout
    private lateinit var screenWaiting: LinearLayout
    private lateinit var screenResult:  LinearLayout
    private lateinit var etPassword:    EditText
    private lateinit var etConfirm:     EditText
    private lateinit var btnSend:       Button
    private lateinit var btnNext:       Button
    private lateinit var tvError:       TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultPass:  TextView
    private lateinit var ivWaiting:     ImageView
    private lateinit var ivResult:      ImageView

    private var webSocket: WebSocket? = null
    private var pulseAnimator: ObjectAnimator? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenForm    = findViewById(R.id.screenForm)
        screenWaiting = findViewById(R.id.screenWaiting)
        screenResult  = findViewById(R.id.screenResult)
        etPassword    = findViewById(R.id.etPassword)
        etConfirm     = findViewById(R.id.etConfirm)
        btnSend       = findViewById(R.id.btnSend)
        btnNext       = findViewById(R.id.btnNext)
        tvError       = findViewById(R.id.tvError)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvResultPass  = findViewById(R.id.tvResultPassword)
        ivWaiting     = findViewById(R.id.ivWaiting)
        ivResult      = findViewById(R.id.ivResult)

        btnSend.setOnClickListener { onSend() }
        btnNext.setOnClickListener { showForm() }

        connect()
    }

    // ── Navegación entre pantallas ────────────────────────────

    private fun showForm() {
        etPassword.text.clear()
        etConfirm.text.clear()
        tvError.visibility = View.INVISIBLE
        screenResult.visibility  = View.GONE
        screenWaiting.visibility = View.GONE
        screenForm.visibility    = View.VISIBLE
    }

    private fun showWaiting() {
        screenForm.visibility    = View.GONE
        screenResult.visibility  = View.GONE
        screenWaiting.visibility = View.VISIBLE
        startPulse(ivWaiting)
    }

    private fun showResult(cracked: Boolean, password: String) {
        pulseAnimator?.cancel()
        screenForm.visibility    = View.GONE
        screenWaiting.visibility = View.GONE
        screenResult.visibility  = View.VISIBLE

        if (cracked) {
            tvResultTitle.text      = "CONTRASEÑA DETECTADA"
            tvResultTitle.setTextColor(0xFFFF4444.toInt())
            tvResultPass.text       = password
            tvResultPass.visibility = View.VISIBLE
            ivResult.setImageResource(R.drawable.cuarzito_red)
        } else {
            tvResultTitle.text      = "CONTRASEÑA SEGURA ✓"
            tvResultTitle.setTextColor(0xFF00FF55.toInt())
            tvResultPass.visibility = View.GONE
            ivResult.setImageResource(R.drawable.cuarzito_green)
        }
    }

    // ── Lógica de envío ───────────────────────────────────────

    private fun onSend() {
        val pass    = etPassword.text.toString()
        val confirm = etConfirm.text.toString()

        if (pass.isEmpty()) {
            showError("Introduce una contraseña"); return
        }
        if (pass != confirm) {
            showError("Las contraseñas no coinciden"); return
        }

        val ws = webSocket
        if (ws == null) {
            showError("Sin conexión con el sistema"); return
        }

        ws.send(JSONObject().apply {
            put("type",     "password")
            put("value",    pass)
        }.toString())

        showWaiting()
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }

    // ── WebSocket ─────────────────────────────────────────────

    private fun connect() {
        val request = Request.Builder().url("ws://localhost:8767").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // conexión lista, no hace falta notificar al usuario
            }
            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("type") == "verdict") {
                        val cracked  = obj.optBoolean("cracked", false)
                        val password = obj.optString("password", "")
                        runOnUiThread { showResult(cracked, password) }
                    }
                } catch (_: Exception) {}
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                // reconectar tras 3 s
                android.os.Handler(mainLooper).postDelayed({ connect() }, 3000)
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                webSocket = null
            }
        })
    }

    // ── Animación pulso ───────────────────────────────────────

    private fun startPulse(view: View) {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f)
        ).apply {
            duration      = 900
            repeatCount   = ObjectAnimator.INFINITE
            repeatMode    = ObjectAnimator.REVERSE
            interpolator  = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
        webSocket?.close(1000, null)
        client.dispatcher.executorService.shutdown()
    }
}
