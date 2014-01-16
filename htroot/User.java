//User.java
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This File is contributed by Alexander Schier
//last major change: 12.11.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


//You must compile this file with
//javac -classpath .:../Classes Message.java
//if the shell's current path is HTROOT

import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

public class User{

    public static servletProperties respond(final RequestHeader requestHeader, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        final Switchboard sb = Switchboard.getSwitchboard();
        UserDB.Entry entry=null;

        //default values
        prop.put("logged_in", "0");
        prop.put("logged-in_limit", "0");
        prop.put("status", "0");
        prop.put("logged-in_username", "");
        prop.put("logged-in_returnto", "");
        //identified via HTTPPassword
        entry=sb.userDB.proxyAuth((requestHeader.get(RequestHeader.AUTHORIZATION, "xxxxxx")));
        if(entry != null){
        	prop.put("logged-in_identified-by", "1");
        //try via cookie
        }else{
            entry=sb.userDB.cookieAuth(requestHeader.getHeaderCookies());
            prop.put("logged-in_identified-by", "2");
            //try via ip
            if(entry == null){
                entry=sb.userDB.ipAuth((requestHeader.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "xxxxxx")));
                if(entry != null){
                    prop.put("logged-in_identified-by", "0");
                }
            }
        }

        //identified via userDB
        if(entry != null){
            prop.put("logged-in", "1");
            prop.put("logged-in_username", entry.getUserName());
            if(entry.getTimeLimit() > 0){
                prop.put("logged-in_limit", "1");
                final long limit=entry.getTimeLimit();
                final long used=entry.getTimeUsed();
                prop.put("logged-in_limit_timelimit", limit);
                prop.put("logged-in_limit_timeused", used);
                int percent=0;
                if(limit!=0 && used != 0)
                    percent=(int)((float)used/(float)limit*100);
                prop.put("logged-in_limit_percent", percent/3);
                prop.put("logged-in_limit_percent2", (100-percent)/3);
            }
        //logged in via static Password
        }else if(sb.verifyAuthentication(requestHeader)){
            prop.put("logged-in", "2");
        //identified via form-login
        //TODO: this does not work for a static admin, yet.
        }else if(post != null && post.containsKey("username") && post.containsKey("password")){
        	if (post.containsKey("returnto"))
        		prop.putHTML("logged-in_returnto", post.get("returnto"));
            final String username=post.get("username");
            final String password=post.get("password");
            prop.putHTML("logged-in_username", username);

            entry=sb.userDB.passwordAuth(username, password);
            final boolean staticAdmin = sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, "").equals(
                    Digest.encodeMD5Hex(
                            Base64Order.standardCoder.encodeString(username + ":" + password)
                    )
            );
            String cookie="";
            if(entry != null)
                //set a random token in a cookie
                cookie=sb.userDB.getCookie(entry);
            else if(staticAdmin)
                cookie=sb.userDB.getAdminCookie();

            if(entry != null || staticAdmin){
                final ResponseHeader outgoingHeader=new ResponseHeader(200);
                outgoingHeader.setCookie("login", cookie);
                prop.setOutgoingHeader(outgoingHeader);

                prop.put("logged-in", "1");
                prop.put("logged-in_identified-by", "1");
                prop.putHTML("logged-in_username", username);
                if(post.containsKey("returnto")){
                    prop.put(serverObjects.ACTION_LOCATION, post.get("returnto"));
                }
            }
        }

        if (post != null && entry != null) {
            if (post.containsKey("changepass")) {
                prop.put("status", "1"); //password

                if (entry.getMD5EncodedUserPwd().startsWith("MD5:") ?
                        entry.getMD5EncodedUserPwd().equals("MD5:"+Digest.encodeMD5Hex(entry.getUserName() + ":" + sb.getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy") + ":" + post.get("oldpass", ""))) :
                        entry.getMD5EncodedUserPwd().equals(Digest.encodeMD5Hex(entry.getUserName() + ":" + post.get("oldpass", "")))) {
                    if (post.get("newpass").equals(post.get("newpass2"))) {
                        if (!post.get("newpass", "").equals("")) {
                            try {
                                entry.setProperty(UserDB.Entry.MD5ENCODED_USERPWD_STRING, "MD5:" + Digest.encodeMD5Hex(entry.getUserName() + ":" + sb.getConfig(SwitchboardConstants.ADMIN_REALM,"YaCy") + ":" + post.get("newpass", "")));
                                prop.put("status_password", "0"); //changes
                            } catch (final Exception e) {
                                ConcurrentLog.logException(e);
                            }
                        } else {
                            prop.put("status_password", "3"); //empty
                        }
                    } else {
                        prop.put("status_password", "2"); //pws do not match
                    }
                } else {
                    prop.put("status_password", "1"); //old pw wrong
                }
            }
        }
        if(post!=null && post.containsKey("logout")){
            prop.put("logged-in", "0");
            if(entry != null){
                entry.logout((requestHeader.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "xxxxxx")), UserDB.getLoginToken(requestHeader.getHeaderCookies())); //todo: logout cookie
            }else{
                sb.userDB.adminLogout(UserDB.getLoginToken(requestHeader.getHeaderCookies()));
            }
            //XXX: This should not be needed anymore, because of isLoggedout
            if(! (requestHeader.get(RequestHeader.AUTHORIZATION, "xxxxxx")).equals("xxxxxx")){
            	prop.authenticationRequired();
            }
            if(post.containsKey("returnto")){
                prop.putHTML(serverObjects.ACTION_LOCATION, post.get("returnto"));
            }
        }
        // return rewrite properties
        return prop;
    }
}
