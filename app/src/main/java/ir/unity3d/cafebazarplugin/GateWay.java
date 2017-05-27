package ir.unity3d.cafebazarplugin;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import util.IabHelper;
import util.IabResult;


/**
 * Created by Amin on 5/21/15.
 */
public class GateWay extends Activity{

    static final String ACTION_FINISH = "ir.unity3d.cafebazarplugin.ACTION_FINISH";
    private BroadcastReceiver broadcastReceiver;

    public GateWay()
    {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(StoreController.TAG, "Finish broadcast was received");
                if (!GateWay.this.isFinishing()) {
                    finish();
                }
            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_FINISH));
        if (StoreController.sendRequest) {
            StoreController.sendRequest = false;

            Intent i = getIntent();
            String sku = i.getStringExtra("sku");
            String developerPayload = i.getStringExtra("developerPayload");
            boolean inapp = i.getBooleanExtra("inapp", true);

            try {
                if (inapp) {
                    StoreController.instance().getHelper().launchPurchaseFlow(this, sku, StoreController.RC_REQUEST, StoreController.instance().getPurchaseFinishedListener(), developerPayload);
                } else {
                    StoreController.instance().getHelper().launchSubscriptionPurchaseFlow(this, sku, StoreController.RC_REQUEST, StoreController.instance().getPurchaseFinishedListener(), developerPayload);
                }
            } catch (java.lang.IllegalStateException e) {
                StoreController.instance().getPurchaseFinishedListener().onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE, "Cannot start purchase process. Billing unavailable."), null);
            } catch (IabHelper.IabAsyncInProgressException e) {
                StoreController.instance().getPurchaseFinishedListener().onIabPurchaseFinished(new IabResult(IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE, "Cannot start purchase process. Some operation is already in progress: " + e.toString()), null);
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(StoreController.TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (StoreController.instance().getHelper() == null) return;

        // Pass on the activity result to the helper for handling
        if (!StoreController.instance().getHelper().handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d(StoreController.TAG, "onActivityResult handled by IABUtil.");
        }
    }
}
