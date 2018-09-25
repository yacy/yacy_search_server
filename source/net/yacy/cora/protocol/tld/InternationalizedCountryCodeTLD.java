// InternationalizedCountryCodeTLD.java
// -----------------------
// part of YaCy
// Copyright 2017 by luccioman; https://github.com/luccioman
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

package net.yacy.cora.protocol.tld;

/**
 * <p>
 * Enumeration of internationalized country-code top-level domains (IDN ccTLDs).
 * </p>
 * <p>
 * Please use the {@link #getDomainName()} function to get a consistent domain
 * name value.
 * </p>
 * <p>
 * Domains are sorted by order of appearance in the IANA Root Zone Database.
 * Each enumeration key is composed of the country name, the language name and
 * eventually a precision on the used characters id necessary.
 * </p>
 * 
 * @see <a href="https://www.iana.org/domains/root/db">IANA Root Zone Database
 *      reference</a>
 */
public enum InternationalizedCountryCodeTLD {
	
	/** Domain name in Unicode characters : .ಭಾರತ */
	India_Kannada("xn--2scrj9c", "National Internet eXchange of India"),
	/** Domain name in Unicode characters : .한 */
	South_Korea_Korean("xn--3e0b707e", "KISA (Korea Internet & Security Agency)"),
	 /** Domain name in Unicode characters : .ଭାରତ */
	India_Oriya("xn--3hcrj9c", "National Internet eXchange of India"),
	 /** Domain name in Unicode characters : .ভাৰত */
	India_Assamese("xn--45br5cyl", "National Internet eXchange of India"),
	 /** Domain name in Unicode characters : .ভারত */
	India_Bengali("xn--45brj9c", "National Internet Exchange of India"),
	 /** Domain name in Unicode characters : .বাংলা */
	Bangladesh_Bengali("xn--54b7fta0cc", "Posts and Telecommunications Division"),
	/** Domain name in Unicode characters : .қаз */
	Kazakhstan_Kazakh("xn--80ao21a", "Association of IT Companies of Kazakhstan"),
	/** Domain name in Unicode characters : .срб */
	Serbia_Serbian("xn--90a3ac", "Serbian National Internet Domain Registry (RNIDS)"),
	/** Domain name in Unicode characters : .бг */
	Bulgaria_Bulgarian("xn--90ae", "Imena.BG AD"),
	 /** Domain name in Unicode characters : .бел */
	Belarus_Belarusian("xn--90ais", "Reliable Software, Ltd."),
	/** Domain name in Unicode characters : .சிங்கப்பூர் */
	Singapore_Tamil("xn--clchc0ea0b2g2a9gcd", "Singapore Network Information Centre (SGNIC) Pte Ltd"),
	/** Domain name in Unicode characters : .мкд */
	Macedonia_Macedonian("xn--d1alf", "Macedonian Academic Research Network Skopje"),
	/** Domain name in Unicode characters : .ею */
	European_Union_Bulgarian("xn--e1a4c", "EURid vzw/asbl"),
	/** Domain name in Unicode characters : .中国 */
	China_Chinese_Simplified_Chars("xn--fiqs8s", "China Internet Network Information Center (CNNIC)"),
	/** Domain name in Unicode characters : .中國 */
	China_Chinese_Traditional_Chars("xn--fiqz9s", "China Internet Network Information Center (CNNIC)"),
	/** Domain name in Unicode characters : .భారత్ */
	India_Telugu("xn--fpcrj9c3d", "National Internet Exchange of India"),
	/** Domain name in Unicode characters : .ලංකා */
	Sri_Lanka_Sinhalese("xn--fzc2c9e2c", "LK Domain Registry"),
	/** Domain name in Unicode characters : .ભારત */
	India_Gujarati("xn--gecrj9c", "National Internet Exchange of India"),
	/** Domain name in Unicode characters : .भारतम् */
	India_Sanskrit("xn--h2breg3eve", "National Internet eXchange of India"),
	/** Domain name in Unicode characters : .भारत */
	India_Hindi("xn--h2brj9c", "National Internet Exchange of India"),
	/** Domain name in Unicode characters : .भारोत */
	India_Santali("xn--h2brj9c8c", "National Internet eXchange of India"),
	/** Domain name in Unicode characters : .укр */
	Ukraine_Ukrainian("xn--j1amh", "Ukrainian Network Information Centre (UANIC), Inc."),
	/** Domain name in Unicode characters : .香港 */
	Hong_Kong_Chinese("xn--j6w193g", "Hong Kong Internet Registration Corporation Ltd."),
	/** Domain name in Unicode characters : .台湾 */
	Taiwan_Chinese_Simplified_Chars("xn--kprw13d", "Taiwan Network Information Center (TWNIC)"),
	/** Domain name in Unicode characters : .台灣 */
	Taiwan_Chinese_Traditional_Chars("xn--kpry57d", "Taiwan Network Information Center (TWNIC)"),
	/** Domain name in Unicode characters : .мон */
	Mongolia_Mongolian("xn--l1acc", "Datacom Co.,Ltd"),
	/** Domain name in Unicode characters : ‏.الجزائر‎ */
	Algeria_Arabic("xn--lgbbat1ad8j", "CERIST"),
	/** Domain name in Unicode characters : .عمان */
	Oman_Arabic("xn--mgb9awbf.html‏‎", "Telecommunications Regulatory Authority (TRA)"),
	/** Domain name in Unicode characters : ‏.ایران */
	Iran_Persian("xn--mgba3a4f16a‎", "Institute for Research in Fundamental Sciences (IPM)"), 
	/** Domain name in Unicode characters : ‏.امارات */
	United_Arab_Emirates_Arabic("xn--mgbaam7a8h‎", "Telecommunications Regulatory Authority (TRA)"),
	/** Domain name in Unicode characters : ‏.پاکستان */
	Pakistan_Urdu("xn--mgbai9azgqp6j‎", "National Telecommunication Corporation"),
	/** Domain name in Unicode characters : ‏.الاردن */
	Jordan_Arabic("xn--mgbayh7gpa‎", "National Information Technology Center (NITC)"),
	/** Domain name in Unicode characters : .بارت */
	India_Kashmiri("xn--mgbbh1a‎", "National Internet eXchange of India"),
	/** Domain name in Unicode characters : .بھارت */
	India_Urdu("xn--mgbbh1a71e‎", "National Internet Exchange of India"),
	/** Domain name in Unicode characters : ‏.المغرب‎ */
	Morocco_Arabic("xn--mgbc0a9azcg", "Agence Nationale de Réglementation des Télécommunications (ANRT)"),
	/** Domain name in Unicode characters : ‏.السعودية‎ */
	Saudi_Arabia_Arabic("xn--mgberp4a5d4ar", "Communications and Information Technology Commission"),
	/** Domain name in Unicode characters : .ڀارت‎ */
	India_Sindhi("xn--mgbgu82a", "National Internet eXchange of India"),
	/** Domain name in Unicode characters : ‏.سودان‎ */
	Sudan_Arabic("xn--mgbpl2fh", "Sudan Internet Society"),
	/** Domain name in Unicode characters : ‏.عراق‎ */
	Iraq_Arabic("xn--mgbtx2b", "Communications and Media Commission (CMC)"),
	/** Domain name in Unicode characters : ‏.مليسيا‎ */
	Malaysia_Malay("xn--mgbx4cd0ab", "MYNIC Berhad"),
	/** Domain name in Unicode characters : .澳門 */
	Macao_Chinese_Traditional_Chars("xn--mix891f", "Macao Post and Telecommunications Bureau (CTT)"),
	/** Domain name in Unicode characters : .გე */
	Georgia_Georgian("xn--node", "Information Technologies Development Center (ITDC)"),
	/** Domain name in Unicode characters : .ไทย */
	Thailand_Thai("xn--o3cw4h", "Thai Network Information Center Foundation"),
	/** Domain name in Unicode characters : ‏.سورية‎ */
	Syria_Arabic("xn--ogbpf8fl", "National Agency for Network Services (NANS)"),
	/** Domain name in Unicode characters : .рф */
	Russia_Russian("xn--p1ai", "Coordination Center for TLD RU"),
	/** Domain name in Unicode characters : ‏.تونس‎ */
	Tunisia_Arabic("xn--pgbs0dh", "Agence Tunisienne d'Internet"),
	/** Domain name in Unicode characters : .ελ */
	Greece_Greek("xn--qxam", "ICS-FORTH GR"),
	/** Domain name in Unicode characters : .ഭാരതം */
	India_Malayalam("xn--rvc1e0am3e", "National Internet eXchange of India"),
	/** Domain name in Unicode characters : .ਭਾਰਤ */
	India_Punjabi("xn--s9brj9c", "National Internet Exchange of India"),
	/** Domain name in Unicode characters : ‏.مصر‎ */
	Egypt_Arabic("xn--wgbh1c", "National Telecommunication Regulatory Authority - NTRA"),
	/** Domain name in Unicode characters : ‏.قطر‎ */
	Qatar_Arabic("xn--wgbl6a", "Communications Regulatory Authority"),
	/** Domain name in Unicode characters : .இலங்கை */
	Sri_Lanka_Tamil("xn--xkc2al3hye2a", "LK Domain Registry"),
	/** Domain name in Unicode characters : .இந்தியா */
	India_Tamil("xn--xkc2dl3a5ee0h", "National Internet Exchange of India"),
	/** Domain name in Unicode characters : .հայ */
	Armenia_Armenian("xn--y9a3aq", "\"Internet Society\" Non-governmental Organization"),
	/** Domain name in Unicode characters : .新加坡 */
	Singapore_Chinese("xn--yfro4i67o", "Singapore Network Information Centre (SGNIC) Pte Ltd"),
	/** Domain name in Unicode characters : ‏.فلسطين‎ */
	Palestinian_Authority_Arabic("xn--ygbi2ammx", "Ministry of Telecom & Information Technology (MTIT)");
			
	/** The lower case and Punycode encoded domain name */
	private final String domainName;

	/** The TLD manager's name */
	private final String tldManager;

	private InternationalizedCountryCodeTLD(final String domainName, final String tldManager) {
		this.domainName = domainName;
		this.tldManager = tldManager;
	}

	/**
	 * @return the lower cased, Punycode encoded domain name of this enumeration
	 *         instance
	 */
	public String getDomainName() {
		return this.domainName;
	}
	
	/**
	 * @return the name of the organization which manages this top-level domain name
	 */
	public String getTldManager() {
		return this.tldManager;
	}

}
