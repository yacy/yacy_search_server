// indexURL.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 20.05.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRAMIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroTree;
import de.anomic.net.URL;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacySeedDB;

public class indexURL {

 // day formatter for entry export
 protected static final SimpleDateFormat shortDayFormatter = new SimpleDateFormat("yyyyMMdd");
 
 // statics for value lengths
 public static final int urlHashLength               = yacySeedDB.commonHashLength; // 12
 public static final int urlStringLength             = 256;// not too short for links without parameters
 public static final int urlDescrLength              = 80; // The headline of a web page (meta-tag or <h1>)
 public static final int urlNameLength               = 40; // the tag content between <a> and </a>
 public static final int urlErrorLength              = 80; // a reason description for unavailable urls
 public static final int urlDateLength               = 4;  // any date, shortened
 public static final int urlCopyCountLength          = 2;  // counter for numbers of copies of this index
 public static final int urlFlagLength               = 2;  // any stuff
 public static final int urlQualityLength            = 3;  // taken from heuristic
 public static final int urlLanguageLength           = 2;  // taken from TLD suffix as quick-hack
 public static final int urlDoctypeLength            = 1;  // taken from extension
 public static final int urlSizeLength               = 6;  // the source size, from cache
 public static final int urlWordCountLength          = 3;  // the number of words, from condenser
 public static final int urlCrawlProfileHandleLength = 4;  // name of the prefetch profile
 public static final int urlCrawlDepthLength         = 2;  // prefetch depth, first is '0'
 public static final int urlParentBranchesLength     = 3;  // number of anchors of the parent
 public static final int urlForkFactorLength         = 4;  // sum of anchors of all ancestors
 public static final int urlRetryLength              = 2;  // number of load retries
 public static final int urlHostLength               = 8;  // the host as struncated name
 public static final int urlHandleLength             = 4;  // a handle
 
 private static final String[] TLD_NorthAmericaOceania={
    // primary english-speaking countries
    // english-speaking countries from central america are also included
    // includes also dutch and french colonies in the caribbean sea
     "EDU=US Educational",
     "GOV=US Government",
     "MIL=US Military",
     "NET=Network",
     "ORG=Non-Profit Organization",
     "AG=Antigua and Barbuda",
     "AI=Anguilla",
     "AU=Australia",
     "BB=Barbados",
     "BZ=Belize",
     "BM=Bermuda",
     "BS=Bahamas",
     "CA=Canada",
     "DM=Dominica",
     "GD=Grenada",
     "GP=Guadeloupe",
     "KY=Cayman Islands",
     "NZ=New Zealand (Aotearoa)",
     "PM=St. Pierre and Miquelon",
     "US=United States",
     "VC=Saint Vincent and the Grenadines",
     "VG=Virgin Islands (British)",
     "VI=Virgin Islands (U.S.)",
     "VU=Vanuatu",
     "WF=Wallis and Futuna Islands",
     "WS=Samoa"
 };
 private static final String[] TLD_MiddleSouthAmerica = {
     // primary spanish and portugese-speaking
     "AR=Argentina",
     "AW=Aruba",
     "BR=Brazil",
     "BO=Bolivia",
     "CL=Chile",
     "CO=Colombia",
     "CR=Costa Rica",
     "CU=Cuba",
     "DO=Dominican Republic",
     "EC=Ecuador",
     "GF=French Guiana",
     "FK=Falkland Islands (Malvinas)",
     "GY=Guyana",
     "HN=Honduras",
     "JM=Jamaica",
     "MX=Mexico",
     "NI=Nicaragua",
     "PA=Panama",
     "PE=Peru",
     "PY=Paraguay",
     "SR=Suriname",
     "SV=El Salvador",
     "UY=Uruguay",
     "VE=Venezuela"
 };
 private static final String[] TLD_EuropaRussia = {
    // includes also countries that are mainly french- dutch- speaking
    // and culturally close to europe
     "AD=Andorra",
     "AL=Albania",
     "AT=Austria",
     "BA=Bosnia and Herzegovina",
     "BE=Belgium",
     "BG=Bulgaria",
     "CH=Switzerland",
     "CS=Czechoslovakia (former)",
     "CZ=Czech Republic",
     "CY=Cyprus",
     "DE=Germany",
     "DK=Denmark",
     "ES=Spain",
     "EE=Estonia",
     "FI=Finland",
     "FR=France",
     "FX=France, Metropolitan",
     "GB=Great Britain (UK)",
     "GI=Gibraltar",
     "GL=Greenland",
     "GR=Greece",
     "HR=Croatia (Hrvatska)",
     "HU=Hungary",
     "IE=Ireland",
     "IS=Iceland",
     "IT=Italy",
     "LI=Liechtenstein",
     "LT=Lithuania",
     "LU=Luxembourg",
     "LV=Latvia",
     "MD=Moldova",
     "MC=Monaco",
     "MK=Macedonia",
     "MN=Mongolia",
     "MT=Malta",
     "NATO=Nato field",
     "NL=Netherlands",
     "NO=Norway",
     "PL=Poland",
     "PT=Portugal",
     "RO=Romania",
     "RU=Russia",
     "SE=Sweden",
     "SI=Slovenia",
     "SK=Slovak Republic",
     "SU=USSR (former)",
     "UK=United Kingdom",
     "VA=Vatican City State (Holy See)",
     "YU=Yugoslavia"
 };
 private static final String[] TLD_MiddleEastWestAsia = {
     "AE=United Arab Emirates",
     "AF=Afghanistan",
     "AZ=Azerbaijan",
     "BH=Bahrain",
     "IL=Israel",
     "IQ=Iraq",
     "IR=Iran",
     "PK=Pakistan",
     "YE=Yemen"
 };
 private static final String[] TLD_SouthEastAsia = {
     "BD=Bangladesh",
     "BT=Bhutan",
     "CN=China",
     "HK=Hong Kong",
     "ID=Indonesia",
     "IN=India",
     "NP=Nepal",
     "JP=Japan",
     "KH=Cambodia",
     "KP=Korea (North)",
     "KR=Korea (South)",
     "LK=Sri Lanka",
     "SG=Singapore",
     "VN=Viet Nam"
 };
 private static final String[] TLD_Africa = {
     "AO=Angola",
     "BF=Burkina Faso",
     "BI=Burundi",
     "BJ=Benin",
     "BW=Botswana",
     "CF=Central African Republic",
     "CG=Congo",
     "CI=Cote D'Ivoire (Ivory Coast)",
     "CM=Cameroon",
     "DZ=Algeria",
     "EG=Egypt",
     "EH=Western Sahara",
     "ER=Eritrea",
     "ET=Ethiopia",
     "GA=Gabon",
     "GH=Ghana",
     "GM=Gambia",
     "GN=Guinea",
     "KE=Kenya",
     "LR=Liberia",
     "LS=Lesotho",
     "LY=Libya",
     "MA=Morocco",
     "MG=Madagascar",
     "ML=Mali",
     "MR=Mauritania",
     "MU=Mauritius",
     "MW=Malawi",
     "MZ=Mozambique",
     "NA=Namibia",
     "NE=Niger",
     "NG=Nigeria",
     "RE=Reunion",
     "RW=Rwanda",
     "SH=St. Helena",
     "SL=Sierra Leone",
     "SN=Senegal",
     "SO=Somalia",
     "ST=Sao Tome and Principe",
     "SZ=Swaziland",
     "TG=Togo",
     "TN=Tunisia",
     "TZ=Tanzania",
     "UG=Uganda",
     "ZA=South Africa",
     "ZM=Zambia",
     "ZR=Zaire",
     "ZW=Zimbabwe",
     "YT=Mayotte"
 };
 private static final String[] TLD_Generic = {
     "COM=US Commercial",
     "AERO=",
     "BIZ=",
     "COOP=",
     "INFO=",
     "MUSEUM=",
     "NAME=",
     "PRO=",
     "ARPA=",
     "INT=International",
     "ARPA=Arpanet"
 };
 private static final String[] TLD_Unassigned = {
     "AQ=Antarctica",
     "NT=Neutral Zone"
 };

 /*
     http://www.odci.gov/cia/publications/factbook/
     http://en.wikipedia.org/wiki/List_of_countries_by_continent
     "AM=Armenia",
     "AN=Netherlands Antilles",
     "AS=American Samoa",
     "BN=Brunei Darussalam",
     "BV=Bouvet Island",
     "BY=Belarus",
     "CC=Cocos (Keeling) Islands",
     "CK=Cook Islands",
     "CV=Cape Verde",
     "CX=Christmas Island",
     "DJ=Djibouti",
     "FJ=Fiji",
     "FM=Micronesia",
     "FO=Faroe Islands",
     "GE=Georgia",
     "GQ=Equatorial Guinea",
     "GS=S. Georgia and S. Sandwich Isls.",
     "GT=Guatemala",
     "GU=Guam",
     "GW=Guinea-Bissau",
     "HM=Heard and McDonald Islands",
     "HT=Haiti",
     "IO=British Indian Ocean Territory",
     "JO=Jordan",
     "KG=Kyrgyzstan",
     "KI=Kiribati",
     "KM=Comoros",
     "KN=Saint Kitts and Nevis",
     "KW=Kuwait",
     "KZ=Kazakhstan",
     "LA=Laos",
     "LB=Lebanon",
     "LC=Saint Lucia",
     "MH=Marshall Islands",
     "MM=Myanmar",
     "MO=Macau",
     "MP=Northern Mariana Islands",
     "MQ=Martinique",
     "MS=Montserrat",
     "MV=Maldives",
     "MY=Malaysia",
     "NC=New Caledonia",
     "NF=Norfolk Island",
     "NR=Nauru",
     "NU=Niue",
     "OM=Oman",
     "PF=French Polynesia",
     "PG=Papua New Guinea",
     "PH=Philippines",
     "PN=Pitcairn",
     "PR=Puerto Rico",
     "PW=Palau",
     "QA=Qatar",
     "SA=Saudi Arabia",
     "Sb=Solomon Islands",
     "SC=Seychelles",
     "SD=Sudan",
     "SJ=Svalbard and Jan Mayen Islands",
     "SM=San Marino",
     "SY=Syria",
     "TC=Turks and Caicos Islands",
     "TD=Chad",
     "TF=French Southern Territories",
     "TH=Thailand",
     "TJ=Tajikistan",
     "TK=Tokelau",
     "TM=Turkmenistan",
     "TO=Tonga",
     "TP=East Timor",
     "TR=Turkey",
     "TT=Trinidad and Tobago",
     "TV=Tuvalu",
     "TW=Taiwan",
     "UA=Ukraine",
     "UM=US Minor Outlying Islands",
     "UZ=Uzbekistan",
  */
 
 /* TLDs:
 aero, biz, com, coop, edu, gov, info, int, mil, museum, name, net, org, pro, arpa
 AC, AD, AE, AERO, AF, AG, AI, AL, AM, AN, AO, AQ, AR, ARPA, AS, AT, AU, AW, AZ,
 BA, BB, BD, BE, BF, BG, BH, BI, BIZ, BJ, BM, BN, BO, BR, BS, BT, BV, BW, BY, BZ,
 CA, CC, CD, CF, CG, CH, CI, CK, CL, CM, CN, CO, COM, COOP, CR, CU, CV, CX, CY, CZ,
 DE, DJ, DK, DM, DO, DZ, EC, EDU, EE, EG, ER, ES, ET, EU, FI, FJ, FK, FM, FO, FR,
 GA, GB, GD, GE, GF, GG, GH, GI, GL, GM, GN, GOV, GP, GQ, GR, GS, GT, GU, GW, GY,
 HK, HM, HN, HR, HT, HU, ID, IE, IL, IM, IN, INFO, INT, IO, IQ, IR, IS, IT,
 JE, JM, JO, JOBS, JP, KE, KG, KH, KI, KM, KN, KR, KW, KY, KZ,
 LA, LB, LC, LI, LK, LR, LS, LT, LU, LV, LY,
 MA, MC, MD, MG, MH, MIL, MK, ML, MM, MN, MO, MOBI, MP, MQ, MR, MS, MT, MU, MUSEUM, MV, MW, MX, MY, MZ,
 NA, NAME, NC, NE, NET, NF, NG, NI, NL, NO, NP, NR, NU, NZ, OM, ORG,
 PA, PE, PF, PG, PH, PK, PL, PM, PN, PR, PRO, PS, PT, PW, PY, QA, RE, RO, RU, RW,
 SA, SB, SC, SD, SE, SG, SH, SI, SJ, SK, SL, SM, SN, SO, SR, ST, SU, SV, SY, SZ,
 TC, TD, TF, TG, TH, TJ, TK, TL, TM, TN, TO, TP, TR, TRAVEL, TT, TV, TW, TZ,
 UA, UG, UK, UM, US, UY, UZ, VA, VC, VE, VG, VI, VN, VU, WF, WS, YE, YT, YU, ZA, ZM, ZW
  */
 
 public static String dummyHash;
 private static HashMap TLDID = new HashMap();
 private static HashMap TLDName = new HashMap();
 private static void insertTLDProps(String[] TLDList, int id) {
     int p;
     String tld, name;
     Integer ID = new Integer(id);
     for (int i = 0; i < TLDList.length; i++) {
         p = TLDList[i].indexOf('=');
         if (p > 0) {
             tld = TLDList[i].substring(0, p).toLowerCase();
             name = TLDList[i].substring(p + 1);
             TLDID.put(tld, ID);
             TLDName.put(tld, name);
         }
     }
 }
 static {
     // create a dummy hash
     dummyHash = "";
     for (int i = 0; i < urlHashLength; i++) dummyHash += "-";
     
     // assign TLD-ids and names
     insertTLDProps(TLD_EuropaRussia, 0);
     insertTLDProps(TLD_MiddleSouthAmerica, 1);
     insertTLDProps(TLD_SouthEastAsia, 2);
     insertTLDProps(TLD_MiddleEastWestAsia, 3);
     insertTLDProps(TLD_NorthAmericaOceania, 4);
     insertTLDProps(TLD_Africa, 5);
     insertTLDProps(TLD_Generic, 6);
     insertTLDProps(TLD_Unassigned, 7);
 }
 
 
 // the class object
 protected kelondroIndex    urlIndexFile = null;
 protected kelondroRAMIndex urlIndexCache = null;
 
 public indexURL() {
     urlIndexFile = null;
     urlIndexCache = null;
 }

 public int size() {
     try {
        return urlIndexFile.size() + ((urlIndexCache == null) ? 0 : urlIndexCache.size());
    } catch (IOException e) {
        return 0;
    }
 }

 public void store(kelondroRow.Entry entry, boolean cached) throws IOException {
     if ((cached) && (urlIndexCache != null))
         synchronized (urlIndexCache) {
             urlIndexCache.put(entry);
         }
     else
         urlIndexFile.put(entry);
 }
 
 public void flushCacheSome() {
     if (urlIndexCache == null) return;
     if (urlIndexCache.size() == 0) return;
     int flush = Math.max(1, urlIndexCache.size() / 10);
     while (flush-- > 0) flushCacheOnce();
 }
 
 public void flushCacheOnce() {
     if (urlIndexCache == null) return;
     if (urlIndexCache.size() == 0) return;
     synchronized (urlIndexCache) {
         Iterator i = urlIndexCache.rows(true, false, null);
         try {
             urlIndexFile.put((kelondroRow.Entry) i.next());
             i.remove();
         } catch (IOException e) {
             e.printStackTrace();
         }
     }
 }
 
 public boolean remove(String hash) {
     if (hash == null) return false;
     try {
         urlIndexFile.remove(hash.getBytes());
         if (urlIndexCache != null) synchronized (urlIndexCache) {urlIndexCache.remove(hash.getBytes());}
         return true;
     } catch (IOException e) {
         return false;
     }
 }
 
 public void close() throws IOException {
     while ((urlIndexCache != null) && (urlIndexCache.size() > 0)) flushCacheOnce();
     if (urlIndexFile != null) {
         urlIndexFile.close();
         urlIndexFile = null;
     }
     if (urlIndexCache != null) {
         urlIndexCache.close();
         urlIndexCache = null;
     }
 }

 public int writeCacheSize() {
     return (urlIndexCache == null) ? 0 : urlIndexCache.size();
 }
 
 public int cacheNodeChunkSize() {
     if (urlIndexFile instanceof kelondroTree) return ((kelondroTree) urlIndexFile).cacheNodeChunkSize();
     return 0;
 }
 
 public int[] cacheNodeStatus() {
     if (urlIndexFile instanceof kelondroTree) return ((kelondroTree) urlIndexFile).cacheNodeStatus();
     return new int[]{0,0,0,0,0,0,0,0,0,0};
 }
 
 public int cacheObjectChunkSize() {
     if (urlIndexFile instanceof kelondroTree) return ((kelondroTree) urlIndexFile).cacheObjectChunkSize();
     return 0;
 }
 
 public long[] cacheObjectStatus() {
     if (urlIndexFile instanceof kelondroTree) return ((kelondroTree) urlIndexFile).cacheObjectStatus();
     return new long[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
 }
 
 public static final int flagTypeID(String hash) {
     return (kelondroBase64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 32) >> 5;
 }
 public static final int flagTLDID(String hash) {
     return (kelondroBase64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 28) >> 2;
 }
 public static final int flagLengthID(String hash) {
     return (kelondroBase64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 3);
 }
 
 public static final String urlHash(String url) {
     if ((url == null) || (url.length() == 0)) return null;
     try {
         return urlHash(new URL(url));
     } catch (MalformedURLException e) {
         return null;
     }
 }
 public static final String urlHash(URL url) {
    if (url == null) return null;
     String host = url.getHost().toLowerCase();
     int p = host.lastIndexOf('.');
     String tld = "", dom = tld;
     if (p > 0) {
         tld = host.substring(p + 1);
         dom = host.substring(0, p);
     }
     Integer ID = (Integer) TLDID.get(tld);
     int id = (ID == null) ? 7 : ID.intValue();
     boolean isHTTP = url.getProtocol().equals("http");
     p = dom.lastIndexOf('.'); // locate subdomain
     String subdom = "";
     if (p > 0) {
         subdom = dom.substring(0, p);
         dom = dom.substring(p + 1);
     }
     int port = url.getPort();
     if (port <= 0) {
         if (isHTTP) {
             port = 80;
         } else if (url.getProtocol().equalsIgnoreCase("https")) {
             port = 443;
         } else {
             port = 21;
         }
     }
     String path = url.getPath();
     if (path.startsWith("/")) path = path.substring(1);
     if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
     p = path.indexOf('/');
     String rootpath = "";
     if (p > 0) {
         rootpath = path.substring(0, p);
     }
     // we collected enough information to compute the fragments that are basis for hashes
     int l = dom.length();
     int domlengthKey = (l <= 8) ? 0 : (l <= 12) ? 1 : (l <= 16) ? 2 : 3;
     byte flagbyte = (byte) (((isHTTP) ? 0 : 32) | (id << 2) | domlengthKey);
     // form the 'local' part of the hash
     String hash3 = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(url.toNormalform())).substring(0, 5);
     char   hash2 = subdomPortPath(subdom, port, rootpath);
     // form the 'global' part of the hash
     String hash1 = protocolHostPort(url.getProtocol(), host, port);
     char   hash0 = kelondroBase64Order.enhancedCoder.encodeByte(flagbyte);
     // combine the hashes
     return hash3 + hash2 + hash1 + hash0;
 }
 
 private static char subdomPortPath(String subdom, int port, String rootpath) {
     return kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(subdom + ":" + port + ":" + rootpath)).charAt(0);
 }
 
 private static final char rootURLFlag = subdomPortPath("www", 80, "");
 public static final boolean probablyRootURL(String urlHash) {
     return (urlHash.charAt(5) == rootURLFlag);
 }
 
 private static String protocolHostPort(String protocol, String host, int port) {
     return kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(protocol + ":" + host + ":" + port)).substring(0, 5);
 }
 
 private static String[] testTLDs = new String[] {"com", "net", "org", "uk", "fr", "de", "es", "it"};
 public static final URL probablyWordURL(String urlHash, String word) {
     if ((word == null) || (word.length() == 0)) return null;
     String pattern = urlHash.substring(6, 11);
     for (int i = 0; i < testTLDs.length; i++) {
         if (pattern.equals(protocolHostPort("http", "www." + word.toLowerCase() + "." + testTLDs[i], 80)))
            try {
                return new URL("http://www." + word.toLowerCase() + "." + testTLDs[i]);
            } catch (MalformedURLException e) {
                return null;
            }
     }
     return null;
 }
 
 public static final boolean isWordRootURL(String givenURLHash, String word) {
     if (!(probablyRootURL(givenURLHash))) return false;
     URL wordURL = probablyWordURL(givenURLHash, word);
     if (wordURL == null) return false;
     return urlHash(wordURL).equals(givenURLHash);
 }
 
 public static final int domLengthEstimation(String urlHash) {
     // generates an estimation of the original domain length
     int flagbyte = kelondroBase64Order.enhancedCoder.decodeByte(urlHash.charAt(11));
     int domLengthKey = flagbyte & 3;
     switch (domLengthKey) {
         case 0: return 4;
         case 1: return 10;
         case 2: return 14;
         case 3: return 20;
     }
     return 20;
 }
 
 public static int domLengthNormalized(String urlHash) {
     return 255 * domLengthEstimation(urlHash) / 30;
 }
 
 public static final String oldurlHash(URL url) {
    if (url == null) return null;
     String hash = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(url.toNormalform())).substring(0, urlHashLength);
     return hash;
 }
     
 public static final String oldurlHash(String url) throws MalformedURLException {
    if ((url == null) || (url.length() < 10)) return null;
     String hash = kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(new URL(url).toNormalform())).substring(0, urlHashLength);
     return hash;
 }
 

 public static final serverByteBuffer compressIndex(indexContainer inputContainer, indexContainer excludeContainer, long maxtime) {
     // collect references according to domains
     long timeout = (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
     TreeMap doms = new TreeMap();
     synchronized(inputContainer) {
         Iterator i = inputContainer.entries();
         indexEntry iEntry;
         String dom, paths;
         while (i.hasNext()) {
             iEntry = (indexEntry) i.next();
             if (excludeContainer.get(iEntry.urlHash()) != null) continue; // do not include urls that are in excludeContainer
             dom = iEntry.urlHash().substring(6);
             if ((paths = (String) doms.get(dom)) == null) {
                 doms.put(dom, iEntry.urlHash().substring(0, 6));
             } else {
                 doms.put(dom, paths + iEntry.urlHash().substring(0, 6));
             }
             if (System.currentTimeMillis() > timeout) break;
         }
     }
     // construct a result string
     serverByteBuffer bb = new serverByteBuffer(inputContainer.size() * 6);
     bb.append('{');
     Iterator i = doms.entrySet().iterator();
     Map.Entry entry;
     while (i.hasNext()) {
         entry = (Map.Entry) i.next();
         bb.append((String) entry.getKey());
         bb.append(':');
         bb.append((String) entry.getValue());
         if (System.currentTimeMillis() > timeout) break;
         if (i.hasNext()) bb.append(',');
     }
     bb.append('}');
     bb.trim();
     return bb;
 }
 
 public static final void decompressIndex(TreeMap target, serverByteBuffer ci, String peerhash) {
     // target is a mapping from url-hashes to a string of peer-hashes
     if ((ci.byteAt(0) == '{') && (ci.byteAt(ci.length() - 1) == '}')) {
         //System.out.println("DEBUG-DECOMPRESS: input is " + ci.toString());
         ci = ci.trim(1, ci.length() - 1);
         String dom, url, peers;
         while ((ci.length() >= 13) && (ci.byteAt(6) == ':')) {
             dom = ci.toString(0, 6);
             ci.trim(7);
             while ((ci.length() > 0) && (ci.byteAt(0) != ',')) {
                 url = ci.toString(0, 6) + dom;
                 ci.trim(6);
                 peers = (String) target.get(url);
                 if (peers == null) {
                     target.put(url, peerhash);
                 } else {
                     target.put(url, peers + peerhash);
                 }
                 //System.out.println("DEBUG-DECOMPRESS: " + url + ":" + target.get(url));
             }
             if (ci.byteAt(0) == ',') ci.trim(1);
         }
     }
 }
 
}
