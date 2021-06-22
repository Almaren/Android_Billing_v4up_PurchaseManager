package com.almatime.billing

/**
 * PENDING - not completed transaction.
 *
 * @author Alexander Khrapunsky
 * @version 1.0.0, 16.03.21.
 * @since 1.0.0
 */
enum class PurchaseResult {

    SUCCEED,
    PENDING,
    FAILED,
    CANCELLED,
    NETWORK_ERROR,
    UNKNOWN_ERROR

}