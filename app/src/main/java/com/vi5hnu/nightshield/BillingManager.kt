package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
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
 *  1. [init] is called once in MainActivity.onCreate — loads cached status,
 *     then connects to Play and verifies in the background.
 *  2. [purchase] launches the Play billing sheet from a UI-attached Activity.
 *  3. On success, [ProGate.grant] is called and status is cached in SharedPreferences.
 *  4. On reinstall, [queryPurchases] runs automatically on connect and restores access.
 *     Users can also trigger this manually via the "Restore Purchase" button → [restore].
 *
 * Product to create in Play Console:
 *   ID: night_shield_pro  |  Type: One-time  |  Price: ₹49
 */
object BillingManager {

    const val PRODUCT_ID = "night_shield_pro"
    private const val PREFS = "billing_prefs"
    private const val KEY_IS_PRO = "is_pro"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var billingClient: BillingClient? = null

    /**
     * Play's localised price for [PRODUCT_ID] (e.g. "₹49", "$0.99"), or null until Play answers.
     *
     * The paywall used to print a hardcoded "₹49", which is wrong in every other currency and
     * silently stale after a price change. Callers should fall back to their own copy while this
     * is null.
     */
    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call once from [MainActivity.onCreate].
     * Immediately applies cached pro status so UI never flickers,
     * then connects and verifies with Play in the background.
     */
    fun init(context: Context) {
        // Guard against multiple calls (service + activity in same process) to avoid leaking BillingClient
        if (billingClient != null) return

        if (context.billingPrefs().getBoolean(KEY_IS_PRO, false)) ProGate.grant()

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases?.forEach { handlePurchase(context.applicationContext, it) }
                }
            }
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
     * Launch the Google Play purchase sheet.
     * Must be called from a live, UI-attached [Activity].
     */
    fun purchase(activity: Activity) {
        val client = billingClient?.takeIf { it.isReady } ?: return
        client.queryProductDetailsAsync(productDetailsParams()) { result, productDetailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            // PBL 8+: the callback now delivers a QueryProductDetailsResult (fetched list +
            // unfetched products) instead of a bare List<ProductDetails>.
            val details = productDetailsResult.productDetailsList.firstOrNull()
                ?: return@queryProductDetailsAsync

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
     * Re-query Play purchases — used by the "Restore Purchase" button.
     * On reinstall with the same Google account, this automatically restores pro access.
     */
    fun restore(context: Context) {
        queryPurchases(context.applicationContext)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun startConnection(context: Context) {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryPurchases(context)
                        queryPrice()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
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

    private fun queryPurchases(context: Context) {
        val client = billingClient ?: return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val isPurchased = purchases.any {
                PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (isPurchased) {
                ProGate.grant()
                setProCache(context, true)
                purchases.filter { !it.isAcknowledged }
                    .forEach { acknowledgePurchase(it) }
            } else {
                // No valid purchase found (refunded, revoked, or never purchased)
                val wasPro = context.billingPrefs().getBoolean(KEY_IS_PRO, false)
                ProGate.revoke()
                setProCache(context, false)
                // Reset Pro-only settings so they don't persist after revocation
                if (wasPro) {
                    OverlayHelpers.enforceFreeLimits(context)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            context,
                            "Pro purchase was refunded. Pro features have been disabled.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun handlePurchase(context: Context, purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        ProGate.grant()
        setProCache(context, true)
        if (!purchase.isAcknowledged) acknowledgePurchase(purchase)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        billingClient?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { /* result — log in production via analytics */ }
    }

    private fun setProCache(context: Context, isPro: Boolean) =
        context.billingPrefs().edit().putBoolean(KEY_IS_PRO, isPro).apply()

    private fun Context.billingPrefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
