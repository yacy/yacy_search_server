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

package net.yacy.server;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.json.JSONObject;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.RequestHeader.FileType;
import net.yacy.cora.util.ChunkedBytes;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.Formatter;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;


public class serverObjects implements Serializable, Cloneable {

    private static final long serialVersionUID = 3999165204849858546L;
    public static final String ACTION_AUTHENTICATE = "AUTHENTICATE";

    /** Key for an URL redirection : should be associated with the redirected location.
     * The main servlet handles this to produce an HTTP 302 status. */
    public static final String ACTION_LOCATION = "LOCATION";
	public final static String ADMIN_AUTHENTICATE_MSG = "admin log-in. If you don't know the password, set it with {yacyhome}/bin/passwd.sh {newpassword}";

    private boolean localized = true;

    private final static char BOM = '\uFEFF'; // ByteOrderMark character that may appear at beginnings of Strings (Browser may append that)
    private final Map<String, ChunkedBytes[]> map;

    public serverObjects() {
        super();
        this.map = new ConcurrentHashMap<>();
    }

    protected serverObjects(serverObjects o) {
        super();
        this.map = new ConcurrentHashMap<>(o.map);
        this.localized = o.localized;
    }

    protected serverObjects(final Map<String, ChunkedBytes[]> input) {
        super();
        this.map = new ConcurrentHashMap<>(input);
    }

    public void authenticationRequired() {
    	this.put(ACTION_AUTHENTICATE, ADMIN_AUTHENTICATE_MSG);
    }

    public int size() {
        return this.map.size();
    }

    public void clear() {
        this.map.clear();
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    private static final String removeByteOrderMark(final String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.charAt(0) == BOM) return s.substring(1);
        return s;
    }

    public boolean containsKey(String key) {
    	return key != null && this.map.containsKey(key);
    }
    
    public MultiMapSolrParams getSolrParams() {
        ModifiableSolrParams params = new ModifiableSolrParams();
        for (String key : this.keySet()) {
            ChunkedBytes[] cbs = this.map.get(key);
            if (cbs == null) continue;
            for (ChunkedBytes cb : cbs) params.add(key, UTF8.String(cb.toByteArray()));
        }
        return new MultiMapSolrParams(params.getMap());
    }

    public List<Map.Entry<String, String>> entrySet() {
        List<Map.Entry<String, String>> set = new ArrayList<>(this.map.size() * 2);
        for (Map.Entry<String, ChunkedBytes[]> entry: this.map.entrySet()) {
            ChunkedBytes[] vlist = entry.getValue();
            for (ChunkedBytes v: vlist) set.add(new AbstractMap.SimpleEntry<>(entry.getKey(), removeByteOrderMark(UTF8.String(v.toByteArray()))));
        }
        return set;
    }

    public List<String> values() {
    	List<String> list = new ArrayList<>(this.map.size() * 2);
        for (Map.Entry<String, ChunkedBytes[]> entry: this.map.entrySet()) {
            for (ChunkedBytes v: entry.getValue()) list.add(removeByteOrderMark(UTF8.String(v.toByteArray())));
        }
        return list;
    }

    public Set<String> keySet() {
        return new HashSet<>(this.map.keySet());
    }

    public String[] remove(String key) {
        ChunkedBytes[] cbs = this.map.remove(key);
        String[] arr = new String[cbs == null ? 0 : cbs.length];
        if (cbs != null) {
            for (int i = 0; i < arr.length; i++) arr[i] = UTF8.String(cbs[i].toByteArray());
        }
        return arr;
    }
    
    public void putAll(Map<String, String> m) {
        for (Map.Entry<String, String> e: m.entrySet()) {
            this.put(e.getKey(), e.getValue());
        }
    }

    public void add(final String key, final String value) {
        if (key == null) { return; } // this does nothing
        if (value == null) { return; }
        ChunkedBytes[] a = this.map.get(key);
        if (a == null) {
            this.map.put(key, new ChunkedBytes[]{new ChunkedBytes(value)});
            return;
        }
        ChunkedBytes nv = new ChunkedBytes(value);
        for (ChunkedBytes cb : a) {
            if (cb.equals(nv)) return;
        }
        ChunkedBytes[] aa = new ChunkedBytes[a.length + 1];
        System.arraycopy(a, 0, aa, 0, a.length);
        aa[a.length] = nv;
        this.map.put(key, aa);
        return;
    }

    public void put(final String key, final boolean value) {
        this.put(key, value ? "1" : "0");
    }

    public void put(final String key, final ChunkedBytes value) {
        if (key == null) { return; }
        if (value == null) { this.map.remove(key); return; } // assigning the null value creates the same effect like removing the element
        this.map.put(key, new ChunkedBytes[]{value});
    }

    public void put(final String key, final String value) {
        if (key == null) { return; }
        if (value == null) { this.map.remove(key); return; } // assigning the null value creates the same effect like removing the element
        this.map.put(key, new ChunkedBytes[]{new ChunkedBytes(value)});
    }

    public void add(final String key, final byte[] value) {
        if (value == null) return;
        this.add(key, UTF8.String(value));
    }

    public void put(final String key, final byte[] value) {
        if (value == null) return;
        this.put(key, UTF8.String(value));
    }

    public void put(final String key, final String[] values) {
        if (key == null) { return; } // this does nothing
        if (values == null) { this.map.remove(key); return; } // assigning the null value creates the same effect like removing the element
        ChunkedBytes[] cbs = new ChunkedBytes[values.length];
        for (int i = 0; i < values.length; i++) cbs[i] = new ChunkedBytes(values[i]);
        this.map.put(key, cbs);
    }

    /**
     * Add an unformatted String representation of a double/float value
     * to the map.
     * @param key   key name as String.
     * @param value value as double/float.
     */
    public void put(final String key, final float value) {
        this.put(key, Float.toString(value));
    }

    public void put(final String key, final double value) {
        this.put(key, Double.toString(value));
    }

    /**
     * same as {@link #put(String, double)} but for integer types
     */
    public void put(final String key, final long value) {
        this.put(key, Long.toString(value));
    }

    public void put(final String key, final java.util.Date value) {
        this.put(key, value.toString());
    }

    public void put(final String key, final InetAddress value) {
        this.put(key, value == null ? "" : value.getHostAddress());
    }

    /**
     * Add a String to the map. The content of the String is escaped to be usable in JSON output.
     * @param key   key name as String.
     * @param value a String that will be reencoded for JSON output.
     */
    public void putJSON(final String key, String value) {
        if (value == null) { this.put(key, ""); return; }
        value = JSONObject.quote(value);
        value = value.substring(1, value.length() - 1);
        this.put(key, value);
    }
    
    /**
     * Add a String to the map. The content of the string is first decoded to removed any URL encoding (application/x-www-form-urlencoded).
     * Then the content of the String is escaped to be usable in HTML output.
     * @param key   key name as String.
     * @param value a String that will be reencoded for HTML output.
     * @see CharacterCoding#unicode2html(String, boolean)
     */
    public void putHTML(final String key, final String value) {
        this.put(key, value == null ? "" : CharacterCoding.unicode2html(UTF8.decodeURL(value), true));
    }

    /**
     * Add a String UTF-8 encoded bytes to the map. The content of the string is first decoded to removed any URL encoding (application/x-www-form-urlencoded).
     * Then the content of the String is escaped to be usable in HTML output.
     * @param key   key name as String.
     * @param value the UTF-8 encoded byte array of a String that will be reencoded for HTML output.
     * @see CharacterCoding#unicode2html(String, boolean)
     */
    public void putHTML(final String key, final byte[] value) {
        this.putHTML(key, value == null ? "" : CharacterCoding.unicode2html(UTF8.decodeURL(UTF8.String(value)), true));
    }

	/**
	 * Add a String to the map. The eventual URL encoding
	 * (application/x-www-form-urlencoded) is retained, but the String is still
	 * escaped to be usable in HTML output.
	 *
	 * @param key   key name as String.
	 * @param value a String that will be reencoded for HTML output.
	 * @see CharacterCoding#unicode2html(String, boolean)
	 */
    public void putUrlEncodedHTML(final String key, final String value) {
        this.put(key, value == null ? "" : CharacterCoding.unicode2html(value, true));
    }

    /**
     * Like {@link #putHTML(String, String)} but takes an extra argument defining, if the returned
     * String should be used in normal HTML: <code>false</code>.
     * If forXML is <code>true</code>, then only the characters <b>&amp; &quot; &lt; &gt;</b> will be
     * replaced in the returned String.
     */
    public void putXML(final String key, final String value) {
        this.put(key, value == null ? "" : CharacterCoding.unicode2xml(value, true));
    }

    /**
     * Put the key/value pair, escaping characters depending on the target fileType. When the target is HTML, the content of the string is first decoded to removed any URL encoding (application/x-www-form-urlencoded).
     * @param fileType the response target file type
     * @param key
     * @param value
     */
    public void put(final RequestHeader.FileType fileType, final String key, final String value) {
        if (fileType == FileType.JSON) this.putJSON(key, value == null ? "" : value);
        else if (fileType == FileType.XML) this.putXML(key, value == null ? "" : value);
        else this.putHTML(key, value == null ? "" : value);
    }

	/**
	 * Put the key/value pair, escaping characters depending on the target fileType.
	 * The eventual URL encoding (application/x-www-form-urlencoded) is retained.
	 * @param fileType the response target file type
	 * @param key
	 * @param value
	 */
	public void putUrlEncoded(final RequestHeader.FileType fileType, final String key, final String value) {
		if (fileType == FileType.JSON) {
			this.putJSON(key, value == null ? "" : value);
		} else if (fileType == FileType.XML) {
			this.putXML(key, value == null ? "" : value);
		} else {
			this.putUrlEncodedHTML(key, value == null ? "" : value);
		}
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


    /**
     * Add a String to the map. The content of the String is first parsed and interpreted as Wiki code.
     * @param hostport (optional) peer host and port, added when not empty as the base of relative Wiki link URLs.
     * @param key key name as String.
     * @param wikiCode wiki code content as String.
     */
    public void putWiki(final String hostport, final String key, final String wikiCode){
        this.put(key, Switchboard.wikiParser.transform(hostport, wikiCode));
    }

    /**
     * Add a String to the map. The content of the String is first parsed and interpreted as Wiki code.
     * @param key key name as String.
     * @param wikiCode wiki code content as String.
     */
    public void putWiki(final String key, final String wikiCode){
        this.putWiki(null, key, wikiCode);
    }

    /**
     * Add a byte array to the map. The content of the array is first parsed and interpreted as Wiki code.
     * @param hostport (optional) peer host and port, added when not empty as the base of relative Wiki link URLs.
     * @param key key name as String.
     * @param wikiCode wiki code content as byte array.
     */
    public void putWiki(final String hostport, final String key, final byte[] wikiCode) {
        try {
            this.put(key, Switchboard.wikiParser.transform(hostport, wikiCode));
        } catch (final Exception e) {
            this.put(key, "Internal error pasting wiki-code: " + e.getMessage());
        }
    }

    /**
     * Add a byte array to the map. The content of the array is first parsed and interpreted as Wiki code.
     * @param key key name as String.
     * @param wikiCode wiki code content as byte array.
     */
    public void putWiki(final String key, final byte[] wikiCode) {
    	this.putWiki(null, key, wikiCode);
    }

    // inc variant: for counters
    public long inc(final String key) {
        String c = this.get(key);
        if (c == null) c = "0";
        final long l = Long.parseLong(c) + 1;
        this.put(key, Long.toString(l));
        return l;
    }

    public String[] getParams(String name) {
        ChunkedBytes[] cbs = this.map.get(name);
        String[] s = new String[cbs == null ? 0 : cbs.length];
        if (cbs != null) {
            for (int i = 0; i < s.length; i++) {
            	s[i] = removeByteOrderMark(cbs[i] == null ? null : UTF8.String(cbs[i].toByteArray()));
            }
        }
        return s;
    }

    /**
     * Get the content of a post field as a String
     * This is a convenience method for the most common case.
     * It returns an UTF-8 decoded String of maximum length 2GB.
     * @param key
     * @return
     */
    public String get(String key) {
        ChunkedBytes[] cbs = this.map.get(key);
        String s = (cbs == null || cbs.length == 0) ? null : UTF8.String(cbs[0].toByteArray());
        return removeByteOrderMark(s);
    }


    /**
     * Get the content of a post field as a byte array
     * This supports content a maximum length of 2GB.
     * You should probably use getInputStream() instead.
     * @param key
     * @return
     */
    public byte[] getBytes(final String key) {
        ChunkedBytes[] cbs = this.map.get(key);
        return cbs == null || cbs.length == 0 ? null : cbs[0].toByteArray();
    }

    /**
     * Get the content of a post field as an InputStream
     * This supports content of arbitrary length (> 2GB)
     * @param key
     * @return
     */
    public InputStream getInputStream(final String key) {
        ChunkedBytes[] cbs = this.map.get(key);
        return cbs == null || cbs.length == 0 ? null : cbs[0].openStream();
    }

    // string variant
    public String get(final String key, final String dflt) {
        final String result = this.get(key);
        return (result == null) ? dflt : result;
    }

    public ChunkedBytes get(final String key, final ChunkedBytes dflt) {
        ChunkedBytes[] cbs = this.map.get(key);
        return cbs == null || cbs.length == 0 ? dflt : cbs[0];
    }

    public int getInt(final String key, final int dflt) {
        final String s = this.get(key);
        if (s == null) return dflt;
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public long getLong(final String key, final long dflt) {
        final String s = this.get(key);
        if (s == null) return dflt;
        try {
            return Long.parseLong(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public float getFloat(final String key, final float dflt) {
        final String s = this.get(key);
        if (s == null) return dflt;
        try {
            return Float.parseFloat(s);
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public double getDouble(final String key, final double dflt) {
        final String s = this.get(key);
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
        String s = this.get(key);
        if (s == null) return false;
        s = s.toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("on") || s.equals("1");
    }

    /**
     * @param keyMapper a regular expression for keys matching
     * @return a set of all values where their key mappes the keyMapper
     * @throws PatternSyntaxException when the keyMapper syntax is not valid
     */
    public String[] getAll(final String keyMapper) throws PatternSyntaxException {
        // the keyMapper may contain regular expressions as defined in String.matches
        // this method is particulary useful when parsing the result of checkbox forms
        final List<String> v = new ArrayList<>();
        final Pattern keyPattern = Pattern.compile(keyMapper);
        for (final Map.Entry<String, String> entry: this.entrySet()) {
            if (keyPattern.matcher(entry.getKey()).matches()) {
                v.add(entry.getValue());
            }
        }

        return v.toArray(new String[0]);
    }

    /**
     * @param keyMapper a regular expression for keys matching
     * @return a map of keys/values where keys matches the keyMapper
     * @throws PatternSyntaxException when the keyMapper syntax is not valid
     */
    public Map<String, String> getMatchingEntries(final String keyMapper) throws PatternSyntaxException  {
        // the keyMapper may contain regular expressions as defined in String.matches
        // this method is particulary useful when parsing the result of checkbox forms
    	final Pattern keyPattern = Pattern.compile(keyMapper);
        final Map<String, String> map = new ConcurrentHashMap<>();
        for (final Map.Entry<String, String> entry: this.entrySet()) {
            if (keyPattern.matcher(entry.getKey()).matches()) {
            	map.put(entry.getKey(), entry.getValue());
            }
        }

        return map;
    }

    // put all elements of another hashtable into the own table
    public void putAll(final serverObjects add) {
        for (final Map.Entry<String, String> entry: add.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    // convenience methods for storing and loading to a file system
    public void store(final File f) throws IOException {
        try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(f))) {
            for (Map.Entry<String, String> e : this.entrySet()) {
                String v = e.getValue()
                    .replace("\\", "\\\\")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("=", "\\=");
                String line = e.getKey() + "=" + v + "\r\n";
                fos.write(UTF8.getBytes(line));
            }
            fos.flush();
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
    public serverObjects clone() {
        ConcurrentHashMap<String, ChunkedBytes[]> copy = new ConcurrentHashMap<>();
        this.map.forEach((k, arr) -> {
            ChunkedBytes[] arrCopy = new ChunkedBytes[arr.length];
            for (int i = 0; i < arr.length; i++) arrCopy[i] = (ChunkedBytes) arr[i].clone();
            copy.put(k, arrCopy);
        });
        serverObjects c = new serverObjects(copy);
        c.localized = this.localized;
        return c;
    }
    
    /**
     * output the objects in a HTTP GET syntax
     */
    @Override
    public String toString() {
        if (this.map.isEmpty()) return "";
        final StringBuilder param = new StringBuilder(this.map.size() * 40);
        for (final Map.Entry<String, String> entry: this.entrySet()) {
            param.append(MultiProtocolURL.escape(entry.getKey()))
                .append('=')
                .append(MultiProtocolURL.escape(entry.getValue()))
                .append('&');
        }
        return param.toString();
    }

    public MultiMapSolrParams toSolrParams(CollectionSchema[] facets) {
        // check if all required post fields are there
        this.map.putIfAbsent(CommonParams.DF, new ChunkedBytes[] {new ChunkedBytes(CollectionSchema.text_t.getSolrFieldName())}); // set default field to the text field
        this.map.putIfAbsent(CommonParams.START, new ChunkedBytes[] {new ChunkedBytes("0")}); // set default start item
        this.map.putIfAbsent(CommonParams.ROWS, new ChunkedBytes[] {new ChunkedBytes("10")}); // set default number of search results

        if (facets != null && facets.length > 0) {
            this.remove("facet");
            this.put("facet", "true");
            for (int i = 0; i < facets.length; i++) this.add(FacetParams.FACET_FIELD, facets[i].getSolrFieldName());
        }
        return this.getSolrParams();
    }

}
