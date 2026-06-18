package com.example.laptopbatterymonitor

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaCommandWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val command = inputData.getString("command") ?: return@withContext Result.failure()
        var connection: HttpURLConnection? = null
        try {
            val url = URL("http://100.121.224.34:9090/media/$command")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doInput = true
            
            Log.d("MediaWorker", "Sending $command to laptop...")
            connection.connect()
            
            val responseCode = connection.responseCode
            // Примусово зчитуємо потік, щоб запит точно завершився
            if (responseCode == 200) {
                connection.inputStream.use { it.read() }
            }
            
            Log.d("MediaWorker", "Success! Server responded: $responseCode")
            Result.success()
        } catch (e: Exception) {
            Log.e("MediaWorker", "Failed to send command $command: ${e.message}")
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }
}