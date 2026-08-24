package com.example.museumapp.ui.visitor.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorIllustration
import com.example.museumapp.ui.visitor.components.VisitorSpacing
import kotlinx.coroutines.launch

data class OnboardingPage(val image: String, val title: String, val body: String)

val VisitorOnboardingPages = listOf(
    OnboardingPage(
        VisitorAssets.OnboardingWelcome,
        "Welcome to PSAU Museum Guide",
        "Explore the heritage, artifacts, and stories of the museum."
    ),
    OnboardingPage(
        VisitorAssets.OnboardingExplore,
        "Discover the Collection",
        "Browse artifacts, historical information, facts, and museum stories."
    ),
    OnboardingPage(
        VisitorAssets.OnboardingAiScan,
        "Discover with AI",
        "Scan an artifact and let the museum guide help identify it."
    )
)

fun isVisitorOnboardingLastPage(page: Int): Boolean = page == VisitorOnboardingPages.lastIndex

fun visitorOnboardingActionLabel(page: Int): String =
    if (isVisitorOnboardingLastPage(page)) "Get Started" else "Next"

@Composable
fun VisitorOnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { VisitorOnboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = isVisitorOnboardingLastPage(pagerState.currentPage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = VisitorSpacing.Xl, vertical = VisitorSpacing.Lg),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onComplete) {
                Text("Skip")
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val item = VisitorOnboardingPages[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = VisitorSpacing.Xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VisitorIllustration(
                    model = item.image,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .heightIn(min = 210.dp, max = 330.dp)
                )
                Text(
                    text = item.title,
                    modifier = Modifier.padding(top = VisitorSpacing.Xxl),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item.body,
                    modifier = Modifier
                        .padding(top = VisitorSpacing.Md)
                        .fillMaxWidth(0.94f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            VisitorOnboardingPages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        .padding(horizontal = if (index == pagerState.currentPage) 14.dp else 5.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.heightIn(min = VisitorSpacing.Md))
        Button(
            onClick = {
                if (isLastPage) {
                    onComplete()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(visitorOnboardingActionLabel(pagerState.currentPage))
        }
    }
}
