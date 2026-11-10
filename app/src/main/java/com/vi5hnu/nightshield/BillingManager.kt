package com.vi5hnu.nightshield

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import com.android.billingclient.api.*
import com.vi5hnu.nightshield.billing.OfferInput
import com.vi5hnu.nightshield.billing.OfferSelector
import com.vi5hnu.nightshield.billing.ProOffer
import com.vi5hnu.nightshield.billing.PurchaseVerifier
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Google Play in-app billing for the "night_shield_pro" one-time product.
 *
 * Flow:
 *  1. [init] is called once per process — applies the cached status, then connects to Play and
 *     verifies in the background.
 *  2. [refresh] re-verifies whenever the app comes back to the foreground, so entitlement is not
 *     frozen at whatever the first connection attempt happened to return.
 *  3. [purchase] launches the Play billing sheet from a UI-attached Activity.
 *  4. On success, [ProGate.grant] is called and the status is cached in SharedPreferences.
 *  5. On reinstall, [queryPurchases] runs on connect and restores access. Users can also trigger
 *     it manually with "Restore purchase" → [restore].
 *
 * Entitlement always comes from Play. The SharedPreferences cache exists only so the UI does not
 * flicker while Play is being consulted, and it is excluded from backup (see
 * res/xml/backup_rules.xml) so it cannot travel to a device that never paid.
 *
 * Security: every purchase is checked by [PurchaseVerifier] (Play's RSA signature + our package)
 * before it grants anything or is acknowledged. The cache stores that signed receipt rather than a
 * bare boolean and re-verifies it on read, so flipping a value in billing_prefs.xml unlocks nothing.
 *
 * Product in Play Console:
 *   ID: night_shield_pro  |  Type: One-time
 *
 * Offers: discount offers configured in Play Console are only returned by
 * `getOneTimePurchaseOfferDetailsList()` and only charged when their offer token is passed to the
 * billing flow. [OfferSelector] picks which one to advertise; [offer] carries it to the paywall and
 * [purchase] launches with that same token.
 */
object BillingManager {

    const val PRODUCT_ID = "night_shield_pro"
    private const val PREFS = "billing_prefs"

    /** Play's documented promo-code deep link (a code can be appended as `?code=`). */
    private const val REDEEM_URL = "https://play.google.com/redeem"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /**
     * Pre-receipt cache (app versions before signature verification). Honoured only until this
     * version's first successful Play check replaces it, so an existing buyer who updates while
     * offline keeps Pro. It is then deleted and never read again.
     */
    private const val KEY_LEGACY_IS_PRO = "is_pro"

    /** Play-signed purchase JSON + signature of the owned Pro purchase; absent when not Pro. */
    private const val KEY_RECEIPT_JSON = "receipt_json"
    private const val KEY_RECEIPT_SIG = "receipt_sig"

    /** Random per-install id, hashed into obfuscatedAccountId. Never derived from personal data. */
    private const val KEY_INSTALL_ID = "install_id"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var billingClient: BillingClient? = null

    /** Application context captured at [init], so callbacks can reach prefs and show messages. */
    private var appContext: Context? = null

    /**
     * True between [startConnection] and its callback.
     *
     * Play's client rejects overlapping connection attempts ("already in the process of
     * connecting", result 5), and any API call made against a not-yet-ready client triggers one,
     * so both the connection and the queries are gated on readiness rather than fired blind.
     */
    private var connecting = false

    /**
     * True from the Unlock tap until Play reports back. A double tap used to start two flows; the
     * second failed and showed "Purchase couldn't be completed" over a purchase that was working.
     */
    private var purchaseInFlight = false

    /**
     * The offer the paywall shows and checkout uses: Play's localised price, plus the struck-through
     * full price, savings label and end time when a discount offer is live. Null until Play answers.
     *
     * The paywall used to print a hardcoded price, and later only the base price, so discount
     * offers set up in Play Console were never shown or charged. Callers must handle null rather
     * than invent a number.
     */
    private val _offer = MutableStateFlow<ProOffer?>(null)
    val offer: StateFlow<ProOffer?> = _offer.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call once from [MainActivity.onCreate] (and from services that need entitlement in a cold
     * process). Applies cached pro status immediately, then connects and verifies with Play.
     */
    fun init(context: Context) {
        appContext = context.applicationContext

        // Guard against multiple calls (service + activity in same process) to avoid leaking BillingClient
        if (billingClient != null) return

        if (isProCached(context)) ProGate.grant()

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            // PBL 8+: let the library transparently re-establish the connection whenever
            // the Play service drops and an API call is made. This replaces manual retry
            // logic in onBillingServiceDisconnected (see startConnection).
            .enableAutoServiceReconnection()
            .build()

        startConnection(context.applicationContext)
    }

    /**
     * Re-verify entitlement and price. Call when the app returns to the foreground.
     *
     * Without this, both are decided once per process by the first connection attempt: a paying
     * user who launched while offline would stay locked out, and a refund would go unnoticed for
     * as long as the process lives — which, with a long-running foreground service, can be days.
     */
    fun refresh() {
        val context = appContext ?: return
        val client = billingClient
        when {
            client == null -> init(context)
            client.isReady -> {
                queryPurchases(context)
                queryOffer()
            }
            // The initial connection never completed — typically the app was launched offline.
            // Retry it; its callback re-runs both queries. Querying a not-ready client here would
            // only produce a failed reconnection.
            else -> startConnection(context)
        }
    }

    /**
     * Re-fetch the advertised offer only. The paywall calls this when it opens and when the offer
     * it shows reaches its end time, so an expired discount never stays on screen.
     */
    fun refreshOffer() {
        if (billingClient?.isReady == true) queryOffer() else refresh()
    }

    /**
     * Launch the Google Play purchase sheet.
     * Must be called from a live, UI-attached [Activity].
     */
    fun purchase(activity: Activity) {
        if (purchaseInFlight) return
        val client = billingClient
        if (client == null || !client.isReady) {
            // Previously a silent return: the Unlock button simply did nothing and the user had no
            // idea why. Tell them, and kick the connection so the next tap is likely to work.
            toast(activity, "Google Play isn't ready yet. Please try again in a moment.")
            if (client == null) init(activity.applicationContext)
            else startConnection(activity.applicationContext)
            return
        }

        client.queryProductDetailsAsync(productDetailsParams()) { result, productDetailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                toast(activity, "Couldn't load the price from Google Play. Check your connection.")
                return@queryProductDetailsAsync
            }
            // PBL 8+: the callback delivers a QueryProductDetailsResult (fetched list + unfetched
            // products) instead of a bare List<ProductDetails>. ProductDetails is re-queried here
            // rather than cached: Google warns stale objects make launchBillingFlow fail.
            val details = productDetailsResult.productDetailsList.firstOrNull()
            val selected = details?.let { selectOffer(it) }
            if (details == null || selected == null) {
                toast(activity, "Night Shield Pro isn't available on this account right now.")
                return@queryProductDetailsAsync
            }
            // Publish what is about to be charged so the paywall matches the Play sheet.
            _offer.value = selected

            purchaseInFlight = true
            val launch = client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                // Without the token Play charges the base price, discount or not.
                                .apply { selected.offerToken?.let { setOfferToken(it) } }
                                .build()
                        )
                    )
                    // Lets Google's fraud detection spot one install buying across many accounts.
                    .setObfuscatedAccountId(obfuscatedAccountId(activity))
                    // setIsOfferPersonalized is deliberately not set: the EU disclosure applies
                    // only to prices personalised by automated decision-making, which we never do.
                    .build()
            )
            // The sheet never opened, so no onPurchasesUpdated will arrive to clear the flag.
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) purchaseInFlight = false
        }
    }

    /**
     * Entitlement as last verified by Play, readable without starting the billing client.
     *
     * For components that run in a cold process where [ProGate] has not been populated yet — a
     * widget update, for instance — reading the in-memory gate would report "not Pro" for a paying
     * user. This reads the cache Play itself last wrote.
     */
    fun isProCached(context: Context): Boolean {
        val prefs = context.billingPrefs()
        val json = prefs.getString(KEY_RECEIPT_JSON, null)
        val sig = prefs.getString(KEY_RECEIPT_SIG, null)
        if (json != null && sig != null) {
            return PurchaseVerifier.isGenuine(json, sig, context.packageName)
        }
        return prefs.getBoolean(KEY_LEGACY_IS_PRO, false)
    }

    /**
     * Re-query Play purchases — used by the "Restore purchase" button.
     * On reinstall with the same Google account this restores pro access.
     */
    fun restore(context: Context) {
        queryPurchases(context.applicationContext, announceResult = true)
    }

    /**
     * Opens Play's "Redeem code" screen for Play Console promo codes.
     *
     * Redemption happens entirely inside Play; nothing is passed back. When the user returns,
     * [MainActivity.onResume] → [refresh] → [queryPurchases] finds the new purchase, verifies and
     * acknowledges it, and announces the unlock. Codes can also be redeemed in the purchase sheet
     * itself (payment method → Redeem code), which arrives through [onPurchasesUpdated] as usual.
     */
    fun openRedeem(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, REDEEM_URL.toUri())
            .setPackage(PLAY_STORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No Play Store app (rare on a device that installed us from Play): let any handler try.
            try {
                context.startActivity(intent.setPackage(null))
            } catch (_: ActivityNotFoundException) {
                toast(context, "Open the Google Play Store and choose Redeem code.")
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Result of the purchase sheet.
     *
     * Every non-OK code used to be dropped, which made a failed or already-owned purchase
     * indistinguishable from a working app that ignored the tap.
     */
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        purchaseInFlight = false
        val context = appContext ?: return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(context, it) }

            // The account already owns Pro (common after a reinstall). Not an error — re-query so
            // the entitlement is applied instead of leaving the user staring at the paywall.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                queryPurchases(context, announceResult = true)

            // The offer shown ended, hit its purchase limit, or isn't sold in the user's (changed)
            // Play country. Play re-checks eligibility at launch; refresh so the paywall shows
            // what can actually be bought now.
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                queryOffer()
                toast(context, "That offer is no longer available. Showing the current price.")
            }

            // The user backed out of the sheet deliberately; saying anything would be noise.
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
                toast(context, "Google Play is unavailable right now. Please try again later.")

            else ->
                toast(context, "Purchase couldn't be completed. Nothing has been charged.")
        }
    }

    private fun startConnection(context: Context) {
        val client = billingClient ?: return
        if (connecting || client.isReady) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryPurchases(context)
                        queryOffer()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                // No-op: enableAutoServiceReconnection() makes the library re-establish the
                // connection automatically on the next API call. Calling startConnection()
                // here would compete with that and is discouraged by the PBL docs.
            }
        })
    }

    /** Fetches the eligible offers so the paywall can show Play's own numbers. */
    private fun queryOffer() {
        val client = billingClient?.takeIf { it.isReady } ?: return
        client.queryProductDetailsAsync(productDetailsParams()) { result, productDetailsResult ->
            // A failed query keeps the last known offer rather than blanking the price.
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            _offer.value = productDetailsResult.productDetailsList.firstOrNull()?.let { selectOffer(it) }
        }
    }

    /**
     * Every offer this user is eligible for, mapped to [OfferInput] and handed to [OfferSelector].
     *
     * `getOneTimePurchaseOfferDetailsList()` is the only source of discount offers; the singular
     * `getOneTimePurchaseOfferDetails()` returns just the backwards-compatible base option, which is
     * why offers were previously invisible. It is kept as a fallback for a product with no list.
     */
    private fun selectOffer(details: ProductDetails): ProOffer? {
        val offers = details.oneTimePurchaseOfferDetailsList
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(details.oneTimePurchaseOfferDetails)
        return OfferSelector.select(offers.map { it.toOfferInput() }, System.currentTimeMillis())
    }

    private fun ProductDetails.OneTimePurchaseOfferDetails.toOfferInput() = OfferInput(
        offerToken = offerToken,
        offerId = offerId,
        purchaseOptionId = purchaseOptionId,
        formattedPrice = formattedPrice,
        priceMicros = priceAmountMicros,
        currencyCode = priceCurrencyCode,
        fullPriceMicros = fullPriceMicros,
        percentageDiscount = discountDisplayInfo?.percentageDiscount,
        formattedDiscountAmount = discountDisplayInfo?.discountAmount?.formattedDiscountAmount,
        validFromMillis = validTimeWindow?.startTimeMillis,
        validUntilMillis = validTimeWindow?.endTimeMillis,
        isRental = rentalDetails != null,
        isPreorder = preorderDetails != null,
    )

    private fun productDetailsParams() = QueryProductDetailsParams.newBuilder()
        .setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        )
        .build()

    /**
     * The authority on entitlement: whatever Play reports wins.
     *
     * @param announceResult true when the user asked for this explicitly ("Restore purchase" or an
     *   already-owned purchase), so silence would read as a broken button.
     */
    private fun queryPurchases(context: Context, announceResult: Boolean = false) {
        val client = billingClient
        if (client == null || !client.isReady) {
            // Only the user-initiated path says anything: a background re-check that arrives
            // before Play is ready is normal and must stay silent.
            if (announceResult) {
                toast(context, "Connecting to Google Play — please try again in a moment.")
            }
            startConnection(context)
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // A failed query says nothing about entitlement — never revoke on it, or a flaky
                // connection would lock a paying user out of what they bought.
                if (announceResult) {
                    toast(context, "Couldn't reach Google Play. Check your connection and try again.")
                }
                return@queryPurchasesAsync
            }

            // Only a Play-signed purchase counts; a forged one is treated as no purchase at all.
            val owned = purchases.firstOrNull {
                PRODUCT_ID in it.products &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    isGenuine(context, it)
            }
            if (owned != null) {
                val wasPro = isProCached(context)
                ProGate.grant()
                setProCache(context, owned)
                if (!owned.isAcknowledged) acknowledgePurchase(owned)
                when {
                    announceResult -> toast(
                        context,
                        if (wasPro) "Pro is active on this account." else "Pro restored. Enjoy!",
                    )
                    // Unlocked outside the purchase sheet — a promo code redeemed in the Play Store,
                    // or a slow payment that cleared while the app was closed. Granting silently
                    // left people unsure whether the code had worked.
                    !wasPro -> toast(context, "Pro unlocked. Thank you!")
                }
            } else {
                // No valid purchase for this Google account: never purchased, refunded, revoked,
                // or the device is signed into a different account than the one that bought it.
                val wasPro = isProCached(context)
                ProGate.revoke()
                setProCache(context, null)
                // Reset Pro-only settings so they don't persist without entitlement.
                // A slow payment (UPI, cash) that hasn't cleared is not "no purchase" — telling
                // the user nothing was found would invite a second, duplicate payment attempt.
                val isPending = purchases.any {
                    PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PENDING
                }
                if (wasPro) {
                    // A running ad-earned trial keeps Pro features until it ends (ProTrial
                    // applies the limits then), so only enforce when nothing else unlocks them.
                    if (!ProGate.isPro.value) OverlayHelpers.enforceFreeLimits(context)
                    toast(
                        context,
                        "Pro is no longer active on this Google account. Pro features have been turned off.",
                    )
                } else if (announceResult) {
                    toast(
                        context,
                        if (isPending) "Your payment is still pending. Pro unlocks as soon as Google Play confirms it."
                        else "No previous purchase found on this Google account.",
                    )
                }
            }
        }
    }

    private fun handlePurchase(context: Context, purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return

        // Slow payment methods (UPI mandates, cash) land here first. The purchase is real but not
        // yet paid for, so entitlement must wait — tell the user rather than appear to do nothing.
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            toast(context, "Payment pending. Pro unlocks as soon as Google Play confirms it.")
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Not signed by Play: grant nothing and do NOT acknowledge, so Play refunds it after three
        // days if it was somehow real. A genuine one is re-delivered by the next queryPurchases.
        if (!isGenuine(context, purchase)) {
            toast(context, "Purchase couldn't be verified with Google Play. Nothing has been unlocked.")
            return
        }

        ProGate.grant()
        setProCache(context, purchase)
        // Play auto-refunds unacknowledged purchases after three days, so this must not be skipped.
        if (!purchase.isAcknowledged) acknowledgePurchase(purchase)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        billingClient?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { /* Failures retry on the next queryPurchases, well inside Play's 3-day window. */ }
    }

    private fun isGenuine(context: Context, purchase: Purchase): Boolean =
        PurchaseVerifier.isGenuine(purchase.originalJson, purchase.signature, context.packageName)

    /**
     * Stores the verified Pro [purchase] as a signed receipt, or clears it when null. Always drops
     * the legacy boolean: once Play has answered, that answer replaces it for good.
     */
    private fun setProCache(context: Context, purchase: Purchase?) {
        val prefs = context.billingPrefs()
        val isPro = purchase != null
        val changed = isProCached(context) != isPro
        prefs.edit {
            remove(KEY_LEGACY_IS_PRO)
            if (purchase != null) {
                putString(KEY_RECEIPT_JSON, purchase.originalJson)
                putString(KEY_RECEIPT_SIG, purchase.signature)
            } else {
                remove(KEY_RECEIPT_JSON)
                remove(KEY_RECEIPT_SIG)
            }
        }
        // Widgets render Pro-only controls from this cache, so they must redraw when it flips —
        // otherwise a new buyer's intensity widget stays locked until the next unrelated update.
        if (changed) {
            NightShieldWidgetProvider.updateWidget(context)
            IntensityWidgetProvider.updateAll(context)
        }
    }

    /**
     * SHA-256 of a random per-install UUID (64 hex chars, Play's maximum). Play asks for an
     * identifier that can't be traced back to the person, so this deliberately carries no device or
     * account data. It lives in billing_prefs, which is excluded from backup.
     */
    private fun obfuscatedAccountId(context: Context): String {
        val prefs = context.billingPrefs()
        val id = prefs.getString(KEY_INSTALL_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString(KEY_INSTALL_ID, it) } }
        return MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** Billing callbacks arrive off the main thread; Toast requires the main looper. */
    private fun toast(context: Context, message: String) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun Context.billingPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
