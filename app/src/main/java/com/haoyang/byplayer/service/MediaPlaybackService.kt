package com.haoyang.byplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.haoyang.byplayer.ByPlayerApplication
import com.haoyang.byplayer.MainActivity
import com.haoyang.byplayer.R
import com.haoyang.byplayer.model.MusicFile

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var mediaSessionCompat: MediaSessionCompat? = null
    private var currentLyric: String = ""
    private val player: Player
        get() = ByPlayerApplication.instance.player

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, player).build()

        // 创建兼容版本的MediaSession用于AVRCP
        mediaSessionCompat = MediaSessionCompat(this, "ByPlayer").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }

        setupPlayerListener()
        createNotificationChannel()
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState(isPlaying)
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                updateMetadata()
            }
        })
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, player.currentPosition, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .build()

        mediaSessionCompat?.setPlaybackState(playbackState)
    }

    private fun updateMetadata() {
        val metadata = player.mediaMetadata

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title?.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.artist?.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, metadata.albumTitle?.toString())
            // 将当前歌词作为显示文本发送给车机
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, currentLyric)

        mediaSessionCompat?.setMetadata(metadataBuilder.build())
    }

    fun updateCurrentLyric(lyric: String) {
        currentLyric = lyric
        updateMetadata()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        mediaSessionCompat?.release()
        mediaSessionCompat = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.playback_channel_id)
            val channelName = getString(R.string.playback_channel_name)
            val channelDescription = getString(R.string.playback_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_LYRIC") {
            val lyric = intent.getStringExtra("lyric") ?: ""
            updateCurrentLyric(lyric)
        }
        return super.onStartCommand(intent, flags, startId)
    }
}
