package com.remoteadb.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteadb.app.service.ServiceState
import com.remoteadb.app.ui.components.*
import com.remoteadb.app.ui.theme.*
import com.remoteadb.app.utils.DeviceInfo

@Composable
fun HomeScreen(
    serviceState: ServiceState,
    tunnelUrl: String?,
    deviceInfo: DeviceInfo?,
    localIp: String?,
    adbPort: String,
    hasRootAccess: Boolean,
    onToggleService: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val isConnected = serviceState is ServiceState.Running
    val isLoading = serviceState is ServiceState.Starting || serviceState is ServiceState.Stopping
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Background gradient effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = if (isConnected) 0.15f else 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            // Top bar
            TopBar(onSettingsClick = onNavigateToSettings)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Main status card
            MainStatusCard(
                serviceState = serviceState,
                tunnelUrl = tunnelUrl,
                isConnected = isConnected,
                isLoading = isLoading,
                onToggle = onToggleService,
                onCopyUrl = { url ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB URL", url))
                    Toast.makeText(context, "URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Connection info card
            if (tunnelUrl != null || localIp != null) {
                ConnectionInfoCard(
                    tunnelUrl = tunnelUrl,
                    localIp = localIp,
                    port = adbPort
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Device info card
            deviceInfo?.let {
                DeviceInfoCard(deviceInfo = it, hasRootAccess = hasRootAccess)
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Quick actions card
            QuickActionsCard()
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TopBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Remote ADB",
                style = MaterialTheme.typography.headlineMedium,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Secure remote debugging",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DarkCard)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = GoldPrimary
            )
        }
    }
}

@Composable
private fun MainStatusCard(
    serviceState: ServiceState,
    tunnelUrl: String?,
    isConnected: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit,
    onCopyUrl: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    
    GlowingCard(
        modifier = Modifier.fillMaxWidth(),
        isActive = isConnected
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                // Animated glow ring
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(glowScale)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        StatusActive.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                
                // Main circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            color = when {
                                isLoading -> StatusPending.copy(alpha = 0.2f)
                                isConnected -> StatusActive.copy(alpha = 0.2f)
                                else -> DarkSurfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        AnimatedLoadingIndicator()
                    } else {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (isConnected) StatusActive else TextMuted
                        )
                    }
                }
            }
            
            // Status text
            Text(
                text = when (serviceState) {
                    is ServiceState.Running -> "Connected"
                    is ServiceState.Starting -> "Connecting..."
                    is ServiceState.Stopping -> "Disconnecting..."
                    is ServiceState.Error -> "Error"
                    else -> "Disconnected"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    isConnected -> StatusActive
                    isLoading -> StatusPending
                    serviceState is ServiceState.Error -> StatusInactive
                    else -> TextSecondary
                },
                fontWeight = FontWeight.SemiBold
            )
            
            // Error message
            if (serviceState is ServiceState.Error) {
                Text(
                    text = serviceState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusInactive,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                )
            }
            
            // Tunnel URL display
            AnimatedVisibility(
                visible = tunnelUrl != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                tunnelUrl?.let { url ->
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GoldDivider(modifier = Modifier.padding(vertical = 16.dp))
                        
                        Text(
                            text = "Remote URL",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkBackground)
                                .clickable { onCopyUrl(url) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = GoldPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Tap to copy • Use with: adb connect <url>",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Toggle button
            GoldGradientButton(
                text = when {
                    isLoading -> "Please wait..."
                    isConnected -> "Disconnect"
                    else -> "Connect"
                },
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                icon = when {
                    isLoading -> Icons.Default.HourglassEmpty
                    isConnected -> Icons.Default.Stop
                    else -> Icons.Default.PlayArrow
                }
            )
        }
    }
}

@Composable
private fun ConnectionInfoCard(
    tunnelUrl: String?,
    localIp: String?,
    port: String
) {
    GlowingCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Connection Details",
            style = MaterialTheme.typography.titleMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (localIp != null) {
            InfoRow(
                icon = Icons.Outlined.Wifi,
                label = "Local Address",
                value = "$localIp:$port"
            )
        }
        
        if (tunnelUrl != null) {
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow(
                icon = Icons.Outlined.Cloud,
                label = "Remote URL",
                value = tunnelUrl
            )
        }
    }
}

@Composable
private fun DeviceInfoCard(
    deviceInfo: DeviceInfo,
    hasRootAccess: Boolean
) {
    GlowingCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Device Info",
            style = MaterialTheme.typography.titleMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        InfoRow(
            icon = Icons.Outlined.PhoneAndroid,
            label = "Model",
            value = deviceInfo.model
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        InfoRow(
            icon = Icons.Outlined.Android,
            label = "Android",
            value = "${deviceInfo.androidVersion} (SDK ${deviceInfo.sdkVersion})"
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        InfoRow(
            icon = if (hasRootAccess) Icons.Outlined.Security else Icons.Outlined.Warning,
            label = "Root Access",
            value = if (hasRootAccess) "Granted" else "Not Available",
            valueColor = if (hasRootAccess) StatusActive else StatusInactive
        )
    }
}

@Composable
private fun QuickActionsCard() {
    GlowingCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "How to Connect",
            style = MaterialTheme.typography.titleMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepItem(number = 1, text = "Click 'Connect' to start the tunnel")
            StepItem(number = 2, text = "Copy the remote URL")
            StepItem(number = 3, text = "Run: adb connect <url>")
            StepItem(number = 4, text = "You're connected remotely!")
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoldDark,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StepItem(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GoldPrimary, GoldDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = DarkBackground,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
