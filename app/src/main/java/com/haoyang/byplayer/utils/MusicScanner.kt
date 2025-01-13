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
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE  // 添加MIME_TYPE来查看文件类型
        )

        // 使用原始的搜索条件
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        android.util.Log.d("ByPlayer", "开始扫描音乐文件，搜索条件: $selection")
        android.util.Log.d("ByPlayer", "外部存储URI: $collection")

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val count = cursor.count
            android.util.Log.d("ByPlayer", "查询到 $count 个音乐文件")

            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                val mimeType = cursor.getString(mimeTypeColumn)
                android.util.Log.d("ByPlayer", "发现音乐文件: 路径=$path, 类型=$mimeType")

                val musicFile = cursor.toMusicFile()
                if (musicFile != null) {
                    musicFiles.add(musicFile)
                    android.util.Log.d("ByPlayer", "成功添加音乐文件: ${musicFile.title}")
                } else {
                    android.util.Log.e("ByPlayer", "无法解析音乐文件: $path")
                }
            }
        }

        android.util.Log.d("ByPlayer", "扫描完成，最终找到 ${musicFiles.size} 个有效音乐文件")
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
