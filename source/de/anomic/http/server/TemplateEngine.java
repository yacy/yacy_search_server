//TemplateEngine.java 
//-------------------------------------
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//last major change: 16.01.2005

//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$

//extended for multi- and alternatives-templates by Alexander Schier

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

//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.

//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notice above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.http.server;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;


/**
 * A template engine, which substitutes patterns in strings<br>
 *
 * The template engine supports four types of templates:<br>
 * <ol>
 * <li>Normal templates: the template will be replaced by a string.</li>
 * <li>Multi templates: the template will be used more than one time.<br>
 * i.e. for lists</li>
 * <li>3. Alternatives: the program chooses one of multiple alternatives.</li>
 * <li>Includes: another file with templates will be included.</li>
 * </ol>
 *
 * All these templates can be used recursivly.<p>
 * <b>HTML-Example</b><br>
 * <pre>
 * &lt;html&gt;&lt;head&gt;&lt;/head&gt;&lt;body&gt;
 * #{times}#
 * Good #(daytime)#morning::evening#(/daytime)#, #[name]#!(#[num]#. Greeting)&lt;br&gt;
 * #{/times}#
 * &lt;/body&gt;&lt;/html&gt;
 * </pre>
 * <p>
 * The corresponding Hashtable to use this Template:<br>
 * <b>Java Example</b><br>
 * <pre>
 * Hashtable pattern;
 * pattern.put("times", 10); //10 greetings
 * for(int i=0;i<=9;i++){
 * 	pattern.put("times_"+i+"_daytime", 1); //index: 1, second Entry, evening
 * 	pattern.put("times_"+i+"_name", "John Connor");
 * 	pattern.put("times_"+i+"_num", (i+1));
 * }
 * </pre>
 * <p>
 * <b>Recursion</b><br>
 * If you use recursive templates, the templates will be used from
 * the external to the internal templates.
 * In our Example, the Template in #{times}##{/times}# will be repeated ten times.<br>
 * Then the inner Templates will be applied.
 * <p>
 * The inner templates have a prefix, so they may have the same name as a template on another level,
 * or templates which are in another recursive template.<br>
 * <b>The Prefixes:</b>
 * <ul>
 * <li>Multi templates: multitemplatename_index_</li>
 * <li>Alterantives: alternativename_</li>
 * </ul>
 * So the Names in the Hashtable are:
 * <ul>
 * <li>Multi templates: multitemplatename_index_templatename</li>
 * <li>Alterantives: alternativename_templatename</li>
 * </ul>
 * <i>#(alternative)#::#{repeat}##[test]##{/repeat}##(/alternative)#</i><br>
 * would be adressed as "alternative_repeat_"+number+"_test"
 */
public final class TemplateEngine {

    public final static byte hashChar = (byte)'#';
    public final static byte[] slashChar = {(byte)'/'};
    public final static byte pcChar  = (byte)'%';
    public final static byte[] dpdpa = "::".getBytes();

    public final static byte lbr  = (byte)'[';
    public final static byte rbr  = (byte)']';
    public final static byte[] pOpen  = {hashChar, lbr};
    public final static byte[] pClose = {rbr, hashChar};

    public final static byte lcbr  = (byte)'{';
    public final static byte rcbr  = (byte)'}';
    public final static byte[] mOpen  = {hashChar, lcbr};
    public final static byte[] mClose = {rcbr, hashChar};

    public final static byte lrbr  = (byte)'(';
    public final static byte rrbr  = (byte)')';
    public final static byte[] aOpen  = {hashChar, lrbr};
    public final static byte[] aClose = {rrbr, hashChar};

    public final static byte[] iOpen  = {hashChar, pcChar};
    public final static byte[] iClose = {pcChar, hashChar};
    
    public final static byte[] ul = "_".getBytes();
    
    /**
     * transfer until a specified pattern is found; everything but the pattern is transfered so far
     * the function returns true, if the pattern is found
     */
    private final static boolean transferUntil(final PushbackInputStream i, final OutputStream o, final byte[] pattern) throws IOException {
        int b, bb;
        boolean equal;
        while ((b = i.read()) > 0) {
            if ((b & 0xFF) == pattern[0]) {
                // read the whole pattern
                equal = true;
                lo: for (int n = 1; n < pattern.length; n++) {
                    if (((bb = i.read()) & 0xFF) != pattern[n]) {
                        // push back all
                        i.unread(bb);
                        equal = false;
                        for (int nn = n - 1; nn > 0; nn--) i.unread(pattern[nn]);
                        break lo;
                    }
                }
                if (equal) return true;
            }
            o.write(b);
        }
        return false;
    }
    
    private final static boolean transferUntil(final PushbackInputStream i, final OutputStream o, final byte p) throws IOException {
        int b;
        while ((b = i.read()) > 0) {
            if ((b & 0xFF) == p) return true;
            o.write(b);
        }
        return false;
    }

    public final static void writeTemplate(final InputStream in, final OutputStream out, final Map<String, String> pattern, final byte[] dflt) throws IOException {
        if (pattern == null) {
            FileUtils.copy(in, out);
        } else {
            writeTemplate(in, out, pattern, dflt, new byte[0]);
        }
    }

    /**
     * Reads a input stream, and writes the data with replaced templates on a output stream
     */
    private final static byte[] writeTemplate(final InputStream in, final OutputStream out, final Map<String, String> pattern, final byte[] dflt, final byte[] prefix) throws IOException {
        final PushbackInputStream pis = new PushbackInputStream(in, 100);
        ByteArrayOutputStream keyStream = new ByteArrayOutputStream(512);
        byte[] key;
        byte[] multi_key;
        byte[] replacement;
        int bb;
        final ByteBuffer structure = new ByteBuffer();
        while (transferUntil(pis, out, hashChar)) {
            bb = pis.read();
            keyStream.reset();
            
            // #{
            if ((bb & 0xFF) == lcbr) { //multi
                if (transferUntil(pis, keyStream, mClose)) { //close tag
                    //multi_key =  "_" + keyStream.toString(); //for _Key
                    bb = pis.read();
                    if ((bb & 0xFF) != 10){ //kill newline
                        pis.unread(bb);
                    }
                    multi_key = keyStream.toByteArray(); //IMPORTANT: no prefix here
                    keyStream.reset(); //reset stream

                    //this needs multi_key without prefix
                    if (transferUntil(pis, keyStream, appendBytes(mOpen, slashChar, multi_key, mClose))){
                        bb = pis.read();
                        if((bb & 0xFF) != 10){ //kill newline
                            pis.unread(bb);
                        }

                        final byte[] text=keyStream.toByteArray(); //text between #{key}# an #{/key}#
                        int num=0;
                        final String patternKey = getPatternKey(prefix, multi_key);
                        if(pattern.containsKey(patternKey) && pattern.get(patternKey) != null){
                            try{
                                num=Integer.parseInt(pattern.get(patternKey)); // Key contains the iteration number as string
                            }catch(final NumberFormatException e){
                                num=0;
                            }
                        }

                        structure.append('<')
                                 .append(multi_key)
                                 .append(UTF8.getBytes(" type=\"multi\" num=\""))
                                 .append(UTF8.getBytes(Integer.toString(num)))
                                 .append(UTF8.getBytes("\">\n"));
                        for(int i=0;i < num;i++) {
                            final PushbackInputStream pis2 = new PushbackInputStream(new ByteArrayInputStream(text));
                            //System.out.println("recursing with text(prefix="+ multi_key + "_" + i + "_" +"):"); //DEBUG
                            //System.out.println(text);
                            structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,multi_key,i)));
                        }//for
                        structure.append(UTF8.getBytes("</")).append(multi_key).append(UTF8.getBytes(">\n"));
                    } else {//transferUntil
                        Log.logSevere("TEMPLATE", "No Close Key found for #{"+UTF8.String(multi_key)+"}#"); //prefix here?
                    }
                }
                
            // #(
            } else if ((bb & 0xFF) == lrbr) { //alternative
                int others=0;
                final ByteBuffer text= new ByteBuffer();
                
                transferUntil(pis, keyStream, aClose);
                key = keyStream.toByteArray(); //Caution: Key does not contain prefix

                keyStream.reset(); //clear

                boolean byName=false;
                int whichPattern=0;
                byte[] patternName = new byte[0];
                final String patternKey = getPatternKey(prefix, key);
                if(pattern.containsKey(patternKey) && pattern.get(patternKey) != null){
                    final String patternId=pattern.get(patternKey);
                    try{
                        whichPattern=Integer.parseInt(patternId); //index
                    }catch(final NumberFormatException e){
                        whichPattern=0;
                        byName=true;
                        patternName = UTF8.getBytes(patternId);
                    }
                }

                int currentPattern=0;
                boolean found=false;
                keyStream.reset(); //reset stream
                PushbackInputStream pis2;
                if (byName) {
                    //TODO: better Error Handling
                    transferUntil(pis, keyStream, appendBytes(UTF8.getBytes("%%"), patternName, null, null));
                    if(pis.available()==0){
                        Log.logSevere("TEMPLATE", "No such Template: %%" + UTF8.String(patternName));
                        return structure.getBytes();
                    }
                    keyStream.reset();
                    transferUntil(pis, keyStream, dpdpa);
                    pis2 = new PushbackInputStream(new ByteArrayInputStream(keyStream.toByteArray()));
                    structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,key)));
                    transferUntil(pis, keyStream, appendBytes(UTF8.getBytes("#(/"), key, UTF8.getBytes(")#"), null));
                    if(pis.available()==0){
                        Log.logSevere("TEMPLATE", "No Close Key found for #("+UTF8.String(key)+")# (by Name)");
                    }
                } else {
                    while(!found){
                        bb=pis.read(); // performance problem? trace always points to this line
                        if ((bb & 0xFF) == hashChar){
                            bb=pis.read();
                            if ((bb & 0xFF) == lrbr){
                                transferUntil(pis, keyStream, aClose);

                                //reached the end. output last string.                                
                                if (java.util.Arrays.equals(keyStream.toByteArray(),appendBytes(slashChar, key, null,null))) {
                                    pis2 = new PushbackInputStream(new ByteArrayInputStream(text.getBytes()));
                                    //this maybe the wrong, but its the last
                                    structure.append('<').append(key).append(UTF8.getBytes(" type=\"alternative\" which=\"")).append(UTF8.getBytes(Integer.toString(whichPattern))).append(UTF8.getBytes("\" found=\"0\">\n"));
                                    structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,key)));
                                    structure.append(UTF8.getBytes("</")).append(key).append(UTF8.getBytes(">\n"));
                                    found=true;
                                }else if(others >0 && keyStream.toString().startsWith("/")){ //close nested
                                    others--;
                                    text.append(aOpen).append(keyStream.toByteArray()).append(UTF8.getBytes(")#"));
                                } else { //nested
                                    others++;
                                    text.append(aOpen).append(keyStream.toByteArray()).append(UTF8.getBytes(")#"));
                                }
                                keyStream.reset(); //reset stream
                                continue;
                            } //is not #(
                            pis.unread(bb);//is processed in next loop
                            bb = (hashChar);//will be added to text this loop
                            //text += "#";
                        }else if ((bb & 0xFF) == ':' && others==0){//ignore :: in nested Expressions
                            bb=pis.read();
                            if ((bb & 0xFF) == ':'){
                                if(currentPattern == whichPattern){ //found the pattern
                                    pis2 = new PushbackInputStream(new ByteArrayInputStream(text.getBytes()));
                                    structure.append('<').append(key).append(UTF8.getBytes(" type=\"alternative\" which=\"")).append(UTF8.getBytes(Integer.toString(whichPattern))).append(UTF8.getBytes("\" found=\"0\">\n"));
                                    structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,key)));
                                    structure.append(UTF8.getBytes("</")).append(key).append(UTF8.getBytes(">\n"));

                                    transferUntil(pis, keyStream, appendBytes(UTF8.getBytes("#(/"),key,UTF8.getBytes(")#"),null));//to #(/key)#.

                                    found=true;
                                }
                                currentPattern++;
                                text.clear();
                                continue;
                            }
                            text.append(UTF8.getBytes(":"));
                        }
                        if(!found){
                            text.append((byte)bb);/*
                            if(pis.available()==0){
                                serverLog.logSevere("TEMPLATE", "No Close Key found for #("+UTF8.String(key)+")# (by Index)");
                                found=true;
                            }*/
                        }
                    }//while
                }//if(byName) (else branch)
                
            // #[
            } else if ((bb & 0xFF) == lbr) { //normal
                if (transferUntil(pis, keyStream, pClose)) {
                    // pattern detected, write replacement
                    key = keyStream.toByteArray();
                    final String patternKey = getPatternKey(prefix, key);
                    replacement = replacePattern(patternKey, pattern, dflt); //replace
                    structure.append(UTF8.getBytes("<")).append(key).append(UTF8.getBytes(" type=\"normal\">\n"));
                    structure.append(replacement);
                    structure.append(UTF8.getBytes("</")).append(key).append(UTF8.getBytes(">\n"));

                    FileUtils.copy(replacement, out);
                } else {
                    // inconsistency, simply finalize this
                    FileUtils.copy(pis, out);
                    return structure.getBytes();
                }
                
            // #%
            } else if ((bb & 0xFF) == pcChar) { //include
                final ByteBuffer include = new ByteBuffer();                
                keyStream.reset(); //reset stream
                if(transferUntil(pis, keyStream, iClose)){
                    byte[] filename = keyStream.toByteArray();
                    //if(filename.startsWith( Character.toString((char)lbr) ) && filename.endsWith( Character.toString((char)rbr) )){ //simple pattern for filename
                    if((filename[0] == lbr) && (filename[filename.length-1] == rbr)){ //simple pattern for filename
                        final byte[] newFilename = new byte[filename.length-2];
                        System.arraycopy(filename, 1, newFilename, 0, newFilename.length);
                        final String patternkey = getPatternKey(prefix, newFilename);
                        filename= replacePattern(patternkey, pattern, dflt);
                    }
                    if (filename.length > 0 && !java.util.Arrays.equals(filename, dflt)) {
                        BufferedReader br = null;
                        try{
                            //br = new BufferedReader(new InputStreamReader(new FileInputStream( filename ))); //Simple Include
                            br = new BufferedReader( new InputStreamReader(new FileInputStream( HTTPDFileHandler.getLocalizedFile(UTF8.String(filename))),"UTF-8") ); //YaCy (with Locales)
                            //Read the Include
                            String line = "";
                            while ((line = br.readLine()) != null) {
                                include.append(UTF8.getBytes(line)).append(UTF8.getBytes(de.anomic.server.serverCore.CRLF_STRING));
                            }
                        } catch (final IOException e) {
                            //file not found?                    
                            Log.logSevere("FILEHANDLER","Include Error with file " + UTF8.String(filename) + ": " + e.getMessage());
                        } finally {
                            if (br != null) try { br.close(); br=null; } catch (final Exception e) {}
                        }
                        final PushbackInputStream pis2 = new PushbackInputStream(new ByteArrayInputStream(include.getBytes()));
                        structure.append(UTF8.getBytes("<fileinclude file=\"")).append(filename).append(UTF8.getBytes(">\n"));
                        structure.append(writeTemplate(pis2, out, pattern, dflt, prefix));
                        structure.append(UTF8.getBytes("</fileinclude>\n"));
                    }
                }
                
            // # - no special character. This is simply a '#' without meaning
            } else { //no match, but a single hash (output # + bb)
                out.write(hashChar);
                out.write(bb);
            }
        }
        return structure.getBytes();
    }

    private final static byte[] replacePattern(final String key, final Map<String, String> pattern, final byte dflt[]) {
        byte[] replacement;
        Object value;
        if (pattern.containsKey(key)) {
            value = pattern.get(key);
            if (value instanceof byte[]) {
                replacement = (byte[]) value;
            } else if (value instanceof String) {
                //replacement = ((String) value).getBytes();
                replacement = UTF8.getBytes(((String) value));
            } else {
                //replacement = value.toString().getBytes();
                replacement = UTF8.getBytes(value.toString());
            }
        } else {
            replacement = dflt;
        }
        return replacement;
    }

    private final static byte[] newPrefix(final byte[] oldPrefix, final byte[] key) {
        final ByteBuffer newPrefix = new ByteBuffer(oldPrefix.length + key.length + 1);
        newPrefix.append(oldPrefix).append(key).append(ul);
        byte[] result = newPrefix.getBytes();
        try {
            newPrefix.close();
        } catch (IOException e) {
            Log.logException(e);
        }
        return result;
    }

    private final static byte[] newPrefix(final byte[] oldPrefix, final byte[] multi_key, final int i) {
        final ByteBuffer newPrefix = new ByteBuffer(oldPrefix.length + multi_key.length + 8);
        newPrefix.append(oldPrefix).append(multi_key).append(ul).append(UTF8.getBytes(Integer.toString(i))).append(ul);
        try {
            newPrefix.close();
        } catch (IOException e) {
            Log.logException(e);
        }
        return newPrefix.getBytes();
    }

    private final static String getPatternKey(final byte[] prefix, final byte[] key) {
        final ByteBuffer patternKey = new ByteBuffer(prefix.length + key.length);
        patternKey.append(prefix).append(key);
        try {
            return UTF8.String(patternKey.getBytes());
        } finally {
            try {
                patternKey.close();
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }
    
    private final static byte[] appendBytes(final byte[] b1, final byte[] b2, final byte[] b3, final byte[] b4) {
        final ByteBuffer byteArray = new ByteBuffer(b1.length + b2.length + (b3 == null ? 0 : b3.length) + (b4 == null ? 0 : b4.length));
        byteArray.append(b1).append(b2);
        if (b3 != null) byteArray.append(b3);
        if (b4 != null) byteArray.append(b4);
        final byte[] result = byteArray.getBytes();
        try {
            byteArray.close();
        } catch (IOException e) {
            Log.logException(e);
        }
        return result;
    }

    public static void main(final String[] args) {
        // arg1 = test input; arg2 = replacement for pattern 'test'; arg3 = default replacement
        try {
            final InputStream i = new ByteArrayInputStream(UTF8.getBytes(args[0]));
            final Map<String, String> h = new HashMap<String, String>();
            h.put("test", args[1]);
            writeTemplate(new PushbackInputStream(i, 100), System.out, h, UTF8.getBytes(args[2]));
            System.out.flush();
        } catch (final Exception e) {
            Log.logException(e);
        }
    }
}
