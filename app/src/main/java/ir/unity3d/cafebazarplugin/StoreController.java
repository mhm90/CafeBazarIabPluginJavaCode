package ir.unity3d.cafebazarplugin;

import java.util.List;

import com.unity3d.player.UnityPlayer;

import util.IabHelper;
import util.IabResult;
import util.Inventory;
import util.Purchase;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData.Item;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class StoreController{

    public static String TAG = "CafeBazarPlugin";
    public static boolean sendRequest = false;
	// (arbitrary) request code for the purchase flow
	static final int RC_REQUEST = 10001;
    private static StoreController _instance;


	private IabHelper mHelper;
	private String UnityStoreHandler = "StoreHandler";
	private String base64EncodedPublicKey;
	private String payload = "";
	private Activity CurrentActivty;
	private Inventory _inventory;

    public IabHelper getHelper() {
        return mHelper;
    }

    public IabHelper.OnIabPurchaseFinishedListener getPurchaseFinishedListener() {
        return mPurchaseFinishedListener;
    }

    public static StoreController instance() {
        if (_instance == null) {
            _instance = new StoreController();
        }
        return _instance;
    }

    public StoreController()
    {
        _instance = this;
    }

	public void startSetup(String publicKey, String _payload, Object activty , String _enableDebug) {
        base64EncodedPublicKey = publicKey;
        payload = _payload;
        CurrentActivty = (Activity) activty;
		Log.d(TAG, "Create Iab Helper.");
		mHelper = new IabHelper(CurrentActivty, base64EncodedPublicKey);
		if (_enableDebug.equals("TRUE")) {
			mHelper.enableDebugLogging(true , TAG);
		}
		Log.d(TAG, "Setup Started.");
        UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                    public void onIabSetupFinished(IabResult result) {
                        Log.d(TAG, "Setup finished.");

                        if (!result.isSuccess()) {
                            // Oh noes, there was a problem.
                            complain("Problem setting up in-app billing: " + result);
                            return;
                        }

                        // Have we been disposed of in the meantime? If so, quit.
                        if (mHelper == null)
                            return;

                        // IAB is fully set up. Now, let's get an inventory of stuff we
                        // own.
                        Log.d(TAG, "Setup successful. Querying inventory.");

                        mHelper.queryInventoryAsync(mGotInventoryListener);


                    }
                });
            }
        });
	}

	public void stopService() {
		if (mHelper != null)
			mHelper.dispose();
		mHelper = null;
	}

	// Listener that's called when we finish querying the items and
	// subscriptions we own
	IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		public void onQueryInventoryFinished(IabResult result,
				Inventory inventory) {
			Log.d(TAG, "Query inventory finished.");

			// Have we been disposed of in the meantime? If so, quit.
			if (mHelper == null)
				return;

			// Is it a failure?
			if (result.isFailure()) {
				complain("Failed to query inventory: " + result);
				return;
			}

			Log.d(TAG, "Query inventory was successful.");

			_inventory = inventory;
			List<Purchase> Purchases = inventory.getAllPurchases();
			for (Purchase purchase : Purchases) {
				if (purchase != null && verifyDeveloperPayload(purchase)) {
					UnityPlayer.UnitySendMessage(UnityStoreHandler,
							"ProcessPurchase", purchase.getSku());
					Log.d(TAG,
							"purchase.getItemType() : "
									+ purchase.getItemType()
									+ "  , purchase.getSku() :"
									+ purchase.getSku());
				}
			}
			UnityPlayer.UnitySendMessage(UnityStoreHandler,
					"GetPurchasesFinished", "");
			Log.d(TAG, "Initial inventory query finished; enabling main UI.");
		}
	};

	public void Consume(final String sku) {

		try {
            UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Purchase purchase = _inventory.getPurchase(sku);
                    Log.d(TAG, "Consume Called for sku : " + purchase.getSku() + " , and token : " + purchase.getToken());
                    mHelper.consumeAsync(purchase, mConsumeFinishedListener);
                }
            });
		} catch (Exception e) {
			complain("Failed to consume purchase with sku : " + sku);
		}
	}

	// Called when consumption is complete
	IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
		public void onConsumeFinished(Purchase purchase, IabResult result) {
			Log.d(TAG, "Consumption finished. Purchase: " + purchase
					+ ", result: " + result);

			// if we were disposed of in the meantime, quit.
			if (mHelper == null)
				return;

			// We know this is the "gas" sku because it's the only one we
			// consume,
			// so we don't check which sku was consumed. If you have more than
			// one
			// sku, you probably should check...
			if (result.isSuccess()) {
				// successfully consumed, so we apply the effects of the item in
				// our
				// game world's logic, which in our case means filling the gas
				// tank a bit
				Log.d(TAG, "Consumption successful. Provisioning.");
				UnityPlayer.UnitySendMessage(UnityStoreHandler,
						"ConsumeFinished", purchase.getSku());
			} else {
				complain("Error while consuming: " + result);
			}
			Log.d(TAG, "End consumption flow in java.");
		}
	};

	public void launchPurchaseFlow(final String SKU) {

		Log.d(TAG, "Purchase Called for : " + SKU);
        startProxyPurchaseActivity(SKU, true, payload);

	}

	public void launchSubscriptionPurchaseFlow(final String SKU) {

        Log.d(TAG, "Subscription Purchase Called for : " + SKU);
        startProxyPurchaseActivity(SKU, false, payload);
	}


    private void startProxyPurchaseActivity(String sku, boolean inapp, String developerPayload) {

        if (mHelper == null) {
            Log.e(StoreController.TAG, "IABHelper UnityPlugin not initialized!");
            return;
        }

        sendRequest = true;
        Intent i = new Intent(UnityPlayer.currentActivity, GateWay.class);
        i.putExtra("sku", sku);
        i.putExtra("inapp", inapp);
        i.putExtra("developerPayload", developerPayload);

        // Launch proxy purchase Activity - it will close itself down when we have a response
        UnityPlayer.currentActivity.startActivity(i);
    }

	// Callback for when a purchase is finished
	IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            UnityPlayer.currentActivity.sendBroadcast(new Intent(GateWay.ACTION_FINISH));
			Log.d(TAG, "Purchase finished: " + result + ", purchase: "
					+ purchase);

			// if we were disposed of in the meantime, quit.
			if (mHelper == null)
				return;

			if (result.isFailure()) {
				complain("Error purchasing: " + result);
				return;
			}
			if (!verifyDeveloperPayload(purchase)) {
				complain("Error purchasing. Authenticity verification failed.");
				return;
			}

			mHelper.queryInventoryAsync(mGotInventoryListener);

			
			Log.d(TAG, "Purchase successful.");

			Log.d(TAG, "purchase.getItemType() : " + purchase.getItemType()
					+ "  , purchase.getSku() :" + purchase.getSku());
		}
	};

	void complain(String message) {
		Log.e(TAG, "**** TrivialDrive Error: " + message);
		alert("Error : " + message);
	}

	void alert(String errorMessage) {
		UnityPlayer
				.UnitySendMessage(UnityStoreHandler, "OnError", errorMessage);
	}

	/** Verifies the developer payload of a purchase. */
	boolean verifyDeveloperPayload(Purchase p) {
		String payload = p.getDeveloperPayload();

		/*
		 * TODO: verify that the developer payload of the purchase is correct.
		 * It will be the same one that you sent when initiating the purchase.
		 * 
		 * WARNING: Locally generating a random string when starting a purchase
		 * and verifying it here might seem like a good approach, but this will
		 * fail in the case where the user purchases an item on one device and
		 * then uses your app on a different device, because on the other device
		 * you will not have access to the random string you originally
		 * generated.
		 * 
		 * So a good developer payload has these characteristics:
		 * 
		 * 1. If two different users purchase an item, the payload is different
		 * between them, so that one user's purchase can't be replayed to
		 * another user.
		 * 
		 * 2. The payload must be such that you can verify it even when the app
		 * wasn't the one who initiated the purchase flow (so that items
		 * purchased by the user on one device work on other devices owned by
		 * the user).
		 * 
		 * Using your own server to store and verify developer payloads across
		 * app installations is recommended.
		 */

		return true;
	}

}
