//UserDB.java 
//-------------------------------------
//part of YACY
//
//(C) 2005, 2006 by Martin Thelian
//                  Alexander Schier
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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


package net.yacy.data;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

/**
 * Holds details of users that can login to YaCy, their rights and credentials.
 * Caches succesfull login, holding cookie and/or ip information.
 *
 * In addition a systemadmin (static admin) account is available by default,
 * included in the global Switchboard configuration.
 *
 */
public final class UserDB {
    
    private static final int USERNAME_MIN_LENGTH = 4;
    
    private MapHeap userTable;
    private final File userTableFile;
    private final Map<String, String> ipUsers = new HashMap<String, String>();
    private final Map<String, Entry> cookieUsers = new HashMap<String, Entry>(); // mapping to identify user by a login cookie "login=<token>"
    
    public UserDB(final File userTableFile) throws IOException {
        this.userTableFile = userTableFile;
        userTableFile.getParentFile().mkdirs();
        this.userTable = new MapHeap(userTableFile, 128, NaturalOrder.naturalOrder, 1024 * 64, 10, '_');
    }
    
    void resetDatabase() {
        // deletes the database and creates a new one
        if (userTable != null) {
            userTable.close();
        }
        FileUtils.deletedelete(userTableFile);
        userTableFile.getParentFile().mkdirs();
        try {
            userTable = new MapHeap(userTableFile, 128, NaturalOrder.naturalOrder, 1024 * 64, 10, '_');
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }
    
    public synchronized void close() {
        userTable.close();
    }
    
    public int size() {
        return userTable.size();
    }    
    
    public void removeEntry(final String hostName) {
        try {
            userTable.delete(UTF8.getBytes(hostName));
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }        
    
    public Entry getEntry(String userName) {
        if (userName.length() > 128) {
            userName = userName.substring(0, 127);
        }
        Map<String, String> record = null;
        try {
            record = userTable.get(UTF8.getBytes(userName));
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        
        return (record != null) ? new Entry(userName, record) : null;
    }    
    
    public Entry createEntry(final String userName, final Map<String, String> userProps) throws IllegalArgumentException{
        final Entry entry = new Entry(userName,userProps);
        return entry;
    }
    
    public String addEntry(final Entry entry) {
        try {
            userTable.insert(UTF8.getBytes(entry.userName), entry.mem);
            return entry.userName;
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }    

    /**
     * Use a ProxyAuth Value to authenticate user from HttpHeader.Authentication.
     * This supports only Basic authentication
     * @param header the current request HTTP headers including 'Authorization' header : 
     *  either "BASIC " followed by base64 Encoded String, which contains "username:pw" for basic authentication, 
     *  or "Digest" followed by valid user name, realm, nonce and other necessary parameters.
     * @return the user entry when the authentication header contains valid authentication information or null
     */
    public Entry proxyAuth(final RequestHeader header) {
        Entry entry = null;
        if(header == null) {
        	return entry;
        }
        final String authHeader = header.get(RequestHeader.AUTHORIZATION, "").trim();
        if (authHeader.toUpperCase(Locale.ROOT).startsWith(HttpServletRequest.BASIC_AUTH)) {
        	String auth = authHeader.substring(6); // take out prefix "BASIC"
            final String[] tmp = Base64Order.standardCoder.decodeString(auth.trim()).split(":");
            if (tmp.length == 2) {
            	entry = this.passwordAuth(tmp[0], tmp[1]);
                if (entry == null) {
                	entry = this.md5Auth(tmp[0], tmp[1]);
                }
            }
        } else if (authHeader.toUpperCase(Locale.ROOT).startsWith(HttpServletRequest.DIGEST_AUTH)) {
        	// handle DIGEST auth by servlet container
            final Principal authenticatedUser = header.getUserPrincipal();
            if (authenticatedUser != null && authenticatedUser.getName() != null) { // user is authenticated by Servlet container
            	entry = getEntry(authenticatedUser.getName());
            }
        }
        return entry;
    }

    /**
     * @param header the HTTP request headers
     * @return the authenticated user with valid authentication information found in the headers or null
     */
    public Entry getUser(final RequestHeader header){
        return getUser(header, header.getCookies());
    }

    /**
     * @param header HTTP request headers
     * @param cookies eventual client cookies
     * @return the authenticated user entry from headers or cookies or null
     */
    public Entry getUser(final RequestHeader header, final Cookie[] cookies){
        Entry entry = proxyAuth(header);
        if(entry == null && cookies != null) {
            entry=cookieAuth(cookies);
        }
        return entry;
    }

    /**
     * Determine if a user has admin rights from a 'Authorisation' http header field.
     * Tests both userDB and old style adminpw.
     * 
     * @param header http-headerline for authorisation.
     * @param cookies
     */
    public boolean hasAdminRight(final RequestHeader header, final Cookie[] cookies) {
        final Entry entry = getUser(header, cookies);
        return (entry != null) ? entry.hasRight(AccessRight.ADMIN_RIGHT) : false;
    }

    /**
     * Use HTTP headers to authenticate user and save IP/username for ipAuth.
     * @param header HTTP request headers including 'Authorization' header with either base64 Encoded String, which contains "username:pw", 
     * 	or Digest authentication information including notably user name, realm, and nonce.
     * @param ip IP address.
     */
    public Entry proxyAuth(final RequestHeader header, final String ip) {
        final Entry entry = proxyAuth(header);
        if (entry != null) {
            entry.updateLastAccess(false);
            this.ipUsers.put(ip, entry.getUserName());
        }
        return entry;
    }

    /**
     * Authenticate a user by ip, if he has used proxyAuth in the last 10 minutes.
     * @param ip IP address of user.
     */
    public Entry ipAuth(final String ip) {
        if(this.ipUsers.containsKey(ip)){
            final String user = this.ipUsers.get(ip);
            final Entry entry = this.getEntry(user);
            final Long entryTimestamp = entry.getLastAccess();
            if (entryTimestamp == null ||
                    (System.currentTimeMillis()-entryTimestamp.longValue()) > (1000*60*10) ){ //no timestamp or older than 10 Minutes
                return null;
            }
            return entry; //All OK
        }
        return null;
    }
    
    public Entry passwordAuth(final String user, final String password) {
        final Entry entry = this.getEntry(user);
        final String md5pwd;
        if (entry != null && (md5pwd = entry.getMD5EncodedUserPwd()) != null) {

            boolean authok;
            if (md5pwd.startsWith("MD5:")) { // DIGEST style
                authok = md5pwd.equals("MD5:" + Digest.encodeMD5Hex(user + ":"
                        + Switchboard.getSwitchboard().getConfig(SwitchboardConstants.ADMIN_REALM, "YaCy") + ":" + password));
            } else {
                authok = md5pwd.equals(Digest.encodeMD5Hex(user + ":" + password));
            }
            if (authok) {
                entry.updateLastAccess(false);
                return entry;
            }
        }
        return null;
    }

    public Entry passwordAuth(final String user, final String password, final String ip){
        final Entry entry = passwordAuth(user, password);
        if (entry != null) {
            entry.updateLastAccess(false);
            this.ipUsers.put(ip, entry.getUserName()); //XXX: This is insecure. TODO: use cookieauth
        }
        return entry;
    }
    
    public Entry md5Auth(final String user, final String md5) {
        final Entry entry = this.getEntry(user);
        if (entry != null && entry.getMD5EncodedUserPwd().endsWith(md5)) { // user pwd migth have prefix "MD5:"
            entry.updateLastAccess(false);
            return entry;
            }
        return null;
    }

    /**
     * Returns the user entry matching the cookie login token created and set
     * on login.
     * 
     * @param cookies
     * @return user entry or null
     */
    public Entry cookieAuth(final Cookie[] cookies){
        final String token = getLoginToken(cookies);
        if (cookieUsers.containsKey(token)) {
            final Entry entry = cookieUsers.get(token);
            return entry;
        }
        return null;
    }

    public String getCookie(final Entry entry){
        final Random r = new Random();
        final String token = Long.toString(Math.abs(r.nextLong()), 36);
        cookieUsers.put(token, entry);
        return token;
    }

    /**
     * Extracts the token set as value in a cookie with name "login"
     * @param cookies
     * @return login token string
     */
    public static String getLoginToken(final Cookie[] cookies) {
        if (cookies != null) {
            for (final Cookie c : cookies) {
                if (c.getName().equals("login")) {
                    return c.getValue().trim();
                }
            }
        }
        return "";
    }

    public enum AccessRight {

        //to create new rights, you just add them here
        UPLOAD_RIGHT("uploadRight", "Upload"),
        DOWNLOAD_RIGHT("downloadRight", "Download"),
        ADMIN_RIGHT("adminRight", "Admin"),
        PROXY_RIGHT("proxyRight", "Proxy usage"),
        BLOG_RIGHT("blogRight", "Blog"),
        WIKIADMIN_RIGHT("wikiAdminRight", "Wiki Admin"),
        BOOKMARK_RIGHT("bookmarkRight", "Bookmark"),
        EXTENDED_SEARCH_RIGHT("extendedSearchRight", "Extended Search");

        private String name;
        private String friendlyName;

        AccessRight(final String name, final String friendlyName) {
            this.friendlyName = friendlyName;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public String getFriendlyName() {
            return friendlyName;
        }

    }
    
    public class Entry {
        public static final String MD5ENCODED_USERPWD_STRING = "MD5_user:pwd";  // value = "MD5:" + MD5Hex(username +":"+ realm + ":" pwd)
                                                                                // oldstyle = MD5Hex(username +":"+ pwd)
        public static final String USER_FIRSTNAME = "firstName";
        public static final String USER_LASTNAME = "lastName";
        public static final String USER_ADDRESS = "address";
        public static final String LAST_ACCESS = "lastAccess";
        public static final String TIME_USED = "timeUsed";
        public static final String TIME_LIMIT = "timeLimit";
        public static final String TRAFFIC_SIZE = "trafficSize";
        public static final String TRAFFIC_LIMIT = "trafficLimit";

        public static final int PROXY_ALLOK = 0; //can Surf
        public static final int PROXY_ERROR = 1; //unknown error
        public static final int PROXY_NORIGHT = 2; //no proxy right
        public static final int PROXY_TIMELIMIT_REACHED = 3;
        
        // this is a simple record structure that hold all properties of a user
        private Map<String, String> mem;
        private String userName;
        private final Calendar oldDate, newDate;
        
        public Entry(final String userName, final Map<String, String> mem) throws IllegalArgumentException {
            if ((userName == null) || (userName.isEmpty())) {
                throw new IllegalArgumentException("Username needed.");
            }
            if (userName.length()>128) {
                throw new IllegalArgumentException("Username too long!");
            }
            
            this.userName = userName.trim(); 
            if (this.userName.length() < USERNAME_MIN_LENGTH)  {
                throw new IllegalArgumentException("Username too short. Length should be >= " + USERNAME_MIN_LENGTH);
            }

            this.mem = (mem == null) ? new HashMap<String, String>() : mem;
          
            this.oldDate=Calendar.getInstance();
            this.newDate=Calendar.getInstance();
        }
        
        public String getUserName() {
            return this.userName;
        }
        
        public String getFirstName() {
            return (this.mem.containsKey(USER_FIRSTNAME) ? this.mem.get(USER_FIRSTNAME) : null);
        } 
        
        public String getLastName() {
            return (this.mem.containsKey(USER_LASTNAME) ? this.mem.get(USER_LASTNAME) : null);
        }     
        
        public String getAddress() {
            return (this.mem.containsKey(USER_ADDRESS) ? this.mem.get(USER_ADDRESS) : null);
        } 
        
        public long getTimeUsed() {
            long ret = 0L;
            String s = this.mem.get(TIME_USED);
            if (s != null && s.length() > 0) {
                try{
                    ret = Long.parseLong(s);
                } catch (final NumberFormatException e){
                    ConcurrentLog.logException(e);
                }
            } else {
                try {
                    this.setProperty(TIME_USED,"0");
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
            }
            return ret;
        }
        
        public long getTimeLimit() {
            long ret = 0L;
            String s = this.mem.get(TIME_LIMIT);
            if (s != null && s.length() > 0) {
                try {
                    ret =  Long.parseLong(s);
                } catch (final NumberFormatException e){
                    ConcurrentLog.logException(e);
                }
            } else {
                try {
                    this.setProperty(TIME_LIMIT, "0");
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
            }
            return ret;
        }
        
        public long getTrafficSize() {
            long ret = 0L;
            String s = this.mem.get(TRAFFIC_SIZE);
            if (s != null && s.length() > 0) {
                try {
                    ret = Long.parseLong(s);
                } catch (final NumberFormatException e) {
                    ConcurrentLog.logException(e);
                }
            } else {
                try {
                    this.setProperty(TRAFFIC_SIZE, "0");
                } catch (final Exception e) {
                    ConcurrentLog.logException(e);
                }
            }
            return ret;
        }
        
        public Long getTrafficLimit() {
            return (this.mem.containsKey(TRAFFIC_LIMIT) ? Long.valueOf(this.mem.get(TRAFFIC_LIMIT)) : null);
        }
        
        public long updateTrafficSize(final long responseSize) {
            if (responseSize < 0) {
                throw new IllegalArgumentException("responseSize must be greater or equal zero.");
            }
            
            final long currentTrafficSize = getTrafficSize();
            final long newTrafficSize = currentTrafficSize + responseSize;
            try {
                this.setProperty(TRAFFIC_SIZE,Long.toString(newTrafficSize));
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
            }
            return newTrafficSize;
        }
        
        public Long getLastAccess() {
            return (this.mem.containsKey(LAST_ACCESS) ? Long.valueOf(this.mem.get(LAST_ACCESS)) : null);
        }        
        
        public int surfRight(){
            final long timeUsed=this.updateLastAccess(true);
            final int ret;

            if (!this.hasRight(AccessRight.PROXY_RIGHT)) {
                ret = PROXY_NORIGHT;
            } else if(! (this.getTimeLimit() <= 0 || (timeUsed < this.getTimeLimit())) ){ //no timelimit or timelimit not reached
                ret = PROXY_TIMELIMIT_REACHED;
            } else {
                ret = PROXY_ALLOK;
            }
            return ret;
        }

        public boolean canSurf(){
            return (this.surfRight() == PROXY_ALLOK);
        }

        public long updateLastAccess(final boolean incrementTimeUsed) {
            return updateLastAccess(System.currentTimeMillis(), incrementTimeUsed);
        }

        public long updateLastAccess(final long timeStamp, final boolean incrementTimeUsed) {
            if (timeStamp < 0) {
                throw new IllegalArgumentException();
            }
            
            final Long lastAccess = this.getLastAccess();                                            
            long newTimeUsed = getTimeUsed();            
            
            if (incrementTimeUsed) {
                if ((lastAccess == null) ||  (timeStamp - lastAccess.longValue() >= 1000*60)) {//1 minute
                    newTimeUsed++;  
                    if (lastAccess != null) {
                        this.oldDate.setTime(new Date(lastAccess.longValue()));
                        this.newDate.setTime(new Date(System.currentTimeMillis()));
                        if(
                                this.oldDate.get(Calendar.DAY_OF_MONTH) != this.newDate.get(Calendar.DAY_OF_MONTH) ||
                                this.oldDate.get(Calendar.MONTH) != this.newDate.get(Calendar.MONTH) ||
                                this.oldDate.get(Calendar.YEAR) != this.newDate.get(Calendar.YEAR)
                        ){ //new Day, reset time
                                newTimeUsed=0;
                        }
                    }else{ //no access so far
                            newTimeUsed=0;
                    }
                    this.mem.put(TIME_USED,Long.toString(newTimeUsed));
                    this.mem.put(LAST_ACCESS,Long.toString(timeStamp)); //update Timestamp
                }
            } else {
                this.mem.put(LAST_ACCESS,Long.toString(timeStamp)); //update Timestamp
            }
            
            try {
                UserDB.this.userTable.insert(UTF8.getBytes(getUserName()), this.mem);
            } catch(final Exception e){
                ConcurrentLog.logException(e);
            }
            return newTimeUsed;
        }

        /**
         * @return encode credential = "MD5:" + MD5Hex(username +":"+ realm + ":" pwd) or null. (old style = MD5Hex(username +":"+ pwd)
         */
        public String getMD5EncodedUserPwd() {
            return (this.mem.containsKey(MD5ENCODED_USERPWD_STRING)) ? this.mem.get(MD5ENCODED_USERPWD_STRING) : null;
        }
        
        public Map<String, String> getProperties() {
            return this.mem;
        }
        
        public void setProperty(final String propName, final String newValue) throws IOException, SpaceExceededException {
            this.mem.put(propName,  newValue);
            UserDB.this.userTable.insert(UTF8.getBytes(getUserName()), this.mem);
        }
        
        public String getProperty(final String propName, final String defaultValue) {
            return (this.mem.containsKey(propName) ? this.mem.get(propName) : defaultValue);
        }
        
        public boolean hasRight(final AccessRight accessRight){
            return (this.mem.containsKey(accessRight.toString())) ? this.mem.get(accessRight.toString()).equals("true") : false;
        }
        
        public void logout(final String ip, final String logintoken){
            logout(ip);
            if(cookieUsers.containsKey(logintoken)){
                cookieUsers.remove(logintoken);
            }
        }

        public void logout(final String ip) {
    	   try {
               if (ipUsers.containsKey(ip)){
                       ipUsers.remove(ip);
               }
    	   } catch (final Exception e) {
               ConcurrentLog.logException(e);
           }
        }

        @Override
        public String toString() {
            final StringBuilder str = new StringBuilder();
            
            str.append((this.userName == null) ? "null" : this.userName).append(": ");
            
            if (this.mem != null) {     
                str.append(this.mem.toString());
            } 
            
            return str.toString();
        }    
        
    }
    
    public Iterator<Entry> iterator(final boolean up) {
        // enumerates users
        try {
            return new userIterator(up);
        } catch (final IOException e) {
            return new HashSet<Entry>().iterator();
        }
    }


    public class userIterator implements Iterator<Entry> {
        // the iterator iterates all userNames
        CloneableIterator<byte[]> userIter;
        //userDB.Entry nextEntry;
        
        public userIterator(final boolean up) throws IOException {
            this.userIter = UserDB.this.userTable.keys(up, false);
            //this.nextEntry = null;
        }
        
        @Override
        public boolean hasNext() {
            try {
                return this.userIter.hasNext();
            } catch (final kelondroException e) {
                resetDatabase();
                return false;
            }
        }
        
        @Override
        public Entry next() {
            try {
                return getEntry(UTF8.String(this.userIter.next()));
            } catch (final kelondroException e) {
                resetDatabase();
                return null;
            }
        }
        
        @Override
        public void remove() {
//            if (this.nextEntry != null) {
//                try {
//                    final Object userName = this.nextEntry.getUserName();
//                    if (userName != null) removeEntry((String) userName);
//                } catch (final kelondroException e) {
//                    resetDatabase();
//                }
//            }
            throw new UnsupportedOperationException("Method not implemented yet.");
        }
    }    
    
}
