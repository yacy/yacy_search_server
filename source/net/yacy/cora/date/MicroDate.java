/**
 *  MicroDate
 *  Copyright 2008 by Michael Peter Christen
 *  First released 3.7.2008 at http://yacy.net
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

package net.yacy.cora.date;

import java.util.Date;

import net.yacy.cora.order.Base64Order;


public class MicroDate {

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
        return Base64Order.enhancedCoder.encodeLongSB(microDateHoursInt(time), 3).toString();
    }
    
    public static int microDateHoursInt(final long time) {
        return (int) ((time / hour) % 262144L);
    }
    
    public static int microDateHoursAge(final String mdhs) {
        return microDateHoursInt(System.currentTimeMillis()) - (int) Base64Order.enhancedCoder.decodeLong(mdhs);
    }
    
    public static long reverseMicroDateDays(final long microDateDays) {
        return Math.min(System.currentTimeMillis(), microDateDays * day);
    }
    
    public static void main(final String[] args) {
        
    }
}
