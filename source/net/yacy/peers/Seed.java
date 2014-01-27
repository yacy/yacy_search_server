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

package net.yacy.peers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.MapTools;
import net.yacy.kelondro.util.OS;
import net.yacy.peers.operation.yacyVersion;
import net.yacy.search.Switchboard;
import net.yacy.utils.bitfield;
import net.yacy.utils.crypt;

public class Seed implements Cloneable, Comparable<Seed>, Comparator<Seed>
{

    public static String ANON_PREFIX = "_anon";

    public static final int maxsize = 16000;
    /**
     * <b>substance</b> "sI" (send index/words)
     */
    public static final String INDEX_OUT = "sI";
    /**
     * <b>substance</b> "rI" (received index/words)
     */
    public static final String INDEX_IN = "rI";
    /**
     * <b>substance</b> "sU" (send URLs)
     */
    public static final String URL_OUT = "sU";
    /**
     * <b>substance</b> "rU" (received URLs)
     */
    public static final String URL_IN = "rU";
    /**
     * <b>substance</b> "virgin", a peer which cannot reach any other peers
     */
    public static final String PEERTYPE_VIRGIN = "virgin";
    /**
     * <b>substance</b> "junior", this is a peer which cannot be reached from the outside
     */
    public static final String PEERTYPE_JUNIOR = "junior";
    /**
     * <b>substance</b> "mentee", this is a junior peer with an mentor peer attached as 'remote' server port
     */
    public static final String PEERTYPE_MENTEE = "mentee";
    /**
     * <b>substance</b> "senior", this is a peer with an open port to the public
     */
    public static final String PEERTYPE_SENIOR = "senior";
    /**
     * <b>substance</b> "mentor", this is a senior peer which hosts server ports for mentee peers
     */
    public static final String PEERTYPE_MENTOR = "mentor";
    /**
     * <b>substance</b> "principal", a senior peer which distributes the seed list to an outside hoster (i.e. using ftp upload to a web server)
     */
    public static final String PEERTYPE_PRINCIPAL = "principal";
    /**
     * <b>substance</b> "PeerType"
     */
    public static final String PEERTYPE = "PeerType";

    /** static/dynamic (if the IP changes often for any reason) */
    private static final String IPTYPE = "IPType";
    private static final String FLAGS = "Flags";
    public static final String FLAGSZERO = "    ";
    /** the applications version */
    public static final String VERSION = "Version";

    public static final String YOURTYPE = "yourtype";
    public static final String LASTSEEN = "LastSeen";
    private static final String USPEED = "USpeed";

    /** the name of the peer (user-set) */
    public static final String NAME = "Name";
    public static final String HASH = "Hash";
    /** Birthday - first startup */
    private static final String BDATE = "BDate";
    /** UTC-Offset */
    public static final String UTC = "UTC";
    private static final String PEERTAGS = "Tags";

    /** the speed of indexing (pages/minute) of the peer */
    public static final String ISPEED = "ISpeed";
    /** the speed of retrieval (queries/minute) of the peer */
    public static final String RSPEED = "RSpeed";
    /** the number of minutes that the peer is up in minutes/day (moving average MA30) */
    public static final String UPTIME = "Uptime";
    /** the number of links that the peer has stored (LURL's) */
    public static final String LCOUNT = "LCount";
    /** the number of links that the peer has noticed, but not loaded (NURL's) */
    public static final String NCOUNT = "NCount";
    /** the number of links that the peer provides for remote crawls (ZURL's) */
    public static final String RCOUNT = "RCount";
    /** the number of different words the peer has indexed */
    public static final String ICOUNT = "ICount";
    /** the number of seeds that the peer has stored */
    public static final String SCOUNT = "SCount";
    /** the number of clients that the peer connects (connects/hour as double) */
    public static final String CCOUNT = "CCount";
    public static final String IP = "IP";
    public static final String PORT = "Port";
    public static final String SEEDLISTURL = "seedURL";
    public static final String NEWS = "news"; // news attachment
    public static final String DCT = "dct"; // disconnect time
    public static final String SOLRAVAILABLE ="SorlAvail"; // field to remember if remotePeer solr interface is avail.
    
    /** zero-value */
    private static final String ZERO = "0";

    private static final int FLAG_DIRECT_CONNECT = 0;
    private static final int FLAG_ACCEPT_REMOTE_CRAWL = 1;
    private static final int FLAG_ACCEPT_REMOTE_INDEX = 2;
    private static final int FLAG_ROOT_NODE = 3;
    private static final int FLAG_SSL_AVAILABLE = 4;

    public static final String DFLT_NETWORK_UNIT = "freeworld";
    public static final String DFLT_NETWORK_GROUP = "";

    private static final Random random = new Random(System.currentTimeMillis());

    // class variables
    /** the peer-hash */
    public final String hash;
    /** a set of identity founding values, eg. IP, name of the peer, YaCy-version, ... */
    private final ConcurrentMap<String, String> dna;
    private String alternativeIP = null;
    private long birthdate; // keep this value in ram since it is often used and may cause lockings in concurrent situations.

    public Seed(final String theHash, final ConcurrentMap<String, String> theDna) {
        // create a seed with a pre-defined hash map
        assert theHash != null;
        this.hash = theHash;
        this.dna = theDna;
        String flags = this.dna.get(Seed.FLAGS);
        if (flags == null) flags = Seed.FLAGSZERO;
        while (flags.length() < 4) flags += " ";
        this.dna.put(Seed.FLAGS, flags);
        this.dna.put(Seed.NAME, checkPeerName(get(Seed.NAME, "&empty;")));
        this.birthdate = -1; // this means 'not yet parsed', parse that later when it is used
    }

    private Seed(final String theHash) {
        this.dna = new ConcurrentHashMap<String, String>();

        // settings that can only be computed by originating peer:
        // at first startup -
        this.hash = theHash; // the hash key of the peer - very important. should be static somehow, even after restart
        this.dna.put(Seed.NAME, defaultPeerName());

        // later during operation -
        this.dna.put(Seed.ISPEED, Seed.ZERO);
        this.dna.put(Seed.RSPEED, Seed.ZERO);
        this.dna.put(Seed.UPTIME, Seed.ZERO);
        this.dna.put(Seed.LCOUNT, Seed.ZERO);
        this.dna.put(Seed.NCOUNT, Seed.ZERO);
        this.dna.put(Seed.RCOUNT, Seed.ZERO);
        this.dna.put(Seed.ICOUNT, Seed.ZERO);
        this.dna.put(Seed.SCOUNT, Seed.ZERO);
        this.dna.put(Seed.CCOUNT, Seed.ZERO);
        this.dna.put(Seed.VERSION, Seed.ZERO);

        // settings that is created during the 'hello' phase - in first contact
        this.dna.put(Seed.IP, ""); // 123.234.345.456
        this.dna.put(Seed.PORT, "&empty;");
        this.dna.put(Seed.IPTYPE, "&empty;");

        // settings that can only be computed by visiting peer
        this.dna.put(Seed.USPEED, Seed.ZERO); // the computated uplink speed of the peer

        // settings that are needed to organize the seed round-trip
        this.dna.put(Seed.FLAGS, Seed.FLAGSZERO);
        setFlagDirectConnect(false);
        setFlagAcceptRemoteCrawl(true);
        setFlagAcceptRemoteIndex(true);
        setUnusedFlags();

        // index transfer
        this.dna.put(Seed.INDEX_OUT, Seed.ZERO); // send index
        this.dna.put(Seed.INDEX_IN, Seed.ZERO); // received index
        this.dna.put(Seed.URL_OUT, Seed.ZERO); // send URLs
        this.dna.put(Seed.URL_IN, Seed.ZERO); // received URLs

        // default first filling
        this.dna.put(Seed.BDATE, GenericFormatter.SHORT_SECOND_FORMATTER.format());
        this.dna.put(Seed.LASTSEEN, this.dna.get(Seed.BDATE)); // just as initial setting
        this.dna.put(Seed.UTC, GenericFormatter.UTCDiffString());
        this.dna.put(Seed.PEERTYPE, Seed.PEERTYPE_VIRGIN); // virgin/junior/senior/principal

        this.birthdate = System.currentTimeMillis();
    }

    /**
     * check the peer name: protect against usage as XSS hack
     *
     * @param id
     * @return a checked name without "<" and ">"
     */
    private final static Pattern tp = Pattern.compile("<|>");

    public static String checkPeerName(String name) {
        name = tp.matcher(name).replaceAll("_");
        return name;
    }

    /**
     * generate a default peer name
     *
     * @return
     */
    private static String defaultPeerName() {
        return ANON_PREFIX
            + OS.infoKey()
            + "-"
            + (System.currentTimeMillis() % 77777777L)
            + "-"
            + Network.speedKey;
    }

    /**
     * Checks for the static fragments of a generated default peer name, such as the string 'dpn'
     *
     * @see #makeDefaultPeerName()
     * @param name the peer name to check for default peer name compliance
     * @return whether the given peer name may be a default generated peer name
     */
    public static boolean isDefaultPeerName(final String name) {
        return name.startsWith("_anon");
    }

    /**
     * used when doing routing within a cluster; this can assign a ip and a port that is used instead the
     * address stored in the seed DNA
     */
    public void setAlternativeAddress(final String ipport) {
        if ( ipport == null ) {
            return;
        }
        final int p = ipport.lastIndexOf(':');
        if ( p < 0 ) {
            this.alternativeIP = ipport;
        } else {
            this.alternativeIP = ipport.substring(0, p);
        }

        if (this.alternativeIP.charAt(0) == '[' && this.alternativeIP.charAt(this.alternativeIP.length() - 1) == ']') {
            // IPv6 patch
            this.alternativeIP = this.alternativeIP.substring(1, this.alternativeIP.length() - 1);
        }
    }

    /**
     * try to get the IP<br>
     *
     * @return the IP or null
     */
    public final String getIP() {
        final String ip = get(Seed.IP, Domains.LOCALHOST);
        return (ip == null || ip.isEmpty()) ? Domains.LOCALHOST : ip;
    }

    /**
     * try to get the peertype<br>
     *
     * @return the peertype or null
     */
    public final String getPeerType() {
        return get(Seed.PEERTYPE, "");
    }

    /**
     * try to get the peertype<br>
     *
     * @return the peertype or "virgin"
     */
    public final String orVirgin() {
        return get(Seed.PEERTYPE, Seed.PEERTYPE_VIRGIN);
    }

    /**
     * try to get the peertype<br>
     *
     * @return the peertype or "junior"
     */
    public final String orJunior() {
        return get(Seed.PEERTYPE, Seed.PEERTYPE_JUNIOR);
    }

    /**
     * try to get the peertype<br>
     *
     * @return the peertype or "senior"
     */
    public final String orSenior() {
        return get(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR);
    }

    /**
     * try to get the peertype<br>
     *
     * @return the peertype or "principal"
     */
    public final String orPrincipal() {
        return get(Seed.PEERTYPE, Seed.PEERTYPE_PRINCIPAL);
    }

    /**
     * Get a value from the peer's DNA (its set of peer defining values, e.g. IP, name, version, ...)
     *
     * @param key the key for the value to fetch
     * @param dflt the default value
     */
    public final String get(final String key, final String dflt) {
        final Object o = this.dna.get(key);
        if ( o == null ) {
            return dflt;
        }
        return (String) o;
    }

    public final float getFloat(final String key, final float dflt) {
        final Object o = this.dna.get(key);
        if ( o == null ) {
            return dflt;
        }
        if ( o instanceof String ) {
            try {
                return Float.parseFloat((String) o);
            } catch (final NumberFormatException e ) {
                return dflt;
            }
        } else if ( o instanceof Float ) {
            return ((Float) o).floatValue();
        } else {
            return dflt;
        }
    }

    public final long getLong(final String key, final long dflt) {
        final Object o = this.dna.get(key);
        if ( o == null ) {
            return dflt;
        }
        if ( o instanceof String ) {
            try {
                return Long.parseLong((String) o);
            } catch (final NumberFormatException e ) {
                return dflt;
            }
        } else if ( o instanceof Long ) {
            return ((Long) o).longValue();
        } else if ( o instanceof Integer ) {
            return ((Integer) o).intValue();
        } else {
            return dflt;
        }
    }

    public final void setIP(final String ip) {
        this.dna.put(Seed.IP, ip);
    }

    public final void setPort(final String port) {
        this.dna.put(Seed.PORT, port);
    }

    public final void setType(final String type) {
        this.dna.put(Seed.PEERTYPE, type);
    }

    public final void setJunior() {
        this.dna.put(Seed.PEERTYPE, Seed.PEERTYPE_JUNIOR);
    }

    public final void setSenior() {
        this.dna.put(Seed.PEERTYPE, Seed.PEERTYPE_SENIOR);
    }

    public final void setPrincipal() {
        this.dna.put(Seed.PEERTYPE, Seed.PEERTYPE_PRINCIPAL);
    }

    public final void put(final String key, final String value) {
        synchronized ( this.dna ) {
            this.dna.put(key, value);
        }
    }

    /** @return the DNA-map of this peer */
    public final ConcurrentMap<String, String> getMap() {
        return this.dna;
    }

    public final void setName(final String name) {
        synchronized ( this.dna ) {
            this.dna.put(Seed.NAME, checkPeerName(name));
        }
    }

    public final String getName() {
        return checkPeerName(get(Seed.NAME, "&empty;"));
    }

    public final String getHexHash() {
        return b64Hash2hexHash(this.hash);
    }

    public final void incSI(final int count) {
        String v = this.dna.get(Seed.INDEX_OUT);
        if ( v == null ) {
            v = Seed.ZERO;
        }
        this.dna.put(Seed.INDEX_OUT, Long.toString(Long.parseLong(v) + count));
    }

    public final void incRI(final int count) {
        String v = this.dna.get(Seed.INDEX_IN);
        if ( v == null ) {
            v = Seed.ZERO;
        }
        this.dna.put(Seed.INDEX_IN, Long.toString(Long.parseLong(v) + count));
    }

    public final void incSU(final int count) {
        String v = this.dna.get(Seed.URL_OUT);
        if ( v == null ) {
            v = Seed.ZERO;
        }
        this.dna.put(Seed.URL_OUT, Long.toString(Long.parseLong(v) + count));
    }

    public final void incRU(final int count) {
        String v = this.dna.get(Seed.URL_IN);
        if ( v == null ) {
            v = Seed.ZERO;
        }
        this.dna.put(Seed.URL_IN, Long.toString(Long.parseLong(v) + count));
    }

    public final void resetCounters() {
        this.dna.put(Seed.INDEX_OUT, Seed.ZERO);
        this.dna.put(Seed.INDEX_IN, Seed.ZERO);
        this.dna.put(Seed.URL_OUT, Seed.ZERO);
        this.dna.put(Seed.URL_IN, Seed.ZERO);
    }

    /**
     * <code>12 * 6 bit = 72 bit = 24</code> characters octal-hash
     * <p>
     * Octal hashes are used for cache-dumps that are DHT-ready
     * </p>
     * <p>
     * Cause: the natural order of octal hashes are the same as the b64-order of b64Hashes. a hexhash cannot
     * be used in such cases, and b64Hashes are not appropriate for file names
     * </p>
     *
     * @param b64Hash a base64 hash
     * @return the octal representation of the given base64 hash
     */
    public static String b64Hash2octalHash(final String b64Hash) {
        return Digest.encodeOctal(Base64Order.enhancedCoder.decode(b64Hash));
    }

    /**
     * <code>12 * 6 bit = 72 bit = 18</code> characters hex-hash
     *
     * @param b64Hash a base64 hash
     * @return the hexadecimal representation of the given base64 hash
     */
    public static String b64Hash2hexHash(final String b64Hash) {
        if ( b64Hash.length() > 12 ) {
            return "";
        }
        // the hash string represents 12 * 6 bit = 72 bits. This is too much for a long integer.
        return Digest.encodeHex(Base64Order.enhancedCoder.decode(b64Hash));
    }

    /**
     * @param hexHash a hexadecimal hash
     * @return the base64 representation of the given hex hash
     */
    public static String hexHash2b64Hash(final String hexHash) {
        return Base64Order.enhancedCoder.encode(Digest.decodeHex(hexHash));
    }

    /**
     * The returned version follows this pattern: <code>MAJORVERSION . MINORVERSION 0 SVN REVISION</code>
     *
     * @return the YaCy version of this peer as a float or <code>0</code> if no valid value could be retrieved
     *         from this yacySeed object
     */
    public final Double getVersion() {
        try {
            return Double.parseDouble(get(Seed.VERSION, Seed.ZERO));
        } catch (final NumberFormatException e ) {
            return 0.0d;
        }
    }

    /**
     * get the SVN version of the peer
     *
     * @return
     */
    public final int getRevision() {
        return yacyVersion.revision(get(Seed.VERSION, Seed.ZERO));
    }

    /**
     * @return the public address of the peer as IP:port string or <code>null</code> if no valid values for
     *         either the IP or the port could be retrieved from this yacySeed object
     */
    public final String getPublicAddress() {
        String ip = getIP();
        if (ip == null) ip = Domains.LOCALHOST; // that should not happen

        int p = ip.lastIndexOf(':');
        if (p > 0 && (ip.indexOf(':') == p || "]:".equals(ip.substring(p - 1, p + 1)))) return ip; // includes already the port

        final String port = this.dna.get(Seed.PORT);
        if ( port == null || port.length() < 2 || port.length() > 5 ) {
            return null;
        }

        final StringBuilder sb = new StringBuilder(ip.length() + port.length() + 3);
        if (ip.indexOf(':') >= 0) {
            // IPv6 Address!, see: http://en.wikipedia.org/wiki/IPv6_address#Literal_IPv6_addresses_in_network_resource_identifiers
            if (!ip.startsWith("[")) sb.append('[');
            sb.append(ip);
            if (!ip.endsWith("]")) sb.append(']');
            sb.append(':');
            sb.append(port);
        } else {
            sb.append(ip);
            sb.append(':');
            sb.append(port);
        }
        return sb.toString();
    }

    /**
     * If this seed is part of a cluster, the peer has probably the {@linkplain #alternativeIP} object set to
     * a local IP. If this is present and the public IP of this peer is identical to the public IP of the own
     * seed, construct an address using this IP; otherwise return the public address
     *
     * @see #getPublicAddress()
     * @return the alternative IP:port if present, else the public address
     */
    public final String getClusterAddress() {
        if ( this.alternativeIP == null ) {
            return getPublicAddress();
        }

        final String port = this.dna.get(Seed.PORT);
        if ( port == null || port.length() < 2 || port.length() > 5 ) {
            return null;
        }

        final StringBuilder sb = new StringBuilder(this.alternativeIP.length() + port.length() + 3);
        if (this.alternativeIP.indexOf(':') >= 0) {
            // IPv6 Address!, see: http://en.wikipedia.org/wiki/IPv6_address#Literal_IPv6_addresses_in_network_resource_identifiers
            sb.append('[');
            sb.append(this.alternativeIP);
            sb.append(']');
            sb.append(':');
            sb.append(port);
        } else {
            sb.append(this.alternativeIP);
            sb.append(':');
            sb.append(port);
        }
        return sb.toString();
    }

    /**
     * @return the IP address of the peer represented by this yacySeed object as {@link InetAddress}
     */
    public final InetAddress getInetAddress() {
        return Domains.dnsResolve(getIP());
    }

    /** @return the port number of this seed or <code>-1</code> if not present */
    public final int getPort() {
        final String port = this.dna.get(Seed.PORT);
        if ( port == null ) {
            return -1;
        }
        /*if (port.length() < 2) return -1; It is possible to use port 0-9*/
        return Integer.parseInt(port);
    }

    /** puts the current time into the lastseen field and cares about the time differential to UTC */
    public final void setLastSeenUTC() {
        // because java thinks it must apply the UTC offset to the current time,
        // to create a string that looks like our current time, it adds the local UTC offset to the
        // time. To create a corrected UTC Date string, we first subtract the local UTC offset.
        final GenericFormatter my_SHORT_SECOND_FORMATTER =
            new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second); // use our own formatter to prevent concurrency locks with other processes
        final String ls =
            my_SHORT_SECOND_FORMATTER
                .format(new Date(System.currentTimeMillis() /*- DateFormatter.UTCDiff()*/));
        //System.out.println("SETTING LAST-SEEN of " + this.getName() + " to " + ls);
        this.dna.put(Seed.LASTSEEN, ls);
    }

    /**
     * @return the last seen time converted to UTC in milliseconds
     */
    public final long getLastSeenUTC() {
        try {
            final GenericFormatter my_SHORT_SECOND_FORMATTER =
                new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second); // use our own formatter to prevent concurrency locks with other processes
            final long t = my_SHORT_SECOND_FORMATTER.parse(get(Seed.LASTSEEN, "20040101000000")).getTime();
            // getTime creates a UTC time number. But in this case java thinks, that the given
            // time string is a local time, which has a local UTC offset applied.
            // Therefore java subtracts the local UTC offset, to get a UTC number.
            // But the given time string is already in UTC time, so the subtraction
            // of the local UTC offset is wrong. We correct this here by adding the local UTC
            // offset again.
            return t /*+ DateFormatter.UTCDiff()*/;
        } catch (final java.text.ParseException e ) { // in case of an error make seed look old!!!
            return System.currentTimeMillis() - AbstractFormatter.dayMillis;
        } catch (final java.lang.NumberFormatException e ) {
            return System.currentTimeMillis() - AbstractFormatter.dayMillis;
        }
    }

    /**
     * test if the lastSeen time of the seed has a time-out
     *
     * @param milliseconds the maximum age of the last-seen value
     * @return true, if the time between the last-seen time and now is greater then the given time-out
     */
    public final boolean isLastSeenTimeout(final long milliseconds) {
        final long d = Math.abs(System.currentTimeMillis() - getLastSeenUTC());
        return d > milliseconds;
    }

    public final long getBirthdate() {
        if ( this.birthdate > 0 ) {
            return this.birthdate;
        }
        long b;
        try {
            final GenericFormatter my_SHORT_SECOND_FORMATTER =
                new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second); // use our own formatter to prevent concurrency locks with other processes
            b = my_SHORT_SECOND_FORMATTER.parse(get(Seed.BDATE, "20040101000000")).getTime();
        } catch (final ParseException e ) {
            b = System.currentTimeMillis();
        }
        this.birthdate = b;
        return this.birthdate;
    }

    /** @return the age of the seed in number of days */
    public final int getAge() {
        return (int) Math.abs((System.currentTimeMillis() - getBirthdate()) / 1000 / 60 / 60 / 24);
    }

    public void setPeerTags(final Set<String> keys) {
        this.dna.put(PEERTAGS, MapTools.set2string(keys, "|", false));
    }

    public Set<String> getPeerTags() {
        return MapTools.string2set(get(PEERTAGS, "*"), "|");
    }

    public boolean matchPeerTags(final HandleSet searchHashes) {
        final String peertags = get(PEERTAGS, "");
        if ( peertags.equals("*") ) {
            return true;
        }
        final Set<String> tags = MapTools.string2set(peertags, "|");
        final Iterator<String> i = tags.iterator();
        while ( i.hasNext() ) {
            if ( searchHashes.has(Word.word2hash(i.next())) ) {
                return true;
            }
        }
        return false;
    }

    public int getPPM() {
        try {
            return Integer.parseInt(get(Seed.ISPEED, Seed.ZERO));
        } catch (final NumberFormatException e ) {
            return 0;
        }
    }

    public float getQPM() {
        try {
            return Float.parseFloat(get(Seed.RSPEED, Seed.ZERO));
        } catch (final NumberFormatException e ) {
            return 0f;
        }
    }

    public final long getLinkCount() {
        try {
            return getLong(Seed.LCOUNT, 0);
        } catch (final NumberFormatException e ) {
            return 0;
        }
    }

    public final long getWordCount() {
        try {
            return getLong(Seed.ICOUNT, 0);
        } catch (final NumberFormatException e ) {
            return 0;
        }
    }

    private boolean getFlag(final int flag) {
        final String flags = get(Seed.FLAGS, Seed.FLAGSZERO);
        return (new bitfield(ASCII.getBytes(flags))).get(flag);
    }

    private void setFlag(final int flag, final boolean value) {
        String flags = get(Seed.FLAGS, Seed.FLAGSZERO);
        if ( flags.length() != 4 ) {
            flags = Seed.FLAGSZERO;
        }
        final bitfield f = new bitfield(ASCII.getBytes(flags));
        f.set(flag, value);
        this.dna.put(Seed.FLAGS, UTF8.String(f.getBytes()));
    }

    public final void setFlagDirectConnect(final boolean value) {
        setFlag(FLAG_DIRECT_CONNECT, value);
    }

    public final boolean getFlagDirectConnect() {
        return getFlag(FLAG_DIRECT_CONNECT);
    }

    public final void setFlagAcceptRemoteCrawl(final boolean value) {
        setFlag(FLAG_ACCEPT_REMOTE_CRAWL, value);
    }

    public final boolean getFlagAcceptRemoteCrawl() {
        //if (getVersion() < 0.300) return false;
        //if (getVersion() < 0.334) return true;
        return getFlag(FLAG_ACCEPT_REMOTE_CRAWL);
    }

    public final void setFlagAcceptRemoteIndex(final boolean value) {
        setFlag(FLAG_ACCEPT_REMOTE_INDEX, value);
    }

    public final boolean getFlagAcceptRemoteIndex() {
        //if (getVersion() < 0.335) return false;
        return getFlag(FLAG_ACCEPT_REMOTE_INDEX);
    }

    public final void setFlagRootNode(final boolean value) {
        setFlag(FLAG_ROOT_NODE, value);
    }

    public final boolean getFlagRootNode() {
        double v = getVersion();
        if (v < 1.02009142d) return false;
        return getFlag(FLAG_ROOT_NODE);
    }

    public final void setFlagSSLAvailable(final boolean value) {
        setFlag(FLAG_SSL_AVAILABLE, value);
    }

    public final boolean getFlagSSLAvailable() {
        if (getVersion() < 1.5) return false;
        return getFlag(FLAG_SSL_AVAILABLE);
    }

    /**
     * remembers status of remote Solr interface dynamicly
     * should not be used for the local peer
     * @param value
     */
    public final void setFlagSolrAvailable(final boolean value) {
         if (value) 
             this.dna.put(Seed.SOLRAVAILABLE, "OK");
         else
             this.dna.put(Seed.SOLRAVAILABLE, "NA");
    }

    /**
     * gets the last set result for remote solr status
     *
     * @return if status unknown it returns true
     */
    public final boolean getFlagSolrAvailable() {
        // field is indented to deal with 3 states
        // null = never checked,  "OK"  and "NA" for not available
        String solravail = this.dna.get(Seed.SOLRAVAILABLE);
        boolean my = (solravail != null) && ("NA".equals(solravail));
        return !my;
    }

    public final void setUnusedFlags() {
        for ( int i = 4; i < 20; i++ ) {
            setFlag(i, false);
        }
    }

    public final boolean isType(final String type) {
        return get(Seed.PEERTYPE, "").equals(type);
    }

    public final boolean isVirgin() {
        return get(Seed.PEERTYPE, "").equals(Seed.PEERTYPE_VIRGIN);
    }

    public final boolean isJunior() {
        return get(Seed.PEERTYPE, "").equals(Seed.PEERTYPE_JUNIOR);
    }

    public final boolean isSenior() {
        return get(Seed.PEERTYPE, "").equals(Seed.PEERTYPE_SENIOR);
    }

    public final boolean isPrincipal() {
        return get(Seed.PEERTYPE, "").equals(Seed.PEERTYPE_PRINCIPAL);
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
        return type.equals(Seed.PEERTYPE_SENIOR) || type.equals(Seed.PEERTYPE_PRINCIPAL);
    }

    public long nextLong(final Random random, final long n) {
        return Math.abs(random.nextLong()) % n;
    }

    private static byte[] bestGap(final SeedDB seedDB) {
        final byte[] randomHash = randomHash();
        if ( seedDB == null || seedDB.sizeConnected() <= 2 ) {
            // use random hash
            return randomHash;
        }
        // find gaps
        final TreeMap<Long, String> gaps = hashGaps(seedDB);

        // take one gap; prefer biggest but take also another smaller by chance
        String interval = null;
        while ( !gaps.isEmpty() ) {
            interval = gaps.remove(gaps.lastKey());
            if ( random.nextBoolean() ) {
                break;
            }
        }
        if ( interval == null ) {
            return randomHash();
        }

        // find dht position and size of gap
        final long left = Distribution.horizontalDHTPosition(ASCII.getBytes(interval.substring(0, 12)));
        final long right = Distribution.horizontalDHTPosition(ASCII.getBytes(interval.substring(12)));
        final long gap8 = Distribution.horizontalDHTDistance(left, right) >> 3; //  1/8 of a gap
        final long gapx = gap8 + (Math.abs(random.nextLong()) % (6 * gap8));
        final long gappos = (Long.MAX_VALUE - left >= gapx) ? left + gapx : (left - Long.MAX_VALUE) + gapx;
        final byte[] computedHash = Distribution.positionToHash(gappos);
        // the computed hash is the perfect position (modulo gap4 population and gap alternatives)
        // this is too tight. The hash must be more randomized. We take only (!) the first two bytes
        // of the computed hash and add random bytes at the remaining positions. The first two bytes
        // of the hash may have 64*64 = 2^^10 positions, good for over 1 million peers.
        byte[] combined = new byte[12];
        System.arraycopy(computedHash, 0, combined, 0, 2);
        System.arraycopy(randomHash, 2, combined, 2, 10);
        // patch for the 'first sector' problem
        if ( combined[0] == 'A' || combined[1] == 'D' ) { // for some strange reason there are too many of them
            combined[1] = randomHash[1];
        }
        // finally check if the hash is already known
        while ( seedDB.hasConnected(combined)
            || seedDB.hasDisconnected(combined)
            || seedDB.hasPotential(combined) ) {
            // if we are lucky then this loop will never run
            combined = randomHash();
        }
        return combined;
    }

    private static TreeMap<Long, String> hashGaps(final SeedDB seedDB) {
        final TreeMap<Long, String> gaps = new TreeMap<Long, String>();
        if ( seedDB == null ) {
            return gaps;
        }

        final Iterator<Seed> i = seedDB.seedsConnected(true, false, null, (float) 0.0);
        long l;
        Seed s0 = null, s1, first = null;
        while ( i.hasNext() ) {
            s1 = i.next();
            if ( s0 == null ) {
                s0 = s1;
                first = s0;
                continue;
            }
            l = Distribution.horizontalDHTDistance(
                            Distribution.horizontalDHTPosition(ASCII.getBytes(s0.hash)),
                            Distribution.horizontalDHTPosition(ASCII.getBytes(s1.hash)));
            gaps.put(l, s0.hash + s1.hash);
            s0 = s1;
        }
        // compute also the last gap
        if ( (first != null) && (s0 != null) ) {
            l = Distribution.horizontalDHTDistance(
                            Distribution.horizontalDHTPosition(ASCII.getBytes(s0.hash)),
                            Distribution.horizontalDHTPosition(ASCII.getBytes(first.hash)));
            gaps.put(l, s0.hash + first.hash);
        }
        return gaps;
    }

    public static Seed genLocalSeed(final SeedDB db) {
        // generate a seed for the local peer (as anonymous peer)
        // this is the birthplace of a seed, that then will start to travel to other peers

        final String hashs = ASCII.String(bestGap(db));
        Network.log.info("init: OWN SEED = " + hashs);

        final Seed newSeed = new Seed(hashs);

        // now calculate other information about the host
        final long port = Switchboard.getSwitchboard().getConfigLong("port", 8090); //get port from config
        newSeed.dna.put(Seed.NAME, defaultPeerName() );
        newSeed.dna.put(Seed.PORT, Long.toString(port));
        return newSeed;
    }

    //public static String randomHash() { return "zLXFf5lTteUv"; } // only for debugging

    public static byte[] randomHash() {
        final String hash =
            Base64Order.enhancedCoder
                .encode(Digest.encodeMD5Raw(Long.toString(random.nextLong())))
                .substring(0, 6)
                + Base64Order.enhancedCoder
                    .encode(Digest.encodeMD5Raw(Long.toString(random.nextLong())))
                    .substring(0, 6);
        return ASCII.getBytes(hash);
    }

    public static Seed genRemoteSeed(
        final String seedStr,
        final boolean ownSeed,
        final String patchIP) throws IOException {
        // this method is used to convert the external representation of a seed into a seed object
        // yacyCore.log.logFinest("genRemoteSeed: seedStr=" + seedStr + " key=" + key);

        // check protocol and syntax of seed
        if ( seedStr == null ) {
            throw new IOException("seedStr == null");
        }
        if ( seedStr.isEmpty() ) {
            throw new IOException("seedStr.isEmpty()");
        }
        final String seed = crypt.simpleDecode(seedStr);
        if ( seed == null ) {
            throw new IOException("seed == null");
        }
        if ( seed.isEmpty() ) {
            throw new IOException("seed.isEmpty()");
        }

        // extract hash
        final ConcurrentHashMap<String, String> dna = MapTools.string2map(seed, ",");
        final String hash = dna.remove(Seed.HASH);
        if ( hash == null ) {
            throw new IOException("hash == null");
        }
        final Seed resultSeed = new Seed(hash, dna);

        // check semantics of content
        String testResult = resultSeed.isProper(ownSeed);
        if ( testResult != null && patchIP != null ) {
            // in case that this proper-Test fails and a patchIP is given
            // then replace the given IP in the resultSeed with given patchIP
            // this is done if a remote peer reports its IP in a wrong way (maybe fraud attempt)
            resultSeed.setIP(patchIP);
            testResult = resultSeed.isProper(ownSeed);
        }
        if ( testResult != null ) {
            throw new IOException("seed is not proper (" + testResult + "): " + resultSeed);
        }
        //assert resultSeed.toString().equals(seed) : "\nresultSeed.toString() = " + resultSeed.toString() + ",\n                 seed = " + seed; // debug

        // seed ok
        return resultSeed;
    }

    // TODO: add here IP ranges to accept also intranet networks
    public final String isProper(final boolean checkOwnIP) {
        // checks if everything is ok with that seed

        // check hash
        if ( this.hash == null ) {
            return "hash is null";
        }
        if ( this.hash.length() != Word.commonHashLength ) {
            return "wrong hash length (" + this.hash.length() + ")";
        }

        // name
        final String peerName = this.dna.get(Seed.NAME);
        if ( peerName == null ) {
            return "no peer name given";
        }
        this.dna.put(Seed.NAME, checkPeerName(peerName));

        // type
        final String peerType = getPeerType();
        if ( (peerType == null)
            || !(peerType.equals(Seed.PEERTYPE_VIRGIN)
                || peerType.equals(Seed.PEERTYPE_JUNIOR)
                || peerType.equals(Seed.PEERTYPE_SENIOR) || peerType.equals(Seed.PEERTYPE_PRINCIPAL)) ) {
            return "invalid peerType '" + peerType + "'";
        }

        // check IP
        if ( !checkOwnIP ) {
            // checking of IP is omitted if we read the own seed file
            final String ipCheck = isProperIP(getIP());
            if ( ipCheck != null ) {
                return ipCheck;
            }
        }

        // seedURL
        final String seedURL = this.dna.get(SEEDLISTURL);
        if ( seedURL != null && !seedURL.isEmpty() ) {
            if ( !seedURL.startsWith("http://") && !seedURL.startsWith("https://") ) {
                return "wrong protocol for seedURL";
            }
            try {
                final URL url = new URL(seedURL);
                final String host = url.getHost();
                if (Domains.isIntranet(host)) {
                    return "seedURL in local network rejected";
                }
            } catch (final MalformedURLException e ) {
                return "seedURL malformed";
            }
        }
        return null;
    }

    public static final String isProperIP(final String ipString) {
        // returns null if ipString is proper, a string with the cause otherwise
        if ( ipString == null ) {
            return ipString + " -> IP is null";
        }
        if ( ipString.length() < 8 ) {
            return ipString + " -> IP is too short: ";
        }
        if ( Switchboard.getSwitchboard().isAllIPMode() ) {
            return null;
        }
        final boolean islocal = Domains.isLocal(ipString, null);
        //if (islocal && Switchboard.getSwitchboard().isGlobalMode()) return ipString + " - local IP for global mode rejected";
        if ( !islocal && Switchboard.getSwitchboard().isIntranetMode() ) {
            return ipString + " - global IP for intranet mode rejected";
        }
        return null;
    }

    @Override
    public final String toString() {
        final ConcurrentMap<String, String> copymap = new ConcurrentHashMap<String, String>();
        copymap.putAll(this.dna);
        copymap.put(Seed.HASH, this.hash); // set hash into seed code structure
        String s = MapTools.map2string(copymap, ",", true); // generate string representation
        return s;
    }

    public final String genSeedStr(final String key) {
        // use a default encoding
        final String r = toString();
        final String z = crypt.simpleEncode(r, key, 'z');
        final String b = crypt.simpleEncode(r, key, 'b');
        // the compressed string may be longer than the uncompressed if there is too much overhead for compression meta-info
        // take simply that string which is shorter
        return ( b.length() < z.length() ) ? b : z;
    }

    public final void save(final File f) throws IOException {
        final String out = crypt.simpleEncode(toString(), null, 'p');
        final FileWriter fw = new FileWriter(f);
        fw.write(out, 0, out.length());
        fw.close();
    }

    public static Seed load(final File f) throws IOException {
        final FileReader fr = new FileReader(f);
        final char[] b = new char[(int) f.length()];
        fr.read(b, 0, b.length);
        fr.close();
        final Seed mySeed = genRemoteSeed(new String(b), true, null);
        assert mySeed != null; // in case of an error, an IOException is thrown
        mySeed.dna.put(Seed.IP, ""); // set own IP as unknown
        return mySeed;
    }

    @Override
    public final Seed clone() {
        final ConcurrentHashMap<String, String> ndna = new ConcurrentHashMap<String, String>();
        ndna.putAll(this.dna);
        return new Seed(this.hash, ndna);
    }

    @Override
    public int compareTo(final Seed arg0) {
        final int o1 = hashCode();
        final int o2 = arg0.hashCode();
        if ( o1 > o2 ) {
            return 1;
        }
        if ( o2 > o1 ) {
            return -1;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        return (int) (Base64Order.enhancedCoder.cardinal(this.hash) & (Integer.MAX_VALUE));
    }

    @Override
    public int compare(final Seed o1, final Seed o2) {
        return o1.compareTo(o2);
    }

    @Override
    public boolean equals(Object other) {
    	return this.hash.equals(((Seed) other).hash);
    }
    		
    public static void main(final String[] args) {
        final ScoreMap<Integer> s = new ClusteredScoreMap<Integer>();
        for ( int i = 0; i < 10000; i++ ) {
            final byte[] b = randomHash();
            s.inc(0xff & Base64Order.enhancedCoder.decodeByte(b[0]));
            //System.out.println(ASCII.String(b));
        }
        final Iterator<Integer> i = s.keys(false);
        while ( i.hasNext() ) {
            System.out.println(i.next());
        }
    }

}
