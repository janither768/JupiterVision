package com.jupiter.vision.util

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isOngoing: Boolean
)

class JupiterNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        extractAndStoreNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val current = _notificationsMap.value.toMutableMap()
        val list = current[sbn.packageName]?.toMutableList() ?: return
        list.removeAll { it.key == sbn.key }
        if (list.isEmpty()) {
            current.remove(sbn.packageName)
        } else {
            current[sbn.packageName] = list
        }
        _notificationsMap.value = current
    }

    private fun refreshActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val map = mutableMapOf<String, MutableList<NotificationItem>>()
            for (sbn in active) {
                val item = sbnToItem(sbn) ?: continue
                map.getOrPut(sbn.packageName) { mutableListOf() }.add(item)
            }
            _notificationsMap.value = map
        } catch (_: Throwable) {}
    }

    private fun extractAndStoreNotification(sbn: StatusBarNotification) {
        val item = sbnToItem(sbn) ?: return
        val current = _notificationsMap.value.toMutableMap()
        val list = current.getOrPut(sbn.packageName) { mutableListOf() }.toMutableList()
        list.removeAll { it.key == sbn.key }
        list.add(0, item) // newest first
        current[sbn.packageName] = list.take(10)
        _notificationsMap.value = current
    }

    private fun sbnToItem(sbn: StatusBarNotification): NotificationItem? {
        val n = sbn.notification ?: return null
        val extras = n.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return null

        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0

        return NotificationItem(
            key = sbn.key ?: "${sbn.packageName}_${sbn.id}",
            packageName = sbn.packageName,
            title = title,
            text = text,
            postTime = sbn.postTime,
            isOngoing = isOngoing
        )
    }

    companion object {
        private val _notificationsMap = MutableStateFlow<Map<String, List<NotificationItem>>>(emptyMap())
        val notificationsFlow: StateFlow<Map<String, List<NotificationItem>>> = _notificationsMap.asStateFlow()

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        fun isNotificationAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponentName = ComponentName(context, JupiterNotificationListener::class.java).flattenToString()
            return flat.contains(myComponentName)
        }

        fun openNotificationAccessSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {}
            }
        }
    }
}
