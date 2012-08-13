// Column.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.05.2006 on http://www.anomic.de
//
// This is a part of the kelondro database,
// which is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.kelondro.index;

import java.io.Serializable;

import net.yacy.cora.util.NumberTools;
import net.yacy.kelondro.util.kelondroException;

public final class Column implements Cloneable, Serializable {

    private static final long serialVersionUID=6558500565023465301L;

    public static final int celltype_undefined  = 0;
    public static final int celltype_boolean    = 1;
    public static final int celltype_binary     = 2;
    public static final int celltype_string     = 3;
    public static final int celltype_cardinal   = 4;
    public static final int celltype_bitfield   = 5;

    public static final int encoder_none   = 0;
    public static final int encoder_b64e   = 1;
    public static final int encoder_b256   = 2;
    public static final int encoder_bytes  = 3;

    public          int    cellwidth;
    public    final String nickname;
    protected final int    celltype;
    protected final int    encoder;
    protected final String description;

    public Column(final String nickname, final int celltype, final int encoder, final int cellwidth, final String description) {
        this.celltype = celltype;
        this.cellwidth = cellwidth;
        this.encoder = encoder;
        this.nickname = nickname;
        this.description = description;
    }

    public Column(String celldef) {
        // define column with column syntax
        // example: <UDate-3>

        // cut quotes etc.
        celldef = celldef.trim();
        if (!celldef.isEmpty() && celldef.charAt(0) == '<') celldef = celldef.substring(1);
        if (celldef.endsWith(">")) celldef = celldef.substring(0, celldef.length() - 1);

        // parse type definition
        int p = celldef.indexOf(' ');
        String typename = "";
        if (p < 0) {
            // no typedef
            this.celltype = celltype_undefined;
            this.cellwidth = -1;
        } else {
            typename = celldef.substring(0, p);
            celldef = celldef.substring(p + 1).trim();

            if (typename.equals("boolean")) {
                this.celltype = celltype_boolean;
                this.cellwidth = 1;
            } else if (typename.equals("byte")) {
                this.celltype = celltype_cardinal;
                this.cellwidth = 1;
            } else if (typename.equals("short")) {
                this.celltype = celltype_cardinal;
                this.cellwidth = 2;
            } else if (typename.equals("int")) {
                this.celltype = celltype_cardinal;
                this.cellwidth = 4;
            } else if (typename.equals("long")) {
                this.celltype = celltype_cardinal;
                this.cellwidth = 8;
            } else if (typename.equals("byte[]")) {
                this.celltype = celltype_binary;
                this.cellwidth = -1; // yet undefined
            } else if (typename.equals("char")) {
                this.celltype = celltype_string;
                this.cellwidth = 1;
            } else if (typename.equals("String")) {
                this.celltype = celltype_string;
                this.cellwidth = -1; // yet undefined
            } else if (typename.equals("Cardinal")) {
                this.celltype = celltype_cardinal;
                this.cellwidth = -1; // yet undefined
            } else if (typename.equals("Bitfield")) {
                this.celltype = celltype_bitfield;
                this.cellwidth = -1; // yet undefined
            } else {
                throw new kelondroException("kelondroColumn - undefined type def '" + typename + "'");
            }
        }

        // parse length
        p = celldef.indexOf('-');
        if (p < 0) {
            // if the cell was defined with a type, we dont need to give an explicit with definition
            if (this.cellwidth < 0) throw new kelondroException("kelondroColumn - no cell width definition given");
            final int q = celldef.indexOf(' ');
            if (q < 0) {
                this.nickname = celldef;
                celldef = "";
            } else {
                this.nickname = celldef.substring(0, p);
                celldef = celldef.substring(q + 1);
            }
        } else {
            this.nickname = celldef.substring(0, p);
            final int q = celldef.indexOf(' ');
            if (q < 0) {
                try {
                    this.cellwidth = NumberTools.parseIntDecSubstring(celldef, p + 1);
                } catch (final NumberFormatException e) {
                    throw new kelondroException("kelondroColumn - cellwidth description wrong:" + celldef.substring(p + 1));
                }
                celldef = "";
            } else {
                try {
                    this.cellwidth = NumberTools.parseIntDecSubstring(celldef, p + 1, q);
                } catch (final NumberFormatException e) {
                    throw new kelondroException("kelondroColumn - cellwidth description wrong:" + celldef.substring(p + 1, q));
                }
                celldef = celldef.substring(q + 1);
            }
        }

        // check length constraints
        if (this.cellwidth < 0) throw new kelondroException("kelondroColumn - no cell width given for " + this.nickname);
        if (((typename.equals("boolean")) && (this.cellwidth > 1)) ||
            ((typename.equals("byte")) && (this.cellwidth > 1)) ||
            ((typename.equals("short")) && (this.cellwidth > 2)) ||
            ((typename.equals("int")) && (this.cellwidth > 4)) ||
            ((typename.equals("long")) && (this.cellwidth > 8)) ||
            ((typename.equals("char")) && (this.cellwidth > 1))
           ) throw new kelondroException("kelondroColumn - cell width " + this.cellwidth + " too wide for type " + typename);
        /*
        if (((typename.equals("short")) && (this.cellwidth <= 1)) ||
            ((typename.equals("int")) && (this.cellwidth <= 2)) ||
            ((typename.equals("long")) && (this.cellwidth <= 4))
           ) throw new kelondroException("kelondroColumn - cell width " + this.cellwidth + " not appropriate for type " + typename);
        */
        // parse/check encoder type
        if (!celldef.isEmpty() && celldef.charAt(0) == '{') {
            p = celldef.indexOf('}');
            final String expf = celldef.substring(1, p);
            celldef = celldef.substring(p + 1).trim();
                 if (expf.equals("b64e")) this.encoder = encoder_b64e;
            else if (expf.equals("b256")) this.encoder = encoder_b256;
            else if (expf.equals("bytes")) this.encoder = encoder_bytes;
            else {
                if (this.celltype == celltype_undefined)      this.encoder = encoder_bytes;
                else if (this.celltype == celltype_boolean)   this.encoder = encoder_bytes;
                else if (this.celltype == celltype_binary)    this.encoder = encoder_bytes;
                else if (this.celltype == celltype_string)    this.encoder = encoder_bytes;
                else throw new kelondroException("kelondroColumn - encoder missing for cell '" + this.nickname + "'");
            }
        } else {
            if (this.celltype == celltype_cardinal) throw new kelondroException("kelondroColumn - encoder missing for cell " + this.nickname);
            this.encoder = encoder_bytes;
        }

        assert (this.celltype != celltype_cardinal) || (this.encoder == encoder_b64e) || (this.encoder == encoder_b256);

        // parse/check description
        if (!celldef.isEmpty() && celldef.charAt(0) == '"') {
            p = celldef.indexOf('"', 1);
            this.description = celldef.substring(1, p);
            //unused: celldef = celldef.substring(p + 1).trim();
        } else {
            this.description = this.nickname;
        }
    }

    /**
     * th clone method is useful to produce a similiar column with a different cell width
     * @return the cloned Column
     */
    @Override
    public Object clone() {
    	return new Column(this.nickname, this.celltype, this.encoder, this.cellwidth, this.description);
    }

    /**
     * a column width may change when the object was not yet used.
     * this applies to clones of Column objects which are used as Column producers
     * @param cellwidth
     */
    public void setCellwidth(int cellwidth) {
    	assert this.celltype == celltype_string || this.celltype == celltype_binary;
    	this.cellwidth = cellwidth;
    }

    @Override
    public final String toString() {
        final StringBuilder s = new StringBuilder(20);
        switch (this.celltype) {
        case celltype_undefined:
            s.append(this.nickname);
            s.append('-');
            s.append(this.cellwidth);
            break;
        case celltype_boolean:
            s.append("boolean ");
            s.append(this.nickname);
            break;
        case celltype_binary:
            s.append("byte[] ");
            s.append(this.nickname);
            s.append('-');
            s.append(this.cellwidth);
            break;
        case celltype_string:
            s.append("String ");
            s.append(this.nickname);
            s.append('-');
            s.append(this.cellwidth);
            break;
        case celltype_cardinal:
            s.append("Cardinal ");
            s.append(this.nickname);
            s.append('-');
            s.append(this.cellwidth);
            break;
        case celltype_bitfield:
            s.append("Bitfield ");
            s.append(this.nickname);
            s.append('-');
            s.append(this.cellwidth);
            break;
        default:
            s.append("String ");
            s.append(this.nickname);
            s.append('-');
            s.append(this.cellwidth);
            break;
        }

        switch (this.encoder) {
        case encoder_b64e:
            s.append(" {b64e}");
            break;
        case encoder_b256:
            s.append(" {b256}");
            break;
        default:
            s.append(" {b256}");
            break;
        }
        return s.toString();
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + this.celltype;
		result = prime * result + this.cellwidth;
		result = prime * result + this.encoder;
		result = prime * result
				+ ((this.nickname == null) ? 0 : this.nickname.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (!(obj instanceof Column)) return false;
		final Column other = (Column) obj;
		if (this.celltype != other.celltype) return false;
		if (this.cellwidth != other.cellwidth) return false;
		if (this.encoder != other.encoder) return false;
		if (this.nickname == null) {
			if (other.nickname != null) return false;
		} else if (!this.nickname.equals(other.nickname)) return false;
		return true;
	}

}
