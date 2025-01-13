package com.haoyang.byplayer.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.haoyang.byplayer.model.MusicFile
import java.io.File

class MusicScanner(private val context: Context) {
    fun scanMusicFiles(): List<MusicFile> {
        val musicFiles = mutableListOf<MusicFile>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val musicFile = cursor.toMusicFile()
                musicFile?.let { musicFiles.add(it) }
            }
        }

        return musicFiles
    }

    private fun Cursor.toMusicFile(): MusicFile? {
        return try {
            val id = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val title = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
            val artist = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
            val album = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
            val duration = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
            val path = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))

            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )

            val albumArtUri = try {
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    id
                )
            } catch (e: Exception) {
                null
            }

            // 检查是否存在同名的lrc文件
            val lrcPath = path.substringBeforeLast(".") + ".lrc"
            val lrcFile = File(lrcPath)
            val finalLrcPath = if (lrcFile.exists()) lrcPath else null

            MusicFile(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                uri = contentUri,
                albumArtUri = albumArtUri,
                lrcPath = finalLrcPath
            )
        } catch (e: Exception) {
            Log.e("MusicScanner", "Error scanning music file", e)
            null
        }
    }
}
