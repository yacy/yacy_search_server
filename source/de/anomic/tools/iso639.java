// iso639.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.09.2008 on http://yacy.net
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

package de.anomic.tools;

import java.util.HashMap;

public class iso639 {

    static final String[] codes = {
        "aa-Afar",
        "ab-Abkhazian",
        "af-Afrikaans",
        "am-Amharic",
        "ar-Arabic",
        "as-Assamese",
        "ay-Aymara",
        "az-Azerbaijani",
        "ba-Bashkir",
        "be-Byelorussian",
        "bg-Bulgarian",
        "bh-Bihari",
        "bi-Bislama",
        "bn-Bengali;-Bangla",
        "bo-Tibetan",
        "br-Breton",
        "ca-Catalan",
        "co-Corsican",
        "cs-Czech",
        "cy-Welsh",
        "da-Danish",
        "de-German",
        "dz-Bhutani",
        "el-Greek",
        "en-English",
        "eo-Esperanto",
        "es-Spanish",
        "et-Estonian",
        "eu-Basque",
        "fa-Persian",
        "fi-Finnish",
        "fj-Fiji",
        "fo-Faeroese",
        "fr-French",
        "fy-Frisian",
        "ga-Irish",
        "gd-Scots-Gaelic",
        "gl-Galician",
        "gn-Guarani",
        "gu-Gujarati",
        "ha-Hausa",
        "hi-Hindi",
        "hr-Croatian",
        "hu-Hungarian",
        "hy-Armenian",
        "ia-Interlingua",
        "ie-Interlingue",
        "ik-Inupiak",
        "in-Indonesian",
        "is-Icelandic",
        "it-Italian",
        "iw-Hebrew",
        "ja-Japanese",
        "ji-Yiddish",
        "jw-Javanese",
        "ka-Georgian",
        "kk-Kazakh",
        "kl-Greenlandic",
        "km-Cambodian",
        "kn-Kannada",
        "ko-Korean",
        "ks-Kashmiri",
        "ku-Kurdish",
        "ky-Kirghiz",
        "la-Latin",
        "ln-Lingala",
        "lo-Laothian",
        "lt-Lithuanian",
        "lv-Latvian,-Lettish",
        "mg-Malagasy",
        "mi-Maori",
        "mk-Macedonian",
        "ml-Malayalam",
        "mn-Mongolian",
        "mo-Moldavian",
        "mr-Marathi",
        "ms-Malay",
        "mt-Maltese",
        "my-Burmese",
        "na-Nauru",
        "ne-Nepali",
        "nl-Dutch",
        "no-Norwegian",
        "oc-Occitan",
        "om-(Afan)-Oromo",
        "or-Oriya",
        "pa-Punjabi",
        "pl-Polish",
        "ps-Pashto,-Pushto",
        "pt-Portuguese",
        "qu-Quechua",
        "rm-Rhaeto-Romance",
        "rn-Kirundi",
        "ro-Romanian",
        "ru-Russian",
        "rw-Kinyarwanda",
        "sa-Sanskrit",
        "sd-Sindhi",
        "sg-Sangro",
        "sh-Serbo-Croatian",
        "si-Singhalese",
        "sk-Slovak",
        "sl-Slovenian",
        "sm-Samoan",
        "sn-Shona",
        "so-Somali",
        "sq-Albanian",
        "sr-Serbian",
        "ss-Siswati",
        "st-Sesotho",
        "su-Sundanese",
        "sv-Swedish",
        "sw-Swahili",
        "ta-Tamil",
        "te-Tegulu",
        "tg-Tajik",
        "th-Thai",
        "ti-Tigrinya",
        "tk-Turkmen",
        "tl-Tagalog",
        "tn-Setswana",
        "to-Tonga",
        "tr-Turkish",
        "ts-Tsonga",
        "tt-Tatar",
        "tw-Twi",
        "uk-Ukrainian",
        "ur-Urdu",
        "uz-Uzbek",
        "vi-Vietnamese",
        "vo-Volapuk",
        "wo-Wolof",
        "xh-Xhosa",
        "yo-Yoruba",
        "zh-Chinese",
        "zu-Zulu"};

    static HashMap<String, String> mapping = new HashMap<String, String>();

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
        return mapping.get(code.toLowerCase());
    }
    
    /**
     * see if the given country in alpha-2 country code exists
     * @param code, the mnemonic of the country in alpha-2
     * @return true if the code exists
     */
    public static final boolean exists(String code) {
        return mapping.containsKey(code.toLowerCase());
    }
    
}