//kelondroCachedObjectMap - build an object cache over a kelondroMap
//----------------------------------------------------------
//part of YaCy
//
// (C) 2007 by Alexander Schier
//
// last change: $LastChangedDate:  $ by $LastChangedBy: $
// $LastChangedRevision: $
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.kelondro;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class kelondroCachedObjectMap {
    private kelondroMap db;
    private HashMap cache;
    public kelondroCachedObjectMap(kelondroMap db){
        this.db=db;
        cache=new HashMap();
    }
    public void close() throws IOException{
        db.close();
    }
    public void set(String key, kelondroCachedObject entry){
        cache.put(key, entry);
    }
    /*
     * this gives you a Entry, if its cached, and a Map, if not.
     */
    public Object get(String key) throws IOException{
        if(cache.containsKey(key))
            return cache.get(key);
        else
            return db.get(key);
    }
    public void setDirect(String key, kelondroCachedObject entry) throws IOException{
        db.set(key, entry.toMap());
    }
    public void flushCache() throws IOException{
        syncCache();
        cache=new HashMap();
    }
    public void flushEntry(String key) throws IOException{
        syncEntry(key);
        cache.remove(key);
    }
    public void syncEntry(String key) throws IOException{
        if(cache.containsKey(key))
            this.setDirect(key, (kelondroCachedObject)cache.get(key));
    }
    public void syncCache() throws IOException{
        Iterator it=this.cache.keySet().iterator();
        String key;
        while(it.hasNext()){
            key=(String)it.next();
            this.setDirect(key, (kelondroCachedObject)cache.get(key));
        }
    }
    public boolean remove(String key){
        if(cache.containsKey(key))
            cache.remove(key);
        try {
            db.remove(key);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    public Iterator cacheIterator(){
        return this.cache.keySet().iterator();
    }
    public Iterator dbOnlyKeys(boolean up) throws IOException{
        return this.db.keys(up, false);
    }
    public Iterator dbOnlyKeys(boolean up, boolean rotating) throws IOException{
        return this.db.keys(up, rotating);
    }
    public Iterator keys(boolean up, boolean rotating) throws IOException{
        flushCache();
        return dbOnlyKeys(up, rotating);
    }
    public Iterator keys(boolean up) throws IOException{
        return keys(up, false);
    }
    public int size(boolean flushed){
        if(flushed)
            try {
                flushCache();
            } catch (IOException e) {}
        return this.db.size();
    }
    public int size(){
        return size(false);
    }
}
