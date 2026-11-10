package com.vi5hnu.nightshield.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vi5hnu.nightshield.BillingManager
import com.vi5hnu.nightshield.R
import com.vi5hnu.nightshield.billing.OfferBannerState
import com.vi5hnu.nightshield.billing.OfferCopy
import com.vi5hnu.nightshield.billing.ProOffer
import com.vi5hnu.nightshield.ui.components.InfoBanner
import com.vi5hnu.nightshield.ui.components.InfoBannerAction
import com.vi5hnu.nightshield.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Home-screen notice for a live Play discount offer on Pro.
 *
 * Shown only while Play reports a real discount (never for the base price) and only to users who
 * haven't bought Pro; trial users still see it, matching the paywall. Uses the calm [InfoBanner]
 * style so it reads as news, not an ad, and offers "Not now" per offer (see [OfferBannerState]).
 *
 * @param isPaid the user owns Pro; the banner never shows then.
 * @param onSeeOffer opens the paywall, where the price is explained in full.
 */
@Composable
fun OfferBanner(isPaid: Boolean, onSeeOffer: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val offer by BillingManager.offer.collectAsState()
    val endsText = rememberOfferEndsText(offer)
    // Re-read when the offer changes, so a new sale reappears even after an earlier dismissal.
    var dismissed by remember(offer) {
        mutableStateOf(offer?.let { OfferBannerState.isDismissed(context, it) } ?: false)
    }
    val shown = offer?.takeIf { !isPaid && it.isDiscounted && !dismissed }
    // The last discount actually displayed, so the exit animation keeps rendering it after the
    // offer ends or Pro is bought, instead of flashing the base offer's empty labels.
    val lastShown = remember { mutableStateOf<ProOffer?>(null) }
    SideEffect { if (shown != null) lastShown.value = shown }

    AnimatedVisibility(
        visible = shown != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        val o = shown ?: lastShown.value ?: return@AnimatedVisibility
        Column {
            Spacer(Modifier.height(Spacing.md))
            InfoBanner(
                // A discount under 1% has no label; never print "null".
                title = o.discountLabel?.let { "$it on Night Shield Pro" } ?: "Night Shield Pro is on offer",
                text = buildString {
                    append("${o.formattedPrice} instead of ${o.fullFormattedPrice} · one payment, no ads, ever.")
                    endsText?.let { append(" $it.") }
                },
                icon = R.drawable.ic_premium_24,
                actions = listOf(
                    InfoBannerAction("Not now") {
                        OfferBannerState.dismiss(context, o)
                        dismissed = true
                    },
                    InfoBannerAction("See offer", emphasised = true, onClick = onSeeOffer),
                ),
            )
        }
    }
}

/**
 * Offer deadline text ("Offer ends in 2d 5h"), re-evaluated every minute. When the offer's end
 * time passes, Play is asked again so the screen falls back to whatever is buyable now instead of
 * a dead price. Shared by the paywall and [OfferBanner] so both always agree.
 */
@Composable
internal fun rememberOfferEndsText(offer: ProOffer?): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Fresh eligibility each time an offer surface appears (offers can start or end while
    // the app is in the background).
    LaunchedEffect(Unit) { BillingManager.refreshOffer() }
    LaunchedEffect(offer?.endsAtMillis) {
        val end = offer?.endsAtMillis ?: return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= end) {
                BillingManager.refreshOffer()
                return@LaunchedEffect
            }
            delay(minOf(60_000L, end - now))
        }
    }
    return OfferCopy.endsText(offer?.endsAtMillis, now)
}
