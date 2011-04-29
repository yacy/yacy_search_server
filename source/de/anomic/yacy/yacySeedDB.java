// yacySeedDB.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.yacy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.SoftReference;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.blob.MapDataMining;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.kelondroException;

//import de.anomic.http.client.Client;
import de.anomic.http.server.AlternativeDomainNames;
import de.anomic.http.server.HTTPDemon;
//import de.anomic.http.server.ResponseContainer;
import de.anomic.search.Switchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.dht.PartitionScheme;
import de.anomic.yacy.dht.VerticalWordPartitionScheme;

public final class yacySeedDB implements AlternativeDomainNames {
  
    // global statics

    private static final int dhtActivityMagic = 32;
    
    /**
     * <p><code>public static final String <strong>DBFILE_OWN_SEED</strong> = "mySeed.txt"</code></p>
     * <p>Name of the file containing the database holding this peer's seed</p>
     */
    public static final String DBFILE_OWN_SEED = "mySeed.txt";
    
    public static final String[]      sortFields = new String[] {yacySeed.LCOUNT, yacySeed.RCOUNT, yacySeed.ICOUNT, yacySeed.UPTIME, yacySeed.VERSION, yacySeed.LASTSEEN};
    public static final String[]   longaccFields = new String[] {yacySeed.LCOUNT, yacySeed.ICOUNT, yacySeed.ISPEED};
    public static final String[] doubleaccFields = new String[] {yacySeed.RSPEED};
    
    // class objects
    private File seedActiveDBFile, seedPassiveDBFile, seedPotentialDBFile;
    private File myOwnSeedFile;
    private MapDataMining seedActiveDB, seedPassiveDB, seedPotentialDB;
    
    protected int lastSeedUpload_seedDBSize = 0;
    public long lastSeedUpload_timeStamp = System.currentTimeMillis();
    protected String lastSeedUpload_myIP = "";

    public  yacyPeerActions peerActions;
    public  yacyNewsPool newsPool;
    
    private int netRedundancy;
    public  PartitionScheme scheme;
    
    private yacySeed mySeed; // my own seed
    private Set<String> myBotIDs; // list of id's that this bot accepts as robots.txt identification
    private final Hashtable<String, String> nameLookupCache; // a name-to-hash relation
    private final Hashtable<InetAddress, SoftReference<yacySeed>> ipLookupCache;
    
    public yacySeedDB(
            final File networkRoot,
            final String seedActiveDBFileName,
            final String seedPassiveDBFileName,
            final String seedPotentialDBFileName,
            final File myOwnSeedFile, 
            final int redundancy,
            final int partitionExponent,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.seedActiveDBFile = new File(networkRoot, seedActiveDBFileName);
        this.seedPassiveDBFile = new File(networkRoot, seedPassiveDBFileName);
        this.seedPotentialDBFile = new File(networkRoot, seedPotentialDBFileName);
        this.mySeed = null; // my own seed
        this.myOwnSeedFile = myOwnSeedFile;
        this.myBotIDs = new HashSet<String>();
        this.myBotIDs.add("yacy");
        this.myBotIDs.add("yacybot");
        this.myBotIDs.add("yacyproxy");
        this.netRedundancy = redundancy;
        this.scheme = new VerticalWordPartitionScheme(partitionExponent);
        
        // set up seed database
        this.seedActiveDB = openSeedTable(seedActiveDBFile);
        this.seedPassiveDB = openSeedTable(seedPassiveDBFile);
        this.seedPotentialDB = openSeedTable(seedPotentialDBFile);
        
        // start our virtual DNS service for yacy peers with empty cache
        this.nameLookupCache = new Hashtable<String, String>();
        
        // cache for reverse name lookup
        this.ipLookupCache = new Hashtable<InetAddress, SoftReference<yacySeed>>();
        
        // check if we are in the seedCaches: this can happen if someone else published our seed
        removeMySeed();
        
        this.lastSeedUpload_seedDBSize = sizeConnected();

        // tell the httpdProxy how to find this table as address resolver
        HTTPDemon.setAlternativeResolver(this);
        
        // create or init news database
        this.newsPool = new yacyNewsPool(networkRoot, useTailCache, exceed134217727);
        
        // deploy peer actions
        this.peerActions = new yacyPeerActions(this, newsPool);
    }
    
    public void relocate(
            File newNetworkRoot,
            final int redundancy,
            final int partitionExponent,
            final boolean useTailCache,
            final boolean exceed134217727) {
        
        // close old databases
        this.seedActiveDB.close();
        this.seedPassiveDB.close();
        this.seedPotentialDB.close();
        this.newsPool.close();
        this.peerActions.close();
        
        // open new according to the newNetworkRoot
        this.seedActiveDBFile = new File(newNetworkRoot, seedActiveDBFile.getName());
        this.seedPassiveDBFile = new File(newNetworkRoot, seedPassiveDBFile.getName());
        this.seedPotentialDBFile = new File(newNetworkRoot, seedPotentialDBFile.getName());

        // replace my (old) seed with new seed definition from other network
        // but keep the seed name
        String peername = this.myName();        
        this.mySeed = null; // my own seed
        this.myOwnSeedFile = new File(newNetworkRoot, yacySeedDB.DBFILE_OWN_SEED);
        initMySeed();
        this.mySeed.setName(peername);
        
        this.netRedundancy = redundancy;
        this.scheme = new VerticalWordPartitionScheme(partitionExponent);
        
        // set up seed database
        this.seedActiveDB = openSeedTable(seedActiveDBFile);
        this.seedPassiveDB = openSeedTable(seedPassiveDBFile);
        this.seedPotentialDB = openSeedTable(seedPotentialDBFile);
        
        // start our virtual DNS service for yacy peers with empty cache
        this.nameLookupCache.clear();
        
        // cache for reverse name lookup
        this.ipLookupCache.clear();
        
        // check if we are in the seedCaches: this can happen if someone else published our seed
        removeMySeed();
        
        this.lastSeedUpload_seedDBSize = sizeConnected();

        // tell the httpdProxy how to find this table as address resolver
        HTTPDemon.setAlternativeResolver(this);
        
        // create or init news database
        this.newsPool = new yacyNewsPool(newNetworkRoot, useTailCache, exceed134217727);
        
        // deploy peer actions
        this.peerActions = new yacyPeerActions(this, newsPool);
    }
    
    private synchronized void initMySeed() {
        if (this.mySeed != null) return;
        
        // create or init own seed
        if (myOwnSeedFile.length() > 0) try {
            // load existing identity
            mySeed = yacySeed.load(myOwnSeedFile);
            if (mySeed == null) throw new IOException("current seed is null");
        } catch (final IOException e) {
            // create new identity
            Log.logSevere("SEEDDB", "could not load stored mySeed.txt from " + myOwnSeedFile.toString() + ": " + e.getMessage() + ". creating new seed.", e);
            mySeed = yacySeed.genLocalSeed(this);
            try {
                mySeed.save(myOwnSeedFile);
            } catch (final IOException ee) {
                Log.logSevere("SEEDDB", "error saving mySeed.txt (1) to " + myOwnSeedFile.toString() + ": " + ee.getMessage(), ee);
                Log.logException(ee);
                System.exit(-1);
            }
        } else {
            // create new identity
            Log.logInfo("SEEDDB", "could not find stored mySeed.txt at " + myOwnSeedFile.toString() + ": " + ". creating new seed.");
            mySeed = yacySeed.genLocalSeed(this);
            try {
                mySeed.save(myOwnSeedFile);
            } catch (final IOException ee) {
                Log.logSevere("SEEDDB", "error saving mySeed.txt (2) to " + myOwnSeedFile.toString() + ": " + ee.getMessage(), ee);
                Log.logException(ee);
                System.exit(-1);
            }
        }
        this.myBotIDs.add(this.mySeed.getName() + ".yacy");
        this.myBotIDs.add(this.mySeed.hash + ".yacyh");
        mySeed.setIP("");       // we delete the old information to see what we have now
        mySeed.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN); // markup startup condition
    }

    public Set<String> myBotIDs() {
        return this.myBotIDs;
    }
    
    public int redundancy() {
        if (this.mySeed.isJunior()) return 1;
        return this.netRedundancy;
    }
    
    public boolean mySeedIsDefined() {
        return this.mySeed != null;
    }
    
    public yacySeed mySeed() {
        if (this.mySeed == null) {
            if (this.sizeConnected() == 0) try {Thread.sleep(5000);} catch (final InterruptedException e) {} // wait for init
            initMySeed();
            // check if my seed has an IP assigned
            if (this.myIP() == null || this.myIP().length() == 0) {
                this.mySeed.setIP(Domains.myPublicLocalIP().getHostAddress());
            }
        }
        return this.mySeed;
    }
    
    public void setMyName(String name) {
        this.myBotIDs.remove(this.mySeed.getName() + ".yacy");
        this.mySeed.setName(name);
        this.myBotIDs.add(name + ".yacy");
    }
    
    public String myAlternativeAddress() {
        return mySeed().getName() + ".yacy";
    }
    
    public String myIP() {
        return mySeed().getIP();
    }
    
    public int myPort() {
        return mySeed().getPort();
    }
    
    public String myName() {
        return mySeed.getName();
    }
    
    public String myID() {
        return mySeed.hash;
    }
    
    public synchronized void removeMySeed() {
        if (seedActiveDB.isEmpty() && seedPassiveDB.isEmpty() && seedPotentialDB.isEmpty()) return; // avoid that the own seed is initialized too early
        if (this.mySeed == null) initMySeed();
        try {
            seedActiveDB.delete(UTF8.getBytes(mySeed.hash));
            seedPassiveDB.delete(UTF8.getBytes(mySeed.hash));
            seedPotentialDB.delete(UTF8.getBytes(mySeed.hash));
        } catch (final IOException e) { Log.logWarning("yacySeedDB", "could not remove hash ("+ e.getClass() +"): "+ e.getMessage()); }
    }
    
    public void saveMySeed() {
        try {
          this.mySeed().save(myOwnSeedFile);
        } catch (final IOException e) { Log.logWarning("yacySeedDB", "could not save mySeed '"+ myOwnSeedFile +"': "+ e.getMessage()); }
    }
    
    public boolean noDHTActivity() {
        // for small networks, we don't perform DHT transmissions, because it is possible to search over all peers
        return this.sizeConnected() <= dhtActivityMagic;
    }
    
    private synchronized MapDataMining openSeedTable(final File seedDBFile) {
        final File parentDir = new File(seedDBFile.getParent());  
        if (!parentDir.exists()) {
			if(!parentDir.mkdirs())
				Log.logWarning("yacySeedDB", "could not create directories for "+ seedDBFile.getParent());
		}
        try {
            return new MapDataMining(seedDBFile, Word.commonHashLength, Base64Order.enhancedCoder, 1024 * 512, 500, sortFields, longaccFields, doubleaccFields, this);
        } catch (final Exception e) {
            // try again
            FileUtils.deletedelete(seedDBFile);
            try {
                return new MapDataMining(seedDBFile, Word.commonHashLength, Base64Order.enhancedCoder, 1024 * 512, 500, sortFields, longaccFields, doubleaccFields, this);
            } catch (IOException e1) {
                Log.logException(e1);
                System.exit(-1);
                return null;
            }
        }
    }
    
    private synchronized MapDataMining resetSeedTable(MapDataMining seedDB, final File seedDBFile) {
        // this is an emergency function that should only be used if any problem with the
        // seed.db is detected
        yacyCore.log.logWarning("seed-db " + seedDBFile.toString() + " reset (on-the-fly)");
        seedDB.close();
        FileUtils.deletedelete(seedDBFile);
        if (seedDBFile.exists())
        	Log.logWarning("yacySeedDB", "could not delete file "+ seedDBFile);
        // create new seed database
        seedDB = openSeedTable(seedDBFile);
        return seedDB;
    }
    
    public synchronized void resetActiveTable() { seedActiveDB = resetSeedTable(seedActiveDB, seedActiveDBFile); }
    private synchronized void resetPassiveTable() { seedPassiveDB = resetSeedTable(seedPassiveDB, seedPassiveDBFile); }
    private synchronized void resetPotentialTable() { seedPotentialDB = resetSeedTable(seedPotentialDB, seedPotentialDBFile); }
    
    public void close() {
        if (seedActiveDB != null) seedActiveDB.close();
        if (seedPassiveDB != null) seedPassiveDB.close();
        if (seedPotentialDB != null) seedPotentialDB.close();
        newsPool.close();
        peerActions.close();
    }
    
    public Iterator<yacySeed> seedsSortedConnected(final boolean up, final String field) {
        // enumerates seed-type objects: all seeds sequentially ordered by field
        return new seedEnum(up, field, seedActiveDB);
    }
    
    public Iterator<yacySeed> seedsSortedDisconnected(final boolean up, final String field) {
        // enumerates seed-type objects: all seeds sequentially ordered by field
        return new seedEnum(up, field, seedPassiveDB);
    }
    
    public Iterator<yacySeed> seedsSortedPotential(final boolean up, final String field) {
        // enumerates seed-type objects: all seeds sequentially ordered by field
        return new seedEnum(up, field, seedPotentialDB);
    }
    
    public TreeMap<byte[], String> /* peer-b64-hashes/ipport */ clusterHashes(final String clusterdefinition) {
    	// collects seeds according to cluster definition string, which consists of
    	// comma-separated .yacy or .yacyh-domains
    	// the domain may be extended by an alternative address specification of the form
    	// <ip> or <ip>:<port>. The port must be identical to the port specified in the peer seed,
    	// therefore it is optional. The address specification is separated by a '='; the complete
    	// address has therefore the form
    	// address    ::= (<peername>'.yacy'|<peerhexhash>'.yacyh'){'='<ip>{':'<port}}
    	// clusterdef ::= {address}{','address}*
    	final String[] addresses = (clusterdefinition.length() == 0) ? new String[0] : clusterdefinition.split(",");
    	final TreeMap<byte[], String> clustermap = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
    	yacySeed seed;
    	String hash, yacydom, ipport;
    	int p;
    	for (int i = 0; i < addresses.length; i++) {
    		p = addresses[i].indexOf('=');
    		if (p >= 0) {
    			yacydom = addresses[i].substring(0, p);
    			ipport  = addresses[i].substring(p + 1);
    		} else {
    			yacydom = addresses[i];
    			ipport  = null;
    		}
    		if (yacydom.endsWith(".yacyh")) {
    			// find a peer with its hexhash
    			hash = yacySeed.hexHash2b64Hash(yacydom.substring(0, yacydom.length() - 6));
    			seed = get(hash);
    			if (seed == null) {
    				yacyCore.log.logWarning("cluster peer '" + yacydom + "' was not found.");
    			} else {
    				clustermap.put(UTF8.getBytes(hash), ipport);
    			}
    		} else if (yacydom.endsWith(".yacy")) {
    			// find a peer with its name
    			seed = lookupByName(yacydom.substring(0, yacydom.length() - 5));
    			if (seed == null) {
    				yacyCore.log.logWarning("cluster peer '" + yacydom + "' was not found.");
    			} else {
    				clustermap.put(UTF8.getBytes(seed.hash), ipport);
    			}
    		} else {
    			yacyCore.log.logWarning("cluster peer '" + addresses[i] + "' has wrong syntax. the name must end with .yacy or .yacyh");
    		}
    	}
    	return clustermap;
    }
    
    public Iterator<yacySeed> seedsConnected(final boolean up, final boolean rot, final byte[] firstHash, final float minVersion) {
        // enumerates seed-type objects: all seeds sequentially without order
        return new seedEnum(up, rot, (firstHash == null) ? null : firstHash, null, seedActiveDB, minVersion);
    }
    
    private Iterator<yacySeed> seedsDisconnected(final boolean up, final boolean rot, final byte[] firstHash, final float minVersion) {
        // enumerates seed-type objects: all seeds sequentially without order
        return new seedEnum(up, rot, (firstHash == null) ? null : firstHash, null, seedPassiveDB, minVersion);
    }
    
    private Iterator<yacySeed> seedsPotential(final boolean up, final boolean rot, final byte[] firstHash, final float minVersion) {
        // enumerates seed-type objects: all seeds sequentially without order
        return new seedEnum(up, rot, (firstHash == null) ? null : firstHash, null, seedPotentialDB, minVersion);
    }
    
    public yacySeed anySeedVersion(final float minVersion) {
        // return just any seed that has a specific minimum version number
        final Iterator<yacySeed> e = seedsConnected(true, true, yacySeed.randomHash(), minVersion);
        return e.next();
    }

    /**
     * count the number of peers that had been seed within the time limit
     * @param limit the time limit in minutes. 1440 minutes is a day
     * @return the number of peers seen in the given time
     */
    public int sizeActiveSince(long limit) {
        int c = seedActiveDB.size();
        yacySeed seed;
        Iterator<yacySeed> i = seedsSortedDisconnected(false, yacySeed.LASTSEEN);
        while (i.hasNext()) {
            seed = i.next();
            if (seed != null) {
                if (Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60) > limit) break;
                c++;
            }
        }
        i = seedsSortedPotential(false, yacySeed.LASTSEEN);
        while (i.hasNext()) {
            seed = i.next();
            if (seed != null) {
                if (Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60) > limit) break;
                c++;
            }
        }
        return c;
    }
    
     public int sizeConnected() {
        return seedActiveDB.size();
    }
    
    public int sizeDisconnected() {
        return seedPassiveDB.size();
    }
    
    public int sizePotential() {
        return seedPotentialDB.size();
    }
    
    public long countActiveURL() { return seedActiveDB.getLongAcc(yacySeed.LCOUNT); }
    public long countActiveRWI() { return seedActiveDB.getLongAcc(yacySeed.ICOUNT); }
    public long countActivePPM() { return seedActiveDB.getLongAcc(yacySeed.ISPEED); }
    public float countActiveQPM() { return seedActiveDB.getFloatAcc(yacySeed.RSPEED); }
    public long countPassiveURL() { return seedPassiveDB.getLongAcc(yacySeed.LCOUNT); }
    public long countPassiveRWI() { return seedPassiveDB.getLongAcc(yacySeed.ICOUNT); }
    public long countPotentialURL() { return seedPotentialDB.getLongAcc(yacySeed.LCOUNT); }
    public long countPotentialRWI() { return seedPotentialDB.getLongAcc(yacySeed.ICOUNT); }

    public synchronized void addConnected(final yacySeed seed) {
        if (seed.isProper(false) != null) return;
        //seed.put(yacySeed.LASTSEEN, yacyCore.shortFormatter.format(new Date(yacyCore.universalTime())));
        try {
            nameLookupCache.put(seed.getName(), seed.hash);
            final Map<String, String> seedPropMap = seed.getMap();
            synchronized (seedPropMap) {
                seedActiveDB.insert(UTF8.getBytes(seed.hash), seedPropMap);
            }
            seedPassiveDB.delete(UTF8.getBytes(seed.hash));
            seedPotentialDB.delete(UTF8.getBytes(seed.hash));
        } catch (final Exception e) {
            yacyCore.log.logSevere("ERROR add: seed.db corrupt (" + e.getMessage() + "); resetting seed.db", e);
            resetActiveTable();
        }
    }

    protected synchronized void addDisconnected(final yacySeed seed) {
        if (seed.isProper(false) != null) return;
        try {
            nameLookupCache.remove(seed.getName());
            seedActiveDB.delete(UTF8.getBytes(seed.hash));
            seedPotentialDB.delete(UTF8.getBytes(seed.hash));
        } catch (final Exception e) { Log.logWarning("yacySeedDB", "could not remove hash ("+ e.getClass() +"): "+ e.getMessage()); }
        //seed.put(yacySeed.LASTSEEN, yacyCore.shortFormatter.format(new Date(yacyCore.universalTime())));
        try {
            final Map<String, String> seedPropMap = seed.getMap();
            synchronized (seedPropMap) {
                seedPassiveDB.insert(UTF8.getBytes(seed.hash), seedPropMap);
            }
        } catch (final Exception e) {
            yacyCore.log.logSevere("ERROR add: seed.db corrupt (" + e.getMessage() + "); resetting seed.db", e);
            resetPassiveTable();
        }
    }

    protected synchronized void addPotential(final yacySeed seed) {
        if (seed.isProper(false) != null) return;
        try {
            nameLookupCache.remove(seed.getName());
            seedActiveDB.delete(UTF8.getBytes(seed.hash));
            seedPassiveDB.delete(UTF8.getBytes(seed.hash));
        } catch (final Exception e) { Log.logWarning("yacySeedDB", "could not remove hash ("+ e.getClass() +"): "+ e.getMessage()); }
        //seed.put(yacySeed.LASTSEEN, yacyCore.shortFormatter.format(new Date(yacyCore.universalTime())));
        try {
            final Map<String, String> seedPropMap = seed.getMap();
            synchronized (seedPropMap) {
                seedPotentialDB.insert(UTF8.getBytes(seed.hash), seedPropMap);
            }
        } catch (final Exception e) {
            yacyCore.log.logSevere("ERROR add: seed.db corrupt (" + e.getMessage() + "); resetting seed.db", e);
            resetPotentialTable();
        }
    }

    public synchronized void removeDisconnected(final String peerHash) {
    	if(peerHash == null) return;
    	try {
			seedPassiveDB.delete(UTF8.getBytes(peerHash));
		} catch (final IOException e) { Log.logWarning("yacySeedDB", "could not remove hash ("+ e.getClass() +"): "+ e.getMessage()); }
    }
    
    public synchronized void removePotential(final String peerHash) {
    	if(peerHash == null) return;
    	try {
			seedPotentialDB.delete(UTF8.getBytes(peerHash));
		} catch (final IOException e) { Log.logWarning("yacySeedDB", "could not remove hash ("+ e.getClass() +"): "+ e.getMessage()); }
    }
        
    public boolean hasConnected(final byte[] hash) {
        return seedActiveDB.containsKey(hash);
    }

    public boolean hasDisconnected(final byte[] hash) {
        return seedPassiveDB.containsKey(hash);
    }
 
    public boolean hasPotential(final byte[] hash) {
        return seedPotentialDB.containsKey(hash);
    }
        
    private yacySeed get(final String hash, final MapDataMining database) {
        if (hash == null) return null;
        if ((this.mySeed != null) && (hash.equals(mySeed.hash))) return mySeed;
        ConcurrentHashMap<String, String> entry = new ConcurrentHashMap<String, String>();
        try {
            Map<String, String> map = database.get(UTF8.getBytes(hash));
            if (map == null) return null;
            entry.putAll(map);
        } catch (final IOException e) {
            Log.logException(e);
            return null;
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            return null;
        }
        return new yacySeed(hash, entry);
    }
    
    public yacySeed getConnected(final String hash) {
        return get(hash, seedActiveDB);
    }

    public yacySeed getDisconnected(final String hash) {
        return get(hash, seedPassiveDB);
    }
        
    public yacySeed getPotential(final String hash) {
        return get(hash, seedPotentialDB);
    }
    
    public yacySeed get(final String hash) {
        yacySeed seed = getConnected(hash);
        if (seed == null) seed = getDisconnected(hash);
        if (seed == null) seed = getPotential(hash);
        return seed;
    }
    
    public void update(final String hash, final yacySeed seed) {
        if (this.mySeed == null) initMySeed();
        if (hash.equals(mySeed.hash)) {
            mySeed = seed;
            return;
        }
        
        yacySeed s = get(hash, seedActiveDB);
        if (s != null) try { seedActiveDB.insert(UTF8.getBytes(hash), seed.getMap()); return;} catch (final Exception e) {Log.logException(e);}
        
        s = get(hash, seedPassiveDB);
        if (s != null) try { seedPassiveDB.insert(UTF8.getBytes(hash), seed.getMap()); return;} catch (final Exception e) {Log.logException(e);}
        
        s = get(hash, seedPotentialDB);
        if (s != null) try { seedPotentialDB.insert(UTF8.getBytes(hash), seed.getMap()); return;} catch (final Exception e) {Log.logException(e);}
    }
    
    public yacySeed lookupByName(String peerName) {
        // reads a seed by searching by name
        if (peerName.endsWith(".yacy")) peerName = peerName.substring(0, peerName.length() - 5);

        // local peer?
        if (peerName.equals("localpeer")) {
            if (this.mySeed == null) initMySeed();
            return mySeed;
        }
        
        // then try to use the cache
        String seedhash = nameLookupCache.get(peerName);
        yacySeed seed;
        if (seedhash != null) {
        	seed = this.get(seedhash);
        	if (seed != null) return seed;
        }
        
        // enumerate the cache and simultanous insert values
        String name;
    	for (int table = 0; table < 2; table++) {
            final Iterator<yacySeed> e = (table == 0) ? seedsConnected(true, false, null, (float) 0.0) : seedsDisconnected(true, false, null, (float) 0.0);
        	while (e.hasNext()) {
        		seed = e.next();
        		if (seed != null) {
        			name = seed.getName().toLowerCase();
        			if (seed.isProper(false) == null) nameLookupCache.put(name, seed.hash);
        			if (name.equals(peerName)) return seed;
        		}
        	}
        }
        // check local seed
        if (this.mySeed == null) initMySeed();
        name = mySeed.getName().toLowerCase();
        if (mySeed.isProper(false) == null) nameLookupCache.put(name, mySeed.hash);
        if (name.equals(peerName)) return mySeed;
        // nothing found
        return null;
    }
    
    public yacySeed lookupByIP(
            final InetAddress peerIP, 
            final boolean lookupConnected, 
            final boolean lookupDisconnected,
            final boolean lookupPotential
    ) {
        
        if (peerIP == null) return null;
        yacySeed seed = null;        
        
        // local peer?
        if (Domains.isThisHostIP(peerIP)) {
            if (this.mySeed == null) initMySeed();
            return mySeed;
        }
        
        // then try to use the cache
        final SoftReference<yacySeed> ref = ipLookupCache.get(peerIP);
        if (ref != null) {        
            seed = ref.get();
            if (seed != null) return seed;
        }

        int pos = -1;
        String addressStr = null;
        InetAddress seedIPAddress = null;        
        final HandleSet badPeerHashes = new HandleSet(12, Base64Order.enhancedCoder, 0);
        
        if (lookupConnected) {
            // enumerate the cache and simultanous insert values
            final Iterator<yacySeed> e = seedsConnected(true, false, null, (float) 0.0);
            while (e.hasNext()) {
                seed = e.next();
                if (seed != null) {
                    addressStr = seed.getPublicAddress();
                    if (addressStr == null) {
                    	Log.logWarning("YACY","lookupByIP/Connected: address of seed " + seed.getName() + "/" + seed.hash + " is null.");
                    	try {
                            badPeerHashes.put(UTF8.getBytes(seed.hash));
                        } catch (RowSpaceExceededException e1) {
                            Log.logException(e1);
                            break;
                        }
                    	continue; 
                    }
                    if ((pos = addressStr.indexOf(':'))!= -1) {
                        addressStr = addressStr.substring(0,pos);
                    }
                    seedIPAddress = Domains.dnsResolve(addressStr);
                    if (seedIPAddress == null) continue;
                    if (seed.isProper(false) == null) ipLookupCache.put(seedIPAddress, new SoftReference<yacySeed>(seed));
                    if (seedIPAddress.equals(peerIP)) return seed;
                }
            }
            // delete bad peers
            final Iterator<byte[]> i = badPeerHashes.iterator();
            while (i.hasNext()) try {seedActiveDB.delete(i.next());} catch (final IOException e1) {Log.logException(e1);}
            badPeerHashes.clear();
        }
        
        if (lookupDisconnected) {
            // enumerate the cache and simultanous insert values
            final Iterator<yacySeed>e = seedsDisconnected(true, false, null, (float) 0.0);

            while (e.hasNext()) {
                seed = e.next();
                if (seed != null) {
                    addressStr = seed.getPublicAddress();
                    if (addressStr == null) {
                        Log.logWarning("YACY","lookupByIPDisconnected: address of seed " + seed.getName() + "/" + seed.hash + " is null.");
                        try {
                            badPeerHashes.put(UTF8.getBytes(seed.hash));
                        } catch (RowSpaceExceededException e1) {
                            Log.logException(e1);
                            break;
                        }
                        continue;
                    }
                    if ((pos = addressStr.indexOf(':'))!= -1) {
                        addressStr = addressStr.substring(0,pos);
                    }
                    seedIPAddress = Domains.dnsResolve(addressStr);
                    if (seedIPAddress == null) continue;
                    if (seed.isProper(false) == null) ipLookupCache.put(seedIPAddress, new SoftReference<yacySeed>(seed));
                    if (seedIPAddress.equals(peerIP)) return seed;
                }
            }
            // delete bad peers
            final Iterator<byte[]> i = badPeerHashes.iterator();
            while (i.hasNext()) try {seedActiveDB.delete(i.next());} catch (final IOException e1) {Log.logException(e1);}
            badPeerHashes.clear();
        }
        
        if (lookupPotential) {
            // enumerate the cache and simultanous insert values
            final Iterator<yacySeed> e = seedsPotential(true, false, null, (float) 0.0);

            while (e.hasNext()) {
                seed = e.next();
                if ((seed != null) && ((addressStr = seed.getPublicAddress()) != null)) {
                    if ((pos = addressStr.indexOf(':'))!= -1) {
                        addressStr = addressStr.substring(0,pos);
                    }
                    seedIPAddress = Domains.dnsResolve(addressStr);
                    if (seedIPAddress == null) continue;
                    if (seed.isProper(false) == null) ipLookupCache.put(seedIPAddress, new SoftReference<yacySeed>(seed));
                    if (seedIPAddress.equals(peerIP)) return seed;
                }
            }
        }
        
        // check local seed
        if (this.mySeed == null) return null;
        addressStr = mySeed.getPublicAddress();
        if (addressStr == null) return null;
        if ((pos = addressStr.indexOf(':'))!= -1) {
            addressStr = addressStr.substring(0,pos);
        }
        seedIPAddress = Domains.dnsResolve(addressStr);
        if (seedIPAddress == null) return null;
        if (mySeed.isProper(false) == null) ipLookupCache.put(seedIPAddress,  new SoftReference<yacySeed>(mySeed));
        if (seedIPAddress.equals(peerIP)) return mySeed;
        // nothing found
        return null;
    }

    private ArrayList<String> storeSeedList(final File seedFile, final boolean addMySeed) throws IOException {
        PrintWriter pw = null;
        final ArrayList<String> v = new ArrayList<String>(seedActiveDB.size() + 1);
        try {
            
            pw = new PrintWriter(new BufferedWriter(new FileWriter(seedFile)));
            
            // store own peer seed
            String line;
            if (this.mySeed == null) initMySeed();
            if (addMySeed) {
                line = mySeed.genSeedStr(null);
                v.add(line);
                pw.print(line + serverCore.CRLF_STRING);
            }
            
            // store active peer seeds
            yacySeed ys;
            Iterator<yacySeed> se = seedsConnected(true, false, null, (float) 0.0);
            while (se.hasNext()) {
                ys = se.next();
                if (ys != null) {
                    line = ys.genSeedStr(null);
                    v.add(line);
                    pw.print(line + serverCore.CRLF_STRING);
                }
            }
            
            // store some of the not-so-old passive peer seeds (limit: 1 day)
            se = seedsDisconnected(true, false, null, (float) 0.0);
            long timeout = System.currentTimeMillis() - (1000L * 60L * 60L * 24L);
            while (se.hasNext()) {
                ys = se.next();
                if (ys != null) {
                    if (ys.getLastSeenUTC() < timeout) continue; 
                    line = ys.genSeedStr(null);
                    v.add(line);
                    pw.print(line + serverCore.CRLF_STRING);
                }
            }
            pw.flush();
        } finally {
            if (pw != null) try { pw.close(); } catch (final Exception e) {}
        }
        return v;
    }

    protected String uploadSeedList(final yacySeedUploader uploader, 
            final serverSwitch sb,
            final yacySeedDB seedDB,
            final DigestURI seedURL) throws Exception {
        
        // upload a seed file, if possible
        if (seedURL == null) throw new NullPointerException("UPLOAD - Error: URL not given");
        
        String log = null; 
        File seedFile = null;
        try {            
            // create a seed file which for uploading ...    
            seedFile = File.createTempFile("seedFile",".txt", seedDB.myOwnSeedFile.getParentFile());
            seedFile.deleteOnExit();
            if (Log.isFine("YACY")) Log.logFine("YACY", "SaveSeedList: Storing seedlist into tempfile " + seedFile.toString());
            final ArrayList<String> uv = storeSeedList(seedFile, true);            
            
            // uploading the seed file
            if (Log.isFine("YACY")) Log.logFine("YACY", "SaveSeedList: Trying to upload seed-file, " + seedFile.length() + " bytes, " + uv.size() + " entries.");
            log = uploader.uploadSeedFile(sb, seedFile);
            
            // test download
            if (Log.isFine("YACY")) Log.logFine("YACY", "SaveSeedList: Trying to download seed-file '" + seedURL + "'.");
            final Iterator<String> check = downloadSeedFile(seedURL);
            
            // Comparing if local copy and uploaded copy are equal
            final String errorMsg = checkCache(uv, check);
            if (errorMsg == null)
                log = log + "UPLOAD CHECK - Success: the result vectors are equal" + serverCore.CRLF_STRING;
            else {
                throw new Exception("UPLOAD CHECK - Error: the result vector is different. " + errorMsg + serverCore.CRLF_STRING);
            }
        } finally {
            if (seedFile != null)
				try {
				    FileUtils.deletedelete(seedFile);
				} catch (final Exception e) {
					/* ignore this */
				}
        }
        
        return log;
    }
    
    private Iterator<String> downloadSeedFile(final DigestURI seedURL) throws IOException {
        // Configure http headers
        final RequestHeader reqHeader = new RequestHeader();
        reqHeader.put(HeaderFramework.PRAGMA, "no-cache");
        reqHeader.put(HeaderFramework.CACHE_CONTROL, "no-cache"); // httpc uses HTTP/1.0 is this necessary?
        reqHeader.put(HeaderFramework.USER_AGENT, ClientIdentification.getUserAgent());
        
        final HTTPClient client = new HTTPClient();
        client.setHeader(reqHeader.entrySet());
        byte[] content = null;
        try {
            // send request
        	content = client.GETbytes(seedURL);
        } catch (final Exception e) {
        	throw new IOException("Unable to download seed file '" + seedURL + "'. " + e.getMessage());
        }
            
        // check response code
        if (client.getHttpResponse().getStatusLine().getStatusCode() != 200) {
        	throw new IOException("Server returned status: " + client.getHttpResponse().getStatusLine());
        }
            
        try {
            // uncompress it if it is gzipped
            content = FileUtils.uncompressGZipArray(content);

            // convert it into an array
            return FileUtils.strings(content);
        } catch (final Exception e) {
        	throw new IOException("Unable to uncompress seed file '" + seedURL + "'. " + e.getMessage());
        }
    }

    private String checkCache(final ArrayList<String> uv, final Iterator<String> check) {                
        if ((check == null) || (uv == null)) {
            if (Log.isFine("YACY")) Log.logFine("YACY", "SaveSeedList: Local and uploades seed-list are different");
            return "Entry count is different: uv.size() = " + ((uv == null) ? "null" : Integer.toString(uv.size()));
        }
        	
        if (Log.isFine("YACY")) Log.logFine("YACY", "SaveSeedList: Comparing local and uploades seed-list entries ...");
        int i = 0;
        while (check.hasNext() && i < uv.size()) {
        	if (!((uv.get(i)).equals(check.next()))) return "Element at position " + i + " is different.";
        	i++;
        }
        
        // no difference found
        return null;
    }

    /**
     * resolve a yacy address
     */
    public String resolve(String host) {
        yacySeed seed;
        int p;
        String subdom = null;
        if (host.endsWith(".yacyh")) {
            // this is not functional at the moment
            // caused by lowecasing of hashes at the browser client
            p = host.indexOf('.');
            if ((p > 0) && (p != (host.length() - 6))) {
                subdom = host.substring(0, p);
                host = host.substring(p + 1);
            }
            // check if we have a b64-hash or a hex-hash
            String hash = host.substring(0, host.length() - 6);
            if (hash.length() > Word.commonHashLength) {
                // this is probably a hex-hash
                hash = yacySeed.hexHash2b64Hash(hash);
            }
            // check remote seeds
            seed = getConnected(hash); // checks only remote, not local
            // check local seed
            if (seed == null) {
                if (this.mySeed == null) initMySeed();
                if (hash.equals(mySeed.hash))
                    seed = mySeed;
                else return null;
            }
            return seed.getPublicAddress() + ((subdom == null) ? "" : ("/" + subdom));
        } else if (host.endsWith(".yacy")) {
            // identify subdomain
            p = host.indexOf('.');
            if ((p > 0) && (p != (host.length() - 5))) {
                subdom = host.substring(0, p); // no double-dot attack possible, the subdom cannot have ".." in it
                host = host.substring(p + 1); // if ever, the double-dots are here but do not harm
            }
            // identify domain
            final String domain = host.substring(0, host.length() - 5).toLowerCase();
            seed = lookupByName(domain);
            if (seed == null) return null;
            if (this.mySeed == null) initMySeed();
            if ((seed == mySeed) && (!(seed.isOnline()))) {
                // take local ip instead of external
                return Switchboard.getSwitchboard().myPublicIP() + ":" + Switchboard.getSwitchboard().getConfig("port", "8090") + ((subdom == null) ? "" : ("/" + subdom));
            }
            return seed.getPublicAddress() + ((subdom == null) ? "" : ("/" + subdom));
        } else {
            return null;
        }
    }

    private class seedEnum implements Iterator<yacySeed> {
        
        private MapDataMining.mapIterator it;
        private yacySeed nextSeed;
        private MapDataMining database;
        private float minVersion;
        
        private seedEnum(final boolean up, final boolean rot, final byte[] firstKey, final byte[] secondKey, final MapDataMining database, final float minVersion) {
            this.database = database;
            this.minVersion = minVersion;
            try {
                it = (firstKey == null) ? database.maps(up, rot) : database.maps(up, rot, firstKey, secondKey);
                float version;
                while (true) {
                    nextSeed = internalNext();
                    if (nextSeed == null) break;
                    version = nextSeed.getVersion();
                    if (version >= this.minVersion || version == 0.0) break; // include 0.0 to access always developer peers
                }
            } catch (final IOException e) {
                Log.logException(e);
                yacyCore.log.logSevere("ERROR seedLinEnum: seed.db corrupt (" + e.getMessage() + "); resetting seed.db", e);
                if (database == seedActiveDB) seedActiveDB = resetSeedTable(seedActiveDB, seedActiveDBFile);
                if (database == seedPassiveDB) seedPassiveDB = resetSeedTable(seedPassiveDB, seedPassiveDBFile);
                it = null;
            } catch (final kelondroException e) {
                Log.logException(e);
                yacyCore.log.logSevere("ERROR seedLinEnum: seed.db corrupt (" + e.getMessage() + "); resetting seed.db", e);
                if (database == seedActiveDB) seedActiveDB = resetSeedTable(seedActiveDB, seedActiveDBFile);
                if (database == seedPassiveDB) seedPassiveDB = resetSeedTable(seedPassiveDB, seedPassiveDBFile);
                it = null;
            }
        }
        
        private seedEnum(final boolean up, final String field, final MapDataMining database) {
            this.database = database;
            try {
                it = database.maps(up, field);
                nextSeed = internalNext();
            } catch (final kelondroException e) {
                Log.logException(e);
                yacyCore.log.logSevere("ERROR seedLinEnum: seed.db corrupt (" + e.getMessage() + "); resetting seed.db", e);
                if (database == seedActiveDB) seedActiveDB = resetSeedTable(seedActiveDB, seedActiveDBFile);
                if (database == seedPassiveDB) seedPassiveDB = resetSeedTable(seedPassiveDB, seedPassiveDBFile);
                if (database == seedPotentialDB) seedPotentialDB = resetSeedTable(seedPotentialDB, seedPotentialDBFile);
                it = null;
            }
        }
        
        public boolean hasNext() {
            return (nextSeed != null);
        }
        
        private yacySeed internalNext() {
            if (it == null || !(it.hasNext())) return null;
            try {
                Map<String, String> dna0;
                ConcurrentHashMap<String, String> dna;
                while (it.hasNext()) {
                    try {
                        dna0 = it.next();
                    } catch (OutOfMemoryError e) {
                        Log.logException(e);
                        dna0 = null;
                    }
                    assert dna0 != null;
                    if (dna0 == null) continue;
                    if (dna0 instanceof ConcurrentHashMap) {
                        dna = (ConcurrentHashMap<String, String>) dna0;
                    } else {
                        dna = new ConcurrentHashMap<String, String>();
                        dna.putAll(dna0);
                    }
                    final String hash = dna.remove("key");
                    //assert hash != null;
                    if (hash == null) continue; // bad seed
                    return new yacySeed(hash, dna);
                }
                return null;
            } catch (final Exception e) {
                Log.logException(e);
                yacyCore.log.logSevere("ERROR internalNext: seed.db corrupt (" + e.getMessage() + "); resetting seed.db", e);
                if (database == seedActiveDB) seedActiveDB = resetSeedTable(seedActiveDB, seedActiveDBFile);
                if (database == seedPassiveDB) seedPassiveDB = resetSeedTable(seedPassiveDB, seedPassiveDBFile);
                if (database == seedPotentialDB) seedPotentialDB = resetSeedTable(seedPotentialDB, seedPotentialDBFile);
                return null;
            }
        }
        
        public yacySeed next() {
            final yacySeed seed = nextSeed;
            float version;
            try {while (true) {
                nextSeed = internalNext();
                if (nextSeed == null) break;
                version = nextSeed.getVersion();
                if (version >= this.minVersion || version == 0.0) break; // include 0.0 to access always developer peers
            }} catch (final kelondroException e) {
                Log.logException(e);
            	// emergency reset
            	yacyCore.log.logSevere("seed-db emergency reset", e);
            	database.clear();
				nextSeed = null;
				return null;
            }
            return seed;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }

}
