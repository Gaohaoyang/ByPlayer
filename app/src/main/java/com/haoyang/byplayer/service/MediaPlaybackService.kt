package com.haoyang.byplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.haoyang.byplayer.ByPlayerApplication
import com.haoyang.byplayer.MainActivity
import com.haoyang.byplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var mediaSessionCompat: MediaSessionCompat? = null
    private var currentLyric: String = ""
    private val player: Player
        get() = ByPlayerApplication.instance.player

    private lateinit var playerNotificationManager: PlayerNotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val notificationId = 1

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
        setupNotificationManager()
    }

    private fun setupNotificationManager() {
        playerNotificationManager = PlayerNotificationManager.Builder(this, notificationId, getString(R.string.playback_channel_id))
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return player.mediaMetadata.title?.toString() ?: "Unknown Title"
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    val intent = Intent(this@MediaPlaybackService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    return PendingIntent.getActivity(
                        this@MediaPlaybackService, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence {
                    return "${player.mediaMetadata.artist ?: "Unknown Artist"} - ${player.mediaMetadata.albumTitle ?: ""}"
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {
                    player.mediaMetadata.artworkUri?.let { uri ->
                        loadAlbumArt(uri) { bitmap ->
                            callback.onBitmap(bitmap)
                        }
                    }
                    return null
                }
            })
            .setChannelNameResourceId(R.string.playback_channel_name)
            .setChannelDescriptionResourceId(R.string.playback_channel_description)
            .setSmallIconResourceId(R.drawable.ic_music_note)
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopForeground(true)
                    stopSelf()
                }

                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    }
                }
            })
            .build()

        playerNotificationManager.apply {
            setPlayer(player)
            mediaSession?.let { session ->
                setMediaSessionToken(session.sessionCompatToken)
            }
            setUseNextAction(true)
            setUsePreviousAction(true)
            setUsePlayPauseActions(true)
            setUseStopAction(false)
        }
    }

    private fun loadAlbumArt(uri: Uri, callback: (Bitmap) -> Unit) {
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                            callback(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            )
            .build()

        mediaSessionCompat?.setPlaybackState(playbackState)

        // 设置播放器的循环模式
        player.repeatMode = Player.REPEAT_MODE_ALL
    }

    private fun updateMetadata() {
        val songTitle = player.mediaMetadata.title.toString()
        val artist = player.mediaMetadata.artist?.toString()?.takeIf { it != "<unknown>" } ?: ""
        val displayArtist = if (artist.isNotBlank()) artist else songTitle  // 如果艺术家为空，使用歌曲名

        android.util.Log.d("ByPlayer_Meta", "============ 媒体元数据更新 ============")
        android.util.Log.d("ByPlayer_Meta", "原始数据:")
        android.util.Log.d("ByPlayer_Meta", "歌名: $songTitle")
        android.util.Log.d("ByPlayer_Meta", "艺术家: $artist")
        android.util.Log.d("ByPlayer_Meta", "显示艺术家: $displayArtist")
        android.util.Log.d("ByPlayer_Meta", "当前歌词: $currentLyric")
        android.util.Log.d("ByPlayer_Meta", "专辑图片URI: ${player.mediaMetadata.artworkUri}")

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, songTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, displayArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentLyric)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, songTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, displayArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, currentLyric)

        android.util.Log.d("ByPlayer_Meta", "设置的Metadata:")
        android.util.Log.d("ByPlayer_Meta", "METADATA_KEY_TITLE: $songTitle")
        android.util.Log.d("ByPlayer_Meta", "METADATA_KEY_ARTIST: $displayArtist")
        android.util.Log.d("ByPlayer_Meta", "METADATA_KEY_ALBUM: $currentLyric")
        android.util.Log.d("ByPlayer_Meta", "METADATA_KEY_DISPLAY_TITLE: $songTitle")
        android.util.Log.d("ByPlayer_Meta", "METADATA_KEY_DISPLAY_SUBTITLE: $displayArtist")
        android.util.Log.d("ByPlayer_Meta", "METADATA_KEY_DISPLAY_DESCRIPTION: $currentLyric")

        // 添加专辑图片
        var hasArtwork = false
        try {
            // 首先尝试从音频文件中获取内嵌的专辑图片
            player.currentMediaItem?.mediaId?.let { mediaId ->
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this, Uri.parse(mediaId))
                    retriever.embeddedPicture?.let { embeddedArt ->
                        BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.size)?.let { bitmap ->
                            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                            hasArtwork = true
                            android.util.Log.d("ByPlayer_Meta", "成功从音频文件中提取专辑图片")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ByPlayer_Meta", "从音频文件提取专辑图片失败: ${e.message}")
                } finally {
                    retriever.release()
                }
            }

            // 如果没有内嵌图片，尝试使用外部专辑图片
            if (!hasArtwork) {
                player.mediaMetadata.artworkUri?.let { uri ->
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                                hasArtwork = true
                                android.util.Log.d("ByPlayer_Meta", "使用外部专辑图片")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ByPlayer_Meta", "加载外部专辑图片失败: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ByPlayer_Meta", "处理专辑图片失败: ${e.message}")
        }
        android.util.Log.d("ByPlayer_Meta", "是否包含专辑图片: $hasArtwork")
        android.util.Log.d("ByPlayer_Meta", "====================================")

        mediaSessionCompat?.setMetadata(metadataBuilder.build())
    }

    fun updateCurrentLyric(lyric: String) {
        android.util.Log.d("ByPlayer", "收到新歌词更新请求: $lyric")
        if (currentLyric != lyric) {  // 只在歌词变化时更新
            android.util.Log.d("ByPlayer", "歌词已变化，从 '$currentLyric' 更新为 '$lyric'")
            currentLyric = lyric
            updateMetadata()  // 立即更新元数据
        } else {
            android.util.Log.d("ByPlayer", "歌词未变化，跳过更新")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        playerNotificationManager.setPlayer(null)
        mediaSession?.run {
            release()
            mediaSession = null
        }
        mediaSessionCompat?.release()
        mediaSessionCompat = null
        ByPlayerApplication.instance.releasePlayer()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 如果不是在播放状态，则停止服务
        if (!player.isPlaying) {
            stopSelf()
        }
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
