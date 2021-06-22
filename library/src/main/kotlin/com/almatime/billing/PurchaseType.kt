package com.almatime.billing

/**
 * name = ID in IAP Play Console.
 *
 * @author Alexander Khrapunsky
 * @version 1.0.0, 14.03.21.
 * @since 1.0.0
 */
enum class PurchaseType {

    no_ads,
    full_version;

    override fun toString() = "$name"

}