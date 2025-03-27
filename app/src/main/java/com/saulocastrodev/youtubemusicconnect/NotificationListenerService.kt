package com.saulocastrodev.youtubemusicconnect

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {
    companion object {
        var lastSongTitle: String? = null
        var lastSongArtist: String? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val artist = extras.getString(Notification.EXTRA_TEXT)

        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            lastSongTitle = title
            lastSongArtist = artist
        }
    }
}