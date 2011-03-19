//serverObjects.java 
//-----------------------
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//(C) changes by Bjoern 'fuchs' Krombholz
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

/*
  Why do we need this Class?
  The purpose of this class is to provide a hashtable object to the server
  and implementing interfaces. Values to and from cgi pages are encapsulated in
  this object. The server shall be executable in a Java 1.0 environment,
  so the following other options did not comply:

  Properties - setProperty would be needed, but only available in 1.2
  HashMap, TreeMap - only in 1.2
  Hashtable - available in 1.0, but 'put' does not accept null values

  So this class was created as a convenience.
  It will also contain special methods that read data from internet-resources
  in the background, while data can already be read out of the object.
  This shall speed up usage when a slow internet connection is used (dial-up)
 */

package de.anomic.server;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.Formatter;

import de.anomic.search.Switchboard;

public class serverObjects extends HashMap<String, String> implements Cloneable {

    private final static Pattern patternNewline = Pattern.compile("\n");
    private final static Pattern patternDoublequote = Pattern.compile("\"");
    private final static Pattern patternSlash = Pattern.compile("/");
    private final static Pattern patternB = Pattern.compile("\b");
    private final static Pattern patternF = Pattern.compile("\f");
    private final static Pattern patternR = Pattern.compile("\r");
    private final static Pattern patternT = Pattern.compile("\t");
    
    private static final long serialVersionUID = 1L;
    private boolean localized = true; 
    
    private final static char BOM = '\uFEFF'; // ByteOrderMark character that may appear at beginnings of Strings (Browser may append that)

    public serverObjects() {
        super();
    }

    public serverObjects(final int initialCapacity) {
        super(initialCapacity);
    }

    public serverObjects(final Map<String, String> input) {
        super(input);
    }
    
    private static final String removeByteOrderMark(final String s) {
        if (s == null || s.length() == 0) return s;
        if (s.charAt(0) == BOM) return s.substring(1);
        return s;
    }

    /**
     * Add a key-value pair of Objects to the map.
     * @param key   This method will do nothing if the key is <code>null</code>.
     * @param value The value that should be mapped to the key.
     *              If value is <code>null</code>, then the element at <code>key</code>
     *              is removed from the map.
     * @return The value that was added to the map. 
     * @see java.util.Hashtable#insert(K, V)
     */
    @Override
    public String put(final String key, final String value) {
        if (key == null) {
            // this does nothing
            return null;
        } else if (value == null) {
            // assigning the null value creates the same effect like removing the element
            return super.remove(key);
        } else {
            return super.put(key, value);
        }
    }

    /**
     * Add byte array to the map, value is kept as it is.
     * @param key   key name as String.
     * @param value mapped value as a byte array.
     * @return      the previous value as String.
     */
    public String put(final String key, final byte[] value) {
        return this.put(key, UTF8.String(value));
    }

    /**
     * Add an unformatted String representation of a double/float value
     * to the map.
     * @param key   key name as String.
     * @param value value as double/float.
     * @return value as it was added to the map or <code>NaN</code> if an error occured.
     */
    public double put(final String key, final float value) {
        return (null == this.put(key, Float.toString(value))) ? Float.NaN : value;
    }

    /**
     * same as {@link #put(String, double)} but for integer types
     * @return Returns 0 for the error case.
     */
    public long put(final String key, final long value) {
        return (null == this.put(key, Long.toString(value))) ? 0 : value;
    }

    public String put(final String key, final java.util.Date value) {
        return this.put(key, value.toString());
    }
    
    public String put(final String key, final InetAddress value) {
        return this.put(key, value.toString());
    }
    
    /**
     * Add a String to the map. The content of the String is escaped to be usable in JSON output.
     * @param key   key name as String.
     * @param value a String that will be reencoded for JSON output.
     * @return      the modified String that was added to the map.
     */
    public String putJSON(final String key, String value) {
        // value = value.replaceAll("\\", "\\\\");
        value = patternDoublequote.matcher(value).replaceAll("'");
        value = patternSlash.matcher(value).replaceAll("\\/");
        value = patternB.matcher(value).replaceAll("\\b");
        value = patternF.matcher(value).replaceAll("\\f");
        value = patternNewline.matcher(value).replaceAll("\\r");
        value = patternR.matcher(value).replaceAll("\\r");
        value = patternT.matcher(value).replaceAll("\\t");
    	return put(key, value);
    }
    
    public String putJSON(final String key, final byte[] value) {
        return putJSON(key, UTF8.String(value));
    }
    
    /**
     * Add a String to the map. The content of the String is escaped to be usable in HTML output.
     * @param key   key name as String.
     * @param value a String that will be reencoded for HTML output.
     * @return      the modified String that was added to the map.
     * @see CharacterCoding#encodeUnicode2html(String, boolean)
     */
    public String putHTML(final String key, final String value) {
        return put(key, CharacterCoding.unicode2html(value, true));
    }
    
    public String putHTML(final String key, final byte[] value) {
        return putHTML(key, UTF8.String(value));
    }

    /**
     * Like {@link #putHTML(String, String)} but takes an extra argument defining, if the returned
     * String should be used in normal HTML: <code>false</code>.
     * If forXML is <code>true</code>, then only the characters <b>&amp; &quot; &lt; &gt;</b> will be
     * replaced in the returned String.
     */
    public String putXML(final String key, final String value) {
        return put(key, CharacterCoding.unicode2xml(value, true));
    }

    /**
     * Add a byte/long/integer to the map. The number will be encoded into a String using
     * a localized format specified by {@link Formatter} and {@link #setLocalized(boolean)}.
     * @param key   key name as String.
     * @param value integer type value to be added to the map in its formatted String 
     *              representation.
     * @return the String value added to the map.
     */
    public String putNum(final String key, final long value) {
        return this.put(key, Formatter.number(value, this.localized));
    }

    /**
     * Variant for double/float types.
     * @see #putNum(String, long)
     */
    public String putNum(final String key, final double value) {
        return this.put(key, Formatter.number(value, this.localized));
    }

    /**
     * Variant for string encoded numbers.
     * @see #putNum(String, long)
     */
    public String putNum(final String key, final String value) {
        return this.put(key, value == null ? "" : Formatter.number(value));
    }

    
    public String putWiki(String hostport, final String key, final String wikiCode){
        return this.put(key, Switchboard.wikiParser.transform(hostport, wikiCode));
    }
    
    public String putWiki(String hostport, final String key, final byte[] wikiCode) {
        try {
            return this.put(key, Switchboard.wikiParser.transform(hostport, wikiCode));
        } catch (final UnsupportedEncodingException e) {
            return this.put(key, "Internal error pasting wiki-code: " + e.getMessage());
        }
    }

    // inc variant: for counters
    public long inc(final String key) {
        String c = super.get(key);
        if (c == null) c = "0";
        final long l = Long.parseLong(c) + 1;
        super.put(key, Long.toString(l));
        return l;
    }

    // new get with default objects
    public Object get(final String key, final Object dflt) {
        final Object result = super.get(key);
        return (result == null) ? dflt : result;
    }

    // string variant
    public String get(final String key, final String dflt) {
        final String result = removeByteOrderMark(super.get(key));
        return (result == null) ? dflt : result;
    }

    public int getInt(final String key, final int dflt) {
        final String s = removeByteOrderMark(super.get(key));
        if (s == null) return dflt;
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public long getLong(final String key, final long dflt) {
        final String s = removeByteOrderMark(super.get(key));
        if (s == null) return dflt;
        try {
            return Long.parseLong(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public float getFloat(final String key, final float dflt) {
        final String s = removeByteOrderMark(super.get(key));
        if (s == null) return dflt;
        try {
            return Float.parseFloat(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public boolean getBoolean(final String key, final boolean dflt) {
        String s = removeByteOrderMark(super.get(key));
        if (s == null) return dflt;
        s = s.toLowerCase();
        return s.equals("true") || s.equals("on") || s.equals("1");
    }
    
    public boolean hasValue(final String key) {
        final String s = super.get(key);
        return (s != null && !s.isEmpty());
    }

    // returns a set of all values where their key mappes the keyMapper
    public String[] getAll(final String keyMapper) {
        // the keyMapper may contain regular expressions as defined in String.matches
        // this method is particulary useful when parsing the result of checkbox forms
        final List<String> v = new ArrayList<String>();
        for (final Map.Entry<String, String> entry: entrySet()) {
            if (entry.getKey().matches(keyMapper)) {
                v.add(entry.getValue());
            }
        }

        return v.toArray(new String[0]);
    }

    // put all elements of another hashtable into the own table
    public void putAll(final serverObjects add) {
        for (Map.Entry<String, String> entry: add.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    // convenience methods for storing and loading to a file system
    public void store(final File f) throws IOException {
        BufferedOutputStream fos = null;
        try {
            fos = new BufferedOutputStream(new FileOutputStream(f));
            final StringBuilder line = new StringBuilder(64);
            for (final Map.Entry<String, String> entry : entrySet()) {
                line.delete(0, line.length());
                line.append(entry.getKey());
                line.append("=");
                line.append(patternNewline.matcher(entry.getValue()).replaceAll("\\\\n"));
                line.append("\r\n");

                fos.write(UTF8.getBytes(line.toString()));
            }
        } finally {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (final Exception e){}
            }
        }
    }

    /**
     * Defines the localization state of this object.
     * Currently it is used for numbers added with the putNum() methods only.
     * @param loc if <code>true</code> store numbers in a localized format, otherwise
     *            use a default english locale without grouping.
     * @see Formatter#setLocale(String) 
     */
    public void setLocalized(final boolean loc) {
        this.localized = loc;
    }

    @Override
    public Object clone() {
        return super.clone();
    }

    /**
     * output the objects in a HTTP GET syntax
     */
    @Override
    public String toString() {
        if (this.isEmpty()) return "";
        final StringBuilder param = new StringBuilder(this.size() * 40);
        for (final Map.Entry<String, String> entry: this.entrySet()) {
            param.append(MultiProtocolURI.escape(entry.getKey()));
            param.append('=');
            param.append(MultiProtocolURI.escape(entry.getValue()));
            param.append('&');
        }
        param.setLength(param.length() - 1);
        return param.toString();
    }
    
}
