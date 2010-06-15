// BookmarkHelper.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// Methods from this file has been originally contributed by Alexander Schier
// and had been refactored by Michael Christen for better a method structure 30.01.2010
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


package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.data.bookmarksDB.Bookmark;

import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.NaturalOrder;

public class BookmarkDate {

    MapHeap datesTable;
    
    public BookmarkDate(File datesFile) throws IOException {
        this.datesTable = new MapHeap(datesFile, 20, NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
    }
    
    public void close() {
        this.datesTable.close();
    }
    

    public Entry getDate(final String date) {
        Map<String, String> map;
        try {
            map = datesTable.get(date.getBytes());
        } catch (final IOException e) {
            map = null;
        } catch (RowSpaceExceededException e) {
            map = null;
        }
        if (map == null) return new Entry(date);
        return new Entry(date, map);
    }
    
    // rebuilds the datesDB from the bookmarksDB
    public void init(final Iterator<Bookmark> it) {
        Log.logInfo("BOOKMARKS", "start init dates.db from bookmarks.db...");
        //final Iterator<Bookmark> it=bookmarkIterator(true);
        Bookmark bookmark;        
        String date;
        Entry bmDate;
        int count = 0;
        while (it.hasNext()) {
            bookmark=it.next();
//            if (bookmark == null) continue;
            date = String.valueOf(bookmark.getTimeStamp());
            bmDate=getDate(date);
            if(bmDate==null){
                bmDate=new Entry(date);
            }
            bmDate.add(bookmark.getUrlHash());
            bmDate.setDatesTable();
            count++;
        }
        Log.logInfo("BOOKMARKS", "finished init "+datesTable.size()+" dates using " + count + " bookmarks.");
    }
    
    /**
     * Subclass of bookmarksDB, which provide the bookmarksDate object-type
     */    
    public class Entry {
        public static final String URL_HASHES="urlHashes";
        private final Map<String, String> mem;
        String date;

        public Entry(final String mydate){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=new HashMap<String, String>();
            mem.put(URL_HASHES, "");
        }

        public Entry(final String mydate, final Map<String, String> map){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=map;
        }
        public Entry(final String mydate, final ArrayList<String> entries){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=new HashMap<String, String>();
            mem.put(URL_HASHES, listManager.collection2string(entries));
        }
        public void add(final String urlHash){
            final String urlHashes = mem.get(URL_HASHES);
            ArrayList<String> list;
            if(urlHashes != null && !urlHashes.equals("")){
                list=listManager.string2arraylist(urlHashes);
            }else{
                list=new ArrayList<String>();
            }
            if(!list.contains(urlHash) && urlHash != null && !urlHash.equals("")){
                list.add(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.collection2string(list));
            /*if(urlHashes!=null && !urlHashes.equals("") ){
                if(urlHashes.indexOf(urlHash) <0){
                    this.mem.put(URL_HASHES, urlHashes+","+urlHash);
                }
            }else{
                this.mem.put(URL_HASHES, urlHash);
            }*/
        }
        public void delete(final String urlHash){
            final ArrayList<String> list=listManager.string2arraylist(this.mem.get(URL_HASHES));
            if(list.contains(urlHash)){
                list.remove(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.collection2string(list));
        }
        public void setDatesTable() {
            if (this.size() >0) {
                try {
                    datesTable.put(getDateString().getBytes(), mem);
                } catch (Exception e) {
                    Log.logException(e);
                }
            } else {
                try {
                    datesTable.remove(getDateString().getBytes());
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
        }
        public String getDateString(){
            return date;
        }
        public ArrayList<String> getBookmarkList(){
            return listManager.string2arraylist(this.mem.get(URL_HASHES));
        }
        public int size(){
            return listManager.string2arraylist(this.mem.get(URL_HASHES)).size();
        }
    }
}
