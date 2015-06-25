package util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Created by Amin on 6/25/15.
 */
public class JsonUtil {

    public static JSONObject ToJson(Purchase purchase) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ItemType" , purchase.getItemType());
        jsonObject.put("OrderId" , purchase.getOrderId());
        jsonObject.put("PurchaseTime" , purchase.getPurchaseTime());
        jsonObject.put("Signature" , purchase.getSignature() );
        jsonObject.put("Token" , purchase.getToken() );
        jsonObject.put("DeveloperPayload" , purchase.getDeveloperPayload());
        jsonObject.put("PurchaseState" , purchase.getPurchaseState());
        jsonObject.put("PackageName" , purchase.getPackageName());
        jsonObject.put("OriginalJson" , purchase.getOriginalJson());
        jsonObject.put("Sku" , purchase.getSku());

        return jsonObject;
    }

    public static String ToJson(List<Purchase> purchaseList) throws JSONException
    {
        JSONArray jsonArray = new JSONArray();
        for (int i = 0 ; i < purchaseList.size() ; i++)
        {
            jsonArray.put(JsonUtil.ToJson(purchaseList.get(i)));
        }

        return  jsonArray.toString();
    }
}
