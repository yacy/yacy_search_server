package net.yacy.htroot;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class yacychat {

 public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
     // return variable that accumulates replacements
     final Switchboard sb = (Switchboard) env;

     final serverObjects prop = new serverObjects();
     String body = post == null ? "" : post.get("BODY", "");
     JSONObject bodyj = new JSONObject();
     if (body.length() > 0) {
         try {
             bodyj = new JSONObject(new JSONTokener(body));
         } catch (JSONException e) {
             // silently catch this
         }
     }

     // return rewrite properties
     return prop;
 }

}