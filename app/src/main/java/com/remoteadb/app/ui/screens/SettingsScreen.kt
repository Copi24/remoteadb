package com.remoteadb.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remoteadb.app.ui.components.GlowingCard
import com.remoteadb.app.ui.components.GoldDivider
import com.remoteadb.app.ui.components.GoldGradientButton
import com.remoteadb.app.ui.theme.*
import com.remoteadb.app.utils.CloudflareManager
import com.remoteadb.app.utils.TunnelProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    ngrokAuthToken: String,
    adbPort: String,
    autoStartOnBoot: Boolean,
    tunnelProvider: TunnelProvider,
    onNgrokTokenChange: (String) -> Unit,
    onAdbPortChange: (String) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onTunnelProviderChange: (TunnelProvider) -> Unit,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadSuccess by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Top bar
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Tunnel Provider Selection
                SettingsSection(title = "Tunnel Provider") {
                    TunnelProvider.values().forEach { provider ->
                        val isSelected = tunnelProvider == provider
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, GoldPrimary, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onTunnelProviderChange(provider) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) DarkCard else DarkSurfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onTunnelProviderChange(provider) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = GoldPrimary,
                                        unselectedColor = TextMuted
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) GoldPrimary else TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = provider.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                        if (provider != TunnelProvider.values().last()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                // Cloudflare Configuration (if selected)
                AnimatedVisibility(
                    visible = tunnelProvider == TunnelProvider.CLOUDFLARE,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SettingsSection(title = "Cloudflare Setup") {
                        Text(
                            text = "Cloudflare tunnels are FREE and require no account!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusActive
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isDownloading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    progress = downloadProgress / 100f,
                                    color = GoldPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Downloading... $downloadProgress%",
                                    color = TextSecondary
                                )
                            }
                        } else {
                            downloadError?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusInactive
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            if (downloadSuccess) {
                                Text(
                                    text = "✓ Cloudflared is ready!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StatusActive
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            GoldGradientButton(
                                text = if (downloadSuccess) "Re-download" else "Download Cloudflared",
                                onClick = {
                                    scope.launch {
                                        isDownloading = true
                                        downloadError = null
                                        downloadSuccess = false
                                        val success = CloudflareManager.downloadCloudflared(context) { progress ->
                                            downloadProgress = progress
                                        }
                                        isDownloading = false
                                        if (success) {
                                            downloadSuccess = true
                                        } else {
                                            downloadError = "Download failed. Check internet connection."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Default.CloudDownload
                            )
                        }
                    }
                }
                
                // Ngrok Configuration (if selected)
                AnimatedVisibility(
                    visible = tunnelProvider == TunnelProvider.NGROK,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SettingsSection(title = "Ngrok Configuration") {
                        var tokenVisible by remember { mutableStateOf(false) }
                        var editingToken by remember { mutableStateOf(ngrokAuthToken) }
                        
                        OutlinedTextField(
                            value = editingToken,
                            onValueChange = { editingToken = it },
                            label = { Text("Auth Token") },
                            placeholder = { Text("Enter your Ngrok auth token") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = GoldDark.copy(alpha = 0.5f),
                                cursorColor = GoldPrimary,
                                focusedLabelColor = GoldPrimary
                            ),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        imageVector = if (tokenVisible) Icons.Default.VisibilityOff 
                                            else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = GoldDark
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (tokenVisible) 
                                androidx.compose.ui.text.input.VisualTransformation.None 
                            else 
                                androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        GoldGradientButton(
                            text = "Save Token",
                            onClick = { onNgrokTokenChange(editingToken) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = editingToken.isNotBlank() && editingToken != ngrokAuthToken,
                            icon = Icons.Default.Save
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Get your free token at ngrok.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "⚠️ Note: Free ngrok may have limited TCP tunnels",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusPending
                        )
                    }
                }
                
                // ADB Configuration
                SettingsSection(title = "ADB Configuration") {
                    var editingPort by remember { mutableStateOf(adbPort) }
                    
                    OutlinedTextField(
                        value = editingPort,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() } && it.length <= 5) {
                                editingPort = it
                            }
                        },
                        label = { Text("ADB Port") },
                        placeholder = { Text("5555") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = GoldDark.copy(alpha = 0.5f),
                            cursorColor = GoldPrimary,
                            focusedLabelColor = GoldPrimary
                        ),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lan,
                                contentDescription = null,
                                tint = GoldDark
                            )
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    GoldGradientButton(
                        text = "Save Port",
                        onClick = { onAdbPortChange(editingPort) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editingPort.isNotBlank() && editingPort != adbPort,
                        icon = Icons.Default.Save
                    )
                }
                
                // Startup Settings
                SettingsSection(title = "Startup") {
                    SettingsToggleItem(
                        icon = Icons.Outlined.PowerSettingsNew,
                        title = "Auto-start on boot",
                        subtitle = "Automatically start tunnel when device boots",
                        checked = autoStartOnBoot,
                        onCheckedChange = onAutoStartChange
                    )
                }
                
                // About Section
                SettingsSection(title = "About") {
                    SettingsInfoItem(
                        icon = Icons.Outlined.Info,
                        title = "Version",
                        value = "1.1.0"
                    )
                    
                    GoldDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    SettingsInfoItem(
                        icon = Icons.Outlined.Cloud,
                        title = "Active Provider",
                        value = tunnelProvider.displayName
                    )
                    
                    GoldDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    SettingsInfoItem(
                        icon = Icons.Outlined.Code,
                        title = "Open Source",
                        value = "MIT License"
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlowingCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        content()
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GoldDark.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBackground,
                checkedTrackColor = GoldPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String
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
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
