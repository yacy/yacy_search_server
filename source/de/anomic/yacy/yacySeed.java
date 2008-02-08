// yacySeed.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverDomains;
import de.anomic.server.serverSystem;
import de.anomic.tools.bitfield;
import de.anomic.tools.crypt;

public class yacySeed {

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
    /** the number of words the peer has indexed (as it says) */
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
    /** zero-value */
    public static final String ZERO      = "0";
    
    public static final String DFLT_NETWORK_UNIT = "freeworld";
    public static final String DFLT_NETWORK_GROUP = "";

    private static final Random random = new Random(System.currentTimeMillis());
    
    // class variables
    /** the peer-hash */
    public String hash;
    /** a set of identity founding values, eg. IP, name of the peer, YaCy-version, ...*/
    private final HashMap<String, String> dna;
    public int available;
    public int selectscore = -1; // only for debugging
    public String alternativeIP = null;

    public yacySeed(String theHash, HashMap<String, String> theDna) {
        // create a seed with a pre-defined hash map
        this.hash = theHash;
        this.dna = theDna;
        final String flags = (String) this.dna.get(yacySeed.FLAGS);
        if ((flags == null) || (flags.length() != 4)) { this.dna.put(yacySeed.FLAGS, yacySeed.FLAGSZERO); }
        this.available = 0;
    }

    public yacySeed(String theHash) {
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
     * Checks for the static fragments of a generated default peer name, such as the string 'dpn'
     * @see #makeDefaultPeerName()
     * @param name the peer name to check for default peer name compliance
     * @return whether the given peer name may be a default generated peer name
     */
    public static boolean isDefaultPeerName(String name) {
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
    public void setAlternativeAddress(String ipport) {
    	if (ipport == null) return;
    	int p = ipport.indexOf(':');
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
     * @return the peertype or "Virgin"
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
    public final String get(String key, String dflt) {
        final Object o = this.dna.get(key);
        if (o == null) { return dflt; }
        return (String) o;
    }
    
    public final long getLong(String key, long dflt) {
        final Object o = this.dna.get(key);
        if (o == null) { return dflt; }
        if (o instanceof String) try {
        	return Long.parseLong((String) o);
        } catch (NumberFormatException e) {
        	return dflt;
        } else if (o instanceof Long) {
            return ((Long) o).longValue();
        } else if (o instanceof Integer) {
            return (long) ((Integer) o).intValue();
        } else return dflt;
    }

    public final void setIP()                    { dna.put(yacySeed.IP, ""); }
    public final void setIP(final String ip)     { dna.put(yacySeed.IP, ip); }
    public final void setPort(final String port) { dna.put(yacySeed.PORT, port); }
    public final void setJunior()                { dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR); }
    public final void setSenior()                { dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR); }
    public final void setPrincipal()             { dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_PRINCIPAL); }
    
    
    public final void put(String key, String value) {
        synchronized (this.dna) {
            this.dna.put(key, value);
        }
    }

    /** @return the DNA-map of this peer */
    public final HashMap<String, String> getMap() {
        return this.dna;
    }

    public final String getName() {
        return get(yacySeed.NAME, "&empty;");
    }

    public final String getHexHash() {
        return b64Hash2hexHash(this.hash);
    }

    public final void incSI(int count) {
        String v = (String) this.dna.get(yacySeed.INDEX_OUT);
        if (v == null) { v = yacySeed.ZERO; }
        dna.put(yacySeed.INDEX_OUT, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incRI(int count) {
        String v = (String) this.dna.get(yacySeed.INDEX_IN);
        if (v == null) { v = yacySeed.ZERO; }
        dna.put(yacySeed.INDEX_IN, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incSU(int count) {
        String v = (String) this.dna.get(yacySeed.URL_OUT);
        if (v == null) { v = yacySeed.ZERO; }
        dna.put(yacySeed.URL_OUT, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incRU(int count) {
        String v = (String) this.dna.get(yacySeed.URL_IN);
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
    public static String b64Hash2octalHash(String b64Hash) {
        return serverCodings.encodeOctal(kelondroBase64Order.enhancedCoder.decode(b64Hash, "de.anomic.yacy.yacySeed.b64Hash2octalHash()"));
    }

    /**
     * <code>12 * 6 bit = 72 bit = 18</code> characters hex-hash
     * @param b64Hash a base64 hash
     * @return the hexadecimal representation of the given base64 hash
     */
    public static String b64Hash2hexHash(String b64Hash) {
        // the hash string represents 12 * 6 bit = 72 bits. This is too much for a long integer.
        return serverCodings.encodeHex(kelondroBase64Order.enhancedCoder.decode(b64Hash, "de.anomic.yacy.yacySeed.b64Hash2hexHash()"));
    }
    
    /**
     * @param hexHash a hexadecimal hash
     * @return the base64 representation of the given hex hash
     */
    public static String hexHash2b64Hash(String hexHash) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.decodeHex(hexHash));
    }

    /**
     * <code>12 * 6 bit = 72 bit = 9 byte</code>
     * @param b64Hash a base64 hash
     * @return returns a base256 - a byte - representation of the given base64 hash
     */
    public static byte[] b64Hash2b256Hash(String b64Hash) {
        assert b64Hash.length() == 12;
        return kelondroBase64Order.enhancedCoder.decode(b64Hash, "de.anomic.yacy.yacySeed.b64Hash2b256Hash()");
    }
    
    /**
     * @param b256Hash a base256 hash - normal byte number system
     * @return the base64 representation of the given base256 hash
     */
    public static String b256Hash2b64Hash(byte[] b256Hash) {
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
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * @return the public address of the peer as IP:port string or <code>null</code> if no valid values for
     * either the IP or the port could be retrieved from this yacySeed object
     */
    public final String getPublicAddress() {
        String ip = (String) this.dna.get(yacySeed.IP);
        if (ip == null) { return null; }
        if (ip.length() < 8) { return null; } // 10.0.0.0
        // if (ip.equals(yacyCore.seedDB.mySeed.dna.get(yacySeed.IP))) ip = "127.0.0.1";
        // if (this.hash.equals("xxxxxxxxxxxx")) return "192.168.100.1:3300";
        
        final String port = (String) this.dna.get(yacySeed.PORT);
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
    			
        final String port = (String) this.dna.get(yacySeed.PORT);
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
        final String port = (String) this.dna.get(yacySeed.PORT);
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
        String utc = (String) this.dna.get(yacySeed.UTC);
        if (utc == null) { utc = "+0130"; }
        try {
            return serverDate.UTCDiff(utc);
        } catch (IllegalArgumentException e) {
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
        } catch (java.text.ParseException e) {
            return System.currentTimeMillis();
        } catch (java.lang.NumberFormatException e) {
            return System.currentTimeMillis();
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
        } catch (java.text.ParseException e) {
            return -1;
        } catch (java.lang.NumberFormatException e) {
            return -1;
        }
    }

    public void setPeerTags(Set<String> keys) {
        dna.put(PEERTAGS, serverCodings.set2string(keys, "|", false));
    }

    public Set<String> getPeerTags() {
        return serverCodings.string2set(get(PEERTAGS, ""), "|");
    }

    public boolean matchPeerTags(Set<String> searchHashes) {
        Set<String> tags = serverCodings.string2set(get(PEERTAGS, ""), "|");
        Iterator<String> i = tags.iterator();
        while (i.hasNext()) {
        	if (searchHashes.contains(plasmaCondenser.word2hash((String) i.next()))) return true;
        }
        return false;
    }

    public int getPPM() {
        try {
            return Integer.parseInt(get(yacySeed.ISPEED, yacySeed.ZERO));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public double getQPM() {
        try {
            return Double.parseDouble(get(yacySeed.RSPEED, yacySeed.ZERO));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    public final long getLinkCount() {
        try {
            return getLong(yacySeed.LCOUNT, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean getFlag(int flag) {
        final String flags = get(yacySeed.FLAGS, yacySeed.FLAGSZERO);
        return (new bitfield(flags.getBytes())).get(flag);
    }

    private void setFlag(int flag, boolean value) {
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
    public final boolean isPotential() {
        return isVirgin() || isJunior();
    }
    public final boolean isVirgin() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_VIRGIN);
    }
    public final boolean isJunior() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_JUNIOR);
    }
    public final boolean isActive() {
        return isSenior() || isPrincipal();
    }
    public final boolean isSenior() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_SENIOR);
    }
    public final boolean isPrincipal() {
        return get(yacySeed.PEERTYPE, "").equals(yacySeed.PEERTYPE_PRINCIPAL);
    }
    public final boolean isOnline() {
        return isSenior() || isPrincipal();
    }
    public final boolean isOnline(final String type) {
        return type.equals(yacySeed.PEERTYPE_SENIOR) || type.equals(yacySeed.PEERTYPE_PRINCIPAL);
    }
    
    public static final long minDHTNumber   = kelondroBase64Order.enhancedCoder.cardinal(kelondroBase64Order.zero(12));
    public static final long maxDHTDistance = Long.MAX_VALUE;

    public double dhtPosition() {
        // normalized to 1.0
        return dhtPosition(this.hash);
    }

    public static double dhtPosition(String ahash) {
        // normalized to 1.0
        return ((double) kelondroBase64Order.enhancedCoder.cardinal(ahash.getBytes())) / ((double) maxDHTDistance);
    }

    public final static double dhtDistance(String from, String to) {
        // computes a virtual distance, the result must be set in relation to maxDHTDistace
        // if the distance is small, this peer is more responsible for that word hash
        // if the distance is big, this peer is less responsible for that word hash
        if (from == null) return dhtPosition(to);
        final double fromPos = dhtPosition(from);
        final double toPos = dhtPosition(to);
        return (fromPos < toPos) ? (toPos - fromPos) : (1.0 - fromPos + toPos);
    }
    
    private static String bestNewHash(yacySeedDB seedDB) {
        if ((seedDB == null) || (seedDB.sizeConnected() <= 8)) {
            // use random hash
            return randomHash();
        }
        
        int tries = Math.max(1, Math.min(32, seedDB.sizeConnected() / 2));
        String hash;
        String bestHash = null;
        double c, bestC = Double.MAX_VALUE;
        double segmentSize = Math.min(0.9, 4.0 / seedDB.sizeConnected());
        Iterator<yacySeed> i;
        double d;
        yacySeed s;
        for (int t = 0; t < tries; t++) {
            hash = randomHash();
            i = seedDB.seedsConnected(true, true, hash, (float) 0.0);
            c = 0.0;
            while (i.hasNext()) {
                s = i.next();
                d = dhtDistance(hash, s.hash);
                if (d > segmentSize) break;
                c = c + 1.0/d;
            }
            System.out.println("BESTHASH  " + hash + " = " + c);
            if (c < bestC) {
                bestC = c;
                bestHash = hash;
                System.out.println("BESTHASH  " + hash + " is best now");
            }
        }
        if (bestHash == null) return randomHash();
        
        // at this point we know only the position of a peer sequence
        double bestPosition = dhtPosition(bestHash) + (segmentSize / 2);
        if (bestPosition > 1.0) bestPosition = bestPosition - 1.0;
        bestHash = positionToHash(bestPosition);
        System.out.println("BESTHASH  finally is " + bestHash);
        return bestHash;
    }
    
    private static String positionToHash(double t) {
        // transform the position of a peer position into a close peer hash
        assert t >= 0.0 : "t = " + t;
        assert t < 1.0 : "t = " + t;
        
        // now calculate a hash that is closest to the best position
        double d, bestD = Double.MAX_VALUE;
        int tries = 128;
        String hash, bestHash = null;
        for (int v = 0; v < tries; v++) {
            hash = randomHash();
            d = dhtPosition(hash);
            if (Math.abs(d - t) < bestD) {
                bestD = Math.abs(d - t);
                bestHash = hash;
            }
        }
        return bestHash;
    }
    
    public static yacySeed genLocalSeed(plasmaSwitchboard sb) {
        // genera a seed for the local peer
        // this is the birthplace of a seed, that then will start to travel to other peers

        final String hash = bestNewHash(yacyCore.seedDB);
        yacyCore.log.logInfo("init: OWN SEED = " + hash);

        final yacySeed newSeed = new yacySeed(hash);

        // now calculate other information about the host
        newSeed.dna.put(yacySeed.NAME, sb.getConfig("peerName", "unnamed"));
        if ((serverCore.portForwardingEnabled) && (serverCore.portForwarding != null)) {
            newSeed.dna.put(yacySeed.PORT, Integer.toString(serverCore.portForwarding.getPort()));
        } else {
            newSeed.dna.put(yacySeed.PORT, Integer.toString(serverCore.getPortNr(sb.getConfig("port", "8080"))));
        }
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

    public static yacySeed genRemoteSeed(String seedStr, String key, boolean properTest) {
        // this method is used to convert the external representation of a seed into a seed object
//      yacyCore.log.logFinest("genRemoteSeed: seedStr=" + seedStr + " key=" + key);
        if (seedStr == null) { return null; }
        final String seed = crypt.simpleDecode(seedStr, key);
        if (seed == null) { return null; }
        final HashMap<String, String> dna = serverCodings.string2map(seed, ",");
        final String hash = (String) dna.remove(yacySeed.HASH);
        final yacySeed resultSeed = new yacySeed(hash, dna);
        if (properTest) {
            final String testResult = resultSeed.isProper();
            if (testResult != null) {
                yacyCore.log.logFinest("seed is not proper (" + testResult + "): " + resultSeed);
                return null;
            }
        }
        return resultSeed;
    }

    public final String toString() {
        synchronized (this.dna) {
            this.dna.put(yacySeed.HASH, this.hash);                         // set hash into seed code structure
            final String s = serverCodings.map2string(this.dna, ",", true); // generate string representation
            this.dna.remove(yacySeed.HASH);                                 // reconstruct original: hash is stored external
            return s;
        }
    }

    public final String genSeedStr(String key) {
        // use a default encoding
        return this.genSeedStr('z', key);
    }

    public final synchronized String genSeedStr(char method, String key) {
        return crypt.simpleEncode(this.toString(), key, method);
    }

    public final boolean isPeerOK() {
        return this.hash != null && this.isProper() == null;
    }

    public final String isProper() {
        // checks if everything is ok with that seed
        if (this.hash == null) { return "hash is null"; }
        if (this.hash.length() != yacySeedDB.commonHashLength) { return "wrong hash length (" + this.hash.length() + ")"; }
        final String ip = (String) this.dna.get(yacySeed.IP);
        if (ip == null) { return "IP is null"; }
        if (ip.length() < 8) { return "IP is too short: " + ip; }
        if (!natLib.isProper(ip)) { return "IP is not proper: " + ip; } //this does not work with staticIP
        return null;
    }

    public final void save(File f) throws IOException {
        final String out = this.genSeedStr('z', null);
        final FileWriter fw = new FileWriter(f);
        fw.write(out, 0, out.length());
        fw.close();
    }

    public static yacySeed load(File f) throws IOException {
        final FileReader fr = new FileReader(f);
        final char[] b = new char[(int) f.length()];
        fr.read(b, 0, b.length);
        fr.close();
        return genRemoteSeed(new String(b), null, false);
    }

    @SuppressWarnings("unchecked")
    public final Object clone() {
        synchronized (this.dna) {
            return new yacySeed(this.hash, (HashMap<String, String>) (new HashMap<String, String>(this.dna)).clone());
        }
    }

}
