// TemplateProcessingException.java
// Copyright 2018 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.http.servlets;

import org.apache.http.HttpStatus;

/**
 * Use this to indicates that a servlet template processing error occurred, and
 * which HTTP status should be rendered by the HTTP servlet.
 *
 */
@SuppressWarnings("serial")
public class TemplateProcessingException extends RuntimeException {

	/** The HTTP status code that should be rendered. */
	private final int status;

	/**
	 * Default constructor : use a generic message and HTTP status 500 - Internal
	 * Server Error.
	 */
	public TemplateProcessingException() {
		this("An error occurred while processing the template.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
	}

	/**
	 * Create an instance with a detail message, and the default HTTP status 500 -
	 * Internal Server Error.
	 *
	 * @param message the detail message
	 */
	public TemplateProcessingException(final String message) {
		this(message, HttpStatus.SC_INTERNAL_SERVER_ERROR);
	}

	/**
	 * @param message the detail message
	 * @param status  the custom HTTP status code
	 */
	public TemplateProcessingException(final String message, final int status) {
		super(message);
		this.status = status;
	}

	/**
	 * @return the HTTP status code that should be rendered.
	 */
	public int getStatus() {
		return this.status;
	}

}
