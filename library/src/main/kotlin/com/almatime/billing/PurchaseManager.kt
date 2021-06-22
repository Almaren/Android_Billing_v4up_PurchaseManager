package com.almatime.billing

import android.app.Activity
import com.almatime.billing.utils.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

/**
 * API results done with the [PurchaseListener].
 *
 * [BillingClient] - provides synchronous (blocking) and asynchronous (non-blocking) methods for many common
 * in-app billing operations.
 * <b>All methods are supposed to be called from the UI thread and all the asynchronous callbacks
 * will be returned on the UI thread as well”.</b>
 * @link https://developer.android.com/google/play/billing/integrate
 *
 *  If you do not acknowledge a purchase within three days, the user automatically receives a refund,
 *  and Google Play revokes the purchase.
 *
 * @author Alexander Khrapunsky
 * @version 1.0.1, 10.06.21. (coroutines has been added)
 * @since 1.0.0
 */
object PurchaseManager : CoroutineScope {

    private lateinit var billingClient: BillingClient
    private lateinit var listener: PurchaseListener
    private lateinit var refActivity: WeakReference<Activity>
    private val mapSkuDetails = HashMap<PurchaseType, SkuDetails>()
    private val purchasedItems = HashSet<PurchaseType>()              // stores a bought items
    private val ackItemsInProcess = HashSet<PurchaseType>()           // stores items that began an ack process

    private val RETRY_RECONNECTION = 3
    private var countTryReconnect = 0

    private val job = Job()
    private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Log.e("PurchaseManager CoroutineExceptionHandler: $coroutineContext, $throwable")
    }
    override val coroutineContext = Dispatchers.IO + job + coroutineExceptionHandler

    /**
     * Call it in Activity.onCreate(..)
     */
    fun init(activity: Activity, listener: PurchaseListener) {
        this.listener = listener
        refActivity = WeakReference(activity)
        setUpBillingClient(refActivity.get()!!)
    }

    /**
     * Must be called whenever activity has been changed.
     */
    fun reconnect(activity: Activity) {
        countTryReconnect = 0
        if (::refActivity.isInitialized) {
            refActivity.clear()
        }
        refActivity = WeakReference(activity)
        setUpBillingClient(activity)
    }

    fun getItemPrices() {
        val itemPrices = HashMap<PurchaseType, String>()
        for ((itemType, skuDetails) in mapSkuDetails) {
            itemPrices[itemType] = skuDetails.price
        }
        listener.onAvailableItemsFetched(itemPrices)
    }

    /**
     * Google Play calls onPurchasesUpdated() to deliver the result of the purchase operation to a listener that
     * implements the PurchasesUpdatedListener interface.
     * Сall the launchBillingFlow() method from your app's main thread.
     */
    fun purchaseItem(itemType: PurchaseType) {
        val skuDetails = mapSkuDetails[itemType]
        val activity = refActivity.get()
        if (skuDetails == null || activity == null) {
            listener.onPurchasedResult(PurchaseResult.FAILED)
            Log.w("PurchaseManager purchaseItem skuDetails = ${skuDetails == null}, activity = $activity")
            return
        }
        try {
            val flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .build()
            val responseCode = billingClient.launchBillingFlow(activity, flowParams).responseCode
            Log.i("PurchaseManager purchaseItem responseCode = $responseCode")
        } catch (e: Exception) {
            listener.onPurchasedResult(PurchaseResult.FAILED)
            Log.e(e)
        }
    }

    /**
     * It's possible that your app might not be aware of all the purchases a user has made.
     * Here are some scenarios where your app could lose track or be unaware of purchases:
     *
     * 1. Network Issues during the purchase. A user makes a successful purchase and receives confirmation from Google,
     * but their device loses network connectivity before their device receives notification of the purchase through the PurchasesUpdatedListener.
     * 2. Multiple devices: A user buys an item on one device and then expects to see the item when they switch devices.
     * 3.Handling purchases made outside your app: Some purchases, such as promotion redemptions, can be made outside of your app.
     *
     * To handle these situations, be sure that your app calls BillingClient.queryPurchases() in your onResume() and onCreate().
     */
    fun onResume() {
        if (!billingClient.isReady) {
            Log.i("PurchaseManager startConnection")
            startConnection()
        } else {
            launch {
                fetchPurchasedItems()
            }
        }
    }

    private fun setUpBillingClient(activity: Activity) {
        billingClient = BillingClient.newBuilder(activity)
            .setListener(purchaseUpdateListener)
            .enablePendingPurchases()
            .build()
    }

    private fun startConnection() {
        Log.i("startConnection currState = ${billingClient.connectionState}")
        when (billingClient.connectionState) {
            BillingClient.ConnectionState.CONNECTED, BillingClient.ConnectionState.CONNECTING -> {
                return
            }
            BillingClient.ConnectionState.CLOSED -> {
                refActivity.get()?.let {
                    setUpBillingClient(it)
                }
            }
            else -> {}
        }
        billingClient.startConnection(object : BillingClientStateListener {

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    Log.i("Setup Billing Done")
                    launch {
                        fetchPurchasedItems()
                        fetchAvailableProducts()
                    }
                }
            }
            /**
             * This is a pretty unusual occurrence. It happens primarily if the Google Play Store
             * self-upgrades or is force closed.
             */
            override fun onBillingServiceDisconnected() {
                Log.w("Billing client Disconnected")
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                if (countTryReconnect++ < RETRY_RECONNECTION) {
                    startConnection()
                }
            }

        })
    }

    private suspend fun fetchPurchasedItems() {
        Log.i("PurchaseManager fetchPurchasedItems start query..")
        val queryPurchase = billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP)
        val purchases = queryPurchase.purchasesList
        if (purchases.isNullOrEmpty()) {
            return
        }
        processFetchedPurchasedItems(purchases)
    }

    private fun fetchAvailableProducts() {
        val skuList = arrayListOf<String>()
        PurchaseType.values().forEach {
            skuList.add(it.toString())
        }
        if (Log.DEBUG) {
            for (skuItem in skuList) {
                Log.i("PurchaseManager skuList item = ${skuItem.toString()}")
            }
        }
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP)
        // Official sample with context withContext(Dispatchers.IO)
        billingClient.querySkuDetailsAsync(params.build()) { billingResult, skuDetailsList ->
            // Process the result.
            Log.i("PurchaseManager querySkuDetailsAsync, respCode = ${billingResult.responseCode}, skuDetailsList = $skuDetailsList, " +
                "debugMsg = ${billingResult.debugMessage}}")

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !skuDetailsList.isNullOrEmpty()) {
                for (skuDetails in skuDetailsList) {
                    val itemType = PurchaseType.valueOf(skuDetails.sku)
                    mapSkuDetails[itemType] = skuDetails
                }
            }
        }
    }

    private fun processFetchedPurchasedItems(purchases: List<Purchase>) {
        for (purchase in purchases) {
            for (purchaseSku in purchase.skus) {
                val itemType = PurchaseType.valueOf(purchaseSku)
                Log.i("PurchaseManager fetchPurchasedItems iterate purchaseSku $itemType: ${purchase.purchaseState}, isAck = ${purchase.isAcknowledged}, " +
                    "details = ${purchase.originalJson}")

                if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                    Log.i("PurchaseManager fetchPurchasedItems iterate purchase $itemType VERIFY FAILED")
                    // skip current iteration only because other items in purchase list must be checked if present
                    continue
                }

                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (purchase.isAcknowledged) {
                        purchasedItems.add(itemType)
                    } else {
                        preAckPurchase(purchase, itemType)
                    }
                }
            }
        }
        if (purchasedItems.isNotEmpty()) {
            listener.onPurchasedItemsFetched(purchasedItems)
        }
        Log.i("PurchaseManager fetchPurchasedItems purchasedItems: ${purchasedItems}")
    }

    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     * Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     */
    private fun verifyValidSignature(signedData: String, signature: String): Boolean {
        return try {
            Security.verifyPurchase(Security.BASE_64_ENCODED_PUBLIC_KEY, signedData, signature)
        } catch (e: Exception) {
            Log.e(e)
            false
        }
    }

    /**
     * Handle a new purchases. After ACK -> notify user.
     */
    private fun processNewPurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            for (purchaseSku in purchase.skus) {
                val itemType = PurchaseType.valueOf(purchaseSku)
                Log.i("PurchaseManager iterate new purchase $itemType: ${purchase.purchaseState}")

                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        // validate
                        if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                            Log.i("PurchaseManager new purchase $itemType failed verify signature")
                            listener.onPurchasedResult(PurchaseResult.FAILED, itemType)
                            // skip current iteration only because other items in purchase list must be checked if present
                            continue
                        }

                        Log.i("PurchaseManager new purchase $itemType isAck = ${purchase.isAcknowledged}")
                        // if item is purchased and not  Acknowledged
                        if (!purchase.isAcknowledged) {
                            preAckPurchase(purchase, itemType)
                        } else {
                            // Grant entitlement to the user on item purchase
                            purchaseSucceed(itemType)
                        }
                    }
                    Purchase.PurchaseState.PENDING -> {
                        listener.onPurchasedResult(PurchaseResult.PENDING, itemType)
                    }
                    Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                        listener.onPurchasedResult(PurchaseResult.FAILED, itemType)
                    }
                }
            }
        }
    }

    private fun purchaseSucceed(itemType: PurchaseType) {
        purchasedItems.add(itemType)
        listener.onPurchasedResult(PurchaseResult.SUCCEED, itemType)
    }

    /**
     * Once you've verified the purchase, your app is ready to grant entitlement to the user. After granting entitlement,
     * your app must then acknowledge the purchase. This acknowledgement communicates to Google Play that you have
     * granted entitlement for the purchase.
     *
     * Note: You should acknowledge a purchase only when the state is PURCHASED.
     * You cannot acknowledge while a purchase is PENDING. The three day acknowledgement window begins only when
     * the purchase state transitions from 'PENDING' to 'PURCHASED'.
     */
    private fun preAckPurchase(purchase: Purchase, itemType: PurchaseType) {
        synchronized(purchase) {
            Log.i("PurchaseManager preack purchase $itemType: ${purchase.purchaseState}, isInProgress = ${ackItemsInProcess.contains(itemType)}")

            if (!ackItemsInProcess.contains(itemType)) {
                ackItemsInProcess.add(itemType)
            } else {
                // already in ack process
                return
            }
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            ackPurchase(purchase, itemType, acknowledgePurchaseParams)
            Log.i("PurchaseManager preack purchase $itemType: ${purchase.purchaseState} release SYNCH block")
        }
    }

    private fun ackPurchase(purchase: Purchase, itemType: PurchaseType, ackPurchaseParams: AcknowledgePurchaseParams) {
        // Official sample uses withContext
        /*val ackPurchaseResult = withContext(Dispatchers.IO) {
            client.acknowledgePurchase(acknowledgePurchaseParams.build())
        }*/
        billingClient.acknowledgePurchase(ackPurchaseParams) { billingResult ->
            Log.i("PurchaseManager ack purchase in listener: $itemType: ${purchase.purchaseState}, ack status = ${billingResult.responseCode}, " +
                "debugMsg = ${billingResult.debugMessage}")

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK
                || billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                // Grant entitlement to the user on item purchase
                purchaseSucceed(itemType)
            }
            else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                listener.onPurchasedResult(PurchaseResult.CANCELLED)
            }
            else if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
                || billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
                listener.onPurchasedResult(PurchaseResult.NETWORK_ERROR)
            }
            else if (billingResult.responseCode == BillingClient.BillingResponseCode.DEVELOPER_ERROR) {
                // occurred on second ack of the same item, that was already acked.
                Log.i("ack purchase $itemType: ${purchase.purchaseState}, DEV_ERROR, isAck = ${purchase.isAcknowledged}")
                if (!purchase.isAcknowledged) {
                    listener.onPurchasedResult(PurchaseResult.FAILED, itemType)
                }
            }
            else {
                // error
                listener.onPurchasedResult(PurchaseResult.UNKNOWN_ERROR, itemType)
            }

            try {
                ackItemsInProcess.remove(itemType)
            } catch (e: Exception){
            }
        }
    }

    /**
     * Result of purchase Dialog.
     */
    private val purchaseUpdateListener =  PurchasesUpdatedListener { billingResult, purchases ->
        Log.i("PurchaseManager billingResult code = ${billingResult.responseCode} debug: ${billingResult.debugMessage}")

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // if item newly purchased
            processNewPurchases(purchases)
        }
        else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            launch {
                fetchPurchasedItems()
            }
        }
        else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            listener.onPurchasedResult(PurchaseResult.CANCELLED)
        }
        else if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
            || billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
            listener.onPurchasedResult(PurchaseResult.NETWORK_ERROR)
        }
        else {
            // error
            listener.onPurchasedResult(PurchaseResult.UNKNOWN_ERROR)
        }
    }

    fun dispose() {
        if (::refActivity.isInitialized) {
            refActivity.clear()
        }
        billingClient?.endConnection()
        coroutineContext.cancel() // todo check
    }

}
