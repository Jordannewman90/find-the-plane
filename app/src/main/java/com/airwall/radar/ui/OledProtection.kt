package com.airwall.radar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun OledProtectionWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val shifts = listOf(
            0 to 0,
            1 to 0,
            1 to 1,
            0 to 1,
            -1 to 1,
            -1 to 0,
            -1 to -1,
            0 to -1,
            1 to -1
        )
        var index = 0
        while (true) {
            // Shift every 6 minutes by 1 pixel
            delay(6 * 60 * 1000L)
            index = (index + 1) % shifts.size
            offsetX = shifts[index].first
            offsetY = shifts[index].second
        }
    }

    Box(
        modifier = modifier.offset { IntOffset(offsetX, offsetY) }
    ) {
        content()
    }
}
