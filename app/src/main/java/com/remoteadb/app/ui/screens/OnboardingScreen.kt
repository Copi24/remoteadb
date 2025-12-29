package com.remoteadb.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.offset
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
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.PhoneAndroid,
            title = "Remote ADB",
            description = "Access your Android device from anywhere in the world using a secure Cloudflare tunnel. Debug, install apps, and manage your device remotely!",
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
            title = "Zero Setup",
            description = "Each device gets a unique hostname like abc123.676967.xyz. Just tap Connect and you're ready to debug from anywhere!",
            gradient = listOf(GoldDark, GoldPrimary)
        )
    )
    
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
            
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    OnboardingPageContent(pages[page])
                }
                
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
                        text = if (pagerState.currentPage == pages.size - 1) "Get Started" else "Next",
                        onClick = {
                            if (pagerState.currentPage == pages.size - 1) {
                                onComplete()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = Modifier.width(140.dp),
                        icon = if (pagerState.currentPage == pages.size - 1) 
                            Icons.Default.RocketLaunch else Icons.Default.ArrowForward
                    )
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
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(y = offsetY.dp),
            contentAlignment = Alignment.Center
        ) {
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
