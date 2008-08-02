//httpdLimitExceededException.java 
//-----------------------
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
// This file is contributed by Martin Thelian
// last major change: $LastChangedDate: 2006-08-16 21:49:31 +0200 (Mi, 16 Aug 2006) $ by $LastChangedBy: orbiter $
// Revision: $LastChangedRevision: 2414 $
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

package de.anomic.http;

import java.io.IOException;

public class httpdLimitExceededException extends IOException {
    
    private static final long serialVersionUID = 1L;
    private final long limit;
    
    public httpdLimitExceededException(final String errorMsg, final long limit) {
        super(errorMsg);
        this.limit = limit;       
    }
    
    public long getLimit() {
        return this.limit;
    }
}
