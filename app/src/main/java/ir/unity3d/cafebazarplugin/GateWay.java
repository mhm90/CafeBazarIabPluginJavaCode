package ir.unity3d.cafebazarplugin;

import android.app.Activity;
import android.content.Intent;


/**
 * Created by Amin on 5/21/15.
 */
public class GateWay {

    private String base64EncodedPublicKey;
    private String payload = "";
    private Activity CurrentActivty;

    public GateWay(String publicKey, String _payload, Object activty , String _enableDebug)
    {
        base64EncodedPublicKey = publicKey;
        payload = _payload;
        CurrentActivty = (Activity) activty;
        // start billing service
        Intent k = new Intent(CurrentActivty, StoreController.class);
        k.putExtra("PUBLIC_KEY" , publicKey);
        k.putExtra("PAYLOAD" , _payload);
        k.putExtra("DEBUG_MODE" , _enableDebug);
        CurrentActivty.startActivity(k);

    }
}
