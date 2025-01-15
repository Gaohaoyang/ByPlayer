package com.haoyang.byplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.haoyang.byplayer.service.MediaPlaybackService
import com.haoyang.byplayer.ui.LyricsScreen
import com.haoyang.byplayer.viewmodel.MusicPlayerViewModel
import android.content.Intent
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.core.view.WindowCompat
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi

@OptIn(ExperimentalMaterialApi::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MusicPlayerViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 启动媒体服务
        startService(Intent(this, MediaPlaybackService::class.java))

        setContent {
            MaterialTheme {
                val permissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.READ_MEDIA_AUDIO)
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }

                val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

                if (!permissionsState.allPermissionsGranted) {
                    PermissionRequest(permissionsState)
                } else {
                    MainScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 重新连接到媒体服务
        startService(Intent(this, MediaPlaybackService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        // 如果应用被销毁且没有在播放，则停止服务
        if (!viewModel.player.isPlaying) {
            stopService(Intent(this, MediaPlaybackService::class.java))
        }
    }

    override fun onBackPressed() {
        // 如果歌词界面打开，则关闭歌词界面
        if (viewModel.playerState.value.showLyrics) {
            viewModel.hideLyrics()
        } else {
            // 否则，最小化应用而不是关闭
            moveTaskToBack(true)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequest(permissionsState: com.google.accompanist.permissions.MultiplePermissionsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("需要权限来访问音乐文件")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
            Text("授予权限")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreen(viewModel: MusicPlayerViewModel) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // 主要内容区域（歌曲列表或歌词）
        Box(modifier = Modifier.weight(1f)) {
            // 歌曲列表（始终存在，但在显示歌词时被覆盖）
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = refreshing,
                    onRefresh = {
                        android.util.Log.d("ByPlayer", "下拉刷新被触发")
                        viewModel.refreshMusicFiles()
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(playerState.playlist) { musicFile ->
                            MusicListItem(
                                musicFile = musicFile,
                                isPlaying = playerState.currentMusic?.id == musicFile.id && playerState.isPlaying,
                                onClick = { viewModel.playMusic(musicFile) }
                            )
                        }
                    }

                    PullRefreshIndicator(
                        refreshing = refreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            // 歌词界面（作为覆盖层）
            if (playerState.showLyrics && playerState.currentMusic != null) {
                LyricsScreen(
                    lyrics = viewModel.currentLyrics,
                    currentTimeMs = playerState.currentPosition,
                    onDismiss = { viewModel.hideLyrics() }
                )
            }
        }

        // 播放控制栏（固定在底部）
        playerState.currentMusic?.let { currentMusic ->
            PlayerControls(
                musicFile = currentMusic,
                isPlaying = playerState.isPlaying,
                currentLyric = playerState.currentLyric,
                onPlayPause = { viewModel.playPause() },
                viewModel = viewModel,
                showLyrics = playerState.showLyrics,
                onToggleLyrics = { viewModel.toggleLyrics() }
            )
        }
    }
}

@Composable
fun MusicListItem(
    musicFile: com.haoyang.byplayer.model.MusicFile,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = musicFile.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    if (musicFile.artist != "<unknown>") {
                        append(musicFile.artist)
                    }
                    if (musicFile.album != "<unknown>") {
                        if (isNotEmpty() && musicFile.artist != "<unknown>") {
                            append(" - ")
                        }
                        append(musicFile.album)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (isPlaying) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                    contentDescription = "正在播放"
                )
            }
        }
    )
}

@Composable
fun PlayerControls(
    musicFile: com.haoyang.byplayer.model.MusicFile,
    isPlaying: Boolean,
    currentLyric: String,
    onPlayPause: () -> Unit,
    viewModel: MusicPlayerViewModel,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val currentPosition = viewModel.playerState.value.currentPosition
    val duration = viewModel.player.duration.coerceAtLeast(0)

    // 更新滑块位置（仅在不拖动时）
    LaunchedEffect(currentPosition) {
        if (!isDragging && duration > 0) {
            sliderPosition = (currentPosition.toFloat() / duration)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 歌词显示（移到最上方）
            if (!showLyrics && currentLyric.isNotEmpty()) {
                Text(
                    text = currentLyric,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .clickable(onClick = onToggleLyrics),
                    textAlign = TextAlign.Center
                )
            }

            // 歌曲信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = musicFile.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            if (musicFile.artist != "<unknown>") {
                                append(musicFile.artist)
                            }
                            if (musicFile.album != "<unknown>") {
                                if (isNotEmpty()) {
                                    append(" - ")
                                }
                                append(musicFile.album)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                // 歌词显示切换按钮
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        imageVector = if (showLyrics) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (showLyrics) "隐藏歌词" else "显示歌词"
                    )
                }
            }

            // 进度条
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        viewModel.seekTo((sliderPosition * duration).toLong())
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 时间显示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 播放控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 随机播放按钮
                IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "随机播放",
                        tint = if (viewModel.playerState.value.isShuffleMode)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }

                // 上一曲按钮
                IconButton(onClick = { viewModel.playPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "上一曲"
                    )
                }

                // 播放/暂停按钮
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 下一曲按钮
                IconButton(onClick = { viewModel.playNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一曲"
                    )
                }

                // 循环模式按钮
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    val icon = when (viewModel.playerState.value.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "切换循环模式",
                        tint = if (viewModel.playerState.value.repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
