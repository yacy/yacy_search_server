/**
 *  Domains
 *  Copyright 2007 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 23.7.2007 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.protocol;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.plugin.ClassProvider;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.cora.storage.KeyList;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;

import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

public class Domains {
    
    private final static ConcurrentLog log = new ConcurrentLog(Domains.class.getName());
    
    public  static final String LOCALHOST = "127.0.0.1"; // replace with IPv6 0:0:0:0:0:0:0:1 ?
    private static       String LOCALHOST_NAME = LOCALHOST; // this will be replaced with the actual name of the local host

    private static Class<?> InetAddressLocatorClass;
    private static Method InetAddressLocatorGetLocaleInetAddressMethod;
    private static final Set<String> ccSLD_TLD = new HashSet<String>();
    private static final String PRESENT = "";
    private static final Pattern LOCALHOST_PATTERNS = Pattern.compile("(127\\..*)|(localhost)|(\\[?(fe80|0)\\:0\\:0\\:0\\:0\\:0\\:0\\:1.*)");
    private static final Pattern INTRANET_PATTERNS = Pattern.compile("(10\\..*)|(127\\..*)|(172\\.(1[6-9]|2[0-9]|3[0-1])\\..*)|(169\\.254\\..*)|(192\\.168\\..*)|(localhost)|(\\[?\\:\\:1/.*)|(\\[?fc.*\\:.*)|(\\[?fd.*\\:.*)|(\\[?(fe80|0)\\:0\\:0\\:0\\:0\\:0\\:0\\:1.*)", Pattern.CASE_INSENSITIVE);

    private static final int MAX_NAME_CACHE_HIT_SIZE = 10000;
    private static final int MAX_NAME_CACHE_MISS_SIZE = 1000;
    private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors() * 2;

    // a dns cache
    private static final ARC<String, InetAddress> NAME_CACHE_HIT = new ConcurrentARC<String, InetAddress>(MAX_NAME_CACHE_HIT_SIZE, CONCURRENCY_LEVEL);
    private static final ARC<String, String> NAME_CACHE_MISS = new ConcurrentARC<String, String>(MAX_NAME_CACHE_MISS_SIZE, CONCURRENCY_LEVEL);
    private static final ConcurrentHashMap<String, Object> LOOKUP_SYNC = new ConcurrentHashMap<String, Object>(100, 0.75f, Runtime.getRuntime().availableProcessors() * 2);
    private static       List<Pattern> nameCacheNoCachingPatterns = Collections.synchronizedList(new LinkedList<Pattern>());
    public static long cacheHit_Hit = 0, cacheHit_Miss = 0, cacheHit_Insert = 0; // for statistics only; do not write
    public static long cacheMiss_Hit = 0, cacheMiss_Miss = 0, cacheMiss_Insert = 0; // for statistics only; do not write

    /**
     * ! ! !   A T T E N T I O N   A T T E N T I O N   A T T E N T I O N   ! ! !
     *
     * Do not move a TLD to another group (if you do not exactly know what you
     * are doing)! Because it will change the hash of the url!
     */
    private static final String[] TLD_NorthAmericaOceania = {
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
         "GU=Guam", // strategic US basis close to Japan
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
         "IM=Isle of Man",
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
         "INT=International",
         // domains from the OpenNIC project, http://www.opennicproject.org, see also http://wiki.opennic.glue/OpenNICNamespaces
         "GLUE=OpenNIC Internal Architectural use",
         "BBS=OpenNIC Bulletin Board System servers",
         "FREE=OpenNIC NAMESPACE, CERT AUTH",
         "FUR=OpenNIC Furries, Furry Fandom and other Anthropormorphic interest",
         "GEEK=OpenNIC Geek-oriented sites",
         "INDY=OpenNIC independent media and arts",
         "NULL=OpenNIC the DNS version of Usenet's alt. hierarchy",
         "OSS=OpenNIC reserved exclusively for Open Source Software projects",
         "PARODY=OpenNIC non-commercial parody work",
         "DNY=OpenNIC",
         "ING=OpenNIC",
         "GOPHER=OpenNIC",
         "MICRO=OpenNIC"
     };
     private static final String[] ccSLD_TLD_list = new String[] { "com.ac", "net.ac", "gov.ac", "org.ac", "mil.ac", "co.ae",
                     "net.ae", "gov.ae", "ac.ae", "sch.ae", "org.ae", "mil.ae", "pro.ae", "name.ae", "com.af", "edu.af", "gov.af", "net.af", "org.af",
                     "com.al", "edu.al", "gov.al", "mil.al", "net.al", "org.al", "ed.ao", "gv.ao", "og.ao", "co.ao", "pb.ao", "it.ao", "com.ar", "edu.ar",
                     "gob.ar", "gov.ar", "int.ar", "mil.ar", "net.ar", "org.ar", "tur.ar", "gv.at", "ac.at", "co.at", "or.at", "com.au", "net.au", "org.au",
                     "edu.au", "gov.au", "csiro.au", "asn.au", "id.au", "org.ba", "net.ba", "edu.ba", "gov.ba", "mil.ba", "unsa.ba", "untz.ba", "unmo.ba",
                     "unbi.ba", "unze.ba", "co.ba", "com.ba", "rs.ba", "co.bb", "com.bb", "net.bb", "org.bb", "gov.bb", "edu.bb", "info.bb", "store.bb",
                     "tv.bb", "biz.bb", "com.bh", "info.bh", "cc.bh", "edu.bh", "biz.bh", "net.bh", "org.bh", "gov.bh", "com.bn", "edu.bn", "gov.bn",
                     "net.bn", "org.bn", "com.bo", "net.bo", "org.bo", "tv.bo", "mil.bo", "int.bo", "gob.bo", "gov.bo", "edu.bo", "adm.br", "adv.br",
                     "agr.br", "am.br", "arq.br", "art.br", "ato.br", "b.br", "bio.br", "blog.br", "bmd.br", "cim.br", "cng.br", "cnt.br", "com.br",
                     "coop.br", "ecn.br", "edu.br", "eng.br", "esp.br", "etc.br", "eti.br", "far.br", "flog.br", "fm.br", "fnd.br", "fot.br", "fst.br",
                     "g12.br", "ggf.br", "gov.br", "imb.br", "ind.br", "inf.br", "jor.br", "jus.br", "lel.br", "mat.br", "med.br", "mil.br", "mus.br",
                     "net.br", "nom.br", "not.br", "ntr.br", "odo.br", "org.br", "ppg.br", "pro.br", "psc.br", "psi.br", "qsl.br", "rec.br", "slg.br",
                     "srv.br", "tmp.br", "trd.br", "tur.br", "tv.br", "vet.br", "vlog.br", "wiki.br", "zlg.br", "com.bs", "net.bs", "org.bs", "edu.bs",
                     "gov.bs", "com.bz", "edu.bz", "gov.bz", "net.bz", "org.bz", "om.bz", "du.bz", "ov.bz", "et.bz", "rg.bz", "ab.ca", "bc.ca", "mb.ca",
                     "nb.ca", "nf.ca", "nl.ca", "ns.ca", "nt.ca", "nu.ca", "on.ca", "pe.ca", "qc.ca", "sk.ca", "yk.ca", "co.ck", "org.ck", "edu.ck", "gov.ck",
                     "net.ck", "gen.ck", "biz.ck", "info.ck", "ac.cn", "com.cn", "edu.cn", "gov.cn", "mil.cn", "net.cn", "org.cn", "ah.cn", "bj.cn", "cq.cn",
                     "fj.cn", "gd.cn", "gs.cn", "gz.cn", "gx.cn", "ha.cn", "hb.cn", "he.cn", "hi.cn", "hl.cn", "hn.cn", "jl.cn", "js.cn", "jx.cn", "ln.cn",
                     "nm.cn", "nx.cn", "qh.cn", "sc.cn", "sd.cn", "sh.cn", "sn.cn", "sx.cn", "tj.cn", "tw.cn", "xj.cn", "xz.cn", "yn.cn", "zj.cn", "com.co",
                     "org.co", "edu.co", "gov.co", "net.co", "mil.co", "nom.co", "ac.cr", "co.cr", "ed.cr", "fi.cr", "go.cr", "or.cr", "sa.cr", "cr", "ac.cy",
                     "net.cy", "gov.cy", "org.cy", "pro.cy", "name.cy", "ekloges.cy", "tm.cy", "ltd.cy", "biz.cy", "press.cy", "parliament.cy", "com.cy",
                     "edu.do", "gob.do", "gov.do", "com.do", "sld.do", "org.do", "net.do", "web.do", "mil.do", "art.do", "com.dz", "org.dz", "net.dz",
                     "gov.dz", "edu.dz", "asso.dz", "pol.dz", "art.dz", "com.ec", "info.ec", "net.ec", "fin.ec", "med.ec", "pro.ec", "org.ec", "edu.ec",
                     "gov.ec", "mil.ec", "com.eg", "edu.eg", "eun.eg", "gov.eg", "mil.eg", "name.eg", "net.eg", "org.eg", "sci.eg", "com.er", "edu.er",
                     "gov.er", "mil.er", "net.er", "org.er", "ind.er", "rochest.er", "w.er", "com.es", "nom.es", "org.es", "gob.es", "edu.es", "com.et",
                     "gov.et", "org.et", "edu.et", "net.et", "biz.et", "name.et", "info.et", "ac.fj", "biz.fj", "com.fj", "info.fj", "mil.fj", "name.fj",
                     "net.fj", "org.fj", "pro.fj", "co.fk", "org.fk", "gov.fk", "ac.fk", "nom.fk", "net.fk", "fr", "tm.fr", "asso.fr", "nom.fr", "prd.fr",
                     "presse.fr", "com.fr", "gouv.fr", "co.gg", "net.gg", "org.gg", "com.gh", "edu.gh", "gov.gh", "org.gh", "mil.gh", "com.gn", "ac.gn",
                     "gov.gn", "org.gn", "net.gn", "com.gr", "edu.gr", "net.gr", "org.gr", "gov.gr", "mil.gr", "com.gt", "edu.gt", "net.gt", "gob.gt",
                     "org.gt", "mil.gt", "ind.gt", "com.gu", "net.gu", "gov.gu", "org.gu", "edu.gu", "com.hk", "edu.hk", "gov.hk", "idv.hk", "net.hk",
                     "org.hk", "ac.id", "co.id", "net.id", "or.id", "web.id", "sch.id", "mil.id", "go.id", "war.net.id", "ac.il", "co.il", "org.il", "net.il",
                     "k12.il", "gov.il", "muni.il", "idf.il", "in", "co.in", "firm.in", "net.in", "org.in", "gen.in", "ind.in", "ac.in", "edu.in", "res.in",
                     "ernet.in", "gov.in", "mil.in", "nic.in", "iq", "gov.iq", "edu.iq", "com.iq", "mil.iq", "org.iq", "net.iq", "ir", "ac.ir", "co.ir",
                     "gov.ir", "id.ir", "net.ir", "org.ir", "sch.ir", "dnssec.ir", "gov.it", "edu.it", "co.je", "net.je", "org.je", "com.jo", "net.jo",
                     "gov.jo", "edu.jo", "org.jo", "mil.jo", "name.jo", "sch.jo", "ac.jp", "ad.jp", "co.jp", "ed.jp", "go.jp", "gr.jp", "lg.jp", "ne.jp",
                     "or.jp", "co.ke", "or.ke", "ne.ke", "go.ke", "ac.ke", "sc.ke", "me.ke", "mobi.ke", "info.ke", "per.kh", "com.kh", "edu.kh", "gov.kh",
                     "mil.kh", "net.kh", "org.kh", "com.ki", "biz.ki", "de.ki", "net.ki", "info.ki", "org.ki", "gov.ki", "edu.ki", "mob.ki", "tel.ki", "km",
                     "com.km", "coop.km", "asso.km", "nom.km", "presse.km", "tm.km", "medecin.km", "notaires.km", "pharmaciens.km", "veterinaire.km",
                     "edu.km", "gouv.km", "mil.km", "net.kn", "org.kn", "edu.kn", "gov.kn", "kr", "co.kr", "ne.kr", "or.kr", "re.kr", "pe.kr", "go.kr",
                     "mil.kr", "ac.kr", "hs.kr", "ms.kr", "es.kr", "sc.kr", "kg.kr", "seoul.kr", "busan.kr", "daegu.kr", "incheon.kr", "gwangju.kr",
                     "daejeon.kr", "ulsan.kr", "gyeonggi.kr", "gangwon.kr", "chungbuk.kr", "chungnam.kr", "jeonbuk.kr", "jeonnam.kr", "gyeongbuk.kr",
                     "gyeongnam.kr", "jeju.kr", "edu.kw", "com.kw", "net.kw", "org.kw", "gov.kw", "com.ky", "org.ky", "net.ky", "edu.ky", "gov.ky", "com.kz",
                     "edu.kz", "gov.kz", "mil.kz", "net.kz", "org.kz", "com.lb", "edu.lb", "gov.lb", "net.lb", "org.lb", "gov.lk", "sch.lk", "net.lk",
                     "int.lk", "com.lk", "org.lk", "edu.lk", "ngo.lk", "soc.lk", "web.lk", "ltd.lk", "assn.lk", "grp.lk", "hotel.lk", "com.lr", "edu.lr",
                     "gov.lr", "org.lr", "net.lr", "com.lv", "edu.lv", "gov.lv", "org.lv", "mil.lv", "id.lv", "net.lv", "asn.lv", "conf.lv", "com.ly",
                     "net.ly", "gov.ly", "plc.ly", "edu.ly", "sch.ly", "med.ly", "org.ly", "id.ly", "ma", "net.ma", "ac.ma", "org.ma", "gov.ma", "press.ma",
                     "co.ma", "tm.mc", "asso.mc", "co.me", "net.me", "org.me", "edu.me", "ac.me", "gov.me", "its.me", "priv.me", "org.mg", "nom.mg", "gov.mg",
                     "prd.mg", "tm.mg", "edu.mg", "mil.mg", "com.mg", "com.mk", "org.mk", "net.mk", "edu.mk", "gov.mk", "inf.mk", "name.mk", "pro.mk",
                     "com.ml", "net.ml", "org.ml", "edu.ml", "gov.ml", "presse.ml", "gov.mn", "edu.mn", "org.mn", "com.mo", "edu.mo", "gov.mo", "net.mo",
                     "org.mo", "com.mt", "org.mt", "net.mt", "edu.mt", "gov.mt", "aero.mv", "biz.mv", "com.mv", "coop.mv", "edu.mv", "gov.mv", "info.mv",
                     "int.mv", "mil.mv", "museum.mv", "name.mv", "net.mv", "org.mv", "pro.mv", "ac.mw", "co.mw", "com.mw", "coop.mw", "edu.mw", "gov.mw",
                     "int.mw", "museum.mw", "net.mw", "org.mw", "com.mx", "net.mx", "org.mx", "edu.mx", "gob.mx", "com.my", "net.my", "org.my", "gov.my",
                     "edu.my", "sch.my", "mil.my", "name.my", "com.nf", "net.nf", "arts.nf", "store.nf", "web.nf", "firm.nf", "info.nf", "other.nf", "per.nf",
                     "rec.nf", "com.ng", "org.ng", "gov.ng", "edu.ng", "net.ng", "sch.ng", "name.ng", "mobi.ng", "biz.ng", "mil.ng", "gob.ni", "co.ni",
                     "com.ni", "ac.ni", "edu.ni", "org.ni", "nom.ni", "net.ni", "mil.ni", "com.np", "edu.np", "gov.np", "org.np", "mil.np", "net.np",
                     "edu.nr", "gov.nr", "biz.nr", "info.nr", "net.nr", "org.nr", "com.nr", "com.om", "co.om", "edu.om", "ac.om", "sch.om", "gov.om",
                     "net.om", "org.om", "mil.om", "museum.om", "biz.om", "pro.om", "med.om", "edu.pe", "gob.pe", "nom.pe", "mil.pe", "sld.pe", "org.pe",
                     "com.pe", "net.pe", "com.ph", "net.ph", "org.ph", "mil.ph", "ngo.ph", "i.ph", "gov.ph", "edu.ph", "com.pk", "net.pk", "edu.pk", "org.pk",
                     "fam.pk", "biz.pk", "web.pk", "gov.pk", "gob.pk", "gok.pk", "gon.pk", "gop.pk", "gos.pk", "pwr.pl", "com.pl", "biz.pl", "net.pl",
                     "art.pl", "edu.pl", "org.pl", "ngo.pl", "gov.pl", "info.pl", "mil.pl", "waw.pl", "warszawa.pl", "wroc.pl", "wroclaw.pl", "krakow.pl",
                     "katowice.pl", "poznan.pl", "lodz.pl", "gda.pl", "gdansk.pl", "slupsk.pl", "radom.pl", "szczecin.pl", "lublin.pl", "bialystok.pl",
                     "olsztyn.pl", "torun.pl", "gorzow.pl", "zgora.pl", "biz.pr", "com.pr", "edu.pr", "gov.pr", "info.pr", "isla.pr", "name.pr", "net.pr",
                     "org.pr", "pro.pr", "est.pr", "prof.pr", "ac.pr", "com.ps", "net.ps", "org.ps", "edu.ps", "gov.ps", "plo.ps", "sec.ps", "co.pw", "ne.pw",
                     "or.pw", "ed.pw", "go.pw", "belau.pw", "arts.ro", "com.ro", "firm.ro", "info.ro", "nom.ro", "nt.ro", "org.ro", "rec.ro", "store.ro",
                     "tm.ro", "www.ro", "co.rs", "org.rs", "edu.rs", "ac.rs", "gov.rs", "in.rs", "com.sb", "net.sb", "edu.sb", "org.sb", "gov.sb", "com.sc",
                     "net.sc", "edu.sc", "gov.sc", "org.sc", "co.sh", "com.sh", "org.sh", "gov.sh", "edu.sh", "net.sh", "nom.sh", "com.sl", "net.sl",
                     "org.sl", "edu.sl", "gov.sl", "gov.st", "saotome.st", "principe.st", "consulado.st", "embaixada.st", "org.st", "edu.st", "net.st",
                     "com.st", "store.st", "mil.st", "co.st", "edu.sv", "gob.sv", "com.sv", "org.sv", "red.sv", "co.sz", "ac.sz", "org.sz", "com.tr",
                     "gen.tr", "org.tr", "biz.tr", "info.tr", "av.tr", "dr.tr", "pol.tr", "bel.tr", "tsk.tr", "bbs.tr", "k12.tr", "edu.tr", "name.tr",
                     "net.tr", "gov.tr", "web.tr", "tel.tr", "tv.tr", "co.tt", "com.tt", "org.tt", "net.tt", "biz.tt", "info.tt", "pro.tt", "int.tt",
                     "coop.tt", "jobs.tt", "mobi.tt", "travel.tt", "museum.tt", "aero.tt", "cat.tt", "tel.tt", "name.tt", "mil.tt", "edu.tt", "gov.tt",
                     "edu.tw", "gov.tw", "mil.tw", "com.tw", "net.tw", "org.tw", "idv.tw", "game.tw", "ebiz.tw", "club.tw", "com.mu", "gov.mu", "net.mu",
                     "org.mu", "ac.mu", "co.mu", "or.mu", "ac.mz", "co.mz", "edu.mz", "org.mz", "gov.mz", "com.na", "co.na", "ac.nz", "co.nz", "cri.nz",
                     "geek.nz", "gen.nz", "govt.nz", "health.nz", "iwi.nz", "maori.nz", "mil.nz", "net.nz", "org.nz", "parliament.nz", "school.nz", "abo.pa",
                     "ac.pa", "com.pa", "edu.pa", "gob.pa", "ing.pa", "med.pa", "net.pa", "nom.pa", "org.pa", "sld.pa", "com.pt", "edu.pt", "gov.pt",
                     "int.pt", "net.pt", "nome.pt", "org.pt", "publ.pt", "com.py", "edu.py", "gov.py", "mil.py", "net.py", "org.py", "com.qa", "edu.qa",
                     "gov.qa", "mil.qa", "net.qa", "org.qa", "asso.re", "com.re", "nom.re", "ac.ru", "adygeya.ru", "altai.ru", "amur.ru", "arkhangelsk.ru",
                     "astrakhan.ru", "bashkiria.ru", "belgorod.ru", "bir.ru", "bryansk.ru", "buryatia.ru", "cbg.ru", "chel.ru", "chelyabinsk.ru", "chita.ru",
                     "chukotka.ru", "chuvashia.ru", "com.ru", "dagestan.ru", "e-burg.ru", "edu.ru", "gov.ru", "grozny.ru", "int.ru", "irkutsk.ru",
                     "ivanovo.ru", "izhevsk.ru", "jar.ru", "joshkar-ola.ru", "kalmykia.ru", "kaluga.ru", "kamchatka.ru", "karelia.ru", "kazan.ru", "kchr.ru",
                     "kemerovo.ru", "khabarovsk.ru", "khakassia.ru", "khv.ru", "kirov.ru", "koenig.ru", "komi.ru", "kostroma.ru", "kranoyarsk.ru", "kuban.ru",
                     "kurgan.ru", "kursk.ru", "lipetsk.ru", "magadan.ru", "mari.ru", "mari-el.ru", "marine.ru", "mil.ru", "mordovia.ru", "mosreg.ru",
                     "msk.ru", "murmansk.ru", "nalchik.ru", "net.ru", "nnov.ru", "nov.ru", "novosibirsk.ru", "nsk.ru", "omsk.ru", "orenburg.ru", "org.ru",
                     "oryol.ru", "penza.ru", "perm.ru", "pp.ru", "pskov.ru", "ptz.ru", "rnd.ru", "ryazan.ru", "sakhalin.ru", "samara.ru", "saratov.ru",
                     "simbirsk.ru", "smolensk.ru", "spb.ru", "stavropol.ru", "stv.ru", "surgut.ru", "tambov.ru", "tatarstan.ru", "tom.ru", "tomsk.ru",
                     "tsaritsyn.ru", "tsk.ru", "tula.ru", "tuva.ru", "tver.ru", "tyumen.ru", "udm.ru", "udmurtia.ru", "ulan-ude.ru", "vladikavkaz.ru",
                     "vladimir.ru", "vladivostok.ru", "volgograd.ru", "vologda.ru", "voronezh.ru", "vrn.ru", "vyatka.ru", "yakutia.ru", "yamal.ru",
                     "yekaterinburg.ru", "yuzhno-sakhalinsk.ru", "ac.rw", "co.rw", "com.rw", "edu.rw", "gouv.rw", "gov.rw", "int.rw", "mil.rw", "net.rw",
                     "com.sa", "edu.sa", "gov.sa", "med.sa", "net.sa", "org.sa", "pub.sa", "sch.sa", "com.sd", "edu.sd", "gov.sd", "info.sd", "med.sd",
                     "net.sd", "org.sd", "tv.sd", "a.se", "ac.se", "b.se", "bd.se", "c.se", "d.se", "e.se", "f.se", "g.se", "h.se", "i.se", "k.se", "l.se",
                     "m.se", "n.se", "o.se", "org.se", "p.se", "parti.se", "pp.se", "press.se", "r.se", "s.se", "t.se", "tm.se", "u.se", "w.se", "x.se",
                     "y.se", "z.se", "com.sg", "edu.sg", "gov.sg", "idn.sg", "net.sg", "org.sg", "per.sg", "art.sn", "com.sn", "edu.sn", "gouv.sn", "org.sn",
                     "perso.sn", "univ.sn", "com.sy", "edu.sy", "gov.sy", "mil.sy", "net.sy", "news.sy", "org.sy", "ac.th", "co.th", "go.th", "in.th",
                     "mi.th", "net.th", "or.th", "ac.tj", "biz.tj", "co.tj", "com.tj", "edu.tj", "go.tj", "gov.tj", "info.tj", "int.tj", "mil.tj", "name.tj",
                     "net.tj", "nic.tj", "org.tj", "test.tj", "web.tj", "agrinet.tn", "com.tn", "defense.tn", "edunet.tn", "ens.tn", "fin.tn", "gov.tn",
                     "ind.tn", "info.tn", "intl.tn", "mincom.tn", "nat.tn", "net.tn", "org.tn", "perso.tn", "rnrt.tn", "rns.tn", "rnu.tn", "tourism.tn",
                     "ac.tz", "co.tz", "go.tz", "ne.tz", "or.tz", "biz.ua", "cherkassy.ua", "chernigov.ua", "chernovtsy.ua", "ck.ua", "cn.ua", "co.ua",
                     "com.ua", "crimea.ua", "cv.ua", "dn.ua", "dnepropetrovsk.ua", "donetsk.ua", "dp.ua", "edu.ua", "gov.ua", "if.ua", "in.ua",
                     "ivano-frankivsk.ua", "kh.ua", "kharkov.ua", "kherson.ua", "khmelnitskiy.ua", "kiev.ua", "kirovograd.ua", "km.ua", "kr.ua", "ks.ua",
                     "kv.ua", "lg.ua", "lugansk.ua", "lutsk.ua", "lviv.ua", "me.ua", "mk.ua", "net.ua", "nikolaev.ua", "od.ua", "odessa.ua", "org.ua",
                     "pl.ua", "poltava.ua", "pp.ua", "rovno.ua", "rv.ua", "sebastopol.ua", "sumy.ua", "te.ua", "ternopil.ua", "uzhgorod.ua", "vinnica.ua",
                     "vn.ua", "zaporizhzhe.ua", "zhitomir.ua", "zp.ua", "zt.ua", "ac.ug", "co.ug", "go.ug", "ne.ug", "or.ug", "org.ug", "sc.ug", "ac.uk",
                     "bl.uk", "british-library.uk", "co.uk", "cym.uk", "gov.uk", "govt.uk", "icnet.uk", "jet.uk", "lea.uk", "ltd.uk", "me.uk", "mil.uk",
                     "mod.uk", "national-library-scotland.uk", "nel.uk", "net.uk", "nhs.uk", "nic.uk", "nls.uk", "org.uk", "orgn.uk", "parliament.uk",
                     "plc.uk", "police.uk", "sch.uk", "scot.uk", "soc.uk", "dni.us", "fed.us", "isa.us", "kids.us", "nsn.us", "com.uy", "edu.uy", "gub.uy",
                     "mil.uy", "net.uy", "org.uy", "co.ve", "com.ve", "edu.ve", "gob.ve", "info.ve", "mil.ve", "net.ve", "org.ve", "web.ve", "co.vi",
                     "com.vi", "k12.vi", "net.vi", "org.vi", "ac.vn", "biz.vn", "com.vn", "edu.vn", "gov.vn", "health.vn", "info.vn", "int.vn", "name.vn",
                     "net.vn", "org.vn", "pro.vn", "co.ye", "com.ye", "gov.ye", "ltd.ye", "me.ye", "net.ye", "org.ye", "plc.ye", "ac.yu", "co.yu", "edu.yu",
                     "gov.yu", "org.yu", "ac.za", "agric.za", "alt.za", "bourse.za", "city.za", "co.za", "cybernet.za", "db.za", "ecape.school.za", "edu.za",
                     "fs.school.za", "gov.za", "gp.school.za", "grondar.za", "iaccess.za", "imt.za", "inca.za", "kzn.school.za", "landesign.za", "law.za",
                     "lp.school.za", "mil.za", "mpm.school.za", "ncape.school.za", "net.za", "ngo.za", "nis.za", "nom.za", "nw.school.za", "olivetti.za",
                     "org.za", "pix.za", "school.za", "tm.za", "wcape.school.za", "web.za", "ac.zm", "co.zm", "com.zm", "edu.zm", "gov.zm", "net.zm",
                     "org.zm", "sch.zm", "e164.arpa", "au.com", "br.com", "cn.com", "de.com", "eu.com", "gb.com", "hu.com", "no.com", "qc.com", "ru.com",
                     "sa.com", "se.com", "uk.com", "us.com", "uy.com", "za.com", "de.net", "gb.net", "uk.net", "dk.org", "eu.org", "edu.ac", "com.ae",
                     "com.ai", "edu.ai", "gov.ai", "org.ai", "uba.ar", "esc.edu.ar", "priv.at", "conf.au", "info.au", "otc.au", "oz.au", "telememo.au",
                     "com.az", "net.az", "org.az", "ac.be", "belgie.be", "dns.be", "fgov.be", "com.bm", "edu.bm", "gov.bm", "net.bm", "org.bm", "sp.br",
                     "hk.cn", "mo.cn", "arts.co", "firm.co", "info.co", "int.co", "rec.co", "store.co", "web.co", "com.cu", "net.cu", "org.cu", "co.dk",
                     "ass.dz", "k12.ec", "gov.fj", "id.fj", "school.fj", "com.fk", "aeroport.fr", "assedic.fr", "avocat.fr", "avoues.fr", "barreau.fr",
                     "cci.fr", "chambagri.fr", "chirurgiens-dentistes.fr", "experts-comptables.fr", "geometre-expert.fr", "greta.fr", "huissier-justice.fr",
                     "medecin.fr", "notaires.fr", "pharmacien.fr", "port.fr", "veterinaire.fr", "com.ge", "edu.ge", "gov.ge", "mil.ge", "net.ge", "org.ge",
                     "pvt.ge", "ac.gg", "alderney.gg", "gov.gg", "guernsey.gg", "ind.gg", "ltd.gg", "sark.gg", "sch.gg", "mil.gu", "2000.hu", "agrar.hu",
                     "bolt.hu", "casino.hu", "city.hu", "co.hu", "erotica.hu", "erotika.hu", "film.hu", "forum.hu", "games.hu", "hotel.hu", "info.hu",
                     "ingatlan.hu", "jogasz.hu", "konyvelo.hu", "lakas.hu", "media.hu", "news.hu", "org.hu", "priv.hu", "reklam.hu", "sex.hu", "shop.hu",
                     "sport.hu", "suli.hu", "szex.hu", "tm.hu", "tozsde.hu", "utazas.hu", "video.hu", "ac.im", "co.im", "gov.im", "net.im", "nic.im",
                     "org.im", "ac.je", "gov.je", "ind.je", "jersey.je", "ltd.je", "sch.je", "aichi.jp", "akita.jp", "aomori.jp", "chiba.jp", "ehime.jp",
                     "fukui.jp", "fukuoka.jp", "fukushima.jp", "gifu.jp", "gov.jp", "gunma.jp", "hiroshima.jp", "hokkaido.jp", "hyogo.jp", "ibaraki.jp",
                     "ishikawa.jp", "iwate.jp", "kagawa.jp", "kagoshima.jp", "kanagawa.jp", "kanazawa.jp", "kawasaki.jp", "kitakyushu.jp", "kobe.jp",
                     "kochi.jp", "kumamoto.jp", "kyoto.jp", "matsuyama.jp", "mie.jp", "miyagi.jp", "miyazaki.jp", "nagano.jp", "nagasaki.jp", "nagoya.jp",
                     "nara.jp", "net.jp", "niigata.jp", "oita.jp", "okayama.jp", "okinawa.jp", "org.jp", "osaka.jp", "saga.jp", "saitama.jp", "sapporo.jp",
                     "sendai.jp", "shiga.jp", "shimane.jp", "shizuoka.jp", "takamatsu.jp", "tochigi.jp", "tokushima.jp", "tokyo.jp", "tottori.jp",
                     "toyama.jp", "utsunomiya.jp", "wakayama.jp", "yamagata.jp", "yamaguchi.jp", "yamanashi.jp", "yokohama.jp", "kyonggi.kr", "com.la",
                     "net.la", "org.la", "mil.lb", "com.lc", "edu.lc", "gov.lc", "net.lc", "org.lc", "com.mm", "edu.mm", "gov.mm", "net.mm", "org.mm",
                     "tm.mt", "uu.mt", "alt.na", "cul.na", "edu.na", "net.na", "org.na", "telecom.na", "unam.na", "com.nc", "net.nc", "org.nc", "ac.ng",
                     "tel.no", "fax.nr", "mob.nr", "mobil.nr", "mobile.nr", "tel.nr", "tlf.nr", "mod.om", "ac.pg", "com.pg", "net.pg", "agro.pl", "aid.pl",
                     "atm.pl", "auto.pl", "gmina.pl", "gsm.pl", "mail.pl", "media.pl", "miasta.pl", "nieruchomosci.pl", "nom.pl", "pc.pl", "powiat.pl",
                     "priv.pl", "realestate.pl", "rel.pl", "sex.pl", "shop.pl", "sklep.pl", "sos.pl", "szkola.pl", "targi.pl", "tm.pl", "tourism.pl",
                     "travel.pl", "turystyka.pl", "sch.sd", "mil.sh", "mil.tr", "at.tt", "au.tt", "be.tt", "ca.tt", "de.tt", "dk.tt", "es.tt", "eu.tt",
                     "fr.tt", "it.tt", "nic.tt", "se.tt", "uk.tt", "us.tt", "co.tv", "gove.tw", "edu.uk", "arts.ve", "bib.ve", "firm.ve", "gov.ve", "int.ve",
                     "nom.ve", "rec.ve", "store.ve", "tec.ve", "ch.vu", "com.vu", "de.vu", "edu.vu", "fr.vu", "net.vu", "org.vu", "com.ws", "edu.ws",
                     "gov.ws", "net.ws", "org.ws", "edu.ye", "mil.ye", "ac.zw", "co.zw", "gov.zw", "org.zw" };

     static {
         // using http://javainetlocator.sourceforge.net/ if library is present
         // we use this class using reflection to be able to remove it because that class is old and without maintenancy
         InetAddressLocatorClass = ClassProvider.load("net.sf.javainetlocator.InetAddressLocator", new File("lib/InetAddressLocator.jar"));
         InetAddressLocatorGetLocaleInetAddressMethod = ClassProvider.getStaticMethod(InetAddressLocatorClass, "getLocale", new Class[]{InetAddress.class});
         ccSLD_TLD.addAll(Arrays.asList(ccSLD_TLD_list));
     }

    private static Map<String, Integer> TLDID = new ConcurrentHashMap<String, Integer>(32);
    //private static HashMap<String, String> TLDName = new HashMap<String, String>();

    private static void insertTLDProps(final String[] TLDList, final int id) {
        int p;
        String tld;
        //String name;
        final Integer ID = Integer.valueOf(id);
        for (final String TLDelement : TLDList) {
            p = TLDelement.indexOf('=');
            if (p > 0) {
                tld = TLDelement.substring(0, p).toLowerCase();
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
    public static final int TLD_Local_ID               = 7; // a local address

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

    private static KeyList globalHosts = null;
    private static boolean noLocalCheck = false;

    public static void init(final File globalHostsnameCache) {
        if (globalHostsnameCache == null) {
            globalHosts = null;
        } else try {
            globalHosts = new KeyList(globalHostsnameCache);
            log.info("loaded globalHosts cache of hostnames, size = " + globalHosts.size());
        } catch (final IOException e) {
            globalHosts = null;
        }
    }

    /**
     * the isLocal check can be switched off to gain a better crawling speed.
     * however, if the check is switched off, then ALL urls are considered as local
     * this will create url-hashes for global domains which do not fit in environments
     * where the isLocal switch is not de-activated. Please handle this method with great care
     * Bad usage will make peers inoperable.
     * @param v
     */
    public static void setNoLocalCheck(final boolean v) {
        noLocalCheck = v;
    }

    public static synchronized void close() {
        if (globalHosts != null) try {globalHosts.close();} catch (final IOException e) {log.warn(e);}
    }

    /**
    * Does an DNS-Check to resolve a hostname to an IP.
    *
    * @param host Hostname of the host in demand.
    * @return String with the ip. null, if the host could not be resolved.
    */
    public static InetAddress dnsResolveFromCache(String host) throws UnknownHostException {
        if ((host == null) || host.isEmpty()) return null;
        host = host.toLowerCase().trim();

        // trying to resolve host by doing a name cache lookup
        InetAddress ip = NAME_CACHE_HIT.get(host);
        if (ip != null) {
            cacheHit_Hit++;
            return ip;
        }
        cacheHit_Miss++;

        if (NAME_CACHE_MISS.containsKey(host)) {
            cacheMiss_Hit++;
            return null;
        }
        cacheMiss_Miss++;
        throw new UnknownHostException("host not in cache");
    }

    public static void setNoCachingPatterns(final String patternList) throws PatternSyntaxException {
        nameCacheNoCachingPatterns = makePatterns(patternList);
    }

    public static List<Pattern> makePatterns(final String patternList) throws PatternSyntaxException {
    	final String[] entries = (patternList != null) ? CommonPattern.COMMA.split(patternList) : new String[0];
    	final List<Pattern> patterns = new ArrayList<Pattern>(entries.length);
    	for (final String entry : entries) {
            patterns.add(Pattern.compile(entry.trim()));
        }
    	return patterns;
    }

    public static boolean matchesList(final String obj, final List<Pattern> patterns) {
        for (final Pattern nextPattern: patterns) {
            if (nextPattern.matcher(obj).matches()) return true;
        }
        return false;
    }

    public static String getHostName(final InetAddress i) {
        final Collection<String> hosts = NAME_CACHE_HIT.getKeys(i);
        if (!hosts.isEmpty()) return hosts.iterator().next();
        final String host = i.getHostName();
        NAME_CACHE_HIT.insertIfAbsent(host, i);
        cacheHit_Insert++;
        return host;
    }

    /**
     * in case that the host name was resolved using a time-out request
     * it can be nice to push that information to the name cache
     * @param i the inet address
     * @param host the known host name
     */
    public static void setHostName(final InetAddress i, final String host) {
        NAME_CACHE_HIT.insertIfAbsent(host, i);
        cacheHit_Insert++;
    }

    final private static TimeLimiter timeLimiter = new SimpleTimeLimiter(Executors.newFixedThreadPool(20));

    /**
     * resolve a host address using a local DNS cache and a DNS lookup if necessary
     * @param clienthost
     * @return the hosts InetAddress or null if the address cannot be resolved
     */
    public static InetAddress dnsResolve(final String host0) {
        if (host0 == null || host0.isEmpty()) return null;
        final String host = host0.toLowerCase().trim();

        if (MemoryControl.shortStatus()) {
            NAME_CACHE_HIT.clear();
            NAME_CACHE_MISS.clear();
        }
        
        if (host0.endsWith(".yacyh")) {
            // that should not happen here
            return null;
        }

        // try to resolve host by doing a name cache lookup
        InetAddress ip = NAME_CACHE_HIT.get(host);
        if (ip != null) {
            //System.out.println("DNSLOOKUP-CACHE-HIT(CONC) " + host);
            cacheHit_Hit++;
            return ip;
        }
        cacheHit_Miss++;
        if (NAME_CACHE_MISS.containsKey(host)) {
            //System.out.println("DNSLOOKUP-CACHE-MISS(CONC) " + host);
            cacheMiss_Hit++;
            return null;
        }
        cacheMiss_Miss++;

        // call dnsResolveNetBased(host) using concurrency to interrupt execution in case of a time-out
        final Object sync_obj_new = new Object();
        Object sync_obj = LOOKUP_SYNC.putIfAbsent(host, sync_obj_new);
        if (sync_obj == null) sync_obj = sync_obj_new;
        synchronized (sync_obj) {
            // now look again if the host is in the cache where it may be meanwhile because of the synchronization

            ip = NAME_CACHE_HIT.get(host);
            if (ip != null) {
                //System.out.println("DNSLOOKUP-CACHE-HIT(SYNC) " + host);
                LOOKUP_SYNC.remove(host);
                cacheHit_Hit++;
                return ip;
            }
            cacheHit_Miss++;
            if (NAME_CACHE_MISS.containsKey(host)) {
                //System.out.println("DNSLOOKUP-CACHE-MISS(SYNC) " + host);
                LOOKUP_SYNC.remove(host);
                cacheMiss_Hit++;
                return null;
            }
            cacheMiss_Miss++;

            // do the dns lookup on the dns server
            //if (!matchesList(host, nameCacheNoCachingPatterns)) System.out.println("DNSLOOKUP " + host);
            try {
                //final long t = System.currentTimeMillis();
                String oldName = Thread.currentThread().getName();
                Thread.currentThread().setName("Domains: DNS resolve of '" + host + "'"); // thread dump show which host is resolved
                if (InetAddresses.isInetAddress(host)) {
                    try {
                        ip = InetAddresses.forString(host);
                        log.info("using guava for host resolution:"  + host);
                    } catch (final IllegalArgumentException e) {
                        ip = null;
                    }
                }
                Thread.currentThread().setName(oldName);
                if (ip == null) try {
                    ip = timeLimiter.callWithTimeout(new Callable<InetAddress>() {
                        @Override
                        public InetAddress call() throws Exception {
                            return InetAddress.getByName(host);
                        }
                    }, 3000L, TimeUnit.MILLISECONDS, false);
                    //ip = TimeoutRequest.getByName(host, 1000); // this makes the DNS request to backbone
                } catch (final UncheckedTimeoutException e) {
                	// in case of a timeout - maybe cause of massive requests - do not fill NAME_CACHE_MISS
                	LOOKUP_SYNC.remove(host);
                    return null;
                }
                //.out.println("DNSLOOKUP-*LOOKUP* " + host + ", time = " + (System.currentTimeMillis() - t) + "ms");
            } catch (final Throwable e) {
                // add new entries
                NAME_CACHE_MISS.insertIfAbsent(host, PRESENT);
                cacheMiss_Insert++;
                LOOKUP_SYNC.remove(host);
                return null;
            }

            if (ip == null) {
                // add new entries
                NAME_CACHE_MISS.insertIfAbsent(host, PRESENT);
                cacheMiss_Insert++;
                LOOKUP_SYNC.remove(host);
                return null;
            }

            if (!ip.isLoopbackAddress() && !matchesList(host, nameCacheNoCachingPatterns)) {
                // add new ip cache entries
                NAME_CACHE_HIT.insertIfAbsent(host, ip);
                cacheHit_Insert++;

                // add also the isLocal host name caches
                final boolean localp = ip.isAnyLocalAddress() || ip.isLinkLocalAddress() || ip.isSiteLocalAddress();
                if (localp) {
                    localHostNames.add(host);
                } else {
                    if (globalHosts != null) try {
                        globalHosts.add(host);
                    } catch (final IOException e) {}
                }
            }
            LOOKUP_SYNC.remove(host);
            return ip;
        }
    }

    public static void clear() {
        try {
        	globalHosts.clear();
        	NAME_CACHE_HIT.clear();
        	NAME_CACHE_MISS.clear();
        } catch (final IOException e) {}
    }

    /**
    * Returns the number of entries in the nameCacheHit map
    *
    * @return int The number of entries in the nameCacheHit map
    */
    public static int nameCacheHitSize() {
        return NAME_CACHE_HIT.size();
    }

    public static int nameCacheMissSize() {
        return NAME_CACHE_MISS.size();
    }

    public static int nameCacheNoCachingPatternsSize() {
        return nameCacheNoCachingPatterns.size();
    }

    private static Set<InetAddress> localHostAddresses = new HashSet<InetAddress>();
    private static Set<String> localHostNames = new HashSet<String>();
    static {
        try {
            final InetAddress localHostAddress = InetAddress.getLocalHost();
            if (localHostAddress != null) localHostAddresses.add(localHostAddress);
        } catch (final UnknownHostException e) {}
        try {
            final InetAddress[] moreAddresses = InetAddress.getAllByName(LOCALHOST_NAME);
            if (moreAddresses != null) localHostAddresses.addAll(Arrays.asList(moreAddresses));
        } catch (final UnknownHostException e) {}

        // to get the local host name, a dns lookup is necessary.
        // if such a lookup blocks, it can cause that the static initiatializer does not finish fast
        // therefore we start the host name lookup as concurrent thread
        // meanwhile the host name is "127.0.0.1" which is not completely wrong
        new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("Domains: init");
                // try to get local addresses from interfaces
                try {
                    final Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
                    while (nis.hasMoreElements()) {
                        final NetworkInterface ni = nis.nextElement();
                        final Enumeration<InetAddress> addrs = ni.getInetAddresses();
                        while (addrs.hasMoreElements()) {
                            final InetAddress addr = addrs.nextElement();
                            if (addr != null) localHostAddresses.add(addr);
                        }
                    }
                } catch (final SocketException e) {
                }

                // now look up the host name
                try {
                    LOCALHOST_NAME = getHostName(InetAddress.getLocalHost());
                } catch (final UnknownHostException e) {}

                // after the host name was resolved, we try to look up more local addresses
                // using the host name:
                try {
                    final InetAddress[] moreAddresses = InetAddress.getAllByName(LOCALHOST_NAME);
                    if (moreAddresses != null) localHostAddresses.addAll(Arrays.asList(moreAddresses));
                } catch (final UnknownHostException e) {
                }

                // fill a cache of local host names
                for (final InetAddress a: localHostAddresses) {
                    final String hostname = getHostName(a);
                    if (hostname != null) {
                        localHostNames.add(hostname);
                        localHostNames.add(a.getHostAddress());
                    }
                }
            }
        }.start();
    }

    public static InetAddress myPublicLocalIP() {
        // list all addresses
        // for (int i = 0; i < localHostAddresses.length; i++) System.out.println("IP: " + localHostAddresses[i].getHostAddress()); // DEBUG
        if (localHostAddresses.isEmpty()) {
            return null;
        }
        if (localHostAddresses.size() == 1) {
            // only one network connection available
            return localHostAddresses.iterator().next();
        }
        // we have more addresses, find an address that is not local
        int b0, b1;
        for (final InetAddress a: localHostAddresses) {
            b0 = 0Xff & a.getAddress()[0];
            b1 = 0Xff & a.getAddress()[1];
            if (b0 != 10 && // class A reserved
                b0 != 127 && // loopback
                (b0 != 172 || b1 < 16 || b1 > 31) && // class B reserved
                (b0 != 192 || b1 != 168) && // class C reserved
                (a.getHostAddress().indexOf(':',0) < 0))
                return a;
        }
        // there is only a local address
        // return that one that is returned with InetAddress.getLocalHost()
        // if appropriate
        try {
            final InetAddress localHostAddress = InetAddress.getLocalHost();
            if (localHostAddress != null &&
                (0Xff & localHostAddress.getAddress()[0]) != 127 &&
                localHostAddress.getHostAddress().indexOf(':',0) < 0) return localHostAddress;
        } catch (final UnknownHostException e) {
        }
        // we filter out the loopback address 127.0.0.1 and all addresses without a name
        for (final InetAddress a: localHostAddresses) {
            if ((0Xff & a.getAddress()[0]) != 127 &&
                a.getHostAddress().indexOf(':',0) < 0 &&
                a.getHostName() != null &&
                !a.getHostName().isEmpty()) return a;
        }
        // if no address has a name, then take any other than the loopback
        for (final InetAddress a: localHostAddresses) {
            if ((0Xff & a.getAddress()[0]) != 127 &&
                a.getHostAddress().indexOf(':',0) < 0) return a;
        }
        // if all fails, give back whatever we have
        for (final InetAddress a: localHostAddresses) {
            if (a.getHostAddress().indexOf(':',0) < 0) return a;
        }
        // finally, just get any
        return localHostAddresses.iterator().next();
    }

    /**
     * generate a list of intranet InetAddresses without the loopback address 127.0.0.1
     * @return list of all intranet addresses
     */
    public static Set<InetAddress> myIntranetIPs() {
        // list all local addresses
        if (localHostAddresses.size() < 1) try {Thread.sleep(1000);} catch (final InterruptedException e) {}
        final Set<InetAddress> list = new HashSet<InetAddress>();
        if (localHostAddresses.isEmpty()) return list; // give up
        for (final InetAddress a: localHostAddresses) {
            if ((0Xff & a.getAddress()[0]) == 127) continue;
            list.add(a);
        }
        return list;
    }

    public static boolean isThisHostIP(final String hostName) {
        if ((hostName == null) || (hostName.isEmpty())) return false;

        boolean isThisHostIP = false;
        try {
            final InetAddress clientAddress = Domains.dnsResolve(hostName);
            if (clientAddress == null) return false;
            if (clientAddress.isAnyLocalAddress() || clientAddress.isLoopbackAddress()) return true;
            for (final InetAddress a: localHostAddresses) {
                if (a.equals(clientAddress)) {
                    isThisHostIP = true;
                    break;
                }
            }
        } catch (final Exception e) {}
        return isThisHostIP;
    }

    public static boolean isThisHostIP(final InetAddress clientAddress) {
        if (clientAddress == null) return false;

        boolean isThisHostIP = false;
        try {
            if (clientAddress.isAnyLocalAddress() || clientAddress.isLoopbackAddress()) return true;

            for (final InetAddress a: localHostAddresses) {
                if (a.equals(clientAddress)) {
                    isThisHostIP = true;
                    break;
                }
            }
        } catch (final Exception e) {}
        return isThisHostIP;
    }

    public static int getDomainID(final String host, final InetAddress hostaddress) {
        if (host == null || host.isEmpty()) return TLD_Local_ID;
        final int p = host.lastIndexOf('.');
        final String tld = (p > 0) ? host.substring(p + 1) : "";
        final Integer i = TLDID.get(tld);
        if (i != null) return i.intValue();
        return (isLocal(host, hostaddress)) ? TLD_Local_ID : TLD_Generic_ID;
    }

    /**
     * check the host ip string against localhost names
     * @param host
     * @return true if the host from the string is the localhost
     */
    public static boolean isLocalhost(final String host) {
        return (host != null && LOCALHOST_PATTERNS.matcher(host).matches());
    }

    /**
     * check if a given host is the name for a local host address
     * this method will return true if noLocalCheck is switched on. This means that
     * not only local and global addresses are then not distinguished but also that
     * global address hashes do not fit any more to previously stored address hashes since
     * local/global is marked in the hash.
     * @param host
     * @return
     */
    public static boolean isIntranet(final String host) {
        return (noLocalCheck || // DO NOT REMOVE THIS! it is correct to return true if the check is off
                (host != null && INTRANET_PATTERNS.matcher(host).matches()));
    }
    
    /**
     * check if the given host is a local address.
     * the hostaddress is optional and shall be given if the address is already known
     * @param host
     * @param hostaddress may be null if not known yet
     * @return true if the given host is local
     */
    public static boolean isLocal(final String host, final InetAddress hostaddress) {
        return isLocal(host, hostaddress, true);
    }

    private static boolean isLocal(final String host, InetAddress hostaddress, final boolean recursive) {

        if (noLocalCheck || // DO NOT REMOVE THIS! it is correct to return true if the check is off
            host == null ||
            host.isEmpty()) return true;

        // check local ip addresses
        if (isIntranet(host)) return true;
        if (hostaddress != null && (
                isIntranet(hostaddress.getHostAddress()) ||
                isLocal(hostaddress)
            )) return true;

        // check if there are other local IP addresses that are not in
        // the standard IP range
        if (localHostNames.contains(host)) return true;
        if (globalHosts != null && globalHosts.contains(host)) {
            //System.out.println("ISLOCAL-GLOBALHOSTS-HIT " + host);
            return false;
        }

        // check simply if the tld in the host is a known tld
        final int p = host.lastIndexOf('.');
        final String tld = (p > 0) ? host.substring(p + 1) : "";
        final Integer i = TLDID.get(tld);
        if (i != null) return false;

        // check dns lookup: may be a local address even if the domain name looks global
        if (!recursive) return false;
        if (hostaddress == null) hostaddress = dnsResolve(host);
        return isLocal(hostaddress);
    }

    private static boolean isLocal(final InetAddress a) {
        final boolean
            localp = noLocalCheck || // DO NOT REMOVE THIS! it is correct to return true if the check is off
            a == null ||
            a.isAnyLocalAddress() ||
            a.isLinkLocalAddress() ||
            a.isLoopbackAddress() ||
            a.isSiteLocalAddress();
        return localp;
    }

    /**
     * find the locale for a given host. This feature is only available in full quality,
     * if the file InetAddressLocator.jar is placed in the /lib directory (as a plug-in)
     * from http://javainetlocator.sourceforge.net/
     * In case that that you know the InetAddress of the host, DO NOT call this method but the
     * other method with the InetAddress first to get better results.
     * @param host
     * @return the locale for the host
     */
    public static Locale getLocale(final String host) {
        if (host == null) return null;
        final Locale locale = getLocale(dnsResolve(host));
        if (locale != null && locale.getCountry() != null && locale.getCountry().length() > 0) return locale;
        return null;
        /*
        final int p = host.lastIndexOf('.');
        if (p < 0) return null;
        String tld = host.substring(p + 1).toUpperCase();
        if (tld.length() < 2) return null;
        if (tld.length() > 2) tld = "US";
        return new Locale("en", tld);
        */
    }

    /**
     * find the locale for a given Address
     * This uses the InetAddressLocator.jar library
     * TODO: integrate http://www.maxmind.com/app/geolitecountry
     * @param address
     * @return
     */
    public static Locale getLocale(final InetAddress address) {
        if (InetAddressLocatorGetLocaleInetAddressMethod == null) return null;
        if (address == null) return null;
        if (isLocal(address.getHostAddress(), address, false)) return null;
        try {
            return (Locale) InetAddressLocatorGetLocaleInetAddressMethod.invoke(null, new Object[]{address});
        } catch (final IllegalArgumentException e) {
            return null;
        } catch (final IllegalAccessException e) {
            return null;
        } catch (final InvocationTargetException e) {
            return null;
        }
    }

    /**
     * compute the Domain Class Name, which is either the top-level-domain or
     * a combination of the second-level-domain plus top-level-domain if the second-level-domain
     * is a ccSLD ("country code second-level domain"). Such names can be taken from a list of ccSLDs.
     * @param host
     * @return the TLD or ccSLD+TLD if that is on a list
     */
    public static String getDNC(String host) {
        int p0 = host.lastIndexOf('.');
        if (p0 < 0) return host.toLowerCase();
        int p1 = host.lastIndexOf('.', p0 - 1);
        if (p1 < 0) return host.substring(p0 + 1).toLowerCase();
        String ccSLDTLD = host.substring(p1 + 1).toLowerCase();
        return ccSLD_TLD.contains(ccSLDTLD) ? ccSLDTLD : host.substring(p0 + 1).toLowerCase();
    }

    public static void main(final String[] args) {
        /*
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    System.out.println(addr);
                }
            }
        } catch(SocketException e) {
            System.err.println(e);
        }
        */
        InetAddress a;
        a = dnsResolve("yacy.net"); System.out.println(a);
        a = dnsResolve("kaskelix.de"); System.out.println(a);
        a = dnsResolve("yacy.net"); System.out.println(a);

        try { Thread.sleep(1000);} catch (final InterruptedException e) {} // get time for class init
        System.out.println("myPublicLocalIP: " + myPublicLocalIP());
        for (final InetAddress b : myIntranetIPs()) {
            System.out.println("Intranet IP: " + b);
        }
    }
}
