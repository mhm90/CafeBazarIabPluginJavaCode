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

public class StoreController extends Activity {

	// (arbitrary) request code for the purchase flow
	static final int RC_REQUEST = 10001;

	private IabHelper mHelper;
	private String TAG = "CafeBazarPlugin";
	private String UnityStoreHandler = "StoreHandler";

	private String base64EncodedPublicKey;
	private String payload = "";
	private Activity CurrentActivty;
	private Inventory _inventory;

    public StoreController()
    {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        base64EncodedPublicKey = intent.getStringExtra("PUBLIC_KEY");
        payload = intent.getStringExtra("PAYLOAD");
        CurrentActivty = this; //(Activity) activty;

        startSetup(intent.getStringExtra("DEBUG_MODE"));
    }

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            mHelper.dispose();
            mHelper = null;
        }
    }

	public void startSetup(String _enableDebug) {		
		Log.d(TAG, "Create Iab Helper.");
		mHelper = new IabHelper(CurrentActivty, base64EncodedPublicKey);
		if (_enableDebug.equals("TRUE")) {
			mHelper.enableDebugLogging(true , TAG);
		}
		Log.d(TAG, "Setup Started.");
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
				UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
		            @Override
		            public void run() {
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

            moveTaskToBack(true);

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
		UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
		try {
			Purchase purchase = _inventory.getPurchase(sku);
			Log.d(TAG, "Consume Called for sku : " + purchase.getSku() + " , and token : " + purchase.getToken());
			mHelper.consumeAsync(purchase, mConsumeFinishedListener);
		} catch (Exception e) {
			complain("Failed to consume purchase with sku : " + sku);
		}
            }
		});
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
		try {
			Log.d(TAG, "Purchase Called for : " + SKU);
			mHelper.launchPurchaseFlow(this, SKU, RC_REQUEST,
					mPurchaseFinishedListener, payload);
		} catch (Exception e) {
			complain("Error while purchasing : " + e.toString());
		}
	}

	public void launchSubscriptionPurchaseFlow(final String SKU) {
		try {
			Log.d(TAG, "Subscription Purchase Called for : " + SKU);
			mHelper.launchSubscriptionPurchaseFlow(this, SKU,
					RC_REQUEST, mPurchaseFinishedListener, payload);
		} catch (Exception e) {
			complain("Error while purchasing : " + e.toString());
		}
	}

	// Callback for when a purchase is finished
	IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
		public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
			
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
			UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
	            @Override
	            public void run() {
			mHelper.queryInventoryAsync(mGotInventoryListener);
			
	            }
			});
			
			Log.d(TAG, "Purchase successful.");

			Log.d(TAG, "purchase.getItemType() : " + purchase.getItemType()
					+ "  , purchase.getSku() :" + purchase.getSku());
		}
	};

	void complain(String message) {
		Log.e(TAG, "**** TrivialDrive Error: " + message);
		alert("Error : " + message);
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (mHelper == null) return;

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
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
