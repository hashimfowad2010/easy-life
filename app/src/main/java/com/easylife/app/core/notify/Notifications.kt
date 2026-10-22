package com.easylife.app.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.easylife.app.MainActivity
import com.easylife.app.R
import com.easylife.app.core.data.prefs.PrefsRepository
import com.easylife.app.core.i18n.AppLanguage
import com.easylife.app.core.i18n.SNotify
import com.easylife.app.core.i18n.Txt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

enum class Channel(val id: String, val title: Txt, val importance: Int) {
    LIMITS("limits", SNotify.chLimits, NotificationManager.IMPORTANCE_HIGH),
    BILLS("bills", SNotify.chBills, NotificationManager.IMPORTANCE_DEFAULT),
    BUDGET("budget", SNotify.chBudget, NotificationManager.IMPORTANCE_DEFAULT),
    METER("meter_photo", SNotify.chMeter, NotificationManager.IMPORTANCE_LOW),
}

@Singleton
class NotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PrefsRepository,
) {
    /** (Re)creates channels with names in the app language. Safe to call repeatedly. */
    fun ensureCreated(language: AppLanguage? = null) {
        val lang = language ?: runBlocking { prefs.current().language } ?: AppLanguage.EN
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        Channel.entries.forEach { c -> nm.createNotificationChannel(NotificationChannel(c.id, c.title.get(lang), c.importance)) }
    }
}

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Shows a notification that opens [route] in the app. Returns false if notifications are blocked. */
    fun show(channel: Channel, id: Int, title: String, body: String, route: String): Boolean {
        if (!canNotify()) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setColor(0xFF00796B.toInt())
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        /** Quiet hours wrap past midnight (default 22:00 to 08:00). */
        fun inQuietHours(now: LocalTime, startHour: Int, endHour: Int): Boolean {
            val h = now.hour
            return if (startHour == endHour) false else if (startHour > endHour) h >= startHour || h < endHour else h in startHour until endHour
        }
    }
}
