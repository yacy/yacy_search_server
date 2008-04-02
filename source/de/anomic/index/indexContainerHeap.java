// indexContainerHeap.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.03.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroByteOrder;
import de.anomic.kelondro.kelondroBytesIntMap;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.server.logging.serverLog;

public final class indexContainerHeap {

     
    public indexContainerHeap(File databaseRoot, kelondroRow payloadrow, String dumpname, serverLog log) {
    }
    

    public static void dumpHeap(File indexHeapFile, kelondroRow payloadrow, SortedMap<String, indexContainer> cache, serverLog log) throws IOException {
        if (log != null) log.logInfo("creating rwi heap dump '" + indexHeapFile.getName() + "', " + cache.size() + " rwi's");
        if (indexHeapFile.exists()) indexHeapFile.delete();
        OutputStream os = new BufferedOutputStream(new FileOutputStream(indexHeapFile), 64 * 1024);
        long startTime = System.currentTimeMillis();
        long wordcount = 0, urlcount = 0;
        String wordHash;
        indexContainer container;

        // write wCache
        synchronized (cache) {
            for (Map.Entry<String, indexContainer> entry: cache.entrySet()) {
                // get entries
                wordHash = entry.getKey();
                container = entry.getValue();
                
                // put entries on heap
                if (container != null) {
                    os.write(wordHash.getBytes());
                    if (wordHash.length() < payloadrow.primaryKeyLength) {
                        for (int i = 0; i < payloadrow.primaryKeyLength - wordHash.length(); i++) os.write(0);
                    }
                    os.write(container.exportCollection());
                }
                wordcount++;
                urlcount += container.size();
            }
        }
        os.flush();
        os.close();
        if (log != null) log.logInfo("finished rwi heap dump: " + wordcount + " words, " + urlcount + " word/URL relations in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
    }

    public static SortedMap<String, indexContainer> restoreHeap(File indexHeapFile, kelondroRow payloadrow, serverLog log) throws IOException {
        if (!(indexHeapFile.exists())) throw new IOException("file " + indexHeapFile + " does not exist");
        if (log != null) log.logInfo("restoring dump for rwi heap '" + indexHeapFile.getName() + "'");
        
        long start = System.currentTimeMillis();
        SortedMap<String, indexContainer> cache = Collections.synchronizedSortedMap(new TreeMap<String, indexContainer>(new kelondroByteOrder.StringOrder(payloadrow.getOrdering())));
        DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream(indexHeapFile), 64*1024));
        
        long urlCount = 0;
        String wordHash;
        byte[] word = new byte[payloadrow.primaryKeyLength];
        while (is.available() > 0) {
            // read word
            is.read(word);
            wordHash = new String(word);
            // read collection
            
            indexContainer container = new indexContainer(wordHash, kelondroRowSet.importRowSet(is, payloadrow));
            cache.put(wordHash, container);
            urlCount += container.size();
        }
        is.close();
        if (log != null) log.logInfo("finished rwi heap restore: " + cache.size() + " words, " + urlCount + " word/URL relations in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        return cache;
    }
    
    public static kelondroBytesIntMap indexHeap(File indexHeapFile, kelondroRow payloadrow, serverLog log) throws IOException {
        if (!(indexHeapFile.exists())) throw new IOException("file " + indexHeapFile + " does not exist");
        if (indexHeapFile.length() >= (long) Integer.MAX_VALUE) throw new IOException("file " + indexHeapFile + " too large, index can only be crated for files less than 2GB");
        if (log != null) log.logInfo("creating index for rwi heap '" + indexHeapFile.getName() + "'");
        
        long start = System.currentTimeMillis();
        kelondroBytesIntMap index = new kelondroBytesIntMap(payloadrow.primaryKeyLength, (kelondroByteOrder) payloadrow.getOrdering(), 0);
        DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream(indexHeapFile), 64*1024));
        
        long urlCount = 0;
        String wordHash;
        byte[] word = new byte[payloadrow.primaryKeyLength];
        int seek = 0, seek0;
        while (is.available() > 0) {
            // remember seek position
            seek0 = seek;
            
            // read word
            is.read(word);
            wordHash = new String(word);
            seek += wordHash.length();
            
            // read collection
            seek += kelondroRowSet.skipNextRowSet(is, payloadrow);
            index.addi(word, seek0);
        }
        is.close();
        if (log != null) log.logInfo("finished rwi heap indexing: " + urlCount + " word/URL relations in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");
        return index;
    }
}
