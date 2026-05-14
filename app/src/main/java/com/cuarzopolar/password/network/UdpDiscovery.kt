package com.cuarzopolar.password.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

object UdpDiscovery {

    private const val TAG = "UdpDiscovery"
    const val BEACON_PORT = 8768
    private const val BUFFER_SIZE = 512

    data class Beacon(val ip: String, val port: Int)

    suspend fun awaitBeacon(): Beacon = withContext(Dispatchers.IO) {
        val socket = DatagramSocket(BEACON_PORT)
        try {
            socket.broadcast = true
            socket.soTimeout = 3000
            Log.d(TAG, "Listening for beacon on UDP $BEACON_PORT")
            val buf = ByteArray(BUFFER_SIZE)
            val packet = DatagramPacket(buf, buf.size)
            while (isActive) {
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val msg = String(packet.data, 0, packet.length)
                try {
                    val obj = JSONObject(msg)
                    if (obj.optString("type") == "beacon") {
                        val ip   = obj.getString("ip")
                        val port = obj.getInt("port")
                        Log.d(TAG, "Beacon from $ip:$port")
                        return@withContext Beacon(ip, port)
                    }
                } catch (_: Exception) {
                    Log.w(TAG, "Malformed beacon: $msg")
                }
            }
            throw CancellationException()
        } finally {
            socket.close()
            Log.d(TAG, "Socket closed")
        }
    }
}
