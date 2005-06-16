// htmlFilterAbstractScraper.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 18.02.2004
//
// You agree that the Author(s) is (are) not responsible for cost,
// loss of data or any harm that may be caused by usage of this softare or
// this documentation. The usage of this software is on your own risk. The
// installation and usage (starting/running) of this software may allow other
// people or application to access your computer and any attached devices and
// is highly dependent on the configuration of the software which must be
// done by the user of the software;the author(s) is (are) also
// not responsible for proper configuration and usage of the software, even
// if provoked by documentation provided together with the software.
//
// THE SOFTWARE THAT FOLLOWS AS ART OF PROGRAMMING BELOW THIS SECTION
// IS PUBLISHED UNDER THE GPL AS DOCUMENTED IN THE FILE gpl.txt ASIDE THIS
// FILE AND AS IN http://www.gnu.org/licenses/gpl.txt
// ANY CHANGES TO THIS FILE ACCORDING TO THE GPL CAN BE DONE TO THE
// LINES THAT FOLLOWS THIS COPYRIGHT NOTICE HERE, BUT CHANGES MUST NOT
// BE DONE ABOVE OR INSIDE THE COPYRIGHT NOTICE. A RE-DISTRIBUTION
// MUST CONTAIN THE INTACT AND UNCHANGED COPYRIGHT NOTICE.
// CONTRIBUTIONS AND CHANGES TO THE PROGRAM CODE SHOULD BE MARKED AS SUCH.

package de.anomic.htmlFilter;

import java.util.HashSet;
import java.util.Properties;

import de.anomic.server.serverByteBuffer;

public abstract class htmlFilterAbstractScraper implements htmlFilterScraper {

    public static final byte lb = (byte) '<';
    public static final byte rb = (byte) '>';
    public static final byte sl = (byte) '/';
 
    private HashSet      tags0;
    private HashSet      tags1;

    public htmlFilterAbstractScraper(HashSet tags0, HashSet tags1) {
	this.tags0  = tags0;
	this.tags1  = tags1;
    }

    public boolean isTag0(String tag) {
	return (tags0 != null) && (tags0.contains(tag));
    }

    public boolean isTag1(String tag) {
	return (tags1 != null) && (tags1.contains(tag));
    }

    //the 'missing' method that shall be implemented:
    public abstract void scrapeText(byte[] text);
    /* could be easily implemented as:
    { }
    */

    // the other methods must take into account to construct the return value correctly
    public void scrapeTag0(String tagname, Properties tagopts) {
    }

    public void scrapeTag1(String tagname, Properties tagopts, byte[] text) {
    }

    protected static serverByteBuffer stripAllTags(serverByteBuffer bb) {
	int p0, p1;
	while ((p0 = bb.indexOf(lb)) >= 0) {
	    p1 = bb.indexOf(rb, p0);
	    if (p1 >= 0) {
		bb = new serverByteBuffer(bb.getBytes(0, p0)).trim().append((byte) 32).append(new serverByteBuffer(bb.getBytes(p1 + 1)).trim());
	    } else {
		bb = new serverByteBuffer(bb.getBytes(0, p0)).trim().append(new serverByteBuffer(bb.getBytes(p0 + 1)).trim());
	    }
	}
	return bb.trim();
    }

    // string conversions
    private static serverByteBuffer code_iso8859(byte c) {
	String s = code_iso8859s(c);
	if (s == null) return null; else return new serverByteBuffer(s.getBytes());
    }

    private static String code_iso8859s(byte c) {
	switch ((int) c & 0xff) {
        
        // german umlaute and ligaturen
	case 0xc4: return "AE"; case 0xd6: return "OE"; case 0xdc: return "UE";
	case 0xe4: return "ae"; case 0xf6: return "oe"; case 0xfc: return "ue";
        case 0xdf: return "ss";
        
        // accent on letters; i.e. french characters
        case 0xc0: case 0xc1: case 0xc2: case 0xc3: case 0xc5: return  "A";
        case 0xc6: return  "AE";
        case 0xc7: return  "C";
        case 0xc8: case 0xc9: case 0xca: return  "E";
        case 0xcc: case 0xcd: case 0xce: case 0xcf: return  "I";
        case 0xd0: return  "D";
        case 0xd1: return  "N";
        case 0xd2: case 0xd3: case 0xd4: case 0xd5: case 0xd8: return  "O";
        case 0xd7: return  "x";
        case 0xd9: case 0xda: case 0xdb: return  "U";
        case 0xdd: return  "Y";
        case 0xde: return  "p";
        
        case 0xe0: case 0xe1: case 0xe2: case 0xe3: case 0xe5: return  "a";
        case 0xe6: return  "ae";
        case 0xe7: return  "c";
        case 0xe8: case 0xe9: case 0xea: return  "e";
        case 0xec: case 0xed: case 0xee: case 0xef: return  "i";
        case 0xf0: return  "d";
        case 0xf1: return  "n";
        case 0xf2: case 0xf3: case 0xf4: case 0xf5: case 0xf8: return  "o";
        case 0xf7: return  "%";
        case 0xf9: case 0xfa: case 0xfb: return  "u";
        case 0xfd: case 0xff: return  "y";
        case 0xfe: return  "p";
        
	// special characters
        case 0xa4: return " euro ";
	default: return null;
	}
    }

    public static serverByteBuffer convertUmlaute(serverByteBuffer bb) {
	serverByteBuffer t = new serverByteBuffer();
	serverByteBuffer z;
	for (int i = 0; i < bb.length(); i++) {
	    z = code_iso8859(bb.byteAt(i));
	    t.append((z == null) ? (new serverByteBuffer().append(bb.byteAt(i))) : z);
	}
	return t;
    }

    private static String transscripts(String code) {
        if (code.equals("&quot;")) return "\""; //Anf&uuml;hrungszeichen oben
        if (code.equals("&amp;")) return "&"; //Ampersand-Zeichen, kaufm&auml;nnisches Und
        if (code.equals("&lt;")) return "<"; //&ouml;ffnende spitze Klammer
        if (code.equals("&gt;")) return ">"; //schlie&szlig;ende spitze Klammer
        if (code.equals("&nbsp;")) return " "; //Erzwungenes Leerzeichen
        if (code.equals("&iexcl;")) return "!"; //umgekehrtes Ausrufezeichen
        if (code.equals("&cent;")) return " cent "; //Cent-Zeichen
        if (code.equals("&pound;")) return " pound "; //Pfund-Zeichen
        if (code.equals("&curren;")) return " currency "; //W&auml;hrungs-Zeichen
        if (code.equals("&yen;")) return " yen "; //Yen-Zeichen
        if (code.equals("&brvbar;")) return " "; //durchbrochener Strich
        if (code.equals("&sect;")) return " paragraph "; //Paragraph-Zeichen
        if (code.equals("&uml;")) return " "; //P&uuml;nktchen oben
        if (code.equals("&copy;")) return " copyright "; //Copyright-Zeichen
        if (code.equals("&ordf;")) return " "; //Ordinal-Zeichen weiblich
        if (code.equals("&laquo;")) return " "; //angewinkelte Anf&uuml;hrungszeichen links
        if (code.equals("&not;")) return " not "; //Verneinungs-Zeichen
        if (code.equals("&shy;")) return "-"; //kurzer Trennstrich
        if (code.equals("&reg;")) return " trademark "; //Registriermarke-Zeichen
        if (code.equals("&macr;")) return " "; //&Uuml;berstrich
        if (code.equals("&deg;")) return " degree "; //Grad-Zeichen
        if (code.equals("&plusmn;")) return " +/- "; //Plusminus-Zeichen
        if (code.equals("&sup2;")) return " square "; //Hoch-2-Zeichen
        if (code.equals("&sup3;")) return " 3 "; //Hoch-3-Zeichen
        if (code.equals("&acute;")) return " "; //Acute-Zeichen
        if (code.equals("&micro;")) return " micro "; //Mikro-Zeichen
        if (code.equals("&para;")) return " paragraph "; //Absatz-Zeichen
        if (code.equals("&middot;")) return " "; //Mittelpunkt
        if (code.equals("&cedil;")) return " "; //H&auml;kchen unten
        if (code.equals("&sup1;")) return " "; //Hoch-1-Zeichen
        if (code.equals("&ordm;")) return " degree "; //Ordinal-Zeichen m&auml;nnlich
        if (code.equals("&raquo;")) return " "; //angewinkelte Anf&uuml;hrungszeichen rechts
        if (code.equals("&frac14;")) return " quarter "; //ein Viertel
        if (code.equals("&frac12;")) return " half "; //ein Halb
        if (code.equals("&frac34;")) return " 3/4 "; //drei Viertel
        if (code.equals("&iquest;")) return "?"; //umgekehrtes Fragezeichen
        if (code.equals("&Agrave;")) return "A"; //A mit Accent grave
        if (code.equals("&Aacute;")) return "A"; //A mit Accent acute
        if (code.equals("&Acirc;")) return "A"; //A mit Circumflex
        if (code.equals("&Atilde;")) return "A"; //A mit Tilde
        if (code.equals("&Auml;")) return "Ae"; //A Umlaut
        if (code.equals("&Aring;")) return "A"; //A mit Ring
        if (code.equals("&AElig;")) return "A"; //A mit legiertem E
        if (code.equals("&Ccedil;")) return "C"; //C mit H&auml;kchen
        if (code.equals("&Egrave;")) return "E"; //E mit Accent grave
        if (code.equals("&Eacute;")) return "E"; //E mit Accent acute
        if (code.equals("&Ecirc;")) return "E"; //E mit Circumflex
        if (code.equals("&Euml;")) return "E"; //E Umlaut
        if (code.equals("&Igrave;")) return "I"; //I mit Accent grave
        if (code.equals("&Iacute;")) return "I"; //I mit Accent acute
        if (code.equals("&Icirc;")) return "I"; //I mit Circumflex
        if (code.equals("&Iuml;")) return "I"; //I Umlaut
        if (code.equals("&ETH;")) return "D"; //Eth (isl&auml;ndisch)
        if (code.equals("&Ntilde;")) return "N"; //N mit Tilde
        if (code.equals("&Ograve;")) return "O"; //O mit Accent grave
        if (code.equals("&Oacute;")) return "O"; //O mit Accent acute
        if (code.equals("&Ocirc;")) return "O"; //O mit Circumflex
        if (code.equals("&Otilde;")) return "O"; //O mit Tilde
        if (code.equals("&Ouml;")) return "Oe"; //O Umlaut
        if (code.equals("&times;")) return " times "; //Mal-Zeichen
        if (code.equals("&Oslash;")) return "O"; //O mit Schr&auml;gstrich
        if (code.equals("&Ugrave;")) return "U"; //U mit Accent grave
        if (code.equals("&Uacute;")) return "U"; //U mit Accent acute
        if (code.equals("&Ucirc;")) return "U"; //U mit Circumflex
        if (code.equals("&Uuml;")) return "Ue"; //U Umlaut
        if (code.equals("&Yacute;")) return "Y"; //Y mit Accent acute
        if (code.equals("&THORN;")) return "P"; //THORN (isl&auml;ndisch)
        if (code.equals("&szlig;")) return "ss"; //scharfes S
        if (code.equals("&agrave;")) return "a"; //a mit Accent grave
        if (code.equals("&aacute;")) return "a"; //a mit Accent acute
        if (code.equals("&acirc;")) return "a"; //a mit Circumflex
        if (code.equals("&atilde;")) return "a"; //a mit Tilde
        if (code.equals("&auml;")) return "ae"; //a Umlaut
        if (code.equals("&aring;")) return "a"; //a mit Ring
        if (code.equals("&aelig;")) return "a"; //a mit legiertem e
        if (code.equals("&ccedil;")) return "c"; //c mit H&auml;kchen
        if (code.equals("&egrave;")) return "e"; //e mit Accent grave
        if (code.equals("&eacute;")) return "e"; //e mit Accent acute
        if (code.equals("&ecirc;")) return "e"; //e mit Circumflex
        if (code.equals("&euml;")) return "e"; //e Umlaut
        if (code.equals("&igrave;")) return "i"; //i mit Accent grave
        if (code.equals("&iacute;")) return "i"; //i mit Accent acute
        if (code.equals("&icirc;")) return "i"; //i mit Circumflex
        if (code.equals("&iuml;")) return "i"; //i Umlaut
        if (code.equals("&eth;")) return "d"; //eth (isl&auml;ndisch)
        if (code.equals("&ntilde;")) return "n"; //n mit Tilde
        if (code.equals("&ograve;")) return "o"; //o mit Accent grave
        if (code.equals("&oacute;")) return "o"; //o mit Accent acute
        if (code.equals("&ocirc;")) return "o"; //o mit Circumflex
        if (code.equals("&otilde;")) return "o"; //o mit Tilde
        if (code.equals("&ouml;")) return "oe"; //o Umlaut
        if (code.equals("&divide;")) return "%"; //Divisions-Zeichen
        if (code.equals("&oslash;")) return "o"; //o mit Schr&auml;gstrich
        if (code.equals("&ugrave;")) return "u"; //u mit Accent grave
        if (code.equals("&uacute;")) return "u"; //u mit Accent acute
        if (code.equals("&ucirc;")) return "u"; //u mit Circumflex
        if (code.equals("&uuml;")) return "ue"; //u Umlaut
        if (code.equals("&yacute;")) return "y"; //y mit Accent acute
        if (code.equals("&thorn;")) return "p"; //thorn (isl&auml;ndisch)
        if (code.equals("&yuml;")) return "y"; //y Umlaut
        if (code.equals("&Alpha;")) return " Alpha "; //Alpha gro&szlig;
        if (code.equals("&alpha;")) return " alpha "; //alpha klein
        if (code.equals("&Beta;")) return " Beta "; //Beta gro&szlig;
        if (code.equals("&beta;")) return " beta "; //beta klein
        if (code.equals("&Gamma;")) return " Gamma "; //Gamma gro&szlig;
        if (code.equals("&gamma;")) return " gamma "; //gamma klein
        if (code.equals("&Delta;")) return " Delta "; //Delta gro&szlig;
        if (code.equals("&delta;")) return " delta "; //delta klein
        if (code.equals("&Epsilon;")) return " Epsilon "; //Epsilon gro&szlig;
        if (code.equals("&epsilon;")) return " epsilon "; //epsilon klein
        if (code.equals("&Zeta;")) return " Zeta "; //Zeta gro&szlig;
        if (code.equals("&zeta;")) return " zeta "; //zeta klein
        if (code.equals("&Eta;")) return " Eta "; //Eta gro&szlig;
        if (code.equals("&eta;")) return " eta "; //eta klein
        if (code.equals("&Theta;")) return " Theta "; //Theta gro&szlig;
        if (code.equals("&theta;")) return " theta "; //theta klein
        if (code.equals("&Iota;")) return " Iota "; //Iota gro&szlig;
        if (code.equals("&iota;")) return " iota "; //iota klein
        if (code.equals("&Kappa;")) return " Kappa "; //Kappa gro&szlig;
        if (code.equals("&kappa;")) return " kappa "; //kappa klein
        if (code.equals("&Lambda;")) return " Lambda "; //Lambda gro&szlig;
        if (code.equals("&lambda;")) return " lambda "; //lambda klein
        if (code.equals("&Mu;")) return " Mu "; //Mu gro&szlig;
        if (code.equals("&mu;")) return " mu "; //mu klein
        if (code.equals("&Nu;")) return " Nu "; //Nu gro&szlig;
        if (code.equals("&nu;")) return " nu "; //nu klein
        if (code.equals("&Xi;")) return " Xi "; //Xi gro&szlig;
        if (code.equals("&xi;")) return " xi "; //xi klein
        if (code.equals("&Omicron;")) return " Omicron "; //Omicron gro&szlig;
        if (code.equals("&omicron;")) return " omicron "; //omicron klein
        if (code.equals("&Pi;")) return " Pi "; //Pi gro&szlig;
        if (code.equals("&pi;")) return " pi "; //pi klein
        if (code.equals("&Rho;")) return " Rho "; //Rho gro&szlig;
        if (code.equals("&rho;")) return " rho "; //rho klein
        if (code.equals("&Sigma;")) return " Sigma "; //Sigma gro&szlig;
        if (code.equals("&sigmaf;")) return " sigma "; //sigmaf klein
        if (code.equals("&sigma;")) return " sigma "; //sigma klein
        if (code.equals("&Tau;")) return " Tau "; //Tau gro&szlig;
        if (code.equals("&tau;")) return " tau "; //tau klein
        if (code.equals("&Upsilon;")) return " Ypsilon "; //Upsilon gro&szlig;
        if (code.equals("&upsilon;")) return " ypsilon "; //upsilon klein
        if (code.equals("&Phi;")) return " Phi "; //Phi gro&szlig;
        if (code.equals("&phi;")) return " phi "; //phi klein
        if (code.equals("&Chi;")) return " Chi "; //Chi gro&szlig;
        if (code.equals("&chi;")) return " chi "; //chi klein
        if (code.equals("&Psi;")) return " Psi "; //Psi gro&szlig;
        if (code.equals("&psi;")) return " psi "; //psi klein
        if (code.equals("&Omega;")) return " Omega "; //Omega gro&szlig;
        if (code.equals("&omega;")) return " omega "; //omega klein
        if (code.equals("&thetasym;")) return " theta "; //theta Symbol
        if (code.equals("&upsih;")) return " ypsilon "; //upsilon mit Haken
        if (code.equals("&piv;")) return " pi "; //pi Symbol
        if (code.equals("&forall;")) return " for all "; //f&uuml;r alle
        if (code.equals("&part;")) return " part of "; //teilweise
        if (code.equals("&exist;")) return " exists "; //existiert
        if (code.equals("&empty;")) return " null "; //leer
        if (code.equals("&nabla;")) return " nabla "; //nabla
        if (code.equals("&isin;")) return " element of "; //Element von
        if (code.equals("&notin;")) return " not element of "; //kein Element von
        if (code.equals("&ni;")) return " contains "; //enth&auml;lt als Element
        if (code.equals("&prod;")) return " product "; //Produkt
        if (code.equals("&sum;")) return " sum "; //Summe
        if (code.equals("&minus;")) return " minus "; //minus
        if (code.equals("&lowast;")) return " times "; //Asterisk
        if (code.equals("&radic;")) return " sqare root "; //Quadratwurzel
        if (code.equals("&prop;")) return " proportional to "; //proportional zu
        if (code.equals("&infin;")) return " unlimited "; //unendlich
        if (code.equals("&ang;")) return " angle "; //Winkel
        if (code.equals("&and;")) return " and "; //und
        if (code.equals("&or;")) return " or "; //oder
        if (code.equals("&cap;")) return " "; //Schnittpunkt
        if (code.equals("&cup;")) return " unity "; //Einheit
        if (code.equals("&int;")) return " integral "; //Integral
        if (code.equals("&there4;")) return " cause "; //deshalb
        if (code.equals("&sim;")) return " similar to "; //&auml;hnlich wie
        if (code.equals("&cong;")) return " equal "; //ann&auml;hernd gleich
        if (code.equals("&asymp;")) return " equal "; //beinahe gleich
        if (code.equals("&ne;")) return " not equal "; //ungleich
        if (code.equals("&equiv;")) return " identical "; //identisch mit
        if (code.equals("&le;")) return " smaller or equal than "; //kleiner gleich
        if (code.equals("&ge;")) return " greater or equal than "; //gr&ouml;&szlig;er gleich
        if (code.equals("&sub;")) return " subset of "; //Untermenge von
        if (code.equals("&sup;")) return " superset of "; //Obermenge von
        if (code.equals("&nsub;")) return " not subset of "; //keine Untermenge von
        if (code.equals("&sube;")) return ""; //Untermenge von oder gleich mit
        if (code.equals("&supe;")) return ""; //Obermenge von oder gleich mit
        if (code.equals("&oplus;")) return ""; //Direktsumme
        if (code.equals("&otimes;")) return ""; //Vektorprodukt
        if (code.equals("&perp;")) return ""; //senkrecht zu
        if (code.equals("&sdot;")) return ""; //Punkt-Operator
        if (code.equals("&loz;")) return ""; //Raute
        if (code.equals("&lceil;")) return ""; //links oben
        if (code.equals("&rceil;")) return ""; //rechts oben
        if (code.equals("&lfloor;")) return ""; //links unten
        if (code.equals("&rfloor;")) return ""; //rechts unten
        if (code.equals("&lang;")) return ""; //spitze Klammer links
        if (code.equals("&rang;")) return ""; //spitze Klammer rechts
        if (code.equals("&larr;")) return ""; //Pfeil links
        if (code.equals("&uarr;")) return ""; //Pfeil oben
        if (code.equals("&rarr;")) return ""; //Pfeil rechts
        if (code.equals("&darr;")) return ""; //Pfeil unten
        if (code.equals("&harr;")) return ""; //Pfeil links/rechts
        if (code.equals("&crarr;")) return ""; //Pfeil unten-Knick-links
        if (code.equals("&lArr;")) return ""; //Doppelpfeil links
        if (code.equals("&uArr;")) return ""; //Doppelpfeil oben
        if (code.equals("&rArr;")) return ""; //Doppelpfeil rechts
        if (code.equals("&dArr;")) return ""; //Doppelpfeil unten
        if (code.equals("&hArr;")) return ""; //Doppelpfeil links/rechts
        if (code.equals("&bull;")) return ""; //Bullet-Zeichen
        if (code.equals("&hellip;")) return ""; //Horizontale Ellipse
        if (code.equals("&prime;")) return ""; //Minutenzeichen
        if (code.equals("&oline;")) return ""; //&Uuml;berstrich
        if (code.equals("&frasl;")) return ""; //Bruchstrich
        if (code.equals("&weierp;")) return ""; //Weierstrass p
        if (code.equals("&image;")) return ""; //Zeichen f&uuml;r &quot;imagin&auml;r&quot;
        if (code.equals("&real;")) return ""; //Zeichen f&uuml;r &quot;real&quot;
        if (code.equals("&trade;")) return ""; //Trademark-Zeichen
        if (code.equals("&euro;")) return ""; //Euro-Zeichen
        if (code.equals("&alefsym;")) return ""; //Alef-Symbol
        if (code.equals("&spades;")) return ""; //Pik-Zeichen
        if (code.equals("&clubs;")) return ""; //Kreuz-Zeichen
        if (code.equals("&hearts;")) return ""; //Herz-Zeichen
        if (code.equals("&diams;")) return ""; //Karo-Zeichen
        if (code.equals("&ensp;")) return ""; //Leerzeichen Breite n
        if (code.equals("&emsp;")) return ""; //Leerzeichen Breite m
        if (code.equals("&thinsp;")) return ""; //Schmales Leerzeichen
        if (code.equals("&zwnj;")) return ""; //null breiter Nichtverbinder
        if (code.equals("&zwj;")) return ""; //null breiter Verbinder
        if (code.equals("&lrm;")) return ""; //links-nach-rechts-Zeichen
        if (code.equals("&rlm;")) return ""; //rechts-nach-links-Zeichen
        if (code.equals("&ndash;")) return ""; //Gedankenstrich Breite n
        if (code.equals("&mdash;")) return ""; //Gedankenstrich Breite m
        if (code.equals("&lsquo;")) return ""; //einfaches Anf&uuml;hrungszeichen links
        if (code.equals("&rsquo;")) return ""; //einfaches Anf&uuml;hrungszeichen rechts
        if (code.equals("&sbquo;")) return ""; //einfaches low-9-Zeichen
        if (code.equals("&ldquo;")) return ""; //doppeltes Anf&uuml;hrungszeichen links
        if (code.equals("&rdquo;")) return ""; //doppeltes Anf&uuml;hrungszeichen rechts
        if (code.equals("&bdquo;")) return ""; //doppeltes low-9-Zeichen rechts
        if (code.equals("&dagger;")) return ""; //Kreuz
        if (code.equals("&Dagger;")) return ""; //Doppelkreuz
        if (code.equals("&permil;")) return ""; //zu tausend
        if (code.equals("&lsaquo;")) return ""; //angewinkeltes einzelnes Anf.zeichen links
        if (code.equals("&rsaquo;")) return ""; //angewinkeltes einzelnes Anf.zeichen rechts
        
	return "";
    }

    private static byte[] transscript(byte[] code) {
	return transscripts(new String(code)).getBytes();
    }

    protected static serverByteBuffer transscriptAll(serverByteBuffer bb) {
	int p0, p1;
	while ((p0 = bb.indexOf((byte) '&')) >= 0) {
	    p1 = bb.indexOf((byte) ';', p0);
	    if (p1 >= 0)
		bb = new serverByteBuffer(bb.getBytes(0, p0)).append(transscript(bb.getBytes(p0, p1 + 1))).append(bb.getBytes(p1 + 1));
	    else
		bb = new serverByteBuffer(bb.getBytes(0, p0)).append(bb.getBytes(p0 + 1));
	}
	return bb;
    }

    public static serverByteBuffer stripAll(serverByteBuffer bb) {
	//return stripAllTags(s);
	 return convertUmlaute(transscriptAll(stripAllTags(bb)));
    }

    public void close() {
        // free resources
        tags0 = null;
        tags1 = null;
    }
    
    public void finalize() {
        close();
    }
        
}
