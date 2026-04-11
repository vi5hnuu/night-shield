package com.vi5hnu.nightshield

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call once from [MainActivity.onCreate].
     * Immediately applies cached pro status so UI never flickers,
     * then connects and verifies with Play in the background.
     */
    fun init(context: Context) {
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
            .build()

        startConnection(context.applicationContext)
    }

    /**
     * Launch the Google Play purchase sheet.
     * Must be called from a live, UI-attached [Activity].
     */
    fun purchase(activity: Activity) {
        val client = billingClient?.takeIf { it.isReady } ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = detailsList.firstOrNull() ?: return@queryProductDetailsAsync

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
                    scope.launch { queryPurchases(context) }
                }
            }
            // Will reconnect automatically on the next user action that needs billing
            override fun onBillingServiceDisconnected() {}
        })
    }

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
