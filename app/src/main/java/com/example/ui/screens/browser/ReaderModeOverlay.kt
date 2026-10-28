package com.example.ui.screens.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.browser.ReaderArticle

private data class ReaderPalette(
    val background: Color,
    val foreground: Color,
    val secondary: Color
)

private val readerPalettes = listOf(
    ReaderPalette(Color(0xFF101113), Color(0xFFF2EEE8), Color(0xFFB7B0A7)),
    ReaderPalette(Color(0xFFF7F0DE), Color(0xFF33291E), Color(0xFF766957)),
    ReaderPalette(Color(0xFFFFFFFF), Color(0xFF202124), Color(0xFF5F6368))
)

@Composable
fun ReaderModeOverlay(
    article: ReaderArticle,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(article.title) { mutableIntStateOf(18) }
    var lineHeightMultiplier by remember(article.title) { mutableFloatStateOf(1.65f) }
    var paletteIndex by remember(article.title) { mutableIntStateOf(0) }
    var narrowWidth by remember(article.title) { mutableIntStateOf(0) }
    val palette = readerPalettes[paletteIndex]

    Surface(
        modifier = modifier.fillMaxSize(),
        color = palette.background,
        contentColor = palette.foreground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reader Mode", color = palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = article.title.ifBlank { "Simplified page" },
                        color = palette.foreground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { paletteIndex = (paletteIndex + 1) % readerPalettes.size }) {
                    Icon(Icons.Default.Contrast, contentDescription = "Change reader colors", tint = palette.foreground)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Return to original webpage", tint = palette.foreground)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(14) }) {
                    Icon(Icons.Default.TextDecrease, contentDescription = "Decrease font size", tint = palette.foreground)
                }
                Text("${fontSize}sp", color = palette.secondary, fontSize = 12.sp)
                IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(30) }) {
                    Icon(Icons.Default.TextIncrease, contentDescription = "Increase font size", tint = palette.foreground)
                }
                Text("Line spacing", color = palette.secondary, fontSize = 11.sp)
                Slider(
                    value = lineHeightMultiplier,
                    onValueChange = { lineHeightMultiplier = it },
                    valueRange = 1.25f..2.1f,
                    modifier = Modifier.weight(1f)
                )
                Text("${String.format("%.1f", lineHeightMultiplier)}×", color = palette.secondary, fontSize = 11.sp)
            }
            TextButton(
                onClick = { narrowWidth = 1 - narrowWidth },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = if (narrowWidth == 0) "Use full reading width" else "Use comfortable reading width",
                    color = palette.secondary,
                    fontSize = 11.sp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(article.paragraphs) { paragraph ->
                    Text(
                        text = paragraph,
                        color = palette.foreground,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeightMultiplier).sp,
                        modifier = Modifier
                            .widthIn(max = if (narrowWidth == 0) 680.dp else 900.dp)
                            .fillMaxWidth()
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
