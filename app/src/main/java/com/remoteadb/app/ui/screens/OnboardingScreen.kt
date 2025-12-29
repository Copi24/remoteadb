package com.remoteadb.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remoteadb.app.ui.components.GoldGradientButton
import com.remoteadb.app.ui.theme.*
import com.remoteadb.app.utils.CloudflareManager
import com.remoteadb.app.utils.TunnelProvider
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val gradient: List<Color>
)

@OptIn(ExperimentalAnimationApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onProviderSelected: (TunnelProvider) -> Unit,
    onTokenSubmit: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.PhoneAndroid,
            title = "Remote ADB",
            description = "Access your Android device from anywhere in the world using a secure tunnel. Debug, install apps, and manage your device remotely!",
            gradient = listOf(GoldPrimary, GoldDark)
        ),
        OnboardingPage(
            icon = Icons.Default.Security,
            title = "Root Required",
            description = "This app requires ROOT access to enable ADB over TCP. Make sure your device is rooted with Magisk or similar.",
            gradient = listOf(GoldLight, GoldPrimary)
        ),
        OnboardingPage(
            icon = Icons.Default.Cloud,
            title = "Choose Your Tunnel",
            description = "Select a tunnel provider to expose your ADB port to the internet securely.",
            gradient = listOf(GoldDark, GoldPrimary)
        )
    )
    
    var selectedProvider by remember { mutableStateOf(TunnelProvider.CLOUDFLARE) }
    var authToken by remember { mutableStateOf("") }
    var showSetupScreen by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            AnimatedContent(
                targetState = showSetupScreen,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label = "setupScreen"
            ) { showSetup ->
                if (showSetup) {
                    // Setup screen based on provider
                    SetupScreen(
                        provider = selectedProvider,
                        authToken = authToken,
                        onAuthTokenChange = { authToken = it },
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        downloadError = downloadError,
                        onDownloadCloudflared = {
                            scope.launch {
                                isDownloading = true
                                downloadError = null
                                val success = CloudflareManager.downloadCloudflared(context) { progress ->
                                    downloadProgress = progress
                                }
                                isDownloading = false
                                if (!success) {
                                    downloadError = "Download failed. Check internet connection."
                                }
                            }
                        },
                        onComplete = {
                            if (selectedProvider == TunnelProvider.NGROK && authToken.isNotBlank()) {
                                onTokenSubmit(authToken)
                            }
                            onProviderSelected(selectedProvider)
                            onComplete()
                        },
                        onBack = { showSetupScreen = false },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pager
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) { page ->
                            if (page == 2) {
                                // Provider selection page
                                ProviderSelectionPage(
                                    selectedProvider = selectedProvider,
                                    onProviderSelected = { selectedProvider = it }
                                )
                            } else {
                                OnboardingPageContent(pages[page])
                            }
                        }
                        
                        // Page indicators
                        Row(
                            modifier = Modifier.padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(pages.size) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 24.dp else 8.dp, 8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) GoldPrimary else GoldDark.copy(alpha = 0.3f)
                                        )
                                        .animateContentSize()
                                )
                            }
                        }
                        
                        // Navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (pagerState.currentPage > 0) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                ) {
                                    Text("Back", color = TextSecondary)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(64.dp))
                            }
                            
                            GoldGradientButton(
                                text = if (pagerState.currentPage == pages.size - 1) "Continue" else "Next",
                                onClick = {
                                    if (pagerState.currentPage == pages.size - 1) {
                                        showSetupScreen = true
                                    } else {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                modifier = Modifier.width(140.dp),
                                icon = if (pagerState.currentPage == pages.size - 1) 
                                    Icons.Default.Settings else Icons.Default.ArrowForward
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderSelectionPage(
    selectedProvider: TunnelProvider,
    onProviderSelected: (TunnelProvider) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = GoldPrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Choose Tunnel Provider",
            style = MaterialTheme.typography.headlineMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Cloudflare option
        ProviderCard(
            provider = TunnelProvider.CLOUDFLARE,
            isSelected = selectedProvider == TunnelProvider.CLOUDFLARE,
            onClick = { onProviderSelected(TunnelProvider.CLOUDFLARE) },
            benefits = listOf(
                "✓ 100% FREE",
                "✓ No account needed",
                "✓ No signup required",
                "✓ Unlimited usage"
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Ngrok option
        ProviderCard(
            provider = TunnelProvider.NGROK,
            isSelected = selectedProvider == TunnelProvider.NGROK,
            onClick = { onProviderSelected(TunnelProvider.NGROK) },
            benefits = listOf(
                "✓ Free tier available",
                "✓ Requires free account",
                "✓ Static URLs (paid)",
                "✓ Dashboard & logs"
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Recommended: Cloudflare (easiest setup)",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProviderCard(
    provider: TunnelProvider,
    isSelected: Boolean,
    onClick: () -> Unit,
    benefits: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, GoldPrimary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkCard else DarkSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = GoldPrimary,
                    unselectedColor = TextMuted
                )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) GoldPrimary else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                benefits.forEach { benefit ->
                    Text(
                        text = benefit,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (benefit.contains("FREE")) StatusActive else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    provider: TunnelProvider,
    authToken: String,
    onAuthTokenChange: (String) -> Unit,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadError: String?,
    onDownloadCloudflared: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (provider == TunnelProvider.CLOUDFLARE) Icons.Default.CloudDownload else Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = GoldPrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (provider == TunnelProvider.CLOUDFLARE) "Setup Cloudflare" else "Setup Ngrok",
            style = MaterialTheme.typography.headlineMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (provider == TunnelProvider.CLOUDFLARE) {
            // Cloudflare setup
            Text(
                text = "🎉 Cloudflare tunnels are completely FREE!",
                style = MaterialTheme.typography.bodyLarge,
                color = StatusActive,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✓ No account needed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusActive
                    )
                    Text(
                        text = "✓ No signup or payment required",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusActive
                    )
                    Text(
                        text = "✓ Works instantly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusActive
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "We need to download the cloudflared binary (~25MB). This is a one-time download.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isDownloading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = downloadProgress / 100f,
                        color = GoldPrimary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
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
                        color = StatusInactive,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                GoldGradientButton(
                    text = "Download Cloudflared",
                    onClick = onDownloadCloudflared,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.CloudDownload
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Or skip if already downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            // Ngrok setup
            Text(
                text = "Enter your Ngrok auth token to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to get your token:",
                        style = MaterialTheme.typography.titleSmall,
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Go to ngrok.com", color = TextSecondary)
                    Text("2. Create a free account", color = TextSecondary)
                    Text("3. Go to Your Authtoken page", color = TextSecondary)
                    Text("4. Copy and paste it below", color = TextSecondary)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = authToken,
                onValueChange = onAuthTokenChange,
                label = { Text("Ngrok Auth Token") },
                placeholder = { Text("Paste your token here") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = GoldDark.copy(alpha = 0.5f),
                    cursorColor = GoldPrimary,
                    focusedLabelColor = GoldPrimary
                ),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ Ngrok Limitations",
                        style = MaterialTheme.typography.titleSmall,
                        color = StatusPending,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• TCP tunnels may require a paid plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "• Consider Cloudflare instead (100% free)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = TextSecondary)
            }
            
            GoldGradientButton(
                text = "Get Started",
                onClick = onComplete,
                modifier = Modifier.width(140.dp),
                enabled = if (provider == TunnelProvider.NGROK) authToken.isNotBlank() else !isDownloading,
                icon = Icons.Default.RocketLaunch
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated icon with gradient background
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(y = offsetY.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = page.gradient + Color.Transparent
                        )
                    )
            )
            
            // Icon background
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(page.gradient)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = DarkBackground
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge,
            color = GoldPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
