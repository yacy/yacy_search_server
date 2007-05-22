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

package de.anomic.plasma;

import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.net.URL;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.yacy.yacySeedDB;

public class plasmaURL {

    // day formatter for entry export
    public static final SimpleDateFormat shortDayFormatter = new SimpleDateFormat("yyyyMMdd");

    // TLD separation in political and cultural parts
    // https://www.cia.gov/cia/publications/factbook/index.html
    // http://en.wikipedia.org/wiki/List_of_countries_by_continent
    
    private static final String[] TLD_NorthAmericaOceania={
        // primary english-speaking countries
        // english-speaking countries from central america are also included
        // includes also dutch and french colonies in the caribbean sea
        // and US/English/Australian military bases in asia
         "EDU=US Educational",
         "GOV=US Government",
         "MIL=US Military",
         "NET=Network",
         "ORG=Non-Profit Organization",
         "AN=Netherlands Antilles",
         "AS=American Samoa",
         "AG=Antigua and Barbuda",
         "AI=Anguilla",
         "AU=Australia",
         "BB=Barbados",
         "BZ=Belize",
         "BM=Bermuda",
         "BS=Bahamas",
         "CA=Canada",
         "CC=Cocos (Keeling) Islands",
         "CK=Cook Islands",
         "CX=Christmas Island", // located in the Indian Ocean, but belongs to Australia
         "DM=Dominica",
         "FM=Micronesia",
         "FJ=Fiji",
         "GD=Grenada",
         "GP=Guadeloupe",
         "GS=South Georgia and the South Sandwich Islands", // south of south america, but administrated by british, has only a scientific base
         "GU=Guam", // strategical US basis close to Japan
         "HM=Heard and McDonald Islands", // uninhabited, sub-Antarctic island, owned by Australia
         "HT=Haiti",
         "IO=British Indian Ocean Territory", // UK-US naval support facility in the Indian Ocean
         "KI=Kiribati", // 33 coral atolls in the pacific, formerly owned by UK
         "KN=Saint Kitts and Nevis", // islands in the carribean see
         "KY=Cayman Islands",
         "LC=Saint Lucia",
         "MH=Marshall Islands", // formerly US atomic bomb test site, now a key installation in the US missile defense network
         "MP=Northern Mariana Islands", // US strategic location in the western Pacific Ocean
         "NC=New Caledonia",
         "NF=Norfolk Island",
         "NR=Nauru", // independent UN island
         "NU=Niue", // one of world's largest coral islands
         "NZ=New Zealand (Aotearoa)",
         "PG=Papua New Guinea",
         "PN=Pitcairn", // overseas territory of the UK
         "PR=Puerto Rico", // territory of the US with commonwealth status
         "PW=Palau", // was once governed by Micronesia
         "Sb=Solomon Islands",
         "TC=Turks and Caicos Islands", // overseas territory of the UK
         "TK=Tokelau", // group of three atolls in the South Pacific Ocean, british protectorat
         "TO=Tonga",
         "TT=Trinidad and Tobago",
         "TV=Tuvalu", //  nine coral atolls in the South Pacific Ocean; in 2000, Tuvalu leased its TLD ".tv" for $50 million over a 12-year period
         "UM=US Minor Outlying Islands", // nine insular United States possessions in the Pacific Ocean and the Caribbean Sea
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
         "FK=Falkland Islands (Malvinas)",
         "GF=French Guiana",
         "GT=Guatemala",
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
         "AQ=Antarctica",
         "AT=Austria",
         "BA=Bosnia and Herzegovina",
         "BE=Belgium",
         "BG=Bulgaria",
         "BV=Bouvet Island", // this island is uninhabited and covered by ice, south of africa but governed by Norway
         "BY=Belarus",
         "CH=Switzerland",
         "CS=Czechoslovakia (former)",
         "CZ=Czech Republic",
         "CY=Cyprus",
         "DE=Germany",
         "DK=Denmark",
         "ES=Spain",
         "EE=Estonia",
         "FI=Finland",
         "FO=Faroe Islands", // Viking Settlers
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
         "MS=Montserrat", // British island in the Caribbean Sea, almost not populated because of strong vulcanic activity
         "MT=Malta",
         "MQ=Martinique", // island in the eastern Caribbean Sea, overseas department of France
         "NATO=Nato field",
         "NL=Netherlands",
         "NO=Norway",
         "PF=French Polynesia", // French annexed Polynesian island in the South Pacific, French atomic bomb test site
         "PL=Poland",
         "PM=St. Pierre and Miquelon", // french-administrated colony close to canada, belongs to France
         "PT=Portugal",
         "RO=Romania",
         "RU=Russia",
         "SE=Sweden",
         "SI=Slovenia",
         "SJ=Svalbard and Jan Mayen Islands", // part of Norway
         "SM=San Marino",
         "SK=Slovak Republic",
         "SU=USSR (former)",
         "TF=French Southern Territories", // islands in the arctic see, no inhabitants
         "UK=United Kingdom",
         "UA=Ukraine",
         "VA=Vatican City State (Holy See)",
         "YU=Yugoslavia"
     };
     
     private static final String[] TLD_MiddleEastWestAsia = {
         // states that are influenced by islamic culture and arabic language
         // includes also eurasia states and those that had been part of the former USSR and close to southwest asia
         "AE=United Arab Emirates",
         "AF=Afghanistan",
         "AM=Armenia",
         "AZ=Azerbaijan",
         "BH=Bahrain",
         "GE=Georgia",
         "IL=Israel",
         "IQ=Iraq",
         "IR=Iran",
         "JO=Jordan",
         "KG=Kyrgyzstan",
         "KZ=Kazakhstan",
         "KW=Kuwait",
         "LB=Lebanon",
         "OM=Oman",
         "QA=Qatar",
         "SA=Saudi Arabia",
         "SY=Syria",
         "TJ=Tajikistan",
         "TM=Turkmenistan",
         "PK=Pakistan",
         "TR=Turkey",
         "UZ=Uzbekistan",
         "YE=Yemen"
     };
     private static final String[] TLD_SouthEastAsia = {
         "BD=Bangladesh",
         "BN=Brunei Darussalam",
         "BT=Bhutan",
         "CN=China",
         "HK=Hong Kong",
         "ID=Indonesia",
         "IN=India",
         "LA=Laos",
         "NP=Nepal",
         "JP=Japan",
         "KH=Cambodia",
         "KP=Korea (North)",
         "KR=Korea (South)",
         "LK=Sri Lanka",
         "MY=Malaysia",
         "MM=Myanmar", // formerly known as Burma
         "MO=Macau", // Portuguese settlement, part of China, but has some autonomy
         "MV=Maldives", // group of atolls in the Indian Ocean
         "PH=Philippines",
         "SG=Singapore",
         "TP=East Timor",
         "TH=Thailand",
         "TW=Taiwan",
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
         "CV=Cape Verde",
         "DJ=Djibouti",
         "DZ=Algeria",
         "EG=Egypt",
         "EH=Western Sahara",
         "ER=Eritrea",
         "ET=Ethiopia",
         "GA=Gabon",
         "GH=Ghana",
         "GM=Gambia",
         "GN=Guinea",
         "GQ=Equatorial Guinea",
         "GW=Guinea-Bissau",
         "KE=Kenya",
         "KM=Comoros",
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
         "SC=Seychelles",
         "SD=Sudan",
         "SH=St. Helena",
         "SL=Sierra Leone",
         "SN=Senegal",
         "SO=Somalia",
         "ST=Sao Tome and Principe",
         "SZ=Swaziland",
         "TD=Chad",
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
         "ARPA=Arpanet",
         "NT=Neutral Zone"
     };


    /*
     * TLDs: aero, biz, com, coop, edu, gov, info, int, mil, museum, name, net,
     * org, pro, arpa AC, AD, AE, AERO, AF, AG, AI, AL, AM, AN, AO, AQ, AR,
     * ARPA, AS, AT, AU, AW, AZ, BA, BB, BD, BE, BF, BG, BH, BI, BIZ, BJ, BM,
     * BN, BO, BR, BS, BT, BV, BW, BY, BZ, CA, CC, CD, CF, CG, CH, CI, CK, CL,
     * CM, CN, CO, COM, COOP, CR, CU, CV, CX, CY, CZ, DE, DJ, DK, DM, DO, DZ,
     * EC, EDU, EE, EG, ER, ES, ET, EU, FI, FJ, FK, FM, FO, FR, GA, GB, GD, GE,
     * GF, GG, GH, GI, GL, GM, GN, GOV, GP, GQ, GR, GS, GT, GU, GW, GY, HK, HM,
     * HN, HR, HT, HU, ID, IE, IL, IM, IN, INFO, INT, IO, IQ, IR, IS, IT, JE,
     * JM, JO, JOBS, JP, KE, KG, KH, KI, KM, KN, KR, KW, KY, KZ, LA, LB, LC, LI,
     * LK, LR, LS, LT, LU, LV, LY, MA, MC, MD, MG, MH, MIL, MK, ML, MM, MN, MO,
     * MOBI, MP, MQ, MR, MS, MT, MU, MUSEUM, MV, MW, MX, MY, MZ, NA, NAME, NC,
     * NE, NET, NF, NG, NI, NL, NO, NP, NR, NU, NZ, OM, ORG, PA, PE, PF, PG, PH,
     * PK, PL, PM, PN, PR, PRO, PS, PT, PW, PY, QA, RE, RO, RU, RW, SA, SB, SC,
     * SD, SE, SG, SH, SI, SJ, SK, SL, SM, SN, SO, SR, ST, SU, SV, SY, SZ, TC,
     * TD, TF, TG, TH, TJ, TK, TL, TM, TN, TO, TP, TR, TRAVEL, TT, TV, TW, TZ,
     * UA, UG, UK, UM, US, UY, UZ, VA, VC, VE, VG, VI, VN, VU, WF, WS, YE, YT,
     * YU, ZA, ZM, ZW
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
        for (int i = 0; i < yacySeedDB.commonHashLength; i++) dummyHash += "-";

        // assign TLD-ids and names
        insertTLDProps(TLD_EuropaRussia, 0);
        insertTLDProps(TLD_MiddleSouthAmerica, 1);
        insertTLDProps(TLD_SouthEastAsia, 2);
        insertTLDProps(TLD_MiddleEastWestAsia, 3);
        insertTLDProps(TLD_NorthAmericaOceania, 4);
        insertTLDProps(TLD_Africa, 5);
        insertTLDProps(TLD_Generic, 6);
        // the id=7 is used to flag local addresses
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
        if ((url == null) || (url.length() == 0))
            return null;
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
        Integer ID = (serverCore.isNotLocal(tld)) ? (Integer) TLDID.get(tld) : null; // identify local addresses
        int id = (ID == null) ? 7 : ID.intValue(); // local addresses are flagged with id=7
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
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        p = path.indexOf('/');
        String rootpath = "";
        if (p > 0) {
            rootpath = path.substring(0, p);
        }

        // we collected enough information to compute the fragments that are
        // basis for hashes
        int l = dom.length();
        int domlengthKey = (l <= 8) ? 0 : (l <= 12) ? 1 : (l <= 16) ? 2 : 3;
        byte flagbyte = (byte) (((isHTTP) ? 0 : 32) | (id << 2) | domlengthKey);

        // combine the attributes
        StringBuffer hash = new StringBuffer(12);
        // form the 'local' part of the hash
        hash.append(kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(url.toNormalform())).substring(0, 5)); // 5 chars
        hash.append(subdomPortPath(subdom, port, rootpath)); // 1 char
        // form the 'global' part of the hash
        hash.append(protocolHostPort(url.getProtocol(), host, port)); // 5 chars
        hash.append(kelondroBase64Order.enhancedCoder.encodeByte(flagbyte)); // 1 char

        // return result hash
        return new String(hash);
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

    private static String[] testTLDs = new String[] { "com", "net", "org", "uk", "fr", "de", "es", "it" };

    public static final URL probablyWordURL(String urlHash, TreeSet words) {
    	Iterator wi = words.iterator();
    	String word;
        while (wi.hasNext()) {
        	word = (String) wi.next();
        	if ((word == null) || (word.length() == 0)) continue;
        	String pattern = urlHash.substring(6, 11);
        	for (int i = 0; i < testTLDs.length; i++) {
        		if (pattern.equals(protocolHostPort("http", "www." + word.toLowerCase() + "." + testTLDs[i], 80)))
        			try {
        				return new URL("http://www." + word.toLowerCase() + "." + testTLDs[i]);
        			} catch (MalformedURLException e) {
        				return null;
        			}
        	}
        }
        return null;
    }

    public static final boolean isWordRootURL(String givenURLHash, TreeSet words) {
        if (!(probablyRootURL(givenURLHash))) return false;
        URL wordURL = probablyWordURL(givenURLHash, words);
        if (wordURL == null) return false;
        if (urlHash(wordURL).equals(givenURLHash)) return true;
        return false;
    }

    public static final int domLengthEstimation(String urlHash) {
        // generates an estimation of the original domain length
        assert (urlHash != null);
        assert (urlHash.length() == 12) : "urlhash = " + urlHash;
        int flagbyte = kelondroBase64Order.enhancedCoder.decodeByte(urlHash.charAt(11));
        int domLengthKey = flagbyte & 3;
        switch (domLengthKey) {
        case 0:
            return 4;
        case 1:
            return 10;
        case 2:
            return 14;
        case 3:
            return 20;
        }
        return 20;
    }

    public static int domLengthNormalized(String urlHash) {
        return 255 * domLengthEstimation(urlHash) / 30;
    }

    public static final int domDomain(String urlHash) {
        // returns the ID of the domain of the domain
        assert (urlHash != null);
        assert (urlHash.length() == 12) : "urlhash = " + urlHash;
        int flagbyte = kelondroBase64Order.enhancedCoder.decodeByte(urlHash.charAt(11));
        return (flagbyte & 12) >> 2;
    }

    public static boolean isGlobalDomain(String urlhash) {
        return domDomain(urlhash) != 7;
    }

    public static final serverByteBuffer compressIndex(indexContainer inputContainer, indexContainer excludeContainer, long maxtime) {
        // collect references according to domains
        long timeout = (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        TreeMap doms = new TreeMap();
        synchronized (inputContainer) {
            Iterator i = inputContainer.entries();
            indexRWIEntry iEntry;
            String dom, paths;
            while (i.hasNext()) {
                iEntry = (indexRWIEntry) i.next();
                if ((excludeContainer != null) && (excludeContainer.get(iEntry.urlHash()) != null)) continue; // do not include urls that are in excludeContainer
                dom = iEntry.urlHash().substring(6);
                if ((paths = (String) doms.get(dom)) == null) {
                    doms.put(dom, iEntry.urlHash().substring(0, 6));
                } else {
                    doms.put(dom, paths + iEntry.urlHash().substring(0, 6));
                }
                if (System.currentTimeMillis() > timeout)
                    break;
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
            if (System.currentTimeMillis() > timeout)
                break;
            if (i.hasNext())
                bb.append(',');
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


    // doctypes:
    public static final char DT_PDFPS   = 'p';
    public static final char DT_TEXT    = 't';
    public static final char DT_HTML    = 'h';
    public static final char DT_DOC     = 'd';
    public static final char DT_IMAGE   = 'i';
    public static final char DT_MOVIE   = 'm';
    public static final char DT_FLASH   = 'f';
    public static final char DT_SHARE   = 's';
    public static final char DT_AUDIO   = 'a';
    public static final char DT_BINARY  = 'b';
    public static final char DT_UNKNOWN = 'u';

    // appearance locations: (used for flags)
    public static final int AP_TITLE     =  0; // title tag from html header
    public static final int AP_H1        =  1; // headline - top level
    public static final int AP_H2        =  2; // headline, second level
    public static final int AP_H3        =  3; // headline, 3rd level
    public static final int AP_H4        =  4; // headline, 4th level
    public static final int AP_H5        =  5; // headline, 5th level
    public static final int AP_H6        =  6; // headline, 6th level
    public static final int AP_TEXT      =  7; // word appears in text (used to check validation of other appearances against spam)
    public static final int AP_DOM       =  8; // word inside an url: in Domain
    public static final int AP_PATH      =  9; // word inside an url: in path
    public static final int AP_IMG       = 10; // tag inside image references
    public static final int AP_ANCHOR    = 11; // anchor description
    public static final int AP_ENV       = 12; // word appears in environment (similar to anchor appearance)
    public static final int AP_BOLD      = 13; // may be interpreted as emphasized
    public static final int AP_ITALICS   = 14; // may be interpreted as emphasized
    public static final int AP_WEAK      = 15; // for Text that is small or bareley visible
    public static final int AP_INVISIBLE = 16; // good for spam detection
    public static final int AP_TAG       = 17; // for tagged indexeing (i.e. using mp3 tags)
    public static final int AP_AUTHOR    = 18; // word appears in author name
    public static final int AP_OPUS      = 19; // word appears in name of opus, which may be an album name (in mp3 tags)
    public static final int AP_TRACK     = 20; // word appears in track name (i.e. in mp3 tags)
    
    // URL attributes
    public static final int UA_LOCAL    =  0; // URL was crawled locally
    public static final int UA_TILDE    =  1; // tilde appears in URL
    public static final int UA_REDIRECT =  2; // The URL is a redirection
    
    // local flag attributes
    public static final char LT_LOCAL   = 'L';
    public static final char LT_GLOBAL  = 'G';

    // doctype calculation
    public static char docType(URL url) {
        String path = url.getPath();
        // serverLog.logFinest("PLASMA", "docType URL=" + path);
        char doctype = DT_UNKNOWN;
        if (path.endsWith(".gif"))       { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpg"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpeg")) { doctype = DT_IMAGE; }
        else if (path.endsWith(".png"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".html")) { doctype = DT_HTML;  }
        else if (path.endsWith(".txt"))  { doctype = DT_TEXT;  }
        else if (path.endsWith(".doc"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".rtf"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".pdf"))  { doctype = DT_PDFPS; }
        else if (path.endsWith(".ps"))   { doctype = DT_PDFPS; }
        else if (path.endsWith(".avi"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".mov"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".qt"))   { doctype = DT_MOVIE; }
        else if (path.endsWith(".mpg"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".md5"))  { doctype = DT_SHARE; }
        else if (path.endsWith(".mpeg")) { doctype = DT_MOVIE; }
        else if (path.endsWith(".asf"))  { doctype = DT_FLASH; }
        return doctype;
    }

    public static char docType(String mime) {
        // serverLog.logFinest("PLASMA", "docType mime=" + mime);
        char doctype = DT_UNKNOWN;
        if (mime == null) doctype = DT_UNKNOWN;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.endsWith("/gif")) doctype = DT_IMAGE;
        else if (mime.endsWith("/jpeg")) doctype = DT_IMAGE;
        else if (mime.endsWith("/png")) doctype = DT_IMAGE;
        else if (mime.endsWith("/html")) doctype = DT_HTML;
        else if (mime.endsWith("/rtf")) doctype = DT_DOC;
        else if (mime.endsWith("/pdf")) doctype = DT_PDFPS;
        else if (mime.endsWith("/octet-stream")) doctype = DT_BINARY;
        else if (mime.endsWith("/x-shockwave-flash")) doctype = DT_FLASH;
        else if (mime.endsWith("/msword")) doctype = DT_DOC;
        else if (mime.endsWith("/mspowerpoint")) doctype = DT_DOC;
        else if (mime.endsWith("/postscript")) doctype = DT_PDFPS;
        else if (mime.startsWith("text/")) doctype = DT_TEXT;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.startsWith("audio/")) doctype = DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = DT_MOVIE;
        //bz2     = application/x-bzip2
        //dvi     = application/x-dvi
        //gz      = application/gzip
        //hqx     = application/mac-binhex40
        //lha     = application/x-lzh
        //lzh     = application/x-lzh
        //pac     = application/x-ns-proxy-autoconfig
        //php     = application/x-httpd-php
        //phtml   = application/x-httpd-php
        //rss     = application/xml
        //tar     = application/tar
        //tex     = application/x-tex
        //tgz     = application/tar
        //torrent = application/x-bittorrent
        //xhtml   = application/xhtml+xml
        //xla     = application/msexcel
        //xls     = application/msexcel
        //xsl     = application/xml
        //xml     = application/xml
        //Z       = application/x-compress
        //zip     = application/zip
        return doctype;
    }

    // language calculation
    public static String language(URL url) {
        String language = "uk";
        String host = url.getHost();
        int pos = host.lastIndexOf(".");
        if ((pos > 0) && (host.length() - pos == 3)) language = host.substring(pos + 1).toLowerCase();
        return language;
    }
    
}
