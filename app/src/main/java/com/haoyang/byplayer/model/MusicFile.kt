package com.haoyang.byplayer.model

import android.net.Uri

data class MusicFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val lrcPath: String? = null
)
