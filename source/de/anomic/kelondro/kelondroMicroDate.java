// microDate.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 3.7.2008 on http://yacy.net
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

package de.anomic.kelondro;

import java.util.Date;

public class kelondroMicroDate {

    private static final long hour = 3600000L;  // milliseconds of a hour
    private static final long day  = 86400000L; // milliseconds of a day
    
    public static int microDateDays(final Date modified) {
        return microDateDays(modified.getTime());
    }
    
    public static int microDateDays(final long modified) {
        // this calculates a virtual age from a given date
        // the purpose is to have an age in days of a given modified date
        // from a fixed standpoint in the past
        // one day has 60*60*24 seconds = 86400 seconds
        // we take mod 64**3 = 262144, this is the mask of the storage
        return (int) ((modified / day) % 262144L);
    }
        
    public static String microDateHoursStr(final long time) {
        return kelondroBase64Order.enhancedCoder.encodeLong(microDateHoursInt(time), 3);
    }
    
    public static int microDateHoursInt(final long time) {
        return (int) ((time / hour) % 262144L);
    }
    
    public static int microDateHoursAge(final String mdhs) {
        return microDateHoursInt(System.currentTimeMillis()) - (int) kelondroBase64Order.enhancedCoder.decodeLong(mdhs);
    }
    
    public static long reverseMicroDateDays(final long microDateDays) {
        return Math.min(System.currentTimeMillis(), microDateDays * day);
    }
    
    public static void main(final String[] args) {
        
    }
}
