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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverDate;
import de.anomic.server.serverSystem;
import de.anomic.tools.bitfield;
import de.anomic.tools.crypt;

public class yacySeed {

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

    public static final String IPTYPE    = "IPType";
    public static final String FLAGS     = "Flags";
    public static final String FLAGSZERO = "____";
    public static final String VERSION   = "Version";

    public static final String YOURTYPE  = "yourtype";
    public static final String LASTSEEN  = "LastSeen";
    public static final String USPEED    = "USpeed";

    public static final String NAME      = "Name";
    public static final String HASH      = "Hash";
    public static final String BDATE     = "BDate";
    public static final String UTC       = "UTC";
    public static final String PEERTAGS  = "Tags";

    public static final String ISPEED    = "ISpeed";
    public static final String UPTIME    = "Uptime";
    public static final String LCOUNT    = "LCount";
    public static final String NCOUNT    = "NCount";
    public static final String ICOUNT    = "ICount";
    public static final String SCOUNT    = "SCount";
    public static final String CCOUNT    = "CCount"; // Connection Count
    public static final String CRWCNT    = "CRWCnt"; // Citation Rank (Own) - Count
    public static final String CRTCNT    = "CRTCnt"; // Citation Rank (Other) - Count

    public static final String IP        = "IP";
    public static final String PORT      = "Port";
    public static final String YOURIP    = "yourip";
    public static final String MYTIME    = "mytime";
    public static final String SEED      = "seed";
    public static final String EQUAL     = "=";
    public static final String ZERO      = "0";

    // class variables
    public String hash;
    private final Map dna;
    public int available;
    public int selectscore = -1; // only for debugging

    public yacySeed(String theHash, Map theDna) {
        // create a seed with a pre-defined hash map
        this.hash = theHash;
        this.dna = theDna;
        final String flags = (String) this.dna.get(yacySeed.FLAGS);
        if ((flags == null) || (flags.length() != 4)) { this.dna.put(yacySeed.FLAGS, yacySeed.FLAGSZERO); }
        this.available = 0;
    }

    public yacySeed(String theHash) {
        this.dna = new HashMap();

        // settings that can only be computed by originating peer:
        // at first startup -
        this.hash = theHash; // the hash key of the peer - very important. should be static somehow, even after restart
        this.dna.put(yacySeed.NAME, "&empty;");  // the name that the peer has given itself
        this.dna.put(yacySeed.BDATE, "&empty;"); // birthdate - first startup
        this.dna.put(yacySeed.UTC, "+0000");
        // later during operation -
        this.dna.put(yacySeed.ISPEED, yacySeed.ZERO);  // the speed of indexing (pages/minute) of the peer
        this.dna.put(yacySeed.UPTIME, yacySeed.ZERO);  // the number of minutes that the peer is up in minutes/day (moving average MA30)
        this.dna.put(yacySeed.LCOUNT, yacySeed.ZERO);  // the number of links that the peer has stored (LURL's)
        this.dna.put(yacySeed.NCOUNT, yacySeed.ZERO);  // the number of links that the peer has noticed, but not loaded (NURL's)
        this.dna.put(yacySeed.ICOUNT, yacySeed.ZERO);  // the number of words that the peer has indexed (as it says)
        this.dna.put(yacySeed.SCOUNT, yacySeed.ZERO);  // the number of seeds that the peer has stored
        this.dna.put(yacySeed.CCOUNT, yacySeed.ZERO);  // the number of clients that the peer connects (as connects/hour)
        this.dna.put(yacySeed.VERSION, yacySeed.ZERO); // the applications version

        // settings that is created during the 'hello' phase - in first contact
        this.dna.put(yacySeed.IP, "");                 // 123.234.345.456
        this.dna.put(yacySeed.PORT, "&empty;");
        this.dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN); // virgin/junior/senior/principal
        this.dna.put(yacySeed.IPTYPE, "&empty;");      // static/dynamic (if the ip changes often for any reason)

        // settings that can only be computed by visiting peer
        this.dna.put(yacySeed.LASTSEEN, yacyCore.universalDateShortString(new Date())); // for last-seen date
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

    public static String makeDefaultPeerName() {
        // generate a default peer name
        String name = serverCore.publicIP() + "-" + yacyCore.speedKey  + "dpn" + serverSystem.infoKey() + (System.currentTimeMillis() & 99);
        name = name.replace('.', '-');
        name = name.replace('_', '-');
        return name;
    }

    public static boolean isDefaultPeerName(String name) {
        return name != null && name.length() > 10 && name.charAt(0) <= '9' && name.charAt(name.length() - 1) <= '9' && name.indexOf("dpn") > 0;
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

    public final String get(String key, String dflt) {
        final Object o = this.dna.get(key);
        if (o == null) { return dflt; }
        return (String) o;
    }

    public final void setIP()                    { put(yacySeed.IP, ""); }
    public final void setIP(final String ip)     { put(yacySeed.IP, ip); }
    public final void setPort(final String port) { put(yacySeed.PORT, port); }
    public final void setJunior()                { put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_JUNIOR); }
    public final void setSenior()                { put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_SENIOR); }
    public final void setPrincipal()             { put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_PRINCIPAL); }
    public final void setLastSeen()              { put(yacySeed.LASTSEEN, yacyCore.universalDateShortString(new Date(System.currentTimeMillis() + serverDate.UTCDiff() - getUTCDiff()))); }

    public final void put(String key, String value) {
        synchronized (this.dna) {
            this.dna.put(key, value);
        }
    }

    public final Map getMap() {
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
        put(yacySeed.INDEX_OUT, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incRI(int count) {
        String v = (String) this.dna.get(yacySeed.INDEX_IN);
        if (v == null) { v = yacySeed.ZERO; }
        put(yacySeed.INDEX_IN, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incSU(int count) {
        String v = (String) this.dna.get(yacySeed.URL_OUT);
        if (v == null) { v = yacySeed.ZERO; }
        put(yacySeed.URL_OUT, Integer.toString(Integer.parseInt(v) + count));
    }

    public final void incRU(int count) {
        String v = (String) this.dna.get(yacySeed.URL_IN);
        if (v == null) { v = yacySeed.ZERO; }
        put(yacySeed.URL_IN, Integer.toString(Integer.parseInt(v) + count));
    }

    // 12 * 6 bit = 72 bit = 18 characters hex-hash
    public static String b64Hash2hexHash(String b64Hash) {
        // the hash string represents 12 * 6 bit = 72 bits. This is too much for a long integer.
        return serverCodings.encodeHex(kelondroBase64Order.enhancedCoder.decode(b64Hash));
    }

    public static String hexHash2b64Hash(String hexHash) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.decodeHex(hexHash));
    }

    //  12 * 6 bit = 72 bit = 9 byte
    public static byte[] b64Hash2b256Hash(String b64Hash) {
        assert b64Hash.length() == 12;
        return kelondroBase64Order.enhancedCoder.decode(b64Hash);
    }

    public static String b256Hash2b64Hash(byte[] b256Hash) {
        assert b256Hash.length == 9;
        return kelondroBase64Order.enhancedCoder.encode(b256Hash);
    }

    public final float getVersion() {
        try {
            return Float.parseFloat(get(yacySeed.VERSION, yacySeed.ZERO));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public final String getAddress() {
        String ip   = (String) this.dna.get(yacySeed.IP);
        if (ip == null) { return null; }
        if (ip.length() < 8) { return null; } // 10.0.0.0
        if (ip.equals(yacyCore.seedDB.mySeed.dna.get(yacySeed.IP))) ip = "127.0.0.1";

        final String port = (String) this.dna.get(yacySeed.PORT);
        if (port == null) { return null; }
        if (port.length() < 2) { return null; }

        return ip + ":" + port;
    }

    public final long getUTCDiff() {
        String utc = (String) this.dna.get(yacySeed.UTC);
        if (utc == null) { utc = "+0130"; }
        try {
            return serverDate.UTCDiff(utc);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public final long getLastSeenTime() {
        try {
            final long t = yacyCore.parseUniversalDate(get(yacySeed.LASTSEEN, "20040101000000")).getTime();
            // the problem here is: getTime applies a time shift according to local time zone:
            // it substracts the local UTF offset, but it should subtract the remote UTC offset
            // so we correct it by first adding the local UTF offset and then subtracting the remote
            // the time zone was originally the seeds time zone
            // we correct this here
            return t - getUTCDiff() + serverDate.UTCDiff();
        } catch (java.text.ParseException e) {
            return System.currentTimeMillis();
        } catch (java.lang.NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }

    public final int getAge() {
        // returns the age as number of days
        try {
            final long t = yacyCore.parseUniversalDate(get(yacySeed.BDATE, "20040101000000")).getTime();
            return (int) ((System.currentTimeMillis() - (t - getUTCDiff() + serverDate.UTCDiff())) / 1000 / 60 / 60 / 24);
        } catch (java.text.ParseException e) {
            return -1;
        } catch (java.lang.NumberFormatException e) {
            return -1;
        }
    }

    public final void setLastSeenTime() {
        // if we set a last seen time, then we need to respect the seeds UTC offset
        put(yacySeed.LASTSEEN, yacyCore.universalDateShortString(new Date(System.currentTimeMillis() - serverDate.UTCDiff() + getUTCDiff())));
    }

    public void setPeerTags(Set keys) {
        put(PEERTAGS, serverCodings.set2string(keys, "|", false));
    }

    public Set getPeerTags() {
        return serverCodings.string2set(get(PEERTAGS, ""), "|");
    }

    public int getPPM() {
        try {
            return Integer.parseInt(get(yacySeed.ISPEED, yacySeed.ZERO));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public final long getLinkCount() {
        try {
            return Long.parseLong(get(yacySeed.LCOUNT, yacySeed.ZERO));
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
        put(yacySeed.FLAGS, new String(f.getBytes()));
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

    public static String encodeLex(long c, int length) {
        if (length < 0) { length = 0; }
        final StringBuffer s = new StringBuffer();
        if (c == 0) {
            s.insert(0, '-');
        } else {
            while (c > 0) {
                s.insert(0, (char) (32 + (c % 96)));
                c = c / 96;
            }
        }
        if (length != 0 && s.length() > length) {
            throw new RuntimeException("encodeLex result '" + s + "' exceeds demanded length of " + length + " digits");
        }
        if (length == 0) { length = 1; } // rare exception for the case that c == 0
        while (s.length() < length) { s.insert(0, '-'); }
        return s.toString();
    }

    public static long decodeLex(String s) {
        long c = 0;
        for (int i = 0; i < s.length(); i++) {
            c = c * 96 + (byte) s.charAt(i) - 32;
        }
        return c;
    }

    private static long maxLex(int len) {
        // computes the maximum number that can be coded with a lex-encoded String of length len
        long c = 0;
        for (int i = 0; i < len; i++) {
            c = c * 96 + 90;
        }
        return c;
    }

    private static long minLex(int len) {
        // computes the minimum number that can be coded with a lex-encoded String of length len
        long c = 0;
        for (int i = 0; i < len; i++) {
            c = c * 96 + 13;
        }
        return c;
    }

    public static final long minDHTNumber   = minLex(9);
    public static final long maxDHTDistance = maxLex(9) - yacySeed.minDHTNumber;

    public final long dhtDistance(String wordhash) {
        // computes a virtual distance, the result must be set in relation to maxDHTDistace
        // if the distance is small, this peer is more responsible for that word hash
        // if the distance is big, this peer is less responsible for that word hash
        final long myPos = decodeLex(this.hash.substring(0, 9));
        final long wordPos = decodeLex(wordhash.substring(0, 9));
        return (myPos > wordPos) ? (myPos - wordPos) : (myPos + yacySeed.maxDHTDistance - wordPos);
    }

    public final long dhtPosition() {
        // returns an absolute value
        return dhtPosition(this.hash);
    }

    public static long dhtPosition(String ahash) {
        // returns an absolute value
        return decodeLex(ahash.substring(0, 9)) - yacySeed.minDHTNumber;
    }

    public static yacySeed genLocalSeed(plasmaSwitchboard sb) {
        // genera a seed for the local peer
        // this is the birthplace of a seed, that then will start to travel to other peers

        // at first we need a good peer hash
        // that hash should be as static as possible, so that it depends mainly on system
        // variables and can even then be reconstructed if the local seed has disappeared
        final Properties sp = System.getProperties();
        final String slow =
            sp.getProperty("file.encoding", "") +
            sp.getProperty("file.separator", "") +
            sp.getProperty("java.class.path", "") +
            sp.getProperty("java.vendor", "") +
            sp.getProperty("os.arch", "") +
            sp.getProperty("os.name", "") +
            sp.getProperty("path.separator", "") +
            sp.getProperty("user.dir", "") +
            sp.getProperty("user.home", "") +
            sp.getProperty("user.language", "") +
            sp.getProperty("user.name", "") +
            sp.getProperty("user.timezone", "");
        final String medium =
            sp.getProperty("java.class.version", "") +
            sp.getProperty("java.version", "") +
            sp.getProperty("os.version", "") +
            sb.getConfig("peerName", "noname");
        final String fast = Long.toString(System.currentTimeMillis());
        // the resultinh hash does not have any information than can be used to reconstruct the
        // original system information that has been collected here to create the hash
        // We simply distinuguish three parts of the hash: slow, medium and fast changing character of system idenfification
        // the Hash is constructed in such a way, that the slow part influences the main aerea of the distributed hash location
        // more than the fast part. The effect is, that if the peer looses it's seed information and is reconstructed, it
        // still hosts most information of the distributed hash the an appropriate 'position'
        final String hash =
            kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(slow)).substring(0, 4) +
            kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(medium)).substring(0, 4) +
            kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(fast)).substring(0, 4);
        yacyCore.log.logInfo("init: OWN SEED = " + hash);

        if (hash.length() != yacySeedDB.commonHashLength) {
            yacyCore.log.logSevere("YACY Internal error: distributed hash conceptual error");
            System.exit(-1);
        }

        final yacySeed newSeed = new yacySeed(hash);

        // now calculate other information about the host
        newSeed.dna.put(yacySeed.NAME, sb.getConfig("peerName", "unnamed"));
        if ((serverCore.portForwardingEnabled) && (serverCore.portForwarding != null)) {
            newSeed.dna.put(yacySeed.PORT, Integer.toString(serverCore.portForwarding.getPort()));
        } else {
            newSeed.dna.put(yacySeed.PORT, Integer.toString(serverCore.getPortNr(sb.getConfig("port", "8080"))));
        }
        newSeed.dna.put(yacySeed.BDATE, yacyCore.universalDateShortString(new Date()));
        newSeed.dna.put(yacySeed.LASTSEEN, newSeed.dna.get(yacySeed.BDATE)); // just as initial setting
        newSeed.dna.put(yacySeed.UTC, serverDate.UTCDiffString());
        newSeed.dna.put(yacySeed.PEERTYPE, yacySeed.PEERTYPE_VIRGIN);

        return newSeed;
    }

    //public static String randomHash() { return "zLXFf5lTteUv"; } // only for debugging

    public static String randomHash() {
        final String hash =
            kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(System.currentTimeMillis() + "a")).substring(0, 6) +
            kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(System.currentTimeMillis() + "b")).substring(0, 6);
        return hash;
    }

    public static yacySeed genRemoteSeed(String seedStr, String key, boolean properTest) {
        // this method is used to convert the external representation of a seed into a seed object
//      yacyCore.log.logFinest("genRemoteSeed: seedStr=" + seedStr + " key=" + key);
        if (seedStr == null) { return null; }
        final String seed = crypt.simpleDecode(seedStr, key);
        if (seed == null) { return null; }
        final HashMap dna = serverCodings.string2map(seed, ",");
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
        return this.genSeedStr('b', key);
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
        final String out = this.genSeedStr('p', null);
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

    public final Object clone() {
        synchronized (this.dna) {
            return new yacySeed(this.hash, (HashMap) (new HashMap(this.dna)).clone());
        }
    }

/*  public static void main(String[] argv) {
        try {
            plasmaSwitchboard sb = new plasmaSwitchboard("../httpProxy.init", "../httpProxy.conf");
            yacySeed ys = genLocalSeed(sb);
            String yp, yz, yc;
            System.out.println("YACY String    = " + ys.toString());
            System.out.println("YACY SeedStr/p = " + (yp = ys.genSeedStr('p', null)));
            //System.out.println("YACY SeedStr/z = " + (yz = ys.genSeedStr('z', null)));
            System.out.println("YACY SeedStr/c = " + (yc = ys.genSeedStr('c', "abc")));
            System.out.println("YACY remote/p  = " + genRemoteSeed(yp, null).toString());
            //System.out.println("YACY remote/z  = " + genRemoteSeed(yz, null).toString());
            System.out.println("YACY remote/c  = " + genRemoteSeed(yc, "abc").toString());
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    } */

}
