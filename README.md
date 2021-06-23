# Android_Billing_v4up_PurchaseManager
Android Billing v4+ implementation for non consumable products. Written in **Kotlin** using coroutines. Serverless verifying approval method of a purchased token. No UI sample.

[An official integration.](https://developer.android.com/google/play/billing/integrate#kotlin)
[And a sample.](https://github.com/android/play-billing-samples/blob/master/TrivialDriveKotlin/app/src/main/java/com/sample/android/trivialdrivesample/billing/BillingDataSource.kt)

*My implemenation is much easy to use, you can adapt it also for subscriptions and for consumable products.*
*PurchaseManager.kt is well documented.*

## Preparing:
* **First put your developer Base64-encoded RSA public key (from Google Play Console: your app -> monetization setup) in: /local.properties/base64EncodedPublicKey=YOUR_KEY**
* **In order to test billing - Release your application with an integrated billing library but not yet implemented to any testing track (internal testing is better).**
* **For debugging testing: in Google Developers Console (Not Play Console) add your debug key SHA1.**
* **In Google Play Console create a testers group.**

## How to use:
**All methods of PurchaseManager must be called from the main ui thread.**
1. Call init(activity: Activity, listener: PurchaseListener) from Activity.onCreate(..). Via PurchaseListener you will receive available products to purchase, a purchased items, a result from the recent purchase.
2. Call onResume() from Activity.onResume()
3. Call dispose() from Activity.onDestroy()
4. Use getItemPrices() and purchaseItem(itemType: PurchaseType)


