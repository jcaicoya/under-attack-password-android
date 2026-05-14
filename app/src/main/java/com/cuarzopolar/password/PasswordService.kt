package com.cuarzopolar.password

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cuarzopolar.password.network.ConnectionState
import com.cuarzopolar.password.network.UdpDiscovery
import com.cuarzopolar.password.network.WebSocketManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "PasswordService"
private const val CHANNEL_ID = "password_channel"
private const val NOTIF_ID = 1

class PasswordService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun getService(): PasswordService = this@PasswordService
    }

    private val binder = LocalBinder()

    val wsManager = WebSocketManager()
    val connectionState: StateFlow<ConnectionState> get() = wsManager.connectionState

    var onVerdictReceived: ((cracked: Boolean, password: String) -> Unit)? = null

    private var discoveryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat("SIN ENLACE")

        wsManager.onTextMessage = { msg ->
            try {
                val obj = JSONObject(msg)
                if (obj.optString("type") == "verdict") {
                    val cracked  = obj.optBoolean("cracked", false)
                    val password = obj.optString("password", "")
                    onVerdictReceived?.invoke(cracked, password)
                }
            } catch (_: Exception) {
                Log.w(TAG, "Unhandled message: $msg")
            }
        }

        wsManager.connect("localhost")

        lifecycleScope.launch {
            wsManager.connectionState.collect { state ->
                updateNotification(connectionStatusText(state))
                if (state == ConnectionState.CONNECTED) stopDiscovery()
            }
        }

        lifecycleScope.launch {
            wsManager.retriesExhausted.collect { exhausted ->
                if (exhausted) {
                    Log.d(TAG, "Retries exhausted — starting UDP discovery")
                    startDiscovery()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
        wsManager.disconnect()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopDiscovery()
        wsManager.disconnect()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    fun sendPassword(password: String) {
        wsManager.sendText("""{"type":"password","value":"$password"}""")
    }

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = lifecycleScope.launch {
            try {
                val beacon = UdpDiscovery.awaitBeacon()
                if (wsManager.connectionState.value == ConnectionState.DISCONNECTED) {
                    wsManager.connect(beacon.ip, beacon.port)
                }
            } catch (_: Exception) { }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    private fun connectionStatusText(state: ConnectionState): String =
        when (state) {
            ConnectionState.CONNECTED    -> "ENLACE ACTIVO"
            ConnectionState.CONNECTING   -> "CONECTANDO…"
            ConnectionState.DISCONNECTED -> "SIN ENLACE"
        }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Password", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                description = "Mantiene la conexión con la consola del operador"
            }
        )
    }

    private fun buildNotification(statusText: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Password")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat(statusText: String) {
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(statusText), types)
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(statusText))
    }
}
