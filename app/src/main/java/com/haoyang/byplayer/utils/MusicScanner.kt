package com.haoyang.byplayer.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.haoyang.byplayer.model.MusicFile
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MusicScanner(private val context: Context) {
    suspend fun scanMusicFiles(): List<MusicFile> {
        // 首先触发媒体库扫描
        triggerMediaScan()

        val musicFiles = mutableListOf<MusicFile>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        // 使用更严格的搜索条件
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        android.util.Log.d("ByPlayer", "开始扫描音乐文件")
        android.util.Log.d("ByPlayer", "搜索条件: $selection")
        android.util.Log.d("ByPlayer", "排序方式: $sortOrder")
        android.util.Log.d("ByPlayer", "外部存储URI: $collection")

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val count = cursor.count
                android.util.Log.d("ByPlayer", "查询到 $count 个音乐文件")

                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val path = cursor.getString(pathColumn)
                    val mimeType = cursor.getString(mimeTypeColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)

                    android.util.Log.d("ByPlayer", "发现音乐文件:")
                    android.util.Log.d("ByPlayer", "ID: $id")
                    android.util.Log.d("ByPlayer", "路径: $path")
                    android.util.Log.d("ByPlayer", "类型: $mimeType")
                    android.util.Log.d("ByPlayer", "修改时间: $dateModified")

                    val musicFile = cursor.toMusicFile()
                    if (musicFile != null) {
                        musicFiles.add(musicFile)
                        android.util.Log.d("ByPlayer", "成功添加音乐文件: ${musicFile.title}")
                    } else {
                        android.util.Log.e("ByPlayer", "无法解析音乐文件: $path")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ByPlayer", "扫描音乐文件时发生错误", e)
        }

        android.util.Log.d("ByPlayer", "扫描完成，最终找到 ${musicFiles.size} 个有效音乐文件")
        return musicFiles
    }

    private suspend fun triggerMediaScan() = suspendCancellableCoroutine { continuation ->
        android.util.Log.d("ByPlayer", "开始触发媒体库扫描")

        // 获取外部存储的音乐目录
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        // 要扫描的目录列表
        val dirsToScan = listOf(musicDir, downloadDir)

        // 收集所有音频文件路径
        val pathsToScan = mutableListOf<String>()
        dirsToScan.forEach { dir ->
            if (dir.exists()) {
                dir.walk()
                    .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                    .forEach {
                        pathsToScan.add(it.absolutePath)
                        android.util.Log.d("ByPlayer", "添加文件到扫描列表: ${it.absolutePath}")
                    }
            }
        }

        if (pathsToScan.isEmpty()) {
            android.util.Log.d("ByPlayer", "没有找到需要扫描的音频文件")
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        var scanCount = 0
        MediaScannerConnection.scanFile(
            context,
            pathsToScan.toTypedArray(),
            null
        ) { path, uri ->
            android.util.Log.d("ByPlayer", "媒体扫描完成: $path -> $uri")
            scanCount++
            if (scanCount == pathsToScan.size) {
                android.util.Log.d("ByPlayer", "所有文件扫描完成")
                continuation.resume(Unit)
            }
        }
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
            android.util.Log.e("ByPlayer", "解析音乐文件时发生错误", e)
            null
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "mid", "midi"
        )
    }
}
