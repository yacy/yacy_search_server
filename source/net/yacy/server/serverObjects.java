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
//FIXME: it's 2012, do we still need support for Java 1.0?!

  So this class was created as a convenience.
  It will also contain special methods that read data from internet-resources
  in the background, while data can already be read out of the object.
  This shall speed up usage when a slow internet connection is used (dial-up)
 */

package net.yacy.server;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.RequestHeader.FileType;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.Formatter;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;


public class serverObjects implements Serializable, Cloneable {
    
    private static final long serialVersionUID = 3999165204849858546L;
    public static final String ACTION_AUTHENTICATE = "AUTHENTICATE";
    public static final String ACTION_LOCATION = "LOCATION";
	public final static String ADMIN_AUTHENTICATE_MSG = "admin log-in. If you don't know the password, set it with {yacyhome}/bin/passwd.sh {newpassword}";

    private final static Pattern patternNewline = Pattern.compile("\n");
    private final static Pattern patternDoublequote = Pattern.compile("\"");
    private final static Pattern patternSlash = Pattern.compile("/");
    private final static Pattern patternB = Pattern.compile("\b");
    private final static Pattern patternF = Pattern.compile("\f");
    private final static Pattern patternR = Pattern.compile("\r");
    private final static Pattern patternT = Pattern.compile("\t");

    private boolean localized = true;

    private final static char BOM = '\uFEFF'; // ByteOrderMark character that may appear at beginnings of Strings (Browser may append that)
    private final MultiMapSolrParams map;
    
    public serverObjects() {
        super();
        this.map = new MultiMapSolrParams(new HashMap<String, String[]>());
    }

    protected serverObjects(serverObjects o) {
        super();
        this.map = o.map;
    }
    
    protected serverObjects(final Map<String, String[]> input) {
        super();
        this.map = new MultiMapSolrParams(input);
    }

    public void authenticationRequired() {
    	this.put(ACTION_AUTHENTICATE, ADMIN_AUTHENTICATE_MSG);
    }

    public int size() {
        return this.map.toNamedList().size() / 2;
    }
    
    public void clear() {
        this.map.getMap().clear();
    }

    public boolean isEmpty() {
        return this.map.getMap().isEmpty();
    }
    
    private static final String removeByteOrderMark(final String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.charAt(0) == BOM) return s.substring(1);
        return s;
    }

    public boolean containsKey(String key) {
        String[] arr = this.map.getParams(key);
        return arr != null && arr.length > 0;
    }
    
    public MultiMapSolrParams getSolrParams() {
        return this.map;
    }

    public List<Map.Entry<String, String>> entrySet() {
        List<Map.Entry<String, String>> set = new ArrayList<Map.Entry<String, String>>(this.map.getMap().size() * 2);
        Set<Map.Entry<String, String[]>> mset = this.map.getMap().entrySet();
        for (Map.Entry<String, String[]> entry: mset) {
            String[] vlist = entry.getValue();
            for (String v: vlist) set.add(new AbstractMap.SimpleEntry<String, String>(entry.getKey(), v));
        }
        return set;
    }
    
    public Set<String> values() {
        Set<String> set = new HashSet<String>(this.map.getMap().size() * 2);
        for (Map.Entry<String, String[]> entry: this.map.getMap().entrySet()) {
            for (String v: entry.getValue()) set.add(v);
        }
        return set;
    }
    
    public Set<String> keySet() {
        return this.map.getMap().keySet();
    }

    public String[] remove(String key) {
        return this.map.getMap().remove(key);
    }
    
    public int remove(String key, int dflt) {
        final String result = removeByteOrderMark(get(key));
        this.map.getMap().remove(key);
        if (result == null) return dflt;
        try {
            return Integer.parseInt(result);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }
    
    public void putAll(Map<String, String> m) {
        for (Map.Entry<String, String> e: m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    public void add(final String key, final String value) {
        if (key == null) {
            // this does nothing
            return;
        }
        if (value == null) {
            return;
        }
        String[] a = map.getMap().get(key);
        if (a == null) {
            map.getMap().put(key, new String[]{value});
            return;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals(value)) return; // double-check
        }
        String[] aa = new String[a.length + 1];
        System.arraycopy(a, 0, aa, 0, a.length);
        aa[a.length] = value;
        map.getMap().put(key, aa);
        return;
    }

    public void put(final String key, final boolean value) {
        put(key, value ? "1" : "0");
    }
    
    public void put(final String key, final String value) {
        if (key == null) {
            // this does nothing
            return;
        }
        if (value == null) {
            // assigning the null value creates the same effect like removing the element
            map.getMap().remove(key);
            return;
        }
        String[] a = map.getMap().get(key);
        if (a == null) {
            map.getMap().put(key, new String[]{value});
            return;
        }
        map.getMap().put(key, new String[]{value});
    }

    public void add(final String key, final byte[] value) {
        if (value == null) return;
        add(key, UTF8.String(value));
    }

    public void put(final String key, final byte[] value) {
        if (value == null) return;
        put(key, UTF8.String(value));
    }

    public void put(final String key, final String[] values) {
        if (key == null) {
            // this does nothing
            return;
        } else if (values == null) {
            // assigning the null value creates the same effect like removing the element
            map.getMap().remove(key);
            return;
        } else {
            map.getMap().put(key, values);
        }
    }

    /**
     * Add an unformatted String representation of a double/float value
     * to the map.
     * @param key   key name as String.
     * @param value value as double/float.
     */
    public void put(final String key, final float value) {
        put(key, Float.toString(value));
    }

    public void put(final String key, final double value) {
        put(key, Double.toString(value));
    }

    /**
     * same as {@link #put(String, double)} but for integer types
     */
    public void put(final String key, final long value) {
        put(key, Long.toString(value));
    }

    public void put(final String key, final java.util.Date value) {
        put(key, value.toString());
    }

    public void put(final String key, final InetAddress value) {
        put(key, value.toString());
    }

    /**
     * Add a String to the map. The content of the String is escaped to be usable in JSON output.
     * @param key   key name as String.
     * @param value a String that will be reencoded for JSON output.
     */
    public void putJSON(final String key, final String value) {
        put(key, toJSON(value));
    }

    public static String toJSON(String value) {
        // value = value.replaceAll("\\", "\\\\");
        value = patternDoublequote.matcher(value).replaceAll("'");
        value = patternSlash.matcher(value).replaceAll("\\/");
        value = patternB.matcher(value).replaceAll("\\b");
        value = patternF.matcher(value).replaceAll("\\f");
        value = patternNewline.matcher(value).replaceAll("\\r");
        value = patternR.matcher(value).replaceAll("\\r");
        value = patternT.matcher(value).replaceAll("\\t");
        return value;
    }

    /**
     * Add a String to the map. The content of the String is escaped to be usable in HTML output.
     * @param key   key name as String.
     * @param value a String that will be reencoded for HTML output.
     * @return      the modified String that was added to the map.
     * @see CharacterCoding#encodeUnicode2html(String, boolean)
     */
    public void putHTML(final String key, final String value) {
        put(key, CharacterCoding.unicode2html(value, true));
    }

    public void putHTML(final String key, final byte[] value) {
        putHTML(key, UTF8.String(value));
    }

    /**
     * Like {@link #putHTML(String, String)} but takes an extra argument defining, if the returned
     * String should be used in normal HTML: <code>false</code>.
     * If forXML is <code>true</code>, then only the characters <b>&amp; &quot; &lt; &gt;</b> will be
     * replaced in the returned String.
     */
    public void putXML(final String key, final String value) {
        put(key, CharacterCoding.unicode2xml(value, true));
    }

    /**
     * put the key/value pair with a special method according to the given file type
     * @param fileType
     * @param key
     * @param value
     * @return
     */
    public void put(final RequestHeader.FileType fileType, final String key, final String value) {
        if (fileType == FileType.JSON) putJSON(key, value);
        else if (fileType == FileType.XML) putXML(key, value);
        else putHTML(key, value);
    }

    /**
     * Add a byte/long/integer to the map. The number will be encoded into a String using
     * a localized format specified by {@link Formatter} and {@link #setLocalized(boolean)}.
     * @param key   key name as String.
     * @param value integer type value to be added to the map in its formatted String
     *              representation.
     * @return the String value added to the map.
     */
    public void putNum(final String key, final long value) {
        this.put(key, Formatter.number(value, this.localized));
    }

    /**
     * Variant for double/float types.
     * @see #putNum(String, long)
     */
    public void putNum(final String key, final double value) {
        this.put(key, Formatter.number(value, this.localized));
    }

    /**
     * Variant for string encoded numbers.
     * @see #putNum(String, long)
     */
    public void putNum(final String key, final String value) {
        this.put(key, value == null ? "" : Formatter.number(value));
    }


    public void putWiki(final String hostport, final String key, final String wikiCode){
        this.put(key, Switchboard.wikiParser.transform(hostport, wikiCode));
    }

    public void putWiki(final String hostport, final String key, final byte[] wikiCode) {
        try {
            this.put(key, Switchboard.wikiParser.transform(hostport, wikiCode));
        } catch (final UnsupportedEncodingException e) {
            this.put(key, "Internal error pasting wiki-code: " + e.getMessage());
        }
    }

    // inc variant: for counters
    public long inc(final String key) {
        String c = get(key);
        if (c == null) c = "0";
        final long l = Long.parseLong(c) + 1;
        put(key, Long.toString(l));
        return l;
    }

    public String[] getParams(String name) {
      return map.getMap().get(name);
    }

    public String get(String name) {
      String[] arr = map.getMap().get(name);
      return arr == null || arr.length == 0 ? null : arr[0];
    }
    
    // new get with default objects
    public Object get(final String key, final Object dflt) {
        final Object result = get(key);
        return (result == null) ? dflt : result;
    }

    // string variant
    public String get(final String key, final String dflt) {
        final String result = removeByteOrderMark(get(key));
        return (result == null) ? dflt : result;
    }

    public int getInt(final String key, final int dflt) {
        final String s = removeByteOrderMark(get(key));
        if (s == null) return dflt;
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public long getLong(final String key, final long dflt) {
        final String s = removeByteOrderMark(get(key));
        if (s == null) return dflt;
        try {
            return Long.parseLong(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public float getFloat(final String key, final float dflt) {
        final String s = removeByteOrderMark(get(key));
        if (s == null) return dflt;
        try {
            return Float.parseFloat(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public double getDouble(final String key, final double dflt) {
        final String s = removeByteOrderMark(get(key));
        if (s == null) return dflt;
        try {
            return Double.parseDouble(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    /**
     * get the boolean value of a post field
     * DO NOT INTRODUCE A DEFAULT FIELD HERE,
     * this is an application for html checkboxes which do not appear
     * in the post if they are not selected.
     * Therefore the default value MUST be always FALSE.
     * @param key
     * @return the boolean value of a field or false, if the field does not appear.
     */
    public boolean getBoolean(final String key) {
        String s = removeByteOrderMark(get(key));
        if (s == null) return false;
        s = s.toLowerCase();
        return s.equals("true") || s.equals("on") || s.equals("1");
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
        for (final Map.Entry<String, String> entry: add.entrySet()) {
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
        return new serverObjects(this.map.getMap());
    }

    /**
     * output the objects in a HTTP GET syntax
     */
    @Override
    public String toString() {
        if (this.map.getMap().isEmpty()) return "";
        final StringBuilder param = new StringBuilder(this.map.getMap().size() * 40);
        for (final Map.Entry<String, String> entry: entrySet()) {
            param.append(MultiProtocolURL.escape(entry.getKey()))
                .append('=')
                .append(MultiProtocolURL.escape(entry.getValue()))
                .append('&');
        }
        param.setLength(param.length() - 1);
        return param.toString();
    }

    public MultiMapSolrParams toSolrParams(CollectionSchema[] facets) {
        // check if all required post fields are there
        if (!this.containsKey(CommonParams.DF)) this.put(CommonParams.DF, CollectionSchema.text_t.getSolrFieldName()); // set default field to the text field
        if (!this.containsKey(CommonParams.START)) this.put(CommonParams.START, "0"); // set default start item
        if (!this.containsKey(CommonParams.ROWS)) this.put(CommonParams.ROWS, "10"); // set default number of search results

        if (facets != null && facets.length > 0) {
            this.remove("facet");
            this.put("facet", "true");
            for (int i = 0; i < facets.length; i++) this.add("facet.field", facets[i].getSolrFieldName());
        }
        return this.map;
    }

    public static void main(final String[] args) {
        final String v = "ein \"zitat\"";
        System.out.println(toJSON(v));
    }

}
