//userDB.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Date;
import java.util.Calendar;

import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMap;
import de.anomic.server.logging.serverLog;

public final class userDB {
    
    public static final int USERNAME_MAX_LENGTH = 128;
    public static final int USERNAME_MIN_LENGTH = 4;
    
    kelondroMap userTable;
    private final File userTableFile;
    private final int bufferkb;
    
    public userDB(File userTableFile, int bufferkb) throws IOException {
        this.userTableFile = userTableFile;
        this.bufferkb = bufferkb;
        if (userTableFile.exists()) {
            try {
                this.userTable = new kelondroMap(new kelondroDyn(userTableFile, bufferkb * 1024));
            } catch (kelondroException e) {
                userTableFile.delete();
                userTableFile.getParentFile().mkdirs();
                this.userTable = new kelondroMap(new kelondroDyn(userTableFile, bufferkb * 1024, 128, 256));
            }
        } else {
            userTableFile.getParentFile().mkdirs();
            this.userTable = new kelondroMap(new kelondroDyn(userTableFile, bufferkb * 1024, 128, 256));
        }
    }
    
    public int[] dbCacheChunkSize() {
        return userTable.cacheChunkSize();
    }    
    
    public int[] dbCacheFillStatus() {
        return userTable.cacheFillStatus();
    }    
    
    void resetDatabase() {
        // deletes the database and creates a new one
        if (userTable != null) try {
            userTable.close();
        } catch (IOException e) {}
        if (!(userTableFile.delete())) throw new RuntimeException("cannot delete user database");
        try {
            userTableFile.getParentFile().mkdirs();
            userTable = new kelondroMap(new kelondroDyn(userTableFile, this.bufferkb, 256, 512));
        } catch (IOException e){
            serverLog.logSevere("PLASMA", "user.resetDatabase", e);
        }
    }
    
    public void close() {
        try {
            userTable.close();
        } catch (IOException e) {}
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
        try {
            Map record = userTable.get(userName);
            if (record == null) return null;
            return new Entry(userName, record);
        } catch (IOException e) {
            return null;
        }
    }    
    
    public Entry createEntry(String userName, HashMap userProps) {
        Entry entry = new Entry(userName,userProps);
        return entry;
    }
    
    public String addEntry(Entry entry) {
        try {
            userTable.set(entry.userName,entry.mem);
            return entry.userName;
        } catch (IOException e) {
            return null;
        }
    }    
    
    public class Entry {
        public static final String MD5ENCODED_USERPWD_STRING = "MD5_user:pwd";
        public static final String AUTHENTICATION_METHOD = "auth_method";
        public static final String USER_FIRSTNAME = "firstName";
        public static final String USER_LASTNAME = "lastName";
        public static final String USER_ADDRESS = "address";
        public static final String LAST_ACCESS = "lastAccess";
        public static final String TIME_USED = "timeUsed";
        public static final String TIME_LIMIT = "timeLimit";
        public static final String TRAFFIC_SIZE = "trafficSize";
        public static final String TRAFFIC_LIMIT = "trafficLimit";
        
        // this is a simple record structure that hold all properties of a user
        private Map mem;
        private String userName;
		private Calendar oldDate, newDate;
        
        public Entry(String userName, Map mem) {
            if ((userName == null) || (userName.length() == 0)) 
                throw new IllegalArgumentException();
            
            this.userName = userName.trim(); 
            if (this.userName.length() < USERNAME_MIN_LENGTH) 
                throw new IllegalArgumentException("Username to short. Length should be >= " + USERNAME_MIN_LENGTH);
            
            if (mem == null) this.mem = new HashMap();
            else this.mem = mem;            
            
            if (!mem.containsKey(AUTHENTICATION_METHOD))this.mem.put(AUTHENTICATION_METHOD,"yacy");
			this.oldDate=Calendar.getInstance();
			this.newDate=Calendar.getInstance();
        }
        
        public String getUserName() {
            return this.userName;
        }
        
        public String getFirstName() {
            return (this.mem.containsKey(USER_FIRSTNAME)?(String)this.mem.get(USER_FIRSTNAME):null);
        } 
        
        public String getLastName() {
            return (this.mem.containsKey(USER_LASTNAME)?(String)this.mem.get(USER_LASTNAME):null);
        }     
        
        public String getAddress() {
            return (this.mem.containsKey(USER_ADDRESS)?(String)this.mem.get(USER_ADDRESS):null);
        } 
        
        public long getTimeUsed() {
            if (this.mem.containsKey(TIME_USED)) {
                return Long.valueOf((String)this.mem.get(TIME_USED)).longValue();
            }
            try {
                this.setProperty(TIME_USED,"0");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return 0;
        }
        
        public Long getTimeLimit() {
            return (this.mem.containsKey(TIME_LIMIT)?Long.valueOf((String)this.mem.get(TIME_LIMIT)):null);
        }
        
        public long getTrafficSize() {
            if (this.mem.containsKey(TRAFFIC_SIZE)) {
                return Long.valueOf((String)this.mem.get(TRAFFIC_SIZE)).longValue();
            }
            try {
                this.setProperty(TRAFFIC_SIZE,"0");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return 0;
        }
        
        public Long getTrafficLimit() {
            return (this.mem.containsKey(TRAFFIC_LIMIT)?Long.valueOf((String)this.mem.get(TRAFFIC_LIMIT)):null);
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
            return (this.mem.containsKey(LAST_ACCESS)?Long.valueOf((String)this.mem.get(LAST_ACCESS)):null);
        }        
        
		public boolean canSurf(){
			if( this.getTimeLimit().longValue() <= 0 || (this.updateLastAccess(true) < this.getTimeLimit().longValue()) )//no timelimit or timelimit not reached
				return true;
			else
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
                if ((lastAccess == null)||((lastAccess != null)&&(timeStamp-lastAccess.longValue()>=1000*60))) {
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
                userDB.this.userTable.set(getUserName(), this.mem); 
            } catch(Exception e){
                e.printStackTrace();
            }
            return newTimeUsed;
        }
        
        public String getMD5EncodedUserPwd() {
            return (this.mem.containsKey(MD5ENCODED_USERPWD_STRING)?(String)this.mem.get(MD5ENCODED_USERPWD_STRING):null);
        }
        
        public Map getProperties() {
            return this.mem;
        }
        
        public void setProperty(String propName, String newValue) throws IOException {
            this.mem.put(propName,  newValue);
            userDB.this.userTable.set(getUserName(), this.mem);
        }
        
        public String getProperty(String propName, String defaultValue) {
            return (this.mem.containsKey(propName)?(String)this.mem.get(propName):defaultValue);
        }
        
        public String toString() {
            StringBuffer str = new StringBuffer();
            str.append((this.userName==null)?"null":this.userName)
            .append(": ");
            
            if (this.mem != null) {     
                str.append(this.mem.toString());
            } 
            
            return str.toString();
        }    
        
    }
    
    public Iterator iterator(boolean up) {
        // enumerates users
        try {
            return new userIterator(up);
        } catch (IOException e) {
            return new HashSet().iterator();
        }
    }
    
    public class userIterator implements Iterator {
        // the iterator iterates all userNames
        kelondroDyn.dynKeyIterator userIter;
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
        public Object next() {
            try {
                return getEntry((String) this.userIter.next());
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
