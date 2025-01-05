// ISO639.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.09.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support for ISO 639 language codes.
 * @see <a href="https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes">Wikipedia list of ISO 639-1 codes</a>
 * @see <a href="http://www.loc.gov/standards/iso639-2/php/code_list.php">Language Code List from the ISO 639-2 Registration Authority (Library of Congress)</a>
 * @see <a href="http://www-01.sil.org/iso639-3/">Home page of the ISO 639-3 Registration Authority (SIL International)</a>
 * @see <a href="https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry">IANA language subtag registry</a>
 * @see <a href="http://www.loc.gov/standards/iso639-2/php/code_changes.php">Code Changes history from the ISO 639-2 Registration Authority</a> 
 */
public class ISO639 {
	
	/*
	 * Note : using icu4j package classes such as com.ibm.icu.impl.LocaleIDs may be
	 * considered to maintain a more up to date support of ISO 639 codes, notably to
	 * support ISO 639 3 letters language codes.
	 */

	/** ISO 639-1 language codes table : [two letters code] - [ISO Reference name] */
    private static final String[] codes = {
        "aa-Afar",
        "ab-Abkhazian",
        "ae-Avestan",
        "af-Afrikaans",
        "ak-Akan",
        "am-Amharic",
        "an-Aragonese",
        "ar-Arabic",
        "as-Assamese",
        "av-Avaric",
        "ay-Aymara",
        "az-Azerbaijani",
        "ba-Bashkir",
        "be-Belarusian",
        "bg-Bulgarian",
        "bh-Bihari", // collective language code for bho-Bhojpuri, mag-Magahi, and mai-Maithili
        "bi-Bislama",
        "bm-Bambara",
        "bn-Bengali",
        "bo-Tibetan",
        "br-Breton",
        "bs-Bosnian",
        "ca-Catalan",
        "ce-Chechen",
        "ch-Chamorro",
        "co-Corsican",
        "cr-Cree",
        "cs-Czech",
        "cu-Church Slavic",
        "cv-Chuvash",
        "cy-Welsh",
        "da-Danish",
        "de-German",
        "dv-Dhivehi",
        "dz-Dzongkha",
        "ee-Ewe",
        "el-Modern Greek (1453-)",
        "en-English",
        "eo-Esperanto",
        "es-Spanish",
        "et-Estonian",
        "eu-Basque",
        "fa-Persian",
        "ff-Fulah",
        "fi-Finnish",
        "fj-Fijian",
        "fo-Faroese",
        "fr-French",
        "fy-Western Frisian",
        "ga-Irish",
        "gd-Scottish Gaelic",
        "gl-Galician",
        "gn-Guarani",
        "gu-Gujarati",
        "gv-Manx",
        "ha-Hausa",
        "he-Hebrew",
        "hi-Hindi",
        "ho-Hiri Motu",
        "hr-Croatian",
        "ht-Haitian",
        "hu-Hungarian",
        "hy-Armenian",
        "hz-Herero",
        "ia-Interlingua",
        "id-Indonesian",
        "ie-Interlingue",
        "ig-Igbo",
        "ii-Sichuan Yi",
        "ik-Inupiaq",
        "in-Indonesian", // deprecated on 1989-03-11 in favor of id-Indonesian
        "io-Ido",
        "is-Icelandic",
        "it-Italian",
        "iu-Inuktitut",
        "iw-Hebrew", // deprecated on 1989-03-11 in favor of he-Hebrew
        "ja-Japanese",
        "ji-Yiddish", // deprecated on 1989-03-11 in favor of yi-Yiddish
        "jv-Javanese",
        "ka-Georgian",
        "kg-Kongo",
        "ki-Kikuyu",
        "kj-Kuanyama",
        "kk-Kazakh",
        "kl-Kalaallisut; Greenlandic",
        "km-Central Khmer",
        "kn-Kannada",
        "ko-Korean",
        "kr-Kanuri",
        "ks-Kashmiri",
        "ku-Kurdish",
        "kv-Komi",
        "kw-Cornish",
        "ky-Kirghiz",
        "la-Latin",
        "lb-Luxembourgish",
        "lg-Ganda",
        "li-Limburgan",
        "ln-Lingala",
        "lo-Lao",
        "lt-Lithuanian",
        "lu-Luba-Katanga",
        "lv-Latvian",
        "mg-Malagasy",
        "mh-Marshallese",
        "mi-Maori",
        "mk-Macedonian",
        "ml-Malayalam",
        "mn-Mongolian",
        //"mo-Moldavian", // this maps on 'mozilla' :( // deprecated on 2008-11-03 in favor of ro-Romanian to be used for the variant of the Romanian language also known as Moldavian
        "mr-Marathi",
        "ms-Malay",
        "mt-Maltese",
        "my-Burmese",
        "na-Nauru",
        "nb-Norwegian Bokmål",
        "nd-North Ndebele",
        "ne-Nepali",
        "ng-Ndonga",
        "nl-Dutch",
        "nn-Norwegian Nynorsk",
        "no-Norwegian",
        "nr-South Ndebele",
        "nv-Navajo",
        "ny-Nyanja",
        "oc-Occitan (post 1500)",
        "oj-Ojibwa",
        "om-Oromo",
        "or-Oriya",
        "os-Ossetian",
        "pa-Panjabi; Punjabi",
        "pi-Pali",
        "pl-Polish",
        "ps-Pushto; Pashto",
        "pt-Portuguese",
        "qu-Quechua",
        "rm-Romansh",
        "rn-Rundi",
        "ro-Romanian",
        "ru-Russian",
        "rw-Kinyarwanda",
        "sa-Sanskrit",
        "sc-Sardinian",
        "sd-Sindhi",
        "se-Northern Sami",
        "sg-Sango",
        "sh-Serbo-Croatian",
        "si-Sinhala; Sinhalese",
        "sk-Slovak",
        "sl-Slovenian",
        "sm-Samoan",
        "sn-Shona",
        "so-Somali",
        "sq-Albanian",
        "sr-Serbian",
        "ss-Swati",
        "st-Southern Sotho",
        "su-Sundanese",
        "sv-Swedish",
        "sw-Swahili",
        "ta-Tamil",
        "te-Telugu",
        "tg-Tajik",
        "th-Thai",
        "ti-Tigrinya",
        "tk-Turkmen",
        "tl-Tagalog",
        "tn-Tswana",
        "to-Tonga (Tonga Islands)",
        "tr-Turkish",
        "ts-Tsonga",
        "tt-Tatar",
        "tw-Twi",
        "ty-Tahitian",
        "ug-Uighur",
        "uk-Ukrainian",
        "ur-Urdu",
        "uz-Uzbek",
        "ve-Venda",
        "vi-Vietnamese",
        "vo-Volapük",
        "wa-Walloon",
        "wo-Wolof",
        "xh-Xhosa",
        "yi-Yiddish",
        "yo-Yoruba",
        "za-Zhuang",
        "zh-Chinese",
        "zu-Zulu"};

    /** Mapping from 2 letters ISO 639-1 code to ISO language reference name in English. */
    private static Map<String, String> mapping = new ConcurrentHashMap<String, String>(codes.length);

    static {
        for (int i = 0; i < codes.length; i++) {
            mapping.put(codes[i].substring(0, 2), codes[i].substring(3));
        }
    }
    
    /**
     * get the name of the alpha-2 country code
     * @param code, the mnemonic of the country in alpha-2
     * @return the name of the country
     */
    public static final String country(String code) {
        return mapping.get(code.toLowerCase(Locale.ROOT));
    }
    
    /**
     * Check if the given country in alpha-2 country code is supported.
     * @param code, the mnemonic of the country in alpha-2 (ISO 639-1)
     * @return true if the code is not null and is known by this YaCy server
     */
    public static final boolean exists(String code) {
    	if(code == null) {
    		return false;
    	}
        return mapping.containsKey(code.toLowerCase(Locale.ROOT));
    }
    
    /**
     * analyse a user-agent string and return language as given in the agent string
     * @param userAgent string
     * @return the language code if it is possible to parse the string and find a language code or null if not
     */
    public static final String userAgentLanguageDetection(String userAgent) {
        if (userAgent == null || userAgent.length() < 2) return null;
        userAgent = userAgent.toLowerCase(Locale.ROOT);
        if (mapping.containsKey(userAgent.substring(0, 2))) return userAgent.substring(0, 2);
        if (userAgent.length() == 2 && mapping.containsKey(userAgent)) return userAgent;
        if (userAgent.length() == 5 && mapping.containsKey(userAgent.substring(0, 2))) return userAgent.substring(0, 2);
        int p = 2;
        // search for entries like ' en-'
        while (p < userAgent.length() - 1 && (p = userAgent.indexOf('-', p)) > 2) {
            if (userAgent.charAt(p - 3) == ' ' && mapping.containsKey(userAgent.substring(p - 2, p))) return userAgent.substring(p - 2, p);
            p++;
        }
        // search for entries like ' en;'
        p = 1;
        while (p < userAgent.length() - 1 && (p = userAgent.indexOf(';', p)) > 2) {
            if (userAgent.charAt(p - 3) == ' ' && mapping.containsKey(userAgent.substring(p - 2, p))) return userAgent.substring(p - 2, p);
            p++;
        }
        return null;
    }
}