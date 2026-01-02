package com.remoteadb.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.remoteadb.app.shizuku.ShizukuManager
import com.remoteadb.app.ui.components.*
import com.remoteadb.app.ui.theme.*
import com.remoteadb.app.utils.DeviceInfo
import com.remoteadb.app.utils.ExecutionMode
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

@Composable
fun HomeScreen(
    serviceState: ServiceState,
    tunnelUrl: String?,
    deviceId: String,
    deviceInfo: DeviceInfo?,
    localIp: String?,
    adbPort: String,
    executionMode: ExecutionMode,
    shizukuState: ShizukuManager.ShizukuState,
    onRequestShizukuPermission: () -> Unit,
    onToggleService: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val isConnected = serviceState is ServiceState.Running
    val isLoading = serviceState is ServiceState.Starting || serviceState is ServiceState.Stopping
    // Allow the button to trigger Shizuku permission request even before we have executionMode.
    val canConnect = executionMode != ExecutionMode.NONE || shizukuState is ShizukuManager.ShizukuState.NoPermission
    
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
            
            // Permission status card (if no access)
            if (!canConnect) {
                PermissionRequiredCard(
                    shizukuState = shizukuState,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onOpenShizukuApp = {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                // Open Play Store
                                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                                context.startActivity(playIntent)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Please install Shizuku from Play Store", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Main status card
            MainStatusCard(
                serviceState = serviceState,
                tunnelUrl = tunnelUrl,
                deviceId = deviceId,
                executionMode = executionMode,
                isConnected = isConnected,
                isLoading = isLoading,
                canConnect = canConnect,
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
                DeviceInfoCard(
                    deviceId = deviceId,
                    deviceInfo = it, 
                    executionMode = executionMode,
                    shizukuState = shizukuState
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Quick actions card
            QuickActionsCard(executionMode = executionMode)

            Spacer(modifier = Modifier.height(20.dp))

            DiagnosticsCard(
                serviceState = serviceState,
                executionMode = executionMode,
                shizukuState = shizukuState
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    shizukuState: ShizukuManager.ShizukuState,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit
) {
    GlowingCard(
        modifier = Modifier.fillMaxWidth(),
        isActive = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = StatusPending
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Elevated Access Required",
                style = MaterialTheme.typography.titleMedium,
                color = GoldPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "This app needs ROOT or Shizuku to enable wireless ADB.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (shizukuState) {
                is ShizukuManager.ShizukuState.NotInstalled -> {
                    Text(
                        text = "Shizuku is not installed. Install it from Play Store for non-root access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GoldGradientButton(
                        text = "Get Shizuku",
                        onClick = onOpenShizukuApp,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.GetApp
                    )
                }
                is ShizukuManager.ShizukuState.NotRunning -> {
                    Text(
                        text = "Shizuku is installed but not running. Open Shizuku app and start it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GoldGradientButton(
                        text = "Open Shizuku",
                        onClick = onOpenShizukuApp,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.OpenInNew
                    )
                }
                is ShizukuManager.ShizukuState.NoPermission -> {
                    Text(
                        text = "Shizuku is running! Tap to grant permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusActive,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GoldGradientButton(
                        text = "Grant Shizuku Permission",
                        onClick = onRequestShizukuPermission,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Security
                    )
                }
                else -> {
                    Text(
                        text = "Grant ROOT access or install Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
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
    deviceId: String,
    executionMode: ExecutionMode,
    isConnected: Boolean,
    isLoading: Boolean,
    canConnect: Boolean,
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
                            text = "Tap to copy",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // PC instructions
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = DarkBackground
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "On your PC, run:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                when (executionMode) {
                                    ExecutionMode.ROOT -> {
                                        Text(
                                            text = "curl -sL 676967.xyz/c | bash -s $deviceId",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = GoldLight
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "adb connect localhost:5555",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = GoldLight
                                        )
                                    }

                                    ExecutionMode.SHIZUKU -> {
                                        Text(
                                            text = "curl -sLO 676967.xyz/radb.py",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = GoldLight
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "python radb.py $deviceId shell",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = GoldLight
                                        )
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Toggle button
            GoldGradientButton(
                text = when {
                    !canConnect -> "Setup Required"
                    isLoading -> "Please wait..."
                    isConnected -> "Disconnect"
                    else -> "Connect"
                },
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                enabled = canConnect && !isLoading,
                icon = when {
                    !canConnect -> Icons.Default.Lock
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
                label = "Remote Hostname",
                value = tunnelUrl
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Uses cloudflared on PC to connect",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun DeviceInfoCard(
    deviceId: String,
    deviceInfo: DeviceInfo,
    executionMode: ExecutionMode,
    shizukuState: ShizukuManager.ShizukuState
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
            icon = Icons.Outlined.Fingerprint,
            label = "Device ID",
            value = deviceId
        )

        Spacer(modifier = Modifier.height(12.dp))
        
        InfoRow(
            icon = Icons.Outlined.Android,
            label = "Android",
            value = "${deviceInfo.androidVersion} (SDK ${deviceInfo.sdkVersion})"
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        InfoRow(
            icon = when (executionMode) {
                ExecutionMode.ROOT -> Icons.Outlined.Security
                ExecutionMode.SHIZUKU -> Icons.Outlined.Verified
                ExecutionMode.NONE -> Icons.Outlined.Warning
            },
            label = "Access Mode",
            value = when (executionMode) {
                ExecutionMode.ROOT -> "ROOT"
                ExecutionMode.SHIZUKU -> "Shizuku"
                ExecutionMode.NONE -> when (shizukuState) {
                    is ShizukuManager.ShizukuState.NotInstalled -> "None (Install Shizuku)"
                    is ShizukuManager.ShizukuState.NotRunning -> "None (Start Shizuku)"
                    is ShizukuManager.ShizukuState.NoPermission -> "None (Grant Permission)"
                    else -> "None"
                }
            },
            valueColor = when (executionMode) {
                ExecutionMode.ROOT -> StatusActive
                ExecutionMode.SHIZUKU -> StatusActive
                ExecutionMode.NONE -> StatusInactive
            }
        )
    }
}

@Composable
private fun QuickActionsCard(executionMode: ExecutionMode) {
    GlowingCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "How to Connect",
            style = MaterialTheme.typography.titleMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StepItem(number = 1, text = "Tap 'Connect' on your phone")
            StepItem(number = 2, text = "On your PC: run the command shown above")
            when (executionMode) {
                ExecutionMode.ROOT -> {
                    StepItem(number = 3, text = "Then: adb connect localhost:5555")
                }
                ExecutionMode.SHIZUKU -> {
                    StepItem(number = 3, text = "Then: python radb.py DEVICE_ID shell")
                }
                ExecutionMode.NONE -> {
                    StepItem(number = 3, text = "Grant ROOT or Shizuku permission first")
                }
            }
            StepItem(number = 4, text = "Keep the PC tunnel running (Ctrl+C to stop)")
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
private fun DiagnosticsCard(
    serviceState: ServiceState,
    executionMode: ExecutionMode,
    shizukuState: ShizukuManager.ShizukuState
) {
    val context = LocalContext.current

    data class CloudflaredDiag(
        val path: String,
        val exists: Boolean,
        val sizeBytes: Long,
        val canExecute: Boolean,
        val modeOctal: String,
        val execTest: String
    )

    data class Diag(
        val shizukuPing: String,
        val shizukuPermission: String,
        val shizukuId: String,
        val cloudflared: List<CloudflaredDiag>
    )

    var diag by remember { mutableStateOf<Diag?>(null) }

    LaunchedEffect(Unit) {
        diag = withContext(Dispatchers.IO) {
            val shizukuPing = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val shizukuPerm = runCatching { Shizuku.checkSelfPermission() }
                .map {
                    if (it == android.content.pm.PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED($it)"
                }
                .getOrElse { "ERROR(${it.javaClass.simpleName}: ${it.message})" }

            val shizukuId = runCatching {
                if (shizukuState is ShizukuManager.ShizukuState.Ready) {
                    val id = ShizukuManager.executeCommand("id").stdout.trim().lineSequence().firstOrNull().orEmpty()
                    if (id.isNotBlank()) id else "(empty)"
                } else {
                    "(not ready)"
                }
            }.getOrElse { "ERROR(${it.javaClass.simpleName}: ${it.message})" }

            fun diagCloudflared(file: java.io.File): CloudflaredDiag {
                val exists = file.exists()
                val size = runCatching { file.length() }.getOrDefault(0)
                val canExec = runCatching { file.canExecute() }.getOrDefault(false)
                val modeOctal = runCatching {
                    val mode = android.system.Os.stat(file.absolutePath).st_mode and 0x1FF
                    "0" + Integer.toOctalString(mode)
                }.getOrElse { "ERR" }

                val execTest = if (!exists) {
                    "missing"
                } else {
                    runCatching {
                        val p = ProcessBuilder(file.absolutePath, "--version")
                            .redirectErrorStream(true)
                            .start()
                        val finished = p.waitFor(2, TimeUnit.SECONDS)
                        if (!finished) {
                            p.destroy()
                            return@runCatching "timeout"
                        }
                        val out = p.inputStream.bufferedReader().readText().trim()
                        if (out.isNotBlank()) out.lineSequence().first() else "exit=${p.exitValue()}"
                    }.getOrElse { "ERROR(${it.javaClass.simpleName}: ${it.message})" }
                }

                return CloudflaredDiag(
                    path = file.absolutePath,
                    exists = exists,
                    sizeBytes = size,
                    canExecute = canExec,
                    modeOctal = modeOctal,
                    execTest = execTest.take(120)
                )
            }

            val cloudflared = listOf(
                diagCloudflared(java.io.File(context.applicationInfo.nativeLibraryDir, "libcloudflared.so")),
                diagCloudflared(java.io.File(context.codeCacheDir, "cloudflared")),
                diagCloudflared(java.io.File(context.filesDir, "cloudflared"))
            )

            Diag(
                shizukuPing = shizukuPing.toString(),
                shizukuPermission = shizukuPerm,
                shizukuId = shizukuId,
                cloudflared = cloudflared
            )
        }
    }

    GlowingCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.titleMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoRow(
            icon = Icons.Outlined.Info,
            label = "Service State",
            value = when (serviceState) {
                is ServiceState.Running -> "Running"
                is ServiceState.Starting -> "Starting"
                is ServiceState.Stopping -> "Stopping"
                is ServiceState.Error -> "Error"
                else -> "Stopped"
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoRow(
            icon = Icons.Outlined.Security,
            label = "Execution Mode",
            value = executionMode.name
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoRow(
            icon = Icons.Outlined.Verified,
            label = "Shizuku State",
            value = shizukuState::class.simpleName ?: "Unknown"
        )

        diag?.let { d ->
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow(
                icon = Icons.Outlined.Link,
                label = "Shizuku pingBinder",
                value = d.shizukuPing
            )

            Spacer(modifier = Modifier.height(12.dp))
            InfoRow(
                icon = Icons.Outlined.Security,
                label = "Shizuku Permission",
                value = d.shizukuPermission
            )

            Spacer(modifier = Modifier.height(12.dp))
            InfoRow(
                icon = Icons.Outlined.Verified,
                label = "Shizuku id",
                value = d.shizukuId
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "cloudflared",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )

            d.cloudflared.forEach { c ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${c.path}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "exists=${c.exists} size=${c.sizeBytes} canExec=${c.canExecute} mode=${c.modeOctal}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "exec: ${c.execTest}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = GoldLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } ?: run {
            Text(
                text = "Collecting diagnostics...",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
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
