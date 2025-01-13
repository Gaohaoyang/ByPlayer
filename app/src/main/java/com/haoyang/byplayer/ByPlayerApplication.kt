package com.haoyang.byplayer

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class ByPlayerApplication : Application() {
    lateinit var player: Player
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        player = ExoPlayer.Builder(this).build()
    }

    companion object {
        lateinit var instance: ByPlayerApplication
            private set
    }
}
