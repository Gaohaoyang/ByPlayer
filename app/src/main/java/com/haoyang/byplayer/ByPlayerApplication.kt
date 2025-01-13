package com.haoyang.byplayer

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class ByPlayerApplication : Application() {
    private var _player: Player? = null
    val player: Player
        get() {
            if (_player == null) {
                _player = createPlayer()
            }
            return _player!!
        }

    private fun createPlayer(): Player {
        return ExoPlayer.Builder(this)
            .build()
            .apply {
                // 设置默认的播放参数
                playWhenReady = true
                // 当播放器处于后台时继续播放
                setHandleAudioBecomingNoisy(true)
            }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        releasePlayer()
    }

    fun releasePlayer() {
        _player?.release()
        _player = null
    }

    companion object {
        lateinit var instance: ByPlayerApplication
            private set
    }
}
