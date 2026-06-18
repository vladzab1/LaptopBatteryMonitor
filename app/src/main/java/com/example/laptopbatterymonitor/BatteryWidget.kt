package com.example.laptopbatterymonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BatteryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val cmd = when (action) {
            "ACTION_VOL_DOWN" -> "volume_down"
            "ACTION_VOL_UP" -> "volume_up"
            "ACTION_TOGGLE" -> "toggle"
            else -> null
        }
        if (cmd != null) {
            triggerHapticFeedback(context)
            
            val prefs = context.getSharedPreferences("LaptopBatteryPrefs", Context.MODE_PRIVATE)
            
            // Optimistic UI: миттєво змінюємо дані в SharedPreferences
            when (action) {
                "ACTION_VOL_UP", "ACTION_VOL_DOWN" -> {
                    val currentVol = prefs.getInt("volume", 0)
                    val newVol = if (action == "ACTION_VOL_UP") (currentVol + 5).coerceAtMost(100) 
                                 else (currentVol - 5).coerceAtLeast(0)
                    prefs.edit().putInt("volume", newVol).apply()
                    updateAllWidgets(context)
                }
                "ACTION_TOGGLE" -> {
                    val isPlaying = prefs.getBoolean("is_playing", false)
                    prefs.edit().putBoolean("is_playing", !isPlaying).apply()
                    updateAllWidgets(context) // Миттєво перемикаємо іконку Play/Pause
                }
            }

            val req = OneTimeWorkRequestBuilder<MediaCommandWorker>()
                .setInputData(Data.Builder().putString("command", cmd).build())
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }

    private fun triggerHapticFeedback(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (e: Exception) {
            Log.e("BatteryWidget", "Haptic feedback failed", e)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("LaptopBatteryPrefs", Context.MODE_PRIVATE)
            val cap = prefs.getInt("capacity", 0)
            val temp = prefs.getInt("cpu_temp", 0)
            val plugged = prefs.getBoolean("plugged", false)
            val volume = prefs.getInt("volume", 0)
            val isPlaying = prefs.getBoolean("is_playing", false)
            
            Log.d("BatteryWidget", "UI Update: volume=$volume, isPlaying=$isPlaying")

            val views = RemoteViews(context.packageName, R.layout.widget_battery)
            
            views.setTextViewText(R.id.widget_capacity_text, "$cap%")
            views.setTextViewText(R.id.widget_temp_text, "$temp°C")
            views.setTextViewText(R.id.widget_volume_text, "$volume%")
            views.setViewVisibility(R.id.charging_icon, if (plugged) View.VISIBLE else View.GONE)

            // Динамічна іконка Play/Pause
            views.setImageViewResource(R.id.btn_toggle, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

            // Програмне малювання кільця
            val color = if (temp >= 85) "#FF8A80" else "#1B5E20"
            val bitmap = drawBatteryCircle(cap, color)
            views.setImageViewBitmap(R.id.battery_circle_image, bitmap)

            views.setOnClickPendingIntent(R.id.btn_vol_down, getPI(context, "ACTION_VOL_DOWN"))
            views.setOnClickPendingIntent(R.id.btn_toggle, getPI(context, "ACTION_TOGGLE"))
            views.setOnClickPendingIntent(R.id.btn_vol_up, getPI(context, "ACTION_VOL_UP"))

            try { manager.updateAppWidget(id, views) } catch (e: Exception) {
                Log.e("BatteryWidget", "Error updating widget: ${e.message}")
            }
        }

        private fun drawBatteryCircle(percentage: Int, colorHex: String): Bitmap {
            val size = 200 
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 14f
                strokeCap = Paint.Cap.ROUND
            }

            paint.color = Color.parseColor("#1AFFFFFF")
            canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 10f, paint)

            paint.color = Color.parseColor(colorHex)
            val rect = RectF(10f, 10f, size - 10f, size - 10f)
            canvas.drawArc(rect, 270f, (percentage * 3.6f), false, paint)

            return bitmap
        }

        private fun getPI(context: Context, action: String): PendingIntent {
            val intent = Intent(context, BatteryWidget::class.java).setAction(action).setPackage(context.packageName)
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java))
            if (ids != null) {
                for (id in ids) updateAppWidget(context, manager, id)
            }
        }
    }
}