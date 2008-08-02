// kelondroObjectBuffer.java
// ------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This is a part of the kelondro database, which is a part of YaCy
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
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
//
//
// A NOTE FROM THE AUTHOR TO THE USERS:
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// 
// A NOTE FROM THE AUTHOR TO DEVELOPERS:
//
// Contributions and changes to the program code should be marked as such:
// Please enter your own (C) notice below; they must be compatible with the GPL.
// Please mark also all changes in the code; if you don't mark them then they
// can't be identified; thus all unmarked code belong to the copyright holder
// as mentioned above. A good documentation of code authorities will also help
// to maintain the code and the project.
// A re-distribution must contain the intact and unchanged copyright statement.


package de.anomic.kelondro;

public class kelondroObjectBuffer {

    // this is a buffer for a single (only one) key/value object
    // without an index-backend
    
    private int    readHit, readMiss, writeUnique, writeDouble;
    private final String name;
    private byte[] key;
    private Object value;
    
    public kelondroObjectBuffer(final String name) {
        this.name = name;
        this.readHit = 0;
        this.readMiss = 0;
        this.writeUnique = 0;
        this.writeDouble = 0;
        this.key = null;
        this.value = null;
    }

    public String getName() {
        return name;
    }
    
    public String[] status() {
        return new String[]{
                Integer.toString(readHit),
                Integer.toString(readMiss),
                Integer.toString(writeUnique),
                Integer.toString(writeDouble)
                };
    }
    
    private static String[] combinedStatus(final String[] a, final String[] b) {
        return new String[]{
                Integer.toString(Integer.parseInt(a[0]) + Integer.parseInt(b[0])),
                Integer.toString(Integer.parseInt(a[1]) + Integer.parseInt(b[1])),
                Integer.toString(Integer.parseInt(a[2]) + Integer.parseInt(b[2])),
                Integer.toString(Integer.parseInt(a[3]) + Integer.parseInt(b[3]))
        };
    }
    
    public static String[] combinedStatus(final String[][] a, final int l) {
        if ((a == null) || (a.length == 0) || (l == 0)) return null;
        if ((a.length >= 1) && (l == 1)) return a[0];
        if ((a.length >= 2) && (l == 2)) return combinedStatus(a[0], a[1]);
        return combinedStatus(combinedStatus(a, l - 1), a[l - 1]);
    }
    
    public void put(final byte[] key, final Object value) {
        if ((key == null) || (value == null)) return;
        synchronized(this) {
            if (kelondroNaturalOrder.equal(this.key, key)){
                this.writeDouble++;
            } else {
                this.writeUnique++; 
            }
            this.key = key;
            this.value = value;
        }
    }
    
    public void put(final String key, final Object value) {
        if ((key == null) || (value == null)) return;
        synchronized(this) {
            if (kelondroNaturalOrder.equal(this.key, key.getBytes())){
                this.writeDouble++;
            } else {
                this.writeUnique++; 
            }
            this.key = key.getBytes();
            this.value = value;
        }
    }
    
    public Object get(final byte[] key) {
        if (key == null) return null;
        synchronized(this) {
            if (kelondroNaturalOrder.equal(this.key, key)){
                this.readHit++;
                return this.value;
            } else {
                this.readMiss++;
                return null;
            }
        }
    }
    
    public Object get(final String key) {
        if (key == null) return null;
        synchronized(this) {
            if (kelondroNaturalOrder.equal(this.key, key.getBytes())){
                this.readHit++;
                return this.value;
            } else {
                this.readMiss++;
                return null;
            }
        }
    }
    
    public void remove(final byte[] key) {
        if (key == null) return;
        synchronized(this) {
            if (kelondroNaturalOrder.equal(this.key, key)){
                this.key = null;
                this.value = null;
            }
        }
    }
    
    public void remove(final String key) {
        if (key == null) return;
        synchronized(this) {
            if (kelondroNaturalOrder.equal(this.key, key.getBytes())){
                this.key = null;
                this.value = null;
            }
        }
    }
    
}
