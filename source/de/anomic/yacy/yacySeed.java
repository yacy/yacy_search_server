// yacySeed.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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
//
//
// YACY stands for Yet Another CYberspace
//
// the yacySeed Object is the object that bundles and carries all information about
// a single peer in the yacy space.
// The yacySeed object is carried along peers using a string representation, that can
// be compressed and/or scrambled, depending on the purpose of the process.
//
// the yacy status
// any value that is defined here will be overwritten each time the proxy is started
// to prevent that the system gets confused, it should be set to "" which means
// undefined. Other status' that can be reached at run-time are
// junior    - a peer that has no public socket, thus cannot be reached on demand
// senior    - a peer that has a public socked and serves search queries
// principal - a peer like a senior socket and serves as gateway for network definition

package de.anomic.yacy;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.index.indexWord;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.net.natLib;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDate;
import de.anomic.server.serverDomains;
import de.anomic.server.serverSystem;
import de.anomic.tools.bitfield;
import de.anomic.tools.crypt;

public class yacySeed implements Cloneable {

    public static final int partitionExponent = 1;
    public static final int maxsize = 4096;
    /**
     * <b>substance</b> "sI" (send index/words)
     */
    public static final String INDEX_OUT = "sI";
    /**
     * <b>substance</b> "rI" (received index/words)
     */
    public static final String INDEX_IN  = "rI";
    /**
     * <b>substance</b> "sU" (send URLs)
     */
    public static final String URL_OUT = "sU";
    /**
     * <b>substance</b> "rU" (received URLs)
     */
    public static final String URL_IN  = "rU";
    /**
     * <b>substance</b> "virgin"
     */
    public static final String PEERTYPE_VIRGIN = "virgin";
    /**
     * <b>substance</b> "junior"
     */
    public static final String PEERTYPE_JUNIOR = "junior";
    /**
     * <b>substance</b> "senior"
     */
    public static final String PEERTYPE_SENIOR = "senior";
    /**
     * <b>substance</b> "principal"
     */
    public static final String PEERTYPE_PRINCIPAL = "principal";
    /**
     * <b>substance</b> "PeerType"
     */
    public static final String PEERTYPE = "PeerType";

    /** static/dynamic (if the IP changes often for any reason) */
    public static final String IPTYPE    = "IPType";
    public static final String FLAGS     = "Flags";
    public static final String FLAGSZERO = "____";
    /** the applications version */
    public static final String VERSION   = "Version";

    public static final String YOURTYPE  = "yourtype";
    public static final String LASTSEEN  = "LastSeen";
    public static final String USPEED    = "USpeed";

    /** the name of the peer (user-set) */
    public static final String NAME      = "Name";
    public static final String HASH      = "Hash";
    /** Birthday - first startup */
    public static final String BDATE     = "BDate";
    /** UTC-Offset */
    public static final String UTC       = "UTC";
    public static final String PEERTAGS  = "Tags";

    /** the speed of indexing (pages/minute) of the peer */
    public static final String ISPEED    = "ISpeed";
    /** the speed of retrieval (queries/minute) of the peer */
    public static final String RSPEED    = "RSpeed";
    /** the number of minutes that the peer is up in minutes/day (moving average MA30) */
    public static final String UPTIME    = "Uptime";
    /** the number of links that the peer has stored (LURL's) */
    public static final String LCOUNT    = "LCount";
    /** the number of links that the peer has noticed, but not loaded (NURL's) */
    public static final String NCOUNT    = "NCount";
    /** the number of links that the peer provides for remote crawls (ZURL's) */
    public static final String RCOUNT    = "RCount";
    /** the number of different words the peer has indexed */
    public static final String ICOUNT    = "ICount";
    /** the number of seeds that the peer has stored */
    public static final String SCOUNT    = "SCount";
    /** the number of clients that the peer connects (connects/hour as double) */
    public static final String CCOUNT    = "CCount";
    /** Citation Rank (Own) - Count */
    public static final String CRWCNT    = "CRWCnt";
    /** Citation Rank (Other) - Count */
    public static final String CRTCNT    = "CRTCnt";
    public static final String IP        = "IP";
    public static final String PORT      = "Port";
    public static final String SEEDLIST  = "seedURL";
    /** zero-value */
    public static final String ZERO      = "0";
    
    public static final String DFLT_NETWORK_UNIT = "freeworld";
    public static final String DFLT_NETWORK_GROUP = "";

    private static final Random random = new Random(System.currentTimeMillis());
    
    // class variables
    /** the peer-hash */
    public String hash;
    /** a set of identity founding values, eg. IP, name of the peer, YaCy-version, ...*/
    private final Map<String, String> dna;
    public int available;
    public int selectscore = -1; // only for debugging
    public String alternativeIP = null;

    public yacySeed(final String theHash, final Map<String, String> theDna) {
        // create a seed with a pre-defined hash map
        this.hash = theHash;
        this.dna = theDna;
        final String flags = this.dna.get(yacySeed.FLAGS);
        if ((flags == null) || (flags.length() != 4)) { this.dna.put(yacySeed.FLAGS, yacySeed.FLAGSZERO); }
        this.available = 0;
        this.dna.put(yacySeed.NAME, checkPeerName(get(yacySeed.NAME, "&empty;")));
    }

    public yacySeed(final String theHash) {
        this.dna = new HashMap<String, String>();

        // settings that can only be computed by originating peer:
        // at first startup -
        this.hash = theHash; // the hash key of the peer - very important. should be static somehow, even after restart
        this.dna.put(yacySeed.NAME, "&empty;");
        this.dna.put(yacySeed.BDATE, "&empty;");
        this.dna.put(yacySeed.UTC, "+0000");
        // later during operation -
        this.dna.put(yacySeed.ISPEED, yacySeed.ZERO);
        this.dna.put(yacySeed.RSPEED, yacySeed.ZERO);
        this.dna.put(yacySeed.UPTIME, yacySeed.ZERO);
        this.dna.put(yacySeed.LCOUNT, yacySeed.ZERO);
        this.dna.put(yacySeed.NCOUNT, yacySeed.ZERO);
        this.dna.put(yacySeed.RCOUNT, yacySeed.ZERO);
        this.dna.put(yacySeed.ICOUNT, yacySeed.ZERO);
        this.dna.put(yacySeed.SCOUNT, yacySeed.ZERO);
        this.dna.put(yacySeed.CCOUNT, yacySeed.ZERO);
        this.dna.put(yacySeed.VERSION, yacySeed.ZERO);

        // settings that is created during the 'hello' phase - in first contact
        this.dna.put(yacySeed.IP, "");                 // 123.234.345.456
        this.dna.put(yacySeed.PORT, "&empty;");
        this.dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN); // virgin/junior/senior/principal
        this.dna.put(yacySeed.IPTYPE, "&empty;");

        // settings that can only be computed by visiting peer
        this.dna.put(yacySeed.LASTSEEN, serverDate.formatShortSecond(new Date(System.currentTimeMillis() - serverDate.UTCDiff()))); // for last-seen date
        this.dna.put(yacySeed.USPEED, yacySeed.ZERO);  // the computated uplink speed of the peer

        this.dna.put(yacySeed.CRWCNT, yacySeed.ZERO);
        this.dna.put(yacySeed.CRTCNT, yacySeed.ZERO);

        // settings that are needed to organize the seed round-trip
        this.dna.put(yacySeed.FLAGS, yacySeed.FLAGSZERO);
        setFlagDirectConnect(false);
        setFlagAcceptRemoteCrawl(true);
        setFlagAcceptRemoteIndex(true);
        setFlagAcceptCitationReference(true);
        setUnusedFlags();

        // index transfer
        this.dna.put(yacySeed.INDEX_OUT, yacySeed.ZERO); // send index
        this.dna.put(yacySeed.INDEX_IN, yacySeed.ZERO);  // received index
        this.dna.put(yacySeed.URL_OUT, yacySeed.ZERO);   // send URLs
        this.dna.put(yacySeed.URL_IN, yacySeed.ZERO);    // received URLs

        this.available = 0;
    }

    /**
     * Generate a default peer name assembled of the following fragments in order:
     * <ul>
     *   <li>the public IP (may be an IPv4- or IPv6-IP) obtained by {@link serverCore#myPublicIP()} followed by a minus sign (<code>-</code>)</li>
     *   <li>a pseudo-random value, the {@link yacyCore#speedKey}</li>
     *   <li>the string '<code>dpn</code>' which assumingly stands for Default Peer Name</li>
     *   <li>shortened OS information, the {@link serverSystem#infoKey()}</li>
     *   <li>another pseudo-random value derived from the {@link System#currentTimeMillis()}-method modulo 99</li>
     * </ul> 
     * @return a default peer name following the above pattern whereas dots, underscores and colons are replaced by minus signs
     */
    public static String makeDefaultPeerName() {
        String name = serverDomains.myPublicIP() + "-" + yacyCore.speedKey  + "dpn" + serverSystem.infoKey() + (System.currentTimeMillis() & 99);
        name = name.replace('.', '-');
        name = name.replace('_', '-');
        name = name.replace(':', '-');
        return name;
    }
    
    /**
     * check the peer name: protect against usage as XSS hack
     * @param name
     * @return a checked name without "<" and ">"
     */
    private static String checkPeerName(String name) {
        name = name.replaceAll("<", "_");
        name = name.replaceAll(">", "_");
        return name;
    }
    
    /**
     * Checks for the static fragments of a generated default peer name, such as the string 'dpn'
     * @see #makeDefaultPeerName()
     * @param name the peer name to check for default peer name compliance
     * @return whether the given peer name may be a default generated peer name
     */
    public static boolean isDefaultPeerName(final String name) {
        return (name != null &&
                name.length() > 10 &&
                name.charAt(0) <= '9' &&
                name.charAt(name.length() - 1) <= '9' &&
                name.indexOf("dpn") > 0);
    }
    
    /**
     * used when doing routing within a cluster; this can assign a ip and a port
     * that is used instead the address stored in the seed DNA
     */
    public void setAlternativeAddress(final String ipport) {
        if (ipport == null) return;
        final int p = ipport.indexOf(':');
        if (p < 0) this.alternativeIP = ipport; else this.alternativeIP = ipport.substring(0, p);
    }

    /**
     * try to get the IP<br>
     * @return the IP or null
     */
    public final String getIP() { return get(yacySeed.IP, ""); }
    /**
     * try to get the peertype<br>
     * @return the peertype or null
     */
    public final String getPeerType() { return get(yacySeed.PEERTYPE, ""); }
    /**
     * try to get the peertype<br>
     * @return the peertype or "virgin"
     */
    public final String orVirgin() { return get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN); }
    /**
     * try to get the peertype<br>
     * @return the peertype or "junior"
     */
    public final String orJunior() { return get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR); }
    /**
     * try to get the peertype<br>
     * @return the peertype or "senior"
     */
    public final String orSenior() { return get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR); }
    /**
     * try to get the peertype<br>
     * @return the peertype or "principal"
     */
    public final String orPrincipal() { return get(yacySeed.PEERTYPE, yacySeed.PEERTYPE_PRINCIPAL); }

    /**
     * Get a value from the peer's DNA (its set of peer defining values, e.g. IP, name, version, ...)
     * @param key the key for the value to fetch
     * @param dflt the default value
     */
    public final String get(final String key, final String dflt) {
        final Object o = this.dna.get(key);
        if (o == null) { return dflt; }
        return (String) o;
    }
    
    public final long getLong(final String key, final long dflt) {
        final Object o = this.dna.get(key);
        if (o == null) { return dflt; }
        if (o instanceof String) try {
        	return Long.parseLong((String) o);
        } catch (final NumberFormatException e) {
        	return dflt;
        } else if (o instanceof Long) {
            return ((Long) o).longValue();
        } else if (o instanceof Integer) {
            return ((Integer) o).intValue();
        } else return dflt;
    }

    public final void setIP(final String ip)     { dna.put(yacySeed.IP, ip); }
    public final void setPort(final String port) { dna.put(yacySeed.PORT, port); }
    public final void setType(final String type) { dna.put(yacySeed.PEERTYPE, type); }
    public final void setJunior()          { dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR); }
    public final void setSenior()          { dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR); }
    public final void setPrincipal()       { dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_PRINCIPAL); }

    public final void put(final String key, final String value) {
        synchronized (this.dna) {
            this.dna.put(key, value);
        }
    }

    /** @return the DNA-map of this peer */
    public final Map<String, String> getMap() {
        return this.dna;
    }

    public final String getName() {
        return checkPeerName(get(yacySeed.NAME, "&empty;"));
    }

    public final String getHexHash() {
        return b64Hash2hexHash(this.hash);
    }

    public final void incSI(final int count) {
        String v = this.dna.get(yacySeed.INDEX_OUT);
        if (v == null) { v = yacySeed.ZERO; }
        dna.put(yacySeed.INDEX_OUT, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incRI(final int count) {
        String v = this.dna.get(yacySeed.INDEX_IN);
        if (v == null) { v = yacySeed.ZERO; }
        dna.put(yacySeed.INDEX_IN, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incSU(final int count) {
        String v = this.dna.get(yacySeed.URL_OUT);
        if (v == null) { v = yacySeed.ZERO; }
        dna.put(yacySeed.URL_OUT, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incRU(final int count) {
        String v = this.dna.get(yacySeed.URL_IN);
        if (v == null) { v = yacySeed.ZERO; }
        dna.put(yacySeed.URL_IN, Integer.toString(Integer.parseInt(v) + count));
    }

    /**
     * <code>12 * 6 bit = 72 bit = 24</code> characters octal-hash
     * <p>Octal hashes are used for cache-dumps that are DHT-ready</p>
     * <p>
     *   Cause: the natural order of octal hashes are the same as the b64-order of b64Hashes.
     *   a hexhash cannot be used in such cases, and b64Hashes are not appropriate for file names
     * </p>
     * @param b64Hash a base64 hash
     * @return the octal representation of the given base64 hash
     */
    public static String b64Hash2octalHash(final String b64Hash) {
        return serverCodings.encodeOctal(kelondroBase64Order.enhancedCoder.decode(b64Hash, "de.anomic.yacy.yacySeed.b64Hash2octalHash()"));
    }

    /**
     * <code>12 * 6 bit = 72 bit = 18</code> characters hex-hash
     * @param b64Hash a base64 hash
     * @return the hexadecimal representation of the given base64 hash
     */
    public static String b64Hash2hexHash(final String b64Hash) {
        // the hash string represents 12 * 6 bit = 72 bits. This is too much for a long integer.
        return serverCodings.encodeHex(kelondroBase64Order.enhancedCoder.decode(b64Hash, "de.anomic.yacy.yacySeed.b64Hash2hexHash()"));
    }
    
    /**
     * @param hexHash a hexadecimal hash
     * @return the base64 representation of the given hex hash
     */
    public static String hexHash2b64Hash(final String hexHash) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.decodeHex(hexHash));
    }

    /**
     * <code>12 * 6 bit = 72 bit = 9 byte</code>
     * @param b64Hash a base64 hash
     * @return returns a base256 - a byte - representation of the given base64 hash
     */
    public static byte[] b64Hash2b256Hash(final String b64Hash) {
        assert b64Hash.length() == 12;
        return kelondroBase64Order.enhancedCoder.decode(b64Hash, "de.anomic.yacy.yacySeed.b64Hash2b256Hash()");
    }
    
    /**
     * @param b256Hash a base256 hash - normal byte number system
     * @return the base64 representation of the given base256 hash
     */
    public static String b256Hash2b64Hash(final byte[] b256Hash) {
        assert b256Hash.length == 9;
        return kelondroBase64Order.enhancedCoder.encode(b256Hash);
    }
    
    /**
     * The returned version follows this pattern: <code>MAJORVERSION . MINORVERSION 0 SVN REVISION</code> 
     * @return the YaCy version of this peer as a float or <code>0</code> if no valid value could be retrieved
     * from this yacySeed object
     */
    public final float getVersion() {
        try {
            return Float.parseFloat(get(yacySeed.VERSION, yacySeed.ZERO));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * @return the public address of the peer as IP:port string or <code>null</code> if no valid values for
     * either the IP or the port could be retrieved from this yacySeed object
     */
    public final String getPublicAddress() {
        final String ip = this.dna.get(yacySeed.IP);
        if (ip == null) { return null; }
        if (ip.length() < 8) { return null; } // 10.0.0.0
        // if (ip.equals(yacyCore.seedDB.mySeed.dna.get(yacySeed.IP))) ip = "127.0.0.1";
        // if (this.hash.equals("xxxxxxxxxxxx")) return "192.168.100.1:3300";
        
        final String port = this.dna.get(yacySeed.PORT);
        if ((port == null) || (port.length() < 2)) return null;

        return ip + ":" + port;
    }
    
    /**
     * If this seed is part of a cluster, the peer has probably the {@linkplain #alternativeIP} object set to
     * a local IP. If this is present and the public IP of this peer is identical to the public IP of the own seed,
     * construct an address using this IP; otherwise return the public address
     * @see #getPublicAddress()
     * @return the alternative IP:port if present, else the public address
     */
    public final String getClusterAddress() {
    	if (this.alternativeIP == null) return getPublicAddress();
    			
        final String port = this.dna.get(yacySeed.PORT);
        if ((port == null) || (port.length() < 2)) return null;

        return this.alternativeIP + ":" + port;
    }
    
    /**
     * @return the IP address of the peer represented by this yacySeed object as {@link InetAddress}
     */
    public final InetAddress getInetAddress() {
        return natLib.getInetAddress(this.dna.get(yacySeed.IP));
    }
    
    /** @return the portnumber of this seed or <code>-1</code> if not present */
    public final int getPort() {
        final String port = this.dna.get(yacySeed.PORT);
        if (port == null) return -1;
        /*if (port.length() < 2) return -1; It is possible to use port 0-9*/
        return Integer.parseInt(port);
    }
    
    /**
     * To synchronize peer pings the local time differential must be included in calculations.
     * @return the difference to UTC (universal time coordinated) in milliseconds of this yacySeed,
     * the difference to <code>+0130</code> if not present or <code>0</code> if an error occured during conversion
     */
    public final long getUTCDiff() {
        String utc = this.dna.get(yacySeed.UTC);
        if (utc == null) { utc = "+0130"; }
        try {
            return serverDate.UTCDiff(utc);
        } catch (final IllegalArgumentException e) {
            return 0;
        }
    }

    /** puts the current time into the lastseen field and cares about the time differential to UTC */
    public final void setLastSeenUTC() {
        // because java thinks it must apply the UTC offset to the current time,
        // to create a string that looks like our current time, it adds the local UTC offset to the
        // time. To create a corrected UTC Date string, we first subtract the local UTC offset.
        dna.put(yacySeed.LASTSEEN, serverDate.formatShortSecond(new Date(System.currentTimeMillis() - serverDate.UTCDiff())) );
    }
    
    /**
     * @return the last seen time converted to UTC in milliseconds
     */
    public final long getLastSeenUTC() {
        try {
            final long t = serverDate.parseShortSecond(get(yacySeed.LASTSEEN, "20040101000000")).getTime();
            // getTime creates a UTC time number. But in this case java thinks, that the given
            // time string is a local time, which has a local UTC offset applied.
            // Therefore java subtracts the local UTC offset, to get a UTC number.
            // But the given time string is already in UTC time, so the subtraction
            // of the local UTC offset is wrong. We correct this here by adding the local UTC
            // offset again.
            return t + serverDate.UTCDiff();
        } catch (final java.text.ParseException e) { // in case of an error make seed look old!!!
            return System.currentTimeMillis() - serverDate.dayMillis;
        } catch (final java.lang.NumberFormatException e) {
            return System.currentTimeMillis() - serverDate.dayMillis;
        }
    }
    
    /**
     * @see #getLastSeenUTC()
     * @return the last seen value as string representation in the following format: YearMonthDayHoursMinutesSeconds
     * or <code>20040101000000</code> if not present
     */
    public final String getLastSeenString() {
        return get(yacySeed.LASTSEEN, "20040101000000");
    }

    /** @return the age of the seed in number of days */
    public final int getAge() {
        try {
            final long t = serverDate.parseShortSecond(get(yacySeed.BDATE, "20040101000000")).getTime();
            return (int) ((System.currentTimeMillis() - (t - getUTCDiff() + serverDate.UTCDiff())) / 1000 / 60 / 60 / 24);
        } catch (final java.text.ParseException e) {
            return -1;
        } catch (final java.lang.NumberFormatException e) {
            return -1;
        }
    }

    public void setPeerTags(final Set<String> keys) {
        dna.put(PEERTAGS, serverCodings.set2string(keys, "|", false));
    }

    public Set<String> getPeerTags() {
        return serverCodings.string2set(get(PEERTAGS, ""), "|");
    }

    public boolean matchPeerTags(final Set<String> searchHashes) {
        final String peertags = get(PEERTAGS, "");
        if (peertags.equals("*")) return true;
        final Set<String> tags = serverCodings.string2set(peertags, "|");
        final Iterator<String> i = tags.iterator();
        while (i.hasNext()) {
        	if (searchHashes.contains(indexWord.word2hash(i.next()))) return true;
        }
        return false;
    }

    public int getPPM() {
        try {
            return Integer.parseInt(get(yacySeed.ISPEED, yacySeed.ZERO));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    public double getQPM() {
        try {
            return Double.parseDouble(get(yacySeed.RSPEED, yacySeed.ZERO));
        } catch (final NumberFormatException e) {
            return 0d;
        }
    }

    public final long getLinkCount() {
        try {
            return getLong(yacySeed.LCOUNT, 0);
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private boolean getFlag(final int flag) {
        final String flags = get(yacySeed.FLAGS, yacySeed.FLAGSZERO);
        return (new bitfield(flags.getBytes())).get(flag);
    }

    private void setFlag(final int flag, final boolean value) {
        String flags = get(yacySeed.FLAGS, yacySeed.FLAGSZERO);
        if (flags.length() != 4) { flags = yacySeed.FLAGSZERO; }
        final bitfield f = new bitfield(flags.getBytes());
        f.set(flag, value);
        dna.put(yacySeed.FLAGS, new String(f.getBytes()));
    }

    public final void setFlagDirectConnect(final boolean value) { setFlag(0, value); }
    public final void setFlagAcceptRemoteCrawl(final boolean value) { setFlag(1, value); }
    public final void setFlagAcceptRemoteIndex(final boolean value) { setFlag(2, value); }
    public final void setFlagAcceptCitationReference(final boolean value) { setFlag(3, value); }
    public final boolean getFlagDirectConnect() { return getFlag(0); }
    public final boolean getFlagAcceptRemoteCrawl() {
        //if (getVersion() < 0.300) return false;
        //if (getVersion() < 0.334) return true;
        return getFlag(1);
    }
    public final boolean getFlagAcceptRemoteIndex() {
        //if (getVersion() < 0.335) return false;
        return getFlag(2);
    }
    public final boolean getFlagAcceptCitationReference() {
        return getFlag(3);
    }
    public final void setUnusedFlags() {
        for (int i = 4; i < 24; i++) { setFlag(i, true); }
    }
    public final boolean isType(final String type) {
        return get(yacySeed.PEERTYPE, "").equals(type);
    }
    public final boolean isVirgin() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_VIRGIN);
    }
    public final boolean isJunior() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_JUNIOR);
    }
    public final boolean isSenior() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_SENIOR);
    }
    public final boolean isPrincipal() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_PRINCIPAL);
    }
    public final boolean isPotential() {
        return isVirgin() || isJunior();
    }
    public final boolean isActive() {
        return isSenior() || isPrincipal();
    }
    public final boolean isOnline() {
        return isSenior() || isPrincipal();
    }
    public final boolean isOnline(final String type) {
        return type.equals(yacySeed.PEERTYPE_SENIOR) || type.equals(yacySeed.PEERTYPE_PRINCIPAL);
    }

    public final long dhtPosition() {
        // normalized to Long.MAX_VALUE
        return dhtPosition(this.hash);
    }

    private final static double dhtPositionDouble(final String wordHash) {
        // normalized to 1.0
        long c = kelondroBase64Order.enhancedCoder.cardinal(wordHash.getBytes());
        assert c != Long.MAX_VALUE;
        if (c == Long.MAX_VALUE) return 0.999999999999;
        return ((double) c) / ((double) Long.MAX_VALUE);
    }
 
    public final static long dhtPosition(final String wordHash) {
        // normalized to Long.MAX_VALUE
        long c = kelondroBase64Order.enhancedCoder.cardinal(wordHash.getBytes());
        assert c != Long.MAX_VALUE;
        if (c == Long.MAX_VALUE) return Long.MAX_VALUE - 1;
        return c;
    }
    
    /**
     * calculate the DHT position for horizontal and vertical performance scaling:
     * horizontal: scale with number of words
     * vertical: scale with number of references for every word
     * The vertical scaling is selected using the corresponding reference hash, the url hash
     * This has the effect that every vertical position accumulates references for the same url
     * and the urls are not spread over all positions of the DHT. To use this effect, the
     * horizontal DHT position must be normed to a 'rest' value of a partition size
     * This method is compatible to the classic DHT computation as always one of the vertical
     * DHT position corresponds to the classic position. 
     * @param wordHash, the hash of the RWI
     * @param partitions, the number of partitions should be computed with partitions = 2**n, n = scaling factor
     * @param urlHash, the hash of a reference
     * @return a double in the range 0 .. 1.0 (including 0, excluding 1.0), the DHT position
     */
    private final static double dhtPositionDouble(final String wordHash, final String urlHash, final int e) {
        assert wordHash != null;
        assert urlHash != null;
        if (urlHash == null) return dhtPositionDouble(wordHash);
        // calculate the primary DHT position:
        // this is done first using the 'classic' dht position and then
        // calculation an alternative 'first' position considering the partition size
        // because of the number of partitions, the 'original' position is reached as one of the
        // alternative dht positions within the partitions
        double primary = dhtPositionDouble(wordHash); // the hash position for horizontal performance scaling
        // the number of partitions is 2 ** e, the partitions may grow exponentially (every time it is doubled)
        double partitions = (double) (1L << e);
        // modification of the primary position using the partitions to create a normalization:
        double normalization = Math.floor(primary * partitions) / partitions;
        // calculate the shift: the alternative position for vertical performance scaling
        double shift = Math.floor(dhtPositionDouble(urlHash) * partitions) / partitions;
        // the final position is the primary, normalized position plus the shift
        double p = primary - normalization + shift;
        // one of the possible shift positions points to the original dht position:
        // this is where the shift is equal to the normalization, when
        // Math.floor(dhtPosition(wordHash) * partitions) == Math.floor(dhtPosition(urlHash) * partitions)
        assert p < 1.0 : "p = " + p; // because of the normalization never an overflow should occur
        assert p >= 0.0 : "p = " + p;
        return (p < 1.0) ? p : p - 1.0;
    }
    
    public final static long dhtPosition(final String wordHash, final String urlHash, final int e) {
        // this creates 1^^e different positions for the same word hash (according to url hash)
        assert wordHash != null;
        assert urlHash != null;
        if (urlHash == null || e < 1) return dhtPosition(wordHash);
        // the partition size is (Long.MAX + 1) / 2 ** e == 2 ** (63 - e)
        assert e > 0;
        long partitionMask = (1L << (Long.SIZE - 1 - e)) - 1L;
        return (dhtPosition(wordHash) & partitionMask) | (dhtPosition(urlHash) & ~partitionMask);
    }
    
    /**
     * compute all vertical DHT positions for a given word
     * @param wordHash, the hash of the word
     * @param partitions, the number of partitions of the DHT
     * @return a vector of long values, the possible DHT positions
     */
    private final static double[] dhtPositionsDouble(final String wordHash, final int e) {
        assert wordHash != null;
        int partitions = 1 << e;
        double[] d = new double[partitions];
        double primary = dhtPositionDouble(wordHash);
        double partitionSize = 1.0 / (double) partitions;
        d[0] = primary - Math.floor(primary * partitions) / partitions;
        for (int i = 1; i < partitions; i++) {
            d[i] = d[i - 1] + partitionSize;
        }
        return d;
    }
    
    public final static long[] dhtPositions(final String wordHash, final int e) {
        assert wordHash != null;
        int partitions = 1 << e;
        long[] l = new long[partitions];
        long partitionSize = 1L << (Long.SIZE - 1 - e);
        l[0] = dhtPosition(wordHash) & (partitionSize - 1L);
        for (int i = 1; i < partitions; i++) {
            l[i] = l[i - 1] + partitionSize;
        }
        return l;
    }
 
    public final static long dhtDistance(final String word, final yacySeed peer) {
        return dhtDistance(word, peer.hash);
    }
    
    private final static long dhtDistance(final String from, final String to) {
        // the dht distance is a positive value between 0 and 1
        // if the distance is small, the word more probably belongs to the peer
        assert to != null;
        assert from != null;
        final long toPos = dhtPosition(to);
        final long fromPos = dhtPosition(from);
        return dhtDistance(fromPos, toPos);
    }
    
    public final static long dhtDistance(final long fromPos, final long toPos) {
        final long d = toPos - fromPos;
        return (d >= 0) ? d : (d + Long.MAX_VALUE) + 1;
    }
    
    private static String bestGap(final yacySeedDB seedDB) {
        if ((seedDB == null) || (seedDB.sizeConnected() <= 2)) {
            // use random hash
            return randomHash();
        }
        // find gaps
        final TreeMap<Long, String> gaps = hashGaps(seedDB);
        
        // take one gap; prefer biggest but take also another smaller by chance
        String interval = null;
        final Random r = new Random();
        while (gaps.size() > 0) {
            interval = gaps.remove(gaps.lastKey());
            if (r.nextBoolean()) break;
        }
        if (interval == null) return randomHash();
        
        // find dht position and size of gap
        final long gaphalf = dhtDistance(interval.substring(0, 12), interval.substring(12)) >> 1;
        long p = dhtPosition(interval.substring(0, 12));
        long gappos = (Long.MAX_VALUE - p >= gaphalf) ? p + gaphalf : (p - Long.MAX_VALUE) + gaphalf;
        return positionToHash(gappos);
    }
    
    private static TreeMap<Long, String> hashGaps(final yacySeedDB seedDB) {
        final TreeMap<Long, String>gaps = new TreeMap<Long, String>();
        if (seedDB == null) return gaps;
        
        final Iterator<yacySeed> i = seedDB.seedsConnected(true, false, null, (float) 0.0);
        long l;
        yacySeed s0 = null, s1, first = null;
        while (i.hasNext()) {
            s1 = i.next();
            if (s0 == null) {
                s0 = s1;
                first = s0;
                continue;
            }
            l = dhtDistance(s0.hash, s1.hash);
            gaps.put(l, s0.hash + s1.hash);
            s0 = s1;
        }
        // compute also the last gap
        if ((first != null) && (s0 != null)) {
            l = dhtDistance(s0.hash, first.hash);
            gaps.put(l, s0.hash + first.hash);
        }
        return gaps;
    }
    
    static String positionToHash(final double t) {
        // transform the position of a peer position into a close peer hash
        assert t >= 0.0 : "t = " + t;
        assert t < 1.0 : "t = " + t;
        
        return new String(kelondroBase64Order.enhancedCoder.uncardinal((long) (((double) Long.MAX_VALUE) * t))) + "AA";
    }
    
    public static String positionToHash(final long l) {
        // transform the position of a peer position into a close peer hash
       
        return new String(kelondroBase64Order.enhancedCoder.uncardinal(l)) + "AA";
    }
    
    public static yacySeed genLocalSeed(final yacySeedDB db) {
        return genLocalSeed(db, 0, null); // an anonymous peer
    }
    
    public static yacySeed genLocalSeed(final yacySeedDB db, final int port, final String name) {
        // generate a seed for the local peer
        // this is the birthplace of a seed, that then will start to travel to other peers

        final String hash = bestGap(db);
        yacyCore.log.logInfo("init: OWN SEED = " + hash);

        final yacySeed newSeed = new yacySeed(hash);

        // now calculate other information about the host
        newSeed.dna.put(yacySeed.NAME, (name) == null ? "anonymous" : name);
        newSeed.dna.put(yacySeed.PORT, Integer.toString((port <= 0) ? 8080 : port));
        newSeed.dna.put(yacySeed.BDATE, serverDate.formatShortSecond(new Date(System.currentTimeMillis() - serverDate.UTCDiff())) );
        newSeed.dna.put(yacySeed.LASTSEEN, newSeed.dna.get(yacySeed.BDATE)); // just as initial setting
        newSeed.dna.put(yacySeed.UTC, serverDate.UTCDiffString());
        newSeed.dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);

        return newSeed;
    }

    //public static String randomHash() { return "zLXFf5lTteUv"; } // only for debugging

    public static String randomHash() {
        final String hash =
            kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(random.nextLong()))).substring(0, 6) +
            kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(Long.toString(random.nextLong()))).substring(0, 6);
        return hash;
    }

    public static yacySeed genRemoteSeed(final String seedStr, final String key, final boolean ownSeed) {
        // this method is used to convert the external representation of a seed into a seed object
        // yacyCore.log.logFinest("genRemoteSeed: seedStr=" + seedStr + " key=" + key);

        // check protocol and syntax of seed
        if (seedStr == null) { return null; }
        final String seed = crypt.simpleDecode(seedStr, key);
        if (seed == null) { return null; }
        
        // extract hash
        final HashMap<String, String> dna = serverCodings.string2map(seed, ",");
        final String hash = dna.remove(yacySeed.HASH);
        final yacySeed resultSeed = new yacySeed(hash, dna);

        // check semantics of content
        final String testResult = resultSeed.isProper(ownSeed);
        if (testResult != null) {
            if (yacyCore.log.isFinest()) yacyCore.log.logFinest("seed is not proper (" + testResult + "): " + resultSeed);
            return null;
        }
        
        // seed ok
        return resultSeed;
    }

    public final String isProper(final boolean checkOwnIP) {
        // checks if everything is ok with that seed
        
        // check hash
        if (this.hash == null) return "hash is null";
        if (this.hash.length() != yacySeedDB.commonHashLength) return "wrong hash length (" + this.hash.length() + ")";

        // name
        final String peerName = this.dna.get(yacySeed.NAME);
        if (peerName == null) return "no peer name given";
        if (peerName.equalsIgnoreCase("VegaYacyB")) return "bad peer VegaYacyB [ " + this.hash + " ]"; // hack for wrong "VegaYacyB" peers
        dna.put(yacySeed.NAME, checkPeerName(peerName));

        // type
        final String peerType = this.getPeerType();
        if ((peerType == null) || 
            !(peerType.equals(yacySeed.PEERTYPE_VIRGIN) || peerType.equals(yacySeed.PEERTYPE_JUNIOR)
              || peerType.equals(yacySeed.PEERTYPE_SENIOR) || peerType.equals(yacySeed.PEERTYPE_PRINCIPAL)))
            return "invalid peerType '" + peerType + "'";

        // check IP
        if (!checkOwnIP) {
            // checking of IP is omitted if we read the own seed file        
            final String ipCheck = isProperIP(this.dna.get(yacySeed.IP));
            if (ipCheck != null) return ipCheck;
        }
        
        // seedURL
        final String seedURL = this.dna.get(SEEDLIST);
        if (seedURL != null && seedURL.length() > 0) {
            if (!seedURL.startsWith("http://") && !seedURL.startsWith("https://")) return "wrong protocol for seedURL";
            try {
                final URL url = new URL(seedURL);
                final String host = url.getHost();
                if (host.equals("localhost") || host.startsWith("127.") || (host.startsWith("0:0:0:0:0:0:0:1"))) return "seedURL in localhost rejected";
            } catch (final MalformedURLException e) {
                return "seedURL malformed";
            }
        }
        return null;
    }
    
    public static final String isProperIP(final String ipString) {
        // returns null if ipString is proper, a string with the cause otervise
        if (ipString == null) return "IP is null";
        if (ipString.length() > 0 && ipString.length() < 8) return "IP is too short: " + ipString;
        if (!natLib.isProper(ipString)) return "IP is not proper: " + ipString; //this does not work with staticIP
        if (ipString.equals("localhost") || ipString.startsWith("127.") || (ipString.startsWith("0:0:0:0:0:0:0:1"))) return "IP for localhost rejected";
        return null;
    }

    public final String toString() {
        synchronized (this.dna) {
            this.dna.put(yacySeed.HASH, this.hash);                         // set hash into seed code structure
            final String s = serverCodings.map2string(this.dna, ",", true); // generate string representation
            this.dna.remove(yacySeed.HASH);                                 // reconstruct original: hash is stored external
            return s;
        }
    }

    public final String genSeedStr(final String key) {
        // use a default encoding
        final String z = this.genSeedStr('z', key);
        final String b = this.genSeedStr('b', key);
        // the compressed string may be longer that the uncompressed if there is too much overhead for compression meta-info
        // take simply that string that is shorter
        if (b.length() < z.length()) return b; else return z;
    }

    public final synchronized String genSeedStr(final char method, final String key) {
        return crypt.simpleEncode(this.toString(), key, method);
    }

    public final void save(final File f) throws IOException {
        final String out = this.genSeedStr('p', null);
        final FileWriter fw = new FileWriter(f);
        fw.write(out, 0, out.length());
        fw.close();
    }

    public static yacySeed load(final File f) throws IOException {
        final FileReader fr = new FileReader(f);
        final char[] b = new char[(int) f.length()];
        fr.read(b, 0, b.length);
        fr.close();
        final yacySeed mySeed = genRemoteSeed(new String(b), null, true);
        if (mySeed == null) return null;
        mySeed.dna.put(yacySeed.IP, ""); // set own IP as unknown
        return mySeed;
    }

    @SuppressWarnings("unchecked")
    public final yacySeed clone() {
        synchronized (this.dna) {
            return new yacySeed(this.hash, (HashMap<String, String>) (new HashMap<String, String>(this.dna).clone()));
        }
    }


    
    private static int guessedOwn = 0;
    private static int verifiedOwn = 0;
    
    public static boolean shallBeOwnWord(final yacySeedDB seedDB, final String wordhash, int redundancy) {
        if (!guessIfOwnWord(seedDB, wordhash)) return false;
        guessedOwn++;
        if (yacyPeerSelection.verifyIfOwnWord(seedDB, wordhash, redundancy)) {
            verifiedOwn++;
            System.out.println("*** DEBUG shallBeOwnWord: true. verified/guessed ration = " + verifiedOwn + "/" + guessedOwn);
            return true;
        } else {
            System.out.println("*** DEBUG shallBeOwnWord: false. verified/guessed ration = " + verifiedOwn + "/" + guessedOwn);
            return false;
        }
    }
    
    private static boolean guessIfOwnWord(final yacySeedDB seedDB, final String wordhash) {
        if (seedDB == null) return false;
        if (seedDB.mySeed().isPotential()) return false;
        final long[] targets = yacySeed.dhtPositions(wordhash, yacySeed.partitionExponent);
        final long mypos = yacySeed.dhtPosition(seedDB.mySeed().hash);
        for (int i = 0; i < targets.length; i++) {
            long distance = yacySeed.dhtDistance(targets[i], mypos);
            if (distance <= 0) continue;
            if (distance <= Long.MAX_VALUE / seedDB.sizeConnected() * 2) return true;
        }
        return false;
    }
    
    public static void main(String[] args) {
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFn76
        // java -classpath classes de.anomic.yacy.yacySeed hHJBztzcFG76 M8hgtrHG6g12 3
        // test the DHT position calculation
        String wordHash = args[0];
        double dhtd;
        long   dhtl;
        int partitionExponent = 0;
        if (args.length == 3) {
            // the horizontal and vertical position calculation
            String urlHash = args[1];
            partitionExponent = Integer.parseInt(args[2]);
            dhtd = dhtPositionDouble(wordHash, urlHash, partitionExponent);
            dhtl = dhtPosition(wordHash, urlHash, partitionExponent);
        } else {
            // only a horizontal position calculation
            dhtd = dhtPositionDouble(wordHash);
            dhtl = dhtPosition(wordHash);
        }
        System.out.println("DHT Double              = " + dhtd);
        System.out.println("DHT Long                = " + dhtl);
        System.out.println("DHT as Double from Long = " + ((double) dhtl) / ((double) Long.MAX_VALUE));
        System.out.println("DHT as Long from Double = " + (long) (Long.MAX_VALUE * dhtd));
        System.out.println("DHT as b64 from Double  = " + positionToHash(dhtd));
        System.out.println("DHT as b64 from Long    = " + positionToHash(dhtl));
        
        System.out.print("all " + (1 << partitionExponent) + " DHT positions from doubles: ");
        double[] d = dhtPositionsDouble(wordHash, partitionExponent);
        for (int i = 0; i < d.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(positionToHash(d[i]));
        }
        System.out.println();
        
        System.out.print("all " + (1 << partitionExponent) + " DHT positions from long   : ");
        long[] l = dhtPositions(wordHash, partitionExponent);
        for (int i = 0; i < l.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(positionToHash(l[i]));
        }
        System.out.println();
    }
}
