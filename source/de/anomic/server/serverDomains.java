// serverDNSCache.java
// -----------------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 23.07.2007 on http://yacy.net
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

package de.anomic.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.plasma.plasmaSwitchboard;

public class serverDomains {

    // a dns cache
    private static final Map<String, InetAddress> nameCacheHit = new ConcurrentHashMap<String, InetAddress>(); // a not-synchronized map resulted in deadlocks
    private static final Set<String> nameCacheMiss = Collections.synchronizedSet(new HashSet<String>());
    private static final kelondroMScoreCluster<String> nameCacheHitAges = new kelondroMScoreCluster<String>();
    private static final kelondroMScoreCluster<String> nameCacheMissAges = new kelondroMScoreCluster<String>();
    private static final int maxNameCacheHitAge = 24 * 60 * 60; // 24 hours in minutes
    private static final int maxNameCacheMissAge = 24 * 60 * 60; // 24 hours in minutes
    private static final int maxNameCacheHitSize = 3000; 
    private static final int maxNameCacheMissSize = 3000; 
    public  static final List<String> nameCacheNoCachingPatterns = Collections.synchronizedList(new LinkedList<String>());
    private static final Set<String> nameCacheNoCachingList = Collections.synchronizedSet(new HashSet<String>());
    private static final long startTime = System.currentTimeMillis();

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
         "MF=Saint Martin (French part)",
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
         "SB=Solomon Islands",
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
     private static final String[] TLD_EuropeRussia = {
        // includes also countries that are mainly french- dutch- speaking
        // and culturally close to europe
         "AD=Andorra",
         "AL=Albania",
         "AQ=Antarctica",
         "AT=Austria",
         "AX=Aaland Islands",
         "BA=Bosnia and Herzegovina",
         "BE=Belgium",
         "BG=Bulgaria",
         "BV=Bouvet Island", // this island is uninhabited and covered by ice, south of africa but governed by Norway
         "BY=Belarus",
         "CAT=Catalan",
         "CH=Switzerland",
         "CS=Czechoslovakia (former)",
         "CZ=Czech Republic",
         "CY=Cyprus",
         "DE=Germany",
         "DK=Denmark",
         "ES=Spain",
         "EE=Estonia",
         "EU=Europe",
         "FI=Finland",
         "FO=Faroe Islands", // Viking Settlers
         "FR=France",
         "FX=France, Metropolitan",
         "GB=Great Britain (UK)",
         "GG=Guernsey",
         "GI=Gibraltar",
         "GL=Greenland",
         "GR=Greece",
         "HR=Croatia (Hrvatska)",
         "HU=Hungary",
         "IE=Ireland",
         "IS=Iceland",
         "IT=Italy",
         "JE=Jersey",
         "LI=Liechtenstein",
         "LT=Lithuania",
         "LU=Luxembourg",
         "LV=Latvia",
         "MC=Monaco",
         "MD=Moldova",
         "ME=Montenegro",
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
         "RS=Serbia",
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
         "PS=Palestinian Territory",
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
         "ASIA=The Pan-Asia and Asia Pacific community",
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
         "TL=Timor-Leste",
         "TW=Taiwan",
         "VN=Viet Nam"
     };
     private static final String[] TLD_Africa = {
         "AC=Ascension Island",
         "AO=Angola",
         "BF=Burkina Faso",
         "BI=Burundi",
         "BJ=Benin",
         "BW=Botswana",
         "CD=Democratic Republic of the Congo",
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
         "AERO=The air-transport industry",
         "ARPA=operationally-critical infrastructural identifier spaces",
         "BIZ=Business",
         "COOP=cooperative associations",
         "INFO=",
         "JOBS=human resource managers",
         "MOBI=mobile products and services",
         "MUSEUM=Museums",
         "NAME=Individuals",
         "PRO=Credentialed professionals",
         "TEL=Published contact data",
         "TRAVEL=The travel industry",
         "INT=International"
     };

    private static HashMap<String, Integer> TLDID = new HashMap<String, Integer>();
    //private static HashMap<String, String> TLDName = new HashMap<String, String>();

    private static void insertTLDProps(String[] TLDList, int id) {
        int p;
        String tld;
        //String name;
        Integer ID = new Integer(id);
        for (int i = 0; i < TLDList.length; i++) {
            p = TLDList[i].indexOf('=');
            if (p > 0) {
                tld = TLDList[i].substring(0, p).toLowerCase();
                //name = TLDList[i].substring(p + 1);
                TLDID.put(tld, ID);
                //TLDName.put(tld, name);
            }
        }
    }

    // TLD separation, partly separated into language groups
    // https://www.cia.gov/cia/publications/factbook/index.html
    // http://en.wikipedia.org/wiki/List_of_countries_by_continent
    public static final int TLD_EuropeRussia_ID        = 0; // European languages but no english
    public static final int TLD_MiddleSouthAmerica_ID  = 1; // mainly spanish-speaking countries
    public static final int TLD_SouthEastAsia_ID       = 2; // asia
    public static final int TLD_MiddleEastWestAsia_ID  = 3; // middle east
    public static final int TLD_NorthAmericaOceania_ID = 4; // english-speaking countries
    public static final int TLD_Africa_ID              = 5; // africa
    public static final int TLD_Generic_ID             = 6; // anything else, also raw ip numbers

    static {
        // assign TLD-ids and names
        insertTLDProps(TLD_EuropeRussia,        TLD_EuropeRussia_ID);
        insertTLDProps(TLD_MiddleSouthAmerica,  TLD_MiddleSouthAmerica_ID);
        insertTLDProps(TLD_SouthEastAsia,       TLD_SouthEastAsia_ID);
        insertTLDProps(TLD_MiddleEastWestAsia,  TLD_MiddleEastWestAsia_ID);
        insertTLDProps(TLD_NorthAmericaOceania, TLD_NorthAmericaOceania_ID);
        insertTLDProps(TLD_Africa,              TLD_Africa_ID);
        insertTLDProps(TLD_Generic,             TLD_Generic_ID);
        // the id=7 is used to flag local addresses
    }
     
    /**
    * Converts the time to a non negative int
    *
    * @param longTime Time in miliseconds since 01/01/1970 00:00 GMT
    * @return int seconds since startTime
    */
    private static int intTime(long longTime) {
        return (int) Math.max(0, ((longTime - startTime) / 1000));
    }

    /**
    * Does an DNS-Check to resolve a hostname to an IP.
    *
    * @param host Hostname of the host in demand.
    * @return String with the ip. null, if the host could not be resolved.
    */
    public static InetAddress dnsResolveFromCache(String host) throws UnknownHostException {
        if ((host == null) || (host.length() == 0)) return null;
        host = host.toLowerCase().trim();        
        
        // trying to resolve host by doing a name cache lookup
        InetAddress ip = (InetAddress) nameCacheHit.get(host);
        if (ip != null) return ip;
        
        if (nameCacheMiss.contains(host)) return null;
        throw new UnknownHostException("host not in cache");
    }
    
    public static InetAddress dnsResolve(String host) {
        if ((host == null) || (host.length() == 0)) return null;
        host = host.toLowerCase().trim();        
        
        // trying to resolve host by doing a name cache lookup
        InetAddress ip = (InetAddress) nameCacheHit.get(host);
        if (ip != null) return ip;
        
        if (nameCacheMiss.contains(host)) return null;
        //System.out.println("***DEBUG dnsResolve(" + host + ")");
        try {
            boolean doCaching = true;
            ip = InetAddress.getByName(host);
            if ((ip == null) ||
                (ip.isLoopbackAddress()) ||
                (nameCacheNoCachingList.contains(ip.getHostName()))
            ) {
                doCaching = false;
            } else {
                Iterator<String> noCachingPatternIter = nameCacheNoCachingPatterns.iterator();
                String nextPattern;
                while (noCachingPatternIter.hasNext()) {
                    nextPattern = noCachingPatternIter.next();
                    if (ip.getHostName().matches(nextPattern)) {
                        // disallow dns caching for this host
                        nameCacheNoCachingList.add(ip.getHostName());
                        doCaching = false;
                        break;
                    }
                }
            }
            
            if (doCaching) {
                // remove old entries
                flushHitNameCache();
                
                // add new entries
                synchronized (nameCacheHit) {
                    nameCacheHit.put(ip.getHostName(), ip);
                    nameCacheHitAges.setScore(ip.getHostName(), intTime(System.currentTimeMillis()));
                }
            }
            return ip;
        } catch (UnknownHostException e) {
            // remove old entries
            flushMissNameCache();
            
            // add new entries
            nameCacheMiss.add(host);
            nameCacheMissAges.setScore(host, intTime(System.currentTimeMillis()));
        }
        return null;
    }

    /**
    * Returns the number of entries in the nameCacheHit map
    *
    * @return int The number of entries in the nameCacheHit map
    */
    public static int nameCacheHitSize() {
        return nameCacheHit.size();
    }

    public static int nameCacheMissSize() {
        return nameCacheMiss.size();
    }

    /**
    * Returns the number of entries in the nameCacheNoCachingList list
    *
    * @return int The number of entries in the nameCacheNoCachingList list
    */
    public static int nameCacheNoCachingListSize() {
        return nameCacheNoCachingList.size();
    }
    

    /**
    * Removes old entries from the dns hit cache
    */
    public static void flushHitNameCache() {
        int cutofftime = intTime(System.currentTimeMillis()) - maxNameCacheHitAge;
        String k;
        while ((nameCacheHitAges.size() > maxNameCacheHitSize) || (nameCacheHitAges.getMinScore() < cutofftime)) {
            k = (String) nameCacheHitAges.getMinObject();
            if (nameCacheHit.remove(k) == null) break; // ensure termination
            nameCacheHitAges.deleteScore(k);
        }
        
    }
    
    /**
     * Removes old entries from the dns miss cache
     */
     public static void flushMissNameCache() {
        int cutofftime = intTime(System.currentTimeMillis()) - maxNameCacheMissAge;
        String k;
        while ((nameCacheMissAges.size() > maxNameCacheMissSize) || (nameCacheMissAges.getMinScore() < cutofftime)) {
            k = (String) nameCacheMissAges.getMinObject();
            if (!nameCacheMiss.remove(k)) break; // ensure termination
            nameCacheMissAges.deleteScore(k);
        }
        
    }

    private static InetAddress[] localAddresses = null;
    static {
        try {
            localAddresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            localAddresses = new InetAddress[0];
        }
    }
    
    public static int getDomainID(String host) {
        int p = host.lastIndexOf('.');
        String tld = "";
        if (p > 0) {
            tld = host.substring(p + 1);
        }
        Integer i = TLDID.get(tld);
        if (i == null) {
            return (isLocal(host)) ? 7 : TLD_Generic_ID;
        } else {
            return i.intValue();
        }
    }
     
    public static boolean isLocal(String host) {

        // attention! because this method does a dns resolve to look up an IP address,
        // the result may be very slow. Consider 100 milliseconds per access
        
        assert (host != null);

        // check local ip addresses
        if (host.equals("localhost") || host.startsWith("127")
                || host.startsWith("192.168")
                || host.startsWith("10.")
                || host.startsWith("169.254")
                ||
                // 172.16.0.0-172.31.255.255 (I think this is faster than a regex)
                (host.startsWith("172.") && (host.startsWith("172.16.")
                        || host.startsWith("172.17.")
                        || host.startsWith("172.18.")
                        || host.startsWith("172.19.")
                        || host.startsWith("172.20.")
                        || host.startsWith("172.21.")
                        || host.startsWith("172.22.")
                        || host.startsWith("172.23.")
                        || host.startsWith("172.24.")
                        || host.startsWith("172.25.")
                        || host.startsWith("172.26.")
                        || host.startsWith("172.27.")
                        || host.startsWith("172.28.")
                        || host.startsWith("172.29.")
                        || host.startsWith("172.30.")
                        || host.startsWith("172.31."))))
            return true;
        
        // check the tld list
        int p = host.lastIndexOf('.');
        String tld = "";
        if (p > 0) {
            tld = host.substring(p + 1);
        }
        if (TLDID.get(tld) == null) return true;

        // make a dns resolve if a hostname is given and check again
        final InetAddress clientAddress = dnsResolve(host);
        if (clientAddress != null) {
            if ((clientAddress.isAnyLocalAddress()) || (clientAddress.isLoopbackAddress())) return true;
            if (host.charAt(0) > '9') host = clientAddress.getHostAddress();
        }

        // finally check if there are other local IP adresses that are not in
        // the standard IP range
        for (int i = 0; i < localAddresses.length; i++) {
            if (localAddresses[i].equals(clientAddress)) return true;
        }

        // the address must be a global address
        return false;
    }
    
    public static String myPublicIP() {
        // if a static IP was configured, we have to return it here ...
        plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
        if (sb != null) {
            String staticIP = sb.getConfig("staticIP", "");
            if ((!staticIP.equals(""))) {
                return staticIP;
            }
        }

        // otherwise we return the real IP address of this host
        InetAddress pLIP = myPublicLocalIP();
        if (pLIP != null) return pLIP.getHostAddress();
        return null;
    }

    public static InetAddress myPublicLocalIP() {
        try {
            String hostName;
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (java.net.UnknownHostException e) {
                hostName = "localhost"; // hopin' nothing serious happened only the hostname changed while running yacy
                System.err.println("ERROR: (internal) " + e.getMessage());
            }
            // list all addresses
            InetAddress[] ia = InetAddress.getAllByName(hostName);
            // for (int i = 0; i < ia.length; i++) System.out.println("IP: " +
            // ia[i].getHostAddress()); // DEBUG
            if (ia.length == 0) {
                try {
                    return InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    try {
                        return InetAddress.getByName("127.0.0.1");
                    } catch (UnknownHostException ee) {
                        return null;
                    }
                }
            }
            if (ia.length == 1) {
                // only one network connection available
                return ia[0];
            }
            // we have more addresses, find an address that is not local
            int b0, b1;
            for (int i = 0; i < ia.length; i++) {
                b0 = 0Xff & ia[i].getAddress()[0];
                b1 = 0Xff & ia[i].getAddress()[1];
                if ((b0 != 10) && // class A reserved
                        (b0 != 127) && // loopback
                        ((b0 != 172) || (b1 < 16) || (b1 > 31)) && // class B reserved
                        ((b0 != 192) || (b1 != 168)) && // class C reserved
                        (ia[i].getHostAddress().indexOf(":") < 0))
                    return ia[i];
            }
            // there is only a local address, we filter out the possibly
            // returned loopback address 127.0.0.1
            for (int i = 0; i < ia.length; i++) {
                if (((0Xff & ia[i].getAddress()[0]) != 127) && (ia[i].getHostAddress().indexOf(":") < 0)) return ia[i];
            }
            // if all fails, give back whatever we have
            for (int i = 0; i < ia.length; i++) {
                if (ia[i].getHostAddress().indexOf(":") < 0) return ia[i];
            }
            return ia[0];
        } catch (java.net.UnknownHostException e) {
            System.err.println("ERROR: (internal) " + e.getMessage());
            return null;
        }
    }

}
