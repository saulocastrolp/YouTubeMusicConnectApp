package com.saulocastrodev.youtubemusicconnect

import android.app.Notification
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class YTMusicNotificationListener : NotificationListenerService() {

    fun searchYouTubeVideo(title: String, artist: String, apiKey: String, callback: (String?, String?) -> Unit) {
        val query = "$title $artist"
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=${URLEncoder.encode(query, "UTF-8")}&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = URL(url).readText()
                val json = JSONObject(response)
                val items = json.getJSONArray("items")
                if (items.length() > 0) {
                    val item = items.getJSONObject(0)
                    val videoId = item.getJSONObject("id").getString("videoId")
                    val channelId = item.getJSONObject("snippet").getString("channelId")
                    withContext(Dispatchers.Main) { callback(videoId, channelId) }
                } else {
                    withContext(Dispatchers.Main) { callback(null, null) }
                }
            } catch (e: Exception) {
                Log.e("YTSearch", "Erro na pesquisa", e)
                withContext(Dispatchers.Main) { callback(null, null) }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.google.android.apps.youtube.music") {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val artist = extras.getString(Notification.EXTRA_TEXT)

            if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                Log.d("YTNotification", "Title: $title, Artist: $artist")
                return
            }
        }
    }
}