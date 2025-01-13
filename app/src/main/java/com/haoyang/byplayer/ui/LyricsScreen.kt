package com.haoyang.byplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haoyang.byplayer.utils.LrcLine
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun LyricsScreen(
    lyrics: List<LrcLine>,
    currentTimeMs: Long,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 用于处理横滑手势
    var offsetX by remember { mutableFloatStateOf(0f) }
    val offsetXAnimated by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "offset"
    )

    val alphaValue by animateFloatAsState(
        targetValue = (1 - (offsetX.absoluteValue / (200 * density.density))).coerceIn(0f, 1f),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .graphicsLayer {
                translationX = offsetXAnimated
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    offsetX += delta
                },
                onDragStarted = { },
                onDragStopped = {
                    if (offsetX.absoluteValue > 100 * density.density) {
                        onDismiss()
                    }
                    offsetX = 0f
                }
            )
    ) {
        // 找到当前应该高亮的歌词索引
        val currentIndex = lyrics.indexOfLast { it.timeMs <= currentTimeMs }

        // 自动滚动到当前歌词
        LaunchedEffect(currentIndex) {
            if (currentIndex >= 0) {
                coroutineScope.launch {
                    listState.animateScrollToItem(
                        index = maxOf(0, currentIndex - 2),
                        scrollOffset = -100
                    )
                }
            }
        }

        if (lyrics.isEmpty()) {
            Text(
                text = "暂无歌词",
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(alphaValue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alphaValue),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 100.dp)
            ) {
                items(lyrics) { line ->
                    val isCurrentLine = lyrics.indexOf(line) == currentIndex
                    Text(
                        text = line.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrentLine)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
