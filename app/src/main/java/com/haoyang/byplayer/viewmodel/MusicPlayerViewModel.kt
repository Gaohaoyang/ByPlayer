package com.haoyang.byplayer.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.haoyang.byplayer.ByPlayerApplication
import com.haoyang.byplayer.model.MusicFile
import com.haoyang.byplayer.service.MediaPlaybackService
import com.haoyang.byplayer.utils.LrcParser
import com.haoyang.byplayer.utils.LrcLine
import com.haoyang.byplayer.utils.MusicScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val currentMusic: MusicFile? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val currentLyric: String = "",
    val playlist: List<MusicFile> = emptyList(),
    val isShuffleMode: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val showLyrics: Boolean = false
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    val player: Player = ByPlayerApplication.instance.player
    private val musicScanner = MusicScanner(application)
    private val lrcParser = LrcParser()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val currentLyrics: List<LrcLine>
        get() = _currentLyrics
    private var _currentLyrics: List<LrcLine> = emptyList()
    private var originalPlaylist: List<MusicFile> = emptyList()

    init {
        setupPlayerListener()
        loadMusicFiles()
        startPositionUpdateJob()
    }

    private fun startPositionUpdateJob() {
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    updateCurrentPosition()
                }
                kotlinx.coroutines.delay(100) // 每100ms更新一次位置
            }
        }
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState { it.copy(isPlaying = isPlaying) }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateCurrentPosition()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 当切换到新的歌曲时更新当前播放的音乐
                updateCurrentMusic()
            }
        })
    }

    private fun loadMusicFiles() {
        viewModelScope.launch {
            val musicFiles = musicScanner.scanMusicFiles()
            originalPlaylist = musicFiles
            updatePlayerState { it.copy(playlist = musicFiles) }
        }
    }

    fun refreshMusicFiles() {
        viewModelScope.launch {
            android.util.Log.d("ByPlayer", "开始刷新音乐文件列表")
            _isRefreshing.value = true
            try {
                android.util.Log.d("ByPlayer", "正在扫描音乐文件...")
                val musicFiles = musicScanner.scanMusicFiles()
                android.util.Log.d("ByPlayer", "扫描完成，找到 ${musicFiles.size} 个音乐文件")
                originalPlaylist = musicFiles

                // 保持当前播放的歌曲在列表中的位置
                val currentMusic = playerState.value.currentMusic
                if (currentMusic != null) {
                    val currentIndex = musicFiles.indexOfFirst { it.id == currentMusic.id }
                    android.util.Log.d("ByPlayer", "当前播放的歌曲在新列表中的位置: $currentIndex")
                    if (currentIndex != -1) {
                        // 如果当前播放的歌曲仍然存在，更新媒体项
                        val mediaItems = musicFiles.map {
                            MediaItem.Builder()
                                .setMediaId(it.uri.toString())
                                .setUri(it.uri)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(it.title)
                                        .setArtist(it.artist)
                                        .setAlbumTitle(it.album)
                                        .setArtworkUri(it.albumArtUri)
                                        .build()
                                )
                                .build()
                        }
                        android.util.Log.d("ByPlayer", "更新播放器媒体项")
                        player.setMediaItems(mediaItems, currentIndex, player.currentPosition)
                        player.prepare()
                    }
                }

                updatePlayerState { it.copy(playlist = musicFiles) }
                android.util.Log.d("ByPlayer", "播放列表更新完成")
            } catch (e: Exception) {
                android.util.Log.e("ByPlayer", "刷新音乐文件列表失败", e)
            } finally {
                _isRefreshing.value = false
                android.util.Log.d("ByPlayer", "刷新状态重置为 false")
            }
        }
    }

    private fun updateCurrentMusic() {
        val currentItem = player.currentMediaItem ?: return
        val currentMusic = playerState.value.playlist.find { it.uri.toString() == currentItem.mediaId }
        if (currentMusic != null) {
            updatePlayerState { it.copy(currentMusic = currentMusic) }
            loadLyrics(currentMusic)
        }
    }

    private fun loadLyrics(musicFile: MusicFile) {
        musicFile.lrcPath?.let { path ->
            _currentLyrics = lrcParser.parseLrcFile(path)
        } ?: run {
            _currentLyrics = emptyList()
        }
    }

    fun playMusic(musicFile: MusicFile) {
        val currentIndex = playerState.value.playlist.indexOf(musicFile)
        if (currentIndex != -1) {
            // 设置整个播放列表
            val mediaItems = playerState.value.playlist.map {
                MediaItem.Builder()
                    .setMediaId(it.uri.toString())
                    .setUri(it.uri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(it.title)
                            .setArtist(it.artist)
                            .setAlbumTitle(it.album)
                            .setArtworkUri(it.albumArtUri)
                            .build()
                    )
                    .build()
            }
            player.setMediaItems(mediaItems, currentIndex, 0L)
            player.prepare()
            player.play()

            updatePlayerState { it.copy(currentMusic = musicFile) }
            loadLyrics(musicFile)
        }
    }

    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        }
    }

    fun toggleShuffleMode() {
        val newShuffleMode = !playerState.value.isShuffleMode
        player.shuffleModeEnabled = newShuffleMode

        if (newShuffleMode) {
            val currentMusic = playerState.value.currentMusic
            val shuffledList = playerState.value.playlist.toMutableList().apply { shuffle() }
            // 确保当前播放的歌曲在随机播放列表的第一位
            currentMusic?.let { music ->
                val index = shuffledList.indexOf(music)
                if (index > 0) {
                    shuffledList.removeAt(index)
                    shuffledList.add(0, music)
                }
            }
            updatePlayerState { it.copy(playlist = shuffledList, isShuffleMode = true) }
        } else {
            updatePlayerState { it.copy(playlist = originalPlaylist, isShuffleMode = false) }
        }
    }

    fun toggleRepeatMode() {
        val currentMode = player.repeatMode
        val nextMode = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        updatePlayerState { it.copy(repeatMode = nextMode) }
    }

    private fun updateCurrentPosition() {
        val position = player.currentPosition
        val currentLyric = lrcParser.findCurrentLyric(currentLyrics, position)

        // 只有当歌词内容变化时才发送更新
        if (playerState.value.currentLyric != currentLyric) {
            android.util.Log.d("ByPlayer", "歌词内容变化，发送更新到Service")
            // 更新歌词到Service
            val context = getApplication<Application>()
            val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
                action = "UPDATE_LYRIC"
                putExtra("lyric", currentLyric)
            }
            context.startService(serviceIntent)
        }

        updatePlayerState {
            it.copy(
                currentPosition = position,
                currentLyric = currentLyric
            )
        }
    }

    fun playPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    private fun updatePlayerState(update: (PlayerState) -> PlayerState) {
        _playerState.value = update(_playerState.value)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    fun toggleLyrics() {
        updatePlayerState { it.copy(showLyrics = !it.showLyrics) }
    }

    fun hideLyrics() {
        updatePlayerState { it.copy(showLyrics = false) }
    }

    fun showLyrics() {
        updatePlayerState { it.copy(showLyrics = true) }
    }
}
