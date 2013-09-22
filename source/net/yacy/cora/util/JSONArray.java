/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package net.yacy.cora.util;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * A JSONArray is an ordered sequence of values. Its external text form is a
 * string wrapped in square brackets with commas separating the values. The
 * internal form is an object having <code>get</code> and <code>opt</code>
 * methods for accessing the values by index, and <code>put</code> methods for
 * adding or replacing values. The values can be any of these types:
 * <code>Boolean</code>, <code>JSONArray</code>, <code>JSONObject</code>,
 * <code>Number</code>, <code>String</code>, or the
 * <code>JSONObject.NULL object</code>.
 * <p>
 * The constructor can convert a JSON text into a Java object. The
 * <code>toString</code> method converts to JSON text.
 * <p>
 * A <code>get</code> method returns a value if one can be found, and throws an
 * exception if one cannot be found. An <code>opt</code> method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 * <p>
 * The generic <code>get()</code> and <code>opt()</code> methods return an
 * object which you can cast or query for type. There are also typed
 * <code>get</code> and <code>opt</code> methods that do type checking and type
 * coercion for you.
 * <p>
 * The texts produced by the <code>toString</code> methods strictly conform to
 * JSON syntax rules. The constructors are more forgiving in the texts they will
 * accept:
 * <ul>
 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
 *     before the closing bracket.</li>
 * <li>The <code>null</code> value will be inserted when there
 *     is <code>,</code>&nbsp;<small>(comma)</small> elision.</li>
 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
 *     quote)</small>.</li>
 * <li>Strings do not need to be quoted at all if they do not begin with a quote
 *     or single quote, and if they do not contain leading or trailing spaces,
 *     and if they do not contain any of these characters:
 *     <code>{ } [ ] / \ : , = ; #</code> and if they do not look like numbers
 *     and if they are not the reserved words <code>true</code>,
 *     <code>false</code>, or <code>null</code>.</li>
 * <li>Values can be separated by <code>;</code> <small>(semicolon)</small> as
 *     well as by <code>,</code> <small>(comma)</small>.</li>
 * <li>Numbers may have the 
 *     <code>0x-</code> <small>(hex)</small> prefix.</li>
 * </ul>

 * @author JSON.org
 * @version 2009-04-14
 */
public class JSONArray {


    /**
     * The arrayList where the JSONArray's properties are kept.
     */
    private ArrayList<Object> myArrayList;

    /**
     * Construct an empty JSONArray.
     */
    public JSONArray() {
        this.myArrayList = new ArrayList<Object>();
    }

    /**
     * Construct a JSONArray from a JSONTokener.
     * @param x A JSONTokener
     * @throws JSONException If there is a syntax error.
     */
    protected JSONArray(JSONTokener x) throws JSONException {
        this();
        char c = x.nextClean();
        char q;
        if (c == '[') {
            q = ']';
        } else if (c == '(') {
            q = ')';
        } else {
            throw x.syntaxError("A JSONArray text must start with '['");
        }
        if (x.nextClean() == ']') {
            return;
        }
        x.back();
        for (;;) {
            if (x.nextClean() == ',') {
                x.back();
                this.myArrayList.add(null);
            } else {
                x.back();
                this.myArrayList.add(x.nextValue());
            }
            c = x.nextClean();
            switch (c) {
            case ';':
            case ',':
                if (x.nextClean() == ']') {
                    return;
                }
                x.back();
                break;
            case ']':
            case ')':
                if (q != c) {
                    throw x.syntaxError("Expected a '" + new Character(q) + "'");
                }
                return;
            default:
                throw x.syntaxError("Expected a ',' or ']'");
            }
        }
    }

    /**
     * Construct a JSONArray from a Collection.
     * @param collection     A Collection.
     */
    public JSONArray(Collection<Object> collection) {
		this.myArrayList = new ArrayList<Object>();
		if (collection != null) {
			Iterator<Object> iter = collection.iterator();
			while (iter.hasNext()) {
			    Object o = iter.next();
                this.myArrayList.add(JSONObject.wrap(o));  
			}
		}
    }

    /**
     * Construct a JSONArray from an array
     * @throws JSONException If not an array.
     */
    protected JSONArray(Object array) throws JSONException {
        this();
        if (array.getClass().isArray()) {
            int length = Array.getLength(array);
            for (int i = 0; i < length; i += 1) {
                this.put(JSONObject.wrap(Array.get(array, i)));
            }
        } else {
            throw new JSONException("JSONArray initial value should be a string or collection or array.");
        }
    }

     
    /**
     * Get the object value associated with an index.
     * @param index
     *  The index must be between 0 and length() - 1.
     * @return An object value.
     * @throws JSONException If there is no value for the index.
     */
    private Object get(int index) throws JSONException {
        Object o = opt(index);
        if (o == null) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        return o;
    }


    /**
     * Get the boolean value associated with an index.
     * The string values "true" and "false" are converted to boolean.
     *
     * @param index The index must be between 0 and length() - 1.
     * @return      The truth.
     * @throws JSONException If there is no value for the index or if the
     *  value is not convertable to boolean.
     */
    public boolean getBoolean(int index) throws JSONException {
        Object o = get(index);
        if (o.equals(Boolean.FALSE) ||
                (o instanceof String &&
                ((String)o).equalsIgnoreCase("false"))) {
            return false;
        } else if (o.equals(Boolean.TRUE) ||
                (o instanceof String &&
                ((String)o).equalsIgnoreCase("true"))) {
            return true;
        }
        throw new JSONException("JSONArray[" + index + "] is not a Boolean.");
    }

    /**
     * Get the float value associated with an index.
     *
     * @param index The index must be between 0 and length() - 1.
     * @return      The value.
     * @throws   JSONException If the key is not found or if the value cannot
     *  be converted to a number.
     */
    public double getFloat(int index) throws JSONException {
        Object o = get(index);
        try {
            return o instanceof Number ?
                ((Number)o).doubleValue() :
                Float.valueOf((String)o).floatValue();
        } catch (final Exception e) {
            throw new JSONException("JSONArray[" + index + "] is not a number.");
        }
    }
    
    /**
     * Get the int value associated with an index.
     *
     * @param index The index must be between 0 and length() - 1.
     * @return      The value.
     * @throws   JSONException If the key is not found or if the value cannot
     *  be converted to a number.
     *  if the value cannot be converted to a number.
     */
    public int getInt(int index) throws JSONException {
        Object o = get(index);
        return o instanceof Number ?
                ((Number)o).intValue() : (int) getFloat(index);
    }
    
    /**
     * Get the long value associated with an index.
     *
     * @param index The index must be between 0 and length() - 1.
     * @return      The value.
     * @throws   JSONException If the key is not found or if the value cannot
     *  be converted to a number.
     */
    public long getLong(int index) throws JSONException {
        Object o = get(index);
        return o instanceof Number ?
                ((Number)o).longValue() : (long) getFloat(index);
    }


    /**
     * Get the string associated with an index.
     * @param index The index must be between 0 and length() - 1.
     * @return      A string value.
     * @throws JSONException If there is no value for the index.
     */
    public String getString(int index) throws JSONException {
        return get(index).toString();
    }

    /**
     * Make a string from the contents of this JSONArray. The
     * <code>separator</code> string is inserted between each element.
     * Warning: This method assumes that the data structure is acyclical.
     * @param separator A string that will be inserted between the elements.
     * @return a string.
     * @throws JSONException If the array contains an invalid number.
     */
    private String join(String separator) throws JSONException {
        int len = length();
        StringBuilder sb = new StringBuilder(len * 20);

        for (int i = 0; i < len; i += 1) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(JSONObject.valueToString(this.myArrayList.get(i)));
        }
        return sb.toString();
    }

    /**
     * Get the number of elements in the JSONArray, included nulls.
     *
     * @return The length (or size).
     */
    protected int length() {
        return this.myArrayList.size();
    }

    /**
     * Get the optional object value associated with an index.
     * @param index The index must be between 0 and length() - 1.
     * @return      An object value, or null if there is no
     *              object at that index.
     */
    private Object opt(int index) {
        return (index < 0 || index >= length()) ?
            null : this.myArrayList.get(index);
    }

    /**
     * Put a value in the JSONArray, where the value will be a
     * JSONArray which is produced from a Collection.
     * @param value A Collection value.
     * @return      this.
     */
    public JSONArray put(Collection<Object> value) {
        put(new JSONArray(value));
        return this;
    }

    /**
     * Put a value in the JSONArray, where the value will be a
     * JSONObject which is produced from a Map.
     * @param value A Map value.
     * @return      this.
     */
    public JSONArray put(Map<String, Object> value) {
        put(new JSONObject(value));
        return this;
    }


    /**
     * Append an object value. This increases the array's length by one.
     * @param value An object value.  The value should be a
     *  Boolean, Double, Integer, JSONArray, JSONObject, Long, or String, or the
     *  JSONObject.NULL object.
     * @return this.
     */
    protected JSONArray put(Object value) {
        this.myArrayList.add(value);
        return this;
    }

    /**
     * Put a value in the JSONArray, where the value will be a
     * JSONArray which is produced from a Collection.
     * @param index The subscript.
     * @param value A Collection value.
     * @return      this.
     * @throws JSONException If the index is negative or if the value is
     * not finite.
     */
    public JSONArray put(int index, Collection<Object> value) throws JSONException {
        put(index, new JSONArray(value));
        return this;
    }

    /**
     * Put a value in the JSONArray, where the value will be a
     * JSONObject which is produced from a Map.
     * @param index The subscript.
     * @param value The Map value.
     * @return      this.
     * @throws JSONException If the index is negative or if the the value is
     *  an invalid number.
     */
    public JSONArray put(int index, Map<String, Object> value) throws JSONException {
        put(index, new JSONObject(value));
        return this;
    }


    /**
     * Put or replace an object value in the JSONArray. If the index is greater
     *  than the length of the JSONArray, then null elements will be added as
     *  necessary to pad it out.
     * @param index The subscript.
     * @param value The value to put into the array. The value should be a
     *  Boolean, Double, Integer, JSONArray, JSONObject, Long, or String, or the
     *  JSONObject.NULL object.
     * @return this.
     * @throws JSONException If the index is negative or if the the value is
     *  an invalid number.
     */
    private JSONArray put(int index, Object value) throws JSONException {
        JSONObject.testValidity(value);
        if (index < 0) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        if (index < length()) {
            this.myArrayList.set(index, value);
        } else {
            while (index != length()) {
                put(JSONObject.NULL);
            }
            put(value);
        }
        return this;
    }

    /**
     * Make a JSON text of this JSONArray. For compactness, no
     * unnecessary whitespace is added. If it is not possible to produce a
     * syntactically correct JSON text then null will be returned instead. This
     * could occur if the array contains an invalid number.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a printable, displayable, transmittable
     *  representation of the array.
     */
    @Override
    public String toString() {
        try {
            return '[' + join(",") + ']';
        } catch (final Exception e) {
            return null;
        }
    }
    
    /**
     * Make a prettyprinted JSON text of this JSONArray.
     * Warning: This method assumes that the data structure is acyclical.
     * @param indentFactor The number of spaces to add to each level of
     *  indentation.
     * @param indent The indention of the top level.
     * @return a printable, displayable, transmittable
     *  representation of the array.
     * @throws JSONException
     */
    String toString(int indentFactor, int indent) throws JSONException {
        int len = length();
        if (len == 0) {
            return "[]";
        }
        int i;
        StringBuilder sb = new StringBuilder("[");
        if (len == 1) {
            sb.append(JSONObject.valueToString(this.myArrayList.get(0),
                    indentFactor, indent));
        } else {
            int newindent = indent + indentFactor;
            sb.append('\n');
            for (i = 0; i < len; i += 1) {
                if (i > 0) {
                    sb.append(",\n");
                }
                for (int j = 0; j < newindent; j += 1) {
                    sb.append(' ');
                }
                sb.append(JSONObject.valueToString(this.myArrayList.get(i),
                        indentFactor, newindent));
            }
            sb.append('\n');
            for (i = 0; i < indent; i += 1) {
                sb.append(' ');
            }
        }
        sb.append(']');
        return sb.toString();
    }


    /**
     * Write the contents of the JSONArray as JSON text to a writer.
     * For compactness, no whitespace is added.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return The writer.
     * @throws JSONException
     */
    protected Writer write(Writer writer) throws JSONException {
        try {
            boolean b = false;
            int     len = length();

            writer.write('[');

            for (int i = 0; i < len; i += 1) {
                if (b) {
                    writer.write(',');
                }
                Object v = this.myArrayList.get(i);
                if (v instanceof JSONObject) {
                    ((JSONObject)v).write(writer);
                } else if (v instanceof JSONArray) {
                    ((JSONArray)v).write(writer);
                } else {
                    writer.write(JSONObject.valueToString(v));
                }
                b = true;
            }
            writer.write(']');
            return writer;
        } catch (final IOException e) {
           throw new JSONException(e);
        }
    }
}