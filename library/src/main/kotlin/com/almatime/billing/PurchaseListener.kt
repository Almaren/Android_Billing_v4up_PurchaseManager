package com.almatime.billing

/**
 * @author Alexander Khrapunsky
 * @version 1.0.0, 15.03.21.
 * @since 1.0.0
 */
interface PurchaseListener {

    /**
     * @param itemPrices value = formatted localized price.
     */
    fun onAvailableItemsFetched(itemPrices: Map<PurchaseType, String>)

    fun onPurchasedItemsFetched(purchasedItems: Set<PurchaseType>)

    /**
     * On SUCCEED status will be called after validating server ack.
     * On first app start the purchased items fetched, if some of them not acked before, next will process ack and than
     * call this callback.
     * #case_1: In app start (unknown scene), fetching not acked purchased items.
     * #case_2: In Shop Dialog receiving result of the current purchase process.
     */
    fun onPurchasedResult(purchaseResult: PurchaseResult, purchaseType: PurchaseType? = null)

}