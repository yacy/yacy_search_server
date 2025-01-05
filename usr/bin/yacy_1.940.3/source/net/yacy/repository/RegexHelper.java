// RegexHelper.java
// ------------------------
// part of YaCy
// (C) by Marc Nause; marc.nause@gmx.de
// first published on http://www.anomic.de
// Braunchweig, Germany, 2011
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.repository;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


public final class RegexHelper {
    
    /** Private constructor to avoid instantiation of static class. */
    private RegexHelper() { }
    
    /**
     * Checks if a given expression is a valid regular expression.
     * @param expression expression to be checked
     * @return true if the expression is a valid regular expression, else false
     */
    public static boolean isValidRegex(final String expression) {
        if (expression == null) return false;
        boolean ret = true;
        try {
            Pattern.compile(expression);
        } catch (final PatternSyntaxException e) {
            ret = false;
        }
        return ret;
    }
    
}
