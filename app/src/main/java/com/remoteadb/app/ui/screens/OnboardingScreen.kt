package com.remoteadb.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remoteadb.app.ui.components.GoldGradientButton
import com.remoteadb.app.ui.theme.*
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
    onTokenSubmit: (String) -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.PhoneAndroid,
            title = "Remote ADB",
            description = "Access your Android device from anywhere in the world using a secure tunnel.",
            gradient = listOf(GoldPrimary, GoldDark)
        ),
        OnboardingPage(
            icon = Icons.Default.Security,
            title = "Secure Connection",
            description = "Your ADB connection is secured through Ngrok's encrypted tunneling technology.",
            gradient = listOf(GoldLight, GoldPrimary)
        ),
        OnboardingPage(
            icon = Icons.Default.Bolt,
            title = "Easy Setup",
            description = "Just paste your Ngrok auth token and you're ready to connect remotely!",
            gradient = listOf(GoldDark, GoldPrimary)
        )
    )
    
    var authToken by remember { mutableStateOf("") }
    var showTokenInput by remember { mutableStateOf(false) }
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    
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
            
            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPageContent(pages[page])
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
            
            // Token input or navigation
            AnimatedContent(
                targetState = showTokenInput,
                transitionSpec = {
                    slideInVertically { it } + fadeIn() togetherWith
                    slideOutVertically { -it } + fadeOut()
                },
                label = "tokenInput"
            ) { showInput ->
                if (showInput) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = authToken,
                            onValueChange = { authToken = it },
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
                        
                        Text(
                            text = "Get your free token at ngrok.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        
                        GoldGradientButton(
                            text = "Get Started",
                            onClick = {
                                if (authToken.isNotBlank()) {
                                    onTokenSubmit(authToken)
                                    onComplete()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = authToken.isNotBlank(),
                            icon = Icons.Default.RocketLaunch
                        )
                    }
                } else {
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
                            text = if (pagerState.currentPage == pages.size - 1) "Setup" else "Next",
                            onClick = {
                                if (pagerState.currentPage == pages.size - 1) {
                                    showTokenInput = true
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
            
            Spacer(modifier = Modifier.height(32.dp))
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
