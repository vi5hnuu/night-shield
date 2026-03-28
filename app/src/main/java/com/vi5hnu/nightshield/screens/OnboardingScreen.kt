package com.vi5hnu.nightshield.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vi5hnu.nightshield.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

private val pages = listOf(
    OnboardingPage(R.drawable.shield_inactive, R.string.onboarding_title_1, R.string.onboarding_body_1),
    OnboardingPage(R.drawable.vibration_24px,  R.string.onboarding_title_2, R.string.onboarding_body_2),
    OnboardingPage(R.drawable.format_paint_24px, R.string.onboarding_title_3, R.string.onboarding_body_3),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(5f)
            ) { index ->
                val page = pages[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    // Icon in a glowing circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(140.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(page.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(36.dp))

                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(page.bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Dot indicators
            Row(
                modifier = Modifier.padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        animationSpec = tween(300),
                        label = "dot_color_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            Spacer(Modifier.weight(0.5f))

            // Primary action button
            Button(
                onClick = {
                    if (isLastPage) onComplete()
                    else scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isLastPage) stringResource(R.string.onboarding_get_started)
                    else stringResource(R.string.onboarding_next),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!isLastPage) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.height(44.dp))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
