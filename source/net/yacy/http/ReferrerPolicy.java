// ReferrerPolicy.java
// Copyright 2017 by luccioman; https://github.com/luccioman
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

package net.yacy.http;

/**
 * Referrer policies enumeration, as decribed by the related W3C recommendation
 * (https://www.w3.org/TR/referrer-policy/#referrer-policies)
 */
public enum ReferrerPolicy {

	EMPTY(""), 
	NO_REFERRER("no-referrer"), 
	NO_REFERRER_WHEN_DOWNGRADE("no-referrer-when-downgrade"), 
	SAME_ORIGIN("same-origin"), 
	ORIGIN("origin"), 
	STRICT_ORIGIN("strict-origin"), 
	ORIGIN_WHEN_CROSS_ORIGIN("origin-when-cross-origin"), 
	STRICT_ORIGIN_WHEN_CROSS_ORIGIN("strict-origin-when-cross-origin"), 
	UNSAFE_URL("unsafe-url");

	/**
	 * Policy string value
	 */
	private final String value;

	/**
	 * Enumeration private constructor
	 * @param value the policy string value 
	 */
	private ReferrerPolicy(final String value) {
		this.value = value;
	}
	
	/**
	 * @return the policy string value
	 */
	public String getValue() {
		return value;
	}
	
	/**
	 * @param value a policy string value
	 * @return true when this enumeration contains an element with the specified policy value
	 */
	public static boolean contains(final String value) {
		boolean res = false;
		for(ReferrerPolicy policy : ReferrerPolicy.values()) {
			if(policy.getValue().equals(value)) {
				res = true;
				break;
			}
		}
		return res;
	}

}
