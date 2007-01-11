//LogParser.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Matthias Soehnholz
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.server.logging.logParsers;

import java.util.Hashtable;

/**
 * This is the logParser-Interface which all yacy Logalizer-Parser must
 * implement.
 */
public interface LogParser {
    /** 
     * This is the basic parser-method to parse single loglines. It can
     * request to give the current logLine and a number of additional logLines,
     * defined by the return value, to be passed over to the
     * <tt>advancedParse</tt>-method. The method should return -1 if the given
     * line was not processed.
     *
     * TODO: description of logLevels
     *
     * @param logLevel The LogLevel of the line to analyze.
     * @param logLine  The line to be analyze by the parser.
     * @return number of additional lines to be loaded and passed over to the
     * <tt>advancedParse</tt>-method, or if the line was not processed by the
     * parser "-1".
     */
    public int parse(String logLevel, String logLine);
    /**
     * This method prints the Parser-Results to the standard-output.
     */
    public void printResults();
    /**
     * The return value defines which logLines the parser will handle.
     * @return a String that defines the logLines to analyze. For example
     * <b>PLASMA</b> or <b>YACY</b>
     */
    public String getParserType();
    public Hashtable getResults();
    public double getParserVersion();
}
