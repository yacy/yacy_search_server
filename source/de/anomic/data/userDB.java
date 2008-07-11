//userDB.java 
//-------------------------------------
//part of YACY
//
//(C) 2005, 2006 by Martin Thelian
//                  Alexander Schier
//
//last change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBLOBTree;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMapDataMining;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverCodings;

public final class userDB {
    
    public static final int USERNAME_MAX_LENGTH = 128;
    public static final int USERNAME_MIN_LENGTH = 4;
    
    kelondroMapDataMining userTable;
    private final File userTableFile;
	HashMap<String, String> ipUsers = new HashMap<String, String>();
    HashMap<String, Object> cookieUsers = new HashMap<String, Object>();
    
    public userDB(File userTableFile) {
        this.userTableFile = userTableFile;
        userTableFile.getParentFile().mkdirs();
        this.userTable = new kelondroMapDataMining(new kelondroBLOBTree(userTableFile, true, true, 128, 256, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 10);
    }
    
    void resetDatabase() {
        // deletes the database and creates a new one
        if (userTable != null) userTable.close();
        if (!(userTableFile.delete())) throw new RuntimeException("cannot delete user database");
        userTableFile.getParentFile().mkdirs();
        userTable = new kelondroMapDataMining(new kelondroBLOBTree(userTableFile, true, true, 256, 512, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 10);
    }
    
    public void close() {
        userTable.close();
    }
    
    public int size() {
        return userTable.size();
    }    
    
    public void removeEntry(String hostName) {
        try {
            userTable.remove(hostName.toLowerCase());
        } catch (IOException e) {}
    }        
    
    public Entry getEntry(String userName) {
        if(userName.length()>128){
            userName=userName.substring(0, 127);
        }
        HashMap<String, String> record;
        try {
            record = userTable.get(userName);
        } catch (IOException e) {
            return null;
        }
        if (record == null) return null;
        return new Entry(userName, record);
    }    
    
    public Entry createEntry(String userName, HashMap<String, String> userProps) throws IllegalArgumentException{
        Entry entry = new Entry(userName,userProps);
        return entry;
    }
    
    public String addEntry(Entry entry) {
        try {
            userTable.put(entry.userName,entry.mem);
            return entry.userName;
        } catch (IOException e) {
            return null;
        }
    }    

    /**
	 * use a ProxyAuth String to authenticate a user
	 * @param auth a base64 Encoded String, which contains "username:pw".
	 */
	public Entry proxyAuth(String auth) {
        if(auth==null)
            return null;
		Entry entry=null;
		auth=auth.trim().substring(6);
        
        try{
            auth=kelondroBase64Order.standardCoder.decodeString(auth, "de.anomic.data.userDB.proxyAuth()");
        }catch(RuntimeException e){} //no valid Base64
        String[] tmp=auth.split(":");
        if(tmp.length == 2){
            entry=this.passwordAuth(tmp[0], tmp[1]);
//            if(entry != null){
                //return entry;
//			}else{ //wrong/no auth, so auth is removed from browser
				/*FIXME: This cannot work
				try{
    					entry.setProperty(Entry.LOGGED_OUT, "false");
    				}catch(IOException e){}*/
//			}
            return entry;
		}
		return null;
	}
        public Entry getUser(httpHeader header){
            return getUser(header.get(httpHeader.AUTHORIZATION), header.get(httpHeader.CONNECTION_PROP_CLIENTIP), header.getHeaderCookies());
        }
        public Entry getUser(String auth, String ip, String cookies){
        Entry entry=null;
        if(auth != null)
            entry=proxyAuth(auth);
        if(entry == null)
            entry=cookieAuth(cookies);
        return entry;
    }
    /**
     * determinate, if a user has Adminrights from a authorisation http-headerfield
     * it tests both userDB and oldstyle adminpw.
     * @param auth the http-headerline for authorisation
     */
    public boolean hasAdminRight(String auth, String ip, String cookies){
        Entry entry=getUser(auth, ip, cookies);
        if(entry != null)
            return entry.hasAdminRight();
        else if(entry != null && cookieAdminAuth(cookies))
            return entry.hasAdminRight();
        else
            return false;
    }

	/**
	 * use a ProxyAuth String to authenticate a user and save the ip/username for ipAuth
	 * @param auth a base64 Encoded String, which contains "username:pw".
	 * @param ip an ip.
	 */
	public Entry proxyAuth(String auth, String ip){
		Entry entry=proxyAuth(auth);
		if(entry == null){
			return null;
		}
        entry.updateLastAccess(false);
        this.ipUsers.put(ip, entry.getUserName());
        return entry;
	}
	/**
	 * authenticate a user by ip, if he had used proxyAuth in the last 10 Minutes
	 * @param ip the IP of the User
	 */
	public Entry ipAuth(String ip) {
        if(this.ipUsers.containsKey(ip)){
            String user=this.ipUsers.get(ip);
            Entry entry=this.getEntry(user);
            Long entryTimestamp=entry.getLastAccess();
            if(entryTimestamp == null || (System.currentTimeMillis()-entryTimestamp.longValue()) > (1000*60*10) ){ //no timestamp or older than 10 Minutes
                return null;
            }
            return entry; //All OK
        }
        return null;
	}
    public Entry passwordAuth(String user, String password){
        Entry entry=this.getEntry(user);
        if( entry != null && entry.getMD5EncodedUserPwd().equals(serverCodings.encodeMD5Hex(user+":"+password)) ){
                if(entry.isLoggedOut()){
                    try{
                        entry.setProperty(Entry.LOGGED_OUT, "false");
                    }catch(IOException e){}
                    return null;
                }
                return entry;
        }
        return null;
    }
    public Entry passwordAuth(String user, String password, String ip){
        Entry entry=passwordAuth(user, password);
        if(entry == null){
            return null;
        }
        entry.updateLastAccess(false);
        this.ipUsers.put(ip, entry.getUserName()); //XXX: This is insecure. TODO: use cookieauth
        return entry;
    }
    public Entry md5Auth(String user, String md5){
        Entry entry=this.getEntry(user);
        if( entry != null && entry.getMD5EncodedUserPwd().equals(md5)){
            if(entry.isLoggedOut()){
                try{
				    entry.setProperty(Entry.LOGGED_OUT, "false");
                }catch(IOException e){}
                return null;
            }
			return entry;
        }
		return null;
    }
    public Entry cookieAuth(String cookieString){
        String token=getLoginToken(cookieString);
        if(cookieUsers.containsKey(token)){
            Object entry=cookieUsers.get(token);
            if(entry instanceof Entry) //String would mean static Admin
                return (Entry)entry;
        }
        return null;
    }
    public boolean cookieAdminAuth(String cookieString){
        String token=getLoginToken(cookieString);
        if(cookieUsers.containsKey(token)){
            Object entry=cookieUsers.get(token);
            if(entry instanceof String && entry.equals("admin"))
                return true;
        }
        return false;
    }
    public String getCookie(Entry entry){
        Random r = new Random();
        String token = Long.toString(Math.abs(r.nextLong()), 36);
        cookieUsers.put(token, entry);
        return token;
    }
    public String getAdminCookie(){
        Random r = new Random();
        String token = Long.toString(Math.abs(r.nextLong()), 36);
        cookieUsers.put(token, "admin");
        return token;
    }
    
    public static String getLoginToken(String cookies){
        String[] cookie=cookies.split(";"); //TODO: Mozilla uses "; "
        String[] pair;
        for(int i=0;i<cookie.length;i++){
            pair=cookie[i].split("=");
            if(pair[0].trim().equals("login")){
                return pair[1].trim();
            }
        }
        return "";
    }
    public void adminLogout(String logintoken){
        if(cookieUsers.containsKey(logintoken)){
            //XXX: We could check, if its == "admin", but we want to logout anyway.
            cookieUsers.remove(logintoken);
        }
    }
    
    
    public class Entry {
        public static final String MD5ENCODED_USERPWD_STRING = "MD5_user:pwd";
        public static final String AUTHENTICATION_METHOD = "auth_method";
        public static final String LOGGED_OUT = "loggedOut";
        public static final String USER_FIRSTNAME = "firstName";
        public static final String USER_LASTNAME = "lastName";
        public static final String USER_ADDRESS = "address";
        public static final String LAST_ACCESS = "lastAccess";
        public static final String TIME_USED = "timeUsed";
        public static final String TIME_LIMIT = "timeLimit";
        public static final String TRAFFIC_SIZE = "trafficSize";
        public static final String TRAFFIC_LIMIT = "trafficLimit";
        public static final String UPLOAD_RIGHT = "uploadRight";
        public static final String DOWNLOAD_RIGHT = "downloadRight";
        public static final String ADMIN_RIGHT = "adminRight";
        public static final String PROXY_RIGHT = "proxyRight";
        public static final String BLOG_RIGHT = "blogRight";
        public static final String WIKIADMIN_RIGHT = "wikiAdminRight";
        public static final String BOOKMARK_RIGHT = "bookmarkRight";
        
        //to create new rights, you just need to edit this strings
        public static final String RIGHT_TYPES=
        	ADMIN_RIGHT+","+DOWNLOAD_RIGHT+","+UPLOAD_RIGHT+","+PROXY_RIGHT+","+
        	BLOG_RIGHT+","+BOOKMARK_RIGHT+","+WIKIADMIN_RIGHT;
        public static final String RIGHT_NAMES="Admin,Download,Upload,Proxy usage,Blog,Bookmark,Wiki Admin,SOAP";
        
        public static final int PROXY_ALLOK = 0; //can Surf
        public static final int PROXY_ERROR = 1; //unknown error
        public static final int PROXY_NORIGHT = 2; //no proxy right
        public static final int PROXY_TIMELIMIT_REACHED = 3;
        
        // this is a simple record structure that hold all properties of a user
        HashMap<String, String> mem;
        String userName;
		private Calendar oldDate, newDate;
        
        public Entry(String userName, HashMap<String, String> mem) throws IllegalArgumentException {
            if ((userName == null) || (userName.length() == 0)) 
                throw new IllegalArgumentException("Username needed.");
            if(userName.length()>128){
                throw new IllegalArgumentException("Username too long!");
            }
            
            this.userName = userName.trim(); 
            if (this.userName.length() < USERNAME_MIN_LENGTH) 
                throw new IllegalArgumentException("Username to short. Length should be >= " + USERNAME_MIN_LENGTH);
            
            if (mem == null) this.mem = new HashMap<String, String>();
            else this.mem = mem;            
            
            if (mem == null || !mem.containsKey(AUTHENTICATION_METHOD))this.mem.put(AUTHENTICATION_METHOD,"yacy");
			this.oldDate=Calendar.getInstance();
			this.newDate=Calendar.getInstance();
        }
        
        public String getUserName() {
            return this.userName;
        }
        
        public String getFirstName() {
            return (this.mem.containsKey(USER_FIRSTNAME)?this.mem.get(USER_FIRSTNAME):null);
        } 
        
        public String getLastName() {
            return (this.mem.containsKey(USER_LASTNAME)?this.mem.get(USER_LASTNAME):null);
        }     
        
        public String getAddress() {
            return (this.mem.containsKey(USER_ADDRESS)?this.mem.get(USER_ADDRESS):null);
        } 
        
        public long getTimeUsed() {
            if (this.mem.containsKey(TIME_USED)) {
                try{
                    return Long.valueOf(this.mem.get(TIME_USED)).longValue();
                }catch(NumberFormatException e){
                    return 0;
                }
            }
            try {
                this.setProperty(TIME_USED,"0");
            } catch (IOException e) {}
            return 0;
        }
        
        public long getTimeLimit() {
            if (this.mem.containsKey(TIME_LIMIT)) {
                try{
                    return Long.valueOf(this.mem.get(TIME_LIMIT)).longValue();
                }catch(NumberFormatException e){
                    return 0;
                }
            }
            try {
                this.setProperty(TIME_LIMIT,"0");
            } catch (IOException e) {}
            return 0;
        }
        
        public long getTrafficSize() {
            if (this.mem.containsKey(TRAFFIC_SIZE)) {
                return Long.valueOf(this.mem.get(TRAFFIC_SIZE)).longValue();
            }
            try {
                this.setProperty(TRAFFIC_SIZE,"0");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return 0;
        }
        
        public Long getTrafficLimit() {
            return (this.mem.containsKey(TRAFFIC_LIMIT)?Long.valueOf(this.mem.get(TRAFFIC_LIMIT)):null);
        }
        
        public long updateTrafficSize(long responseSize) {
            if (responseSize < 0) throw new IllegalArgumentException("responseSize must be greater or equal zero.");
            
            long currentTrafficSize = getTrafficSize();
            long newTrafficSize = currentTrafficSize + responseSize;
            try {
                this.setProperty(TRAFFIC_SIZE,Long.toString(newTrafficSize));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return newTrafficSize;
        }
        
        public Long getLastAccess() {
            return (this.mem.containsKey(LAST_ACCESS)?Long.valueOf(this.mem.get(LAST_ACCESS)):null);
        }        
        
        public int surfRight(){
            long timeUsed=this.updateLastAccess(true);
            if(this.hasProxyRight() == false)
                return PROXY_NORIGHT;

            if(! (this.getTimeLimit() <= 0 || ( timeUsed < this.getTimeLimit())) ){ //no timelimit or timelimit not reached
                return PROXY_TIMELIMIT_REACHED;
            }
            return PROXY_ALLOK;
        }
		public boolean canSurf(){
            if(this.surfRight()==PROXY_ALLOK) return true;
            return false;
		}
        public long updateLastAccess(boolean incrementTimeUsed) {
			return updateLastAccess(System.currentTimeMillis(), incrementTimeUsed);
		}
        public long updateLastAccess(long timeStamp, boolean incrementTimeUsed) {
            if (timeStamp < 0) throw new IllegalArgumentException();
            
            Long lastAccess = this.getLastAccess();                                            
            long oldTimeUsed = getTimeUsed();
            long newTimeUsed = oldTimeUsed;            
            
            if (incrementTimeUsed) {
                if ((lastAccess == null)||((lastAccess != null)&&(timeStamp-lastAccess.longValue()>=1000*60))) { //1 minute
                    //this.mem.put(TIME_USED,Long.toString(newTimeUsed = ++oldTimeUsed));  
                    newTimeUsed = ++oldTimeUsed;  
					if(lastAccess != null){
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
            }else{ 
	            this.mem.put(LAST_ACCESS,Long.toString(timeStamp)); //update Timestamp
			}
            
            try {
                userDB.this.userTable.put(getUserName(), this.mem); 
            } catch(Exception e){
                e.printStackTrace();
            }
            return newTimeUsed;
        }
        
        public String getMD5EncodedUserPwd() {
            return (this.mem.containsKey(MD5ENCODED_USERPWD_STRING)?this.mem.get(MD5ENCODED_USERPWD_STRING):null);
        }
        
        public Map<String, String> getProperties() {
            return this.mem;
        }
        
        public void setProperty(String propName, String newValue) throws IOException {
            this.mem.put(propName,  newValue);
            userDB.this.userTable.put(getUserName(), this.mem);
        }
        
        public String getProperty(String propName, String defaultValue) {
            return (this.mem.containsKey(propName)?this.mem.get(propName):defaultValue);
        }
        public boolean hasRight(String rightName){
        	return (this.mem.containsKey(rightName)?this.mem.get(rightName).equals("true"):false);
        }
        /**
         * @deprecated use hasRight(UPLOAD_RIGHT) instead
         */
        public boolean hasUploadRight() {
            return this.hasRight(UPLOAD_RIGHT);
        }
        /**
         * @deprecated use hasRight(DOWNLOAD_RIGHT) instead
         */
        public boolean hasDownloadRight() {
        	return this.hasRight(DOWNLOAD_RIGHT);
        }
        /**
         * @deprecated use hasRight(PROXY_RIGHT) instead
         */
        public boolean hasProxyRight() {
        	return this.hasRight(PROXY_RIGHT);
        }
        /**
         * @deprecated use hasRight(ADMIN_RIGHT) instead
         */
        public boolean hasAdminRight() {
        	return this.hasRight(ADMIN_RIGHT);
        }
        /**
         * @deprecated use hasRight(BLOG_RIGHT) instead
         */
        public boolean hasBlogRight() {
        	return this.hasRight(BLOG_RIGHT);
        }
        /**
         * @deprecated use hasRight(WIKIADMIN_RIGHT) instead
         */
        public boolean hasWikiAdminRight() {
        	return this.hasRight(WIKIADMIN_RIGHT);
        }
        /**
         * @deprecated use hasRight(BOOKMARK_RIGHT) instead
         */
        public boolean hasBookmarkRight() {
        	return this.hasRight(BOOKMARK_RIGHT);
        }
        public boolean isLoggedOut(){
        	   return (this.mem.containsKey(LOGGED_OUT)?this.mem.get(LOGGED_OUT).equals("true"):false);
        }
        public void logout(String ip, String logintoken){
            logout(ip);
            if(cookieUsers.containsKey(logintoken)){
                cookieUsers.remove(logintoken);
            }
        }
        public void logout(String ip){
        	   try{
        		   setProperty(LOGGED_OUT, "true");
        		   if(ipUsers.containsKey(ip)){
        			   ipUsers.remove(ip);
        		   }
        	   }catch(IOException e){}
        }
        public void logout(){
        		logout("xxxxxx");
        }
        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append((this.userName==null)?"null":this.userName)
            .append(": ");
            
            if (this.mem != null) {     
                str.append(this.mem.toString());
            } 
            
            return new String(str);
        }    
        
    }
    
    public Iterator<Entry> iterator(boolean up) {
        // enumerates users
        try {
            return new userIterator(up);
        } catch (IOException e) {
            return new HashSet<Entry>().iterator();
        }
    }


    public class userIterator implements Iterator<Entry> {
        // the iterator iterates all userNames
        kelondroCloneableIterator<byte[]> userIter;
        userDB.Entry nextEntry;
        
        public userIterator(boolean up) throws IOException {
            this.userIter = userDB.this.userTable.keys(up, false);
            this.nextEntry = null;
        }
        public boolean hasNext() {
            try {
                return this.userIter.hasNext();
            } catch (kelondroException e) {
                resetDatabase();
                return false;
            }
        }
        public Entry next() {
            try {
                return getEntry(new String(this.userIter.next()));
            } catch (kelondroException e) {
                resetDatabase();
                return null;
            }
        }
        public void remove() {
            if (this.nextEntry != null) {
                try {
                    Object userName = this.nextEntry.getUserName();
                    if (userName != null) removeEntry((String) userName);
                } catch (kelondroException e) {
                    resetDatabase();
                }
            }
        }
    }    
    
}
