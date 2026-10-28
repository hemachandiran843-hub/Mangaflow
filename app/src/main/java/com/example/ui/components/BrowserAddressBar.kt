package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MangaAdShield
import com.example.ui.theme.MangaAmethyst
import com.example.ui.theme.MangaPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BrowserAddressBar(
    currentUrl: String,
    title: String,
    isLoading: Boolean,
    progress: Int,
    isPrivate: Boolean,
    blockedAds: Int,
    isReaderMode: Boolean,
    isEditing: Boolean,
    addressInput: String,
    onAddressInputChanged: (String) -> Unit,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    onReload: () -> Unit,
    onStopLoading: (() -> Unit)? = null,
    onToggleReaderMode: () -> Unit,
    onOpenShieldSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ad Shield button with live blocked counter badge
                Box(
                    modifier = Modifier
                        .testTag("shield_button")
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenShieldSheet() }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Ad Blocker Shield",
                            tint = if (blockedAds > 0) MangaAdShield else MangaPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (blockedAds > 0) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$blockedAds",
                                color = MangaAdShield,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Omnibox Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (isPrivate) MangaAmethyst.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(enabled = !isEditing) { onStartEditing() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // SSL or Private lock icon
                        Icon(
                            imageVector = if (isPrivate) Icons.Default.VisibilityOff else Icons.Default.Lock,
                            contentDescription = if (isPrivate) "Private Mode" else "Secure Connection",
                            tint = if (isPrivate) MangaAmethyst else Color(0xFF00E599),
                            modifier = Modifier.size(15.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isEditing) {
                            BasicTextField(
                                value = addressInput,
                                onValueChange = onAddressInputChanged,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(if (isPrivate) MangaAmethyst else MangaPrimary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { onSubmitAddress(addressInput) }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("address_input")
                            )

                            IconButton(
                                onClick = onStopEditing,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel edit",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            val displayText = if (currentUrl.startsWith("https://") || currentUrl.startsWith("http://")) {
                                try {
                                    android.net.Uri.parse(currentUrl).host ?: currentUrl
                                } catch (_: Exception) {
                                    currentUrl
                                }
                            } else {
                                title.ifBlank { "Search or type URL" }
                            }

                            Text(
                                text = displayText,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Reader Mode Quick Pill
                        if (!isEditing) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isReaderMode) MangaPrimary else Color.Transparent)
                                    .clickable { onToggleReaderMode() }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = "Reader Mode",
                                    tint = if (isReaderMode) Color.White else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            if (isLoading && onStopLoading != null) {
                                IconButton(
                                    onClick = onStopLoading,
                                    modifier = Modifier.size(28.dp).testTag("stop_loading_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Stop Loading",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = onReload,
                                    modifier = Modifier.size(28.dp).testTag("reload_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reload",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progress bar
            if (isLoading && progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter),
                    color = if (isPrivate) MangaAmethyst else MangaPrimary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
