// SponsoredTLD.java
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

import java.util.Locale;

/**
 * Enumeration of sponsored top-level domains (sTLD). Please use the
 * {@link #getDomainName()} function to get a consistent domain name value,
 * notably for domain names that contain characters that can not be used as part
 * of a Java enum name.
 * 
 * @see <a href="https://www.iana.org/domains/root/db">IANA Root Zone Database
 *      reference</a>
 */
public enum SponsoredTLD {
	/** The air-transport industry */
    aero("Societe Internationale de Telecommunications Aeronautique (SITA INC USA)"),
    // keep the following TLD commented : it is already listed in Domains.TLD_SouthEastAsia and adding it here would modify related URLs hash generation
    // asia("DotAsia Organisation Ltd."),
    // keep the following TLD commented : it is already listed in Domains.TLD_EuropeRussia and adding it here would modify related URLs hash generation 
    // cat("Fundacio puntCAT"),
    /** Cooperative associations */
    coop("DotCooperation LLC"),
    // keep the following TLD commented : it is already listed in Domains.TLD_NorthAmericaOceania and adding it here would modify related URLs hash generation
    // edu("EDUCAUSE"),
    // keep the following TLD commented : it is already listed in Domains.TLD_NorthAmericaOceania and adding it here would modify related URLs hash generation
    // gov("General Services Administration Attn: QTDC, 2E08 (.gov Domain Registration)"),
    /** International */
    INT("Internet Assigned Numbers Authority"),
    /** Human resource managers */
    jobs("Employ Media LLC"),
    // keep the following TLD commented : it is already listed in Domains.TLD_NorthAmericaOceania and adding it here would modify related URLs hash generation
    // mil("DoD Network Information Center"),
    /** Museums */
    museum("Museum Domain Management Association"),
    /** postal services */
    post("Universal Postal Union"),
    /** Published contact data */
    tel("Telnames Ltd."),
    /** The travel industry */
    travel("Tralliance Registry Management Company, LLC."),
    /** adult entertainment (pornography) */
    xxx("ICM Registry LLC");
    
	/** The TLD manager's name */
	private final String tldManager;
	
	private SponsoredTLD(final String tldManager) {
		this.tldManager = tldManager;
	}
	
	/**
	 * @return the name of the organization which manages this top-level domain name
	 */
	public String getTldManager() {
		return this.tldManager;
	}
	
	/**
	 * @return the lower cased, Punycode encoded (when the domain is
	 *         internationalized) domain name of this enumeration instance
	 */
	public String getDomainName() {
		return this.toString().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}
