/**
 *  CommonPattern
 *  Copyright 2012 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 26.11.2012 on http://yacy.net
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

package net.yacy.cora.util;

import java.util.regex.Pattern;

/**
 * This class provides Pattern constants to be used
 * to replace a regex in s.split(regex) method calls.
 * Because s.split(regex) causes an execution of
 * Pattern.compile(regex).split(s, 0), it is wise to pre-compile
 * all regex to a pattern p.
 * Therefore do the following: transform your code into
 * Pattern p = Pattern.compile(regex); p.split(s);
 * The compilation of a specific pattern should be done only once.
 * Therefore this class provides Pattern objects for the most common regex Strings.
 * 
 * The same applies to s.replaceall(regex, replacement) which is equal to
 * Pattern.compile(regex).matcher(s).replaceAll(replacement);
 */
public class CommonPattern {

    public final static Pattern SPACE       = Pattern.compile(" ");
    public final static Pattern COMMA       = Pattern.compile(",");
    public final static Pattern SEMICOLON   = Pattern.compile(";");
    public final static Pattern DOUBLEPOINT = Pattern.compile(":");
    public final static Pattern SLASH       = Pattern.compile("/");
    public final static Pattern BACKSLASH   = Pattern.compile("\\\\");
    public final static Pattern QUESTION    = Pattern.compile("\\?");
    public final static Pattern AMP         = Pattern.compile("&");
    public final static Pattern PLUS        = Pattern.compile(Pattern.quote("+"));
    public final static Pattern DOT         = Pattern.compile("\\.");
    public final static Pattern NEWLINE     = Pattern.compile("\n");
    public final static Pattern VERTICALBAR = Pattern.compile(Pattern.quote("|"));
    public final static Pattern UNDERSCORE  = Pattern.compile("_");
    
}
