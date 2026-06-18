package com.example.laptopbatterymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class BatteryInfo(
    val capacity: Int,
    val status: String,
    val plugged: Boolean,
    val cpuTemp: Int = 0,
    val volume: Int = 0,
    val isPlaying: Boolean = false
)

class BatteryServerService : Service() {

    private var server: EmbeddedServer<*, *>? = null
    private val CHANNEL_ID = "BatteryServerChannel"

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)

        server = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("/battery") {
                    try {
                        val info = call.receive<BatteryInfo>()
                        Log.d("BatteryServer", "Info: $info")
                        saveBatteryData(info)
                        call.respond(HttpStatusCode.OK, "Дані отримано")
                    } catch (e: Exception) {
                        Log.e("BatteryServer", "Error receiving: ${e.message}")
                        call.respond(HttpStatusCode.BadRequest, "Помилка: ${e.localizedMessage}")
                    }
                }
            }
        }.start(wait = false)
    }

    private fun saveBatteryData(info: BatteryInfo) {
        val prefs = getSharedPreferences("LaptopBatteryPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("capacity", info.capacity)
            putString("status", info.status)
            putBoolean("plugged", info.plugged)
            putInt("cpu_temp", info.cpuTemp)
            putInt("volume", info.volume)
            putBoolean("is_playing", info.isPlaying)
            putLong("last_update", System.currentTimeMillis())
            apply()
        }
        BatteryWidget.updateAllWidgets(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновий монітор батареї",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Сервер батареї ноута запущено")
            .setContentText("Очікування даних від CachyOS на порті 8080...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}