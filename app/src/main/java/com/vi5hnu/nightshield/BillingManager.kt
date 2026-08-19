package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.android.billingclient.api.*
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
 * Product in Play Console:
 *   ID: night_shield_pro  |  Type: One-time
 */
object BillingManager {

    const val PRODUCT_ID = "night_shield_pro"
    private const val PREFS = "billing_prefs"
    private const val KEY_IS_PRO = "is_pro"

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
     * Play's localised price for [PRODUCT_ID] (e.g. "₹50.00", "$0.99), or null until Play answers.
     *
     * The paywall used to print a hardcoded price, which is wrong in every other currency and
     * silently stale after a price change. Callers must handle null rather than invent a number.
     */
    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call once from [MainActivity.onCreate] (and from services that need entitlement in a cold
     * process). Applies cached pro status immediately, then connects and verifies with Play.
     */
    fun init(context: Context) {
        appContext = context.applicationContext

        // Guard against multiple calls (service + activity in same process) to avoid leaking BillingClient
        if (billingClient != null) return

        if (context.billingPrefs().getBoolean(KEY_IS_PRO, false)) ProGate.grant()

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
                queryPrice()
            }
            // The initial connection never completed — typically the app was launched offline.
            // Retry it; its callback re-runs both queries. Querying a not-ready client here would
            // only produce a failed reconnection.
            else -> startConnection(context)
        }
    }

    /**
     * Launch the Google Play purchase sheet.
     * Must be called from a live, UI-attached [Activity].
     */
    fun purchase(activity: Activity) {
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
            // products) instead of a bare List<ProductDetails>.
            val details = productDetailsResult.productDetailsList.firstOrNull()
            if (details == null) {
                toast(activity, "Night Shield Pro isn't available on this account right now.")
                return@queryProductDetailsAsync
            }

            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .build()
                        )
                    )
                    .build()
            )
        }
    }

    /**
     * Entitlement as last verified by Play, readable without starting the billing client.
     *
     * For components that run in a cold process where [ProGate] has not been populated yet — a
     * widget update, for instance — reading the in-memory gate would report "not Pro" for a paying
     * user. This reads the cache Play itself last wrote.
     */
    fun isProCached(context: Context): Boolean =
        context.billingPrefs().getBoolean(KEY_IS_PRO, false)

    /**
     * Re-query Play purchases — used by the "Restore purchase" button.
     * On reinstall with the same Google account this restores pro access.
     */
    fun restore(context: Context) {
        queryPurchases(context.applicationContext, announceResult = true)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Result of the purchase sheet.
     *
     * Every non-OK code used to be dropped, which made a failed or already-owned purchase
     * indistinguishable from a working app that ignored the tap.
     */
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val context = appContext ?: return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(context, it) }

            // The account already owns Pro (common after a reinstall). Not an error — re-query so
            // the entitlement is applied instead of leaving the user staring at the paywall.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                queryPurchases(context, announceResult = true)

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
                        queryPrice()
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

    /** Fetches the localised price so the paywall can show Play's own number. */
    private fun queryPrice() {
        val client = billingClient?.takeIf { it.isReady } ?: return
        client.queryProductDetailsAsync(productDetailsParams()) { result, productDetailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            _formattedPrice.value = productDetailsResult.productDetailsList
                .firstOrNull()
                ?.oneTimePurchaseOfferDetails
                ?.formattedPrice
        }
    }

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

            val isPurchased = purchases.any {
                PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (isPurchased) {
                val wasPro = context.billingPrefs().getBoolean(KEY_IS_PRO, false)
                ProGate.grant()
                setProCache(context, true)
                purchases.filter { !it.isAcknowledged }
                    .forEach { acknowledgePurchase(it) }
                if (announceResult) {
                    toast(
                        context,
                        if (wasPro) "Pro is active on this account." else "Pro restored. Enjoy!",
                    )
                }
            } else {
                // No valid purchase for this Google account: never purchased, refunded, revoked,
                // or the device is signed into a different account than the one that bought it.
                val wasPro = context.billingPrefs().getBoolean(KEY_IS_PRO, false)
                ProGate.revoke()
                setProCache(context, false)
                // Reset Pro-only settings so they don't persist without entitlement.
                if (wasPro) {
                    OverlayHelpers.enforceFreeLimits(context)
                    toast(
                        context,
                        "Pro is no longer active on this Google account. Pro features have been turned off.",
                    )
                } else if (announceResult) {
                    toast(context, "No previous purchase found on this Google account.")
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

        ProGate.grant()
        setProCache(context, true)
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

    private fun setProCache(context: Context, isPro: Boolean) =
        context.billingPrefs().edit().putBoolean(KEY_IS_PRO, isPro).apply()

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
