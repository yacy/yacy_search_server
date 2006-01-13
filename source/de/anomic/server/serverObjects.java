// serverObjects.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 05.06.2004
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
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

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
import java.sql.Date;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

public final class serverObjects extends Hashtable implements Cloneable {

    private static final long serialVersionUID = 1L;

	public serverObjects() {
        super();
    }
    
    public serverObjects(int initialCapacity) {
        super(initialCapacity);
    }
    
    public serverObjects(Map input) {
	super(input);
    }

    // new put takes also null values
    public Object put(Object key, Object value) {
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

    // byte[] variant
    public byte[] put(String key, byte[] value) {
	return (byte[]) this.put((Object) key, (Object) value);
    }
    
    // string variant
    public String put(String key, String value) {
	return (String) this.put((Object) key, (Object) value);
    }

    // long variant
    public long put(String key, long value) {
	String result = this.put(key, Long.toString(value));
	if (result == null) return 0; else try {
            return Long.parseLong(result);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // inc variant: for counters
    public long inc(String key) {
	String c = (String) super.get(key);
	if (c == null) c = "0";
	long l = Long.parseLong(c) + 1;
	super.put(key, Long.toString(l));
	return l;
    }

    // new get with default objects
    public Object get(String key, Object dflt) {
	Object result = super.get(key);
	if (result == null) return dflt; else return result;
    }

    // string variant
    public String get(String key, String dflt) {
	return (String) this.get(key, (Object) dflt);
    }

    public int getInt(String key, int dflt) {
	String s = (String) super.get(key);
        if (s == null) return dflt;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
    
    public long getLong(String key, long dflt) {
	String s = (String) super.get(key);
        if (s == null) return dflt;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
    
    // returns a set of all values where their key mappes the keyMapper
    public String[] getAll(String keyMapper) {
        // the keyMapper may contain regular expressions as defined in String.matches
        // this method is particulary useful when parsing the result of checkbox forms
        ArrayList v = new ArrayList();
	Enumeration e = keys();
	String key;
	while (e.hasMoreElements()) {
	    key = (String) e.nextElement();
            if (key.matches(keyMapper)) v.add(get(key));
	}
        // make a String[]
        String[] result = new String[v.size()];
        for (int i = 0; i < v.size(); i++) result[i] = (String) v.get(i);
        return result;
    }

    // put all elements of another hastable into the own table
    public void putAll(serverObjects add) {
	Enumeration e = add.keys();
	Object k;
	while (e.hasMoreElements()) {
	    k = e.nextElement();
	    put(k, add.get(k));
	}
    }

    // convenience methods for storing and loading to a file system
    public void store(File f) throws IOException {
        BufferedOutputStream fos = null;
        try {
            fos = new BufferedOutputStream(new FileOutputStream(f));
            Enumeration e = keys();
            String key, value;
            while (e.hasMoreElements()) {
                key = (String) e.nextElement();
                value = ((String) get(key)).replaceAll("\n", "\\\\n");  
                fos.write((key + "=" + value + "\r\n").getBytes());
            }
        } finally {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (Exception e){}
            }
        }
    }

    public Object clone() {
	return super.clone();
    }

    /*
     * Patch BEGIN:
     * Name: Header Property Patch
     * Date: Fri. 13.01.2006
     * Description: Makes possible to send header properties such as coockies back to the client.
     * Part 1 of 5
     * Questions: sergej.z@list.ru
     */
    /**
     * Holds header properties
     */
    //Since properties such as coockies can be multiple, we cannot use HashMap here. We have to use Vector.
    private Vector requestProperty=new Vector();
    /**
     *
     * Implementation of Map.Entry. Structure that hold two values - exactly what we need!
     */
    class Entry implements Map.Entry
    {
        private Object Key;
        private Object Value;
        Entry(Object Key,String Value){this.Key=Key;this.Value=Value;}
        public Object getKey() {return Key;}
        public Object getValue() {return Value;}
        public Object setValue(Object Value) {return(this.Value=Value);}
    }
    /**
     * Set a header property <b>name</b> with <b>value</b>
     * @param name : name of the property. Ex. Location
     * @param value : value of the property
     *
     * We can achieve redirection using property Location
     * setRequestProperty("Location", "http://www.yacy.net");
     * Coockies can be convinently defined with setCookie method
     */
    public void setRequestProperty(String name, String value)
    {
         /*
         * TODO: Maybe we should check here if the property name is in RFC2616
         * And check for the validity of the value as well...
         * */ 
        requestProperty.add(new Entry(name,value));
       
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Coockie name
     * @param value: Coockie value
     * @param expires: when should this coockie be autmatically deleted. If <b>null</b> - coockie will stay forever
     * @param path: Path the coockie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain: Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     * @param secure: If true coockie will be send only over safe connection such as https
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCoockie(String name, String value, String expires, String path, String domain, boolean secure)
    {
         /*
         * TODO:Here every value can be validated for correctness if needed
         * For example semicolon should be not in any of the values
         * However an exception in this case would be an overhead IMHO.
         */
        String coockieString=name+"="+value+";";
        if(expires!=null)
            coockieString+=" expires="+expires+";";
        if(path!=null)
            coockieString+=" path="+path+";";
        if(domain!=null)
            coockieString+=" domain="+domain+";";
        if(secure)
            coockieString+=" secure;";
        requestProperty.add(new Entry("Set-Cookie",coockieString));
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Coockie name
     * @param value: Coockie value
     * @param expires: when should this coockie be autmatically deleted. If <b>null</b> - coockie will stay forever
     * @param path: Path the coockie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain: Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     *
     * Note: this coockie will be sent over each connection independend if it is safe connection or not.
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCoockie(String name, String value, String expires, String path, String domain)
    {
        setCoockie( name,  value,  expires,  path,  domain, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Coockie name
     * @param value: Coockie value
     * @param expires: when should this coockie be autmatically deleted. If <b>null</b> - coockie will stay forever
     * @param path: Path the coockie belongs to. Default - "/". Can be <b>null</b>.
     *
     * Note: this coockie will be sent over each connection independend if it is safe connection or not.
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCoockie(String name, String value, String expires, String path)
    {
        setCoockie( name,  value,  expires,  path,  null, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Coockie name
     * @param value: Coockie value
     * @param expires: when should this coockie be autmatically deleted. If <b>null</b> - coockie will stay forever
     *
     * Note: this coockie will be sent over each connection independend if it is safe connection or not.
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCoockie(String name, String value, String expires)
    {
        setCoockie( name,  value,  expires,  null,  null, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Coockie name
     * @param value: Coockie value
     *
     * Note: this coockie will be sent over each connection independend if it is safe connection or not. This coockie never expires
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCoockie(String name, String value )
    {
        setCoockie( name,  value,  null,  null,  null, false);
    }
    /**
     * Returns an iterator within all properties can be reached.
     * Is used mainly by httpd.
     * @return iterator to read all request properties.
     *
     * Example:
     *
     * Iterator it=serverObjects.getRequestProperties();
     * while(it.hasNext())
     * {
     *  java.util.Map.Entry e=(java.util.Map.Entry)it.next();
     *  String propertyName=e.getKey();
     *  String propertyValue=e.getValue();
     * }
     */
    public Iterator getRequestProperties()
    {
        return requestProperty.iterator();
    }
    /*
     * Patch END:
     * Name: Header Property Patch
     */ 
}