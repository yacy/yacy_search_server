//httpTemplate.java 
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

package de.anomic.http;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

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
public final class httpTemplate {

    public  static final byte hash = (byte)'#';
    private static final byte[] hasha = {hash};

    private static final byte dp = (byte)':';
    public  static final byte[] dpdpa = {dp, dp};

    private static final byte lbr  = (byte)'[';
    private static final byte rbr  = (byte)']';
    private static final byte[] pOpen  = {hash, lbr};
    private static final byte[] pClose = {rbr, hash};

    private static final byte lcbr  = (byte)'{';
    private static final byte rcbr  = (byte)'}';
    private static final byte[] mOpen  = {hash, lcbr};
    private static final byte[] mClose = {rcbr, hash};

    private static final byte lrbr  = (byte)'(';
    private static final byte rrbr  = (byte)')';
    private static final byte[] aOpen  = {hash, lrbr};
    private static final byte[] aClose = {rrbr, hash};

    private static final byte ps  = (byte)'%';
    private static final byte[] iOpen  = {hash, ps};
    private static final byte[] iClose = {ps, hash};

    public static final byte[] slash = {(byte)'/'};
    
    public static final Object[] meta_quotation = new Object[] {
        new Object[] {pOpen, pClose},
        new Object[] {mOpen, mClose},
        new Object[] {aOpen, aClose},
        new Object[] {iOpen, iClose}
    };

    public static serverByteBuffer[] splitQuotations(serverByteBuffer text) {
        List<serverByteBuffer> l = splitQuotation(text, 0);
        serverByteBuffer[] sbbs = new serverByteBuffer[l.size()];
        for (int i = 0; i < l.size(); i++) sbbs[i] = l.get(i);
        return sbbs;
    }

    public static List<serverByteBuffer> splitQuotation(serverByteBuffer text, int qoff) {
        ArrayList<serverByteBuffer> l = new ArrayList<serverByteBuffer>();
        if (qoff >= meta_quotation.length) {
            if (text.length() > 0) l.add(text);
            return l;
        }
        int p = -1, q;
        byte[] left = (byte[]) ((Object[]) meta_quotation[qoff])[0];
        byte[] right = (byte[]) ((Object[]) meta_quotation[qoff])[1];
        qoff++;
        while ((text.length() > 0) && ((p = text.indexOf(left)) >= 0)) {
            q = text.indexOf(right, p + 1);
            if (q >= 0) {
                // found a pattern
                l.addAll(splitQuotation(new serverByteBuffer(text.getBytes(0, p)), qoff));
                l.add(new serverByteBuffer(text.getBytes(p, q + right.length - p)));
                text = new serverByteBuffer(text.getBytes(q + right.length));
            } else {
                // found only pattern start, no closing parantesis (a syntax error that is silently accepted here)
                l.addAll(splitQuotation(new serverByteBuffer(text.getBytes(0, p)), qoff));
                l.addAll(splitQuotation(new serverByteBuffer(text.getBytes(p)), qoff));
                text.clear();
            }
        }

        // find double-points
        while ((text.length() > 0) && ((p = text.indexOf(dpdpa)) >= 0)) {
            l.addAll(splitQuotation(new serverByteBuffer(text.getBytes(0, p)), qoff));
            l.add(new serverByteBuffer(dpdpa));
            l.addAll(splitQuotation(new serverByteBuffer(text.getBytes(p + 2)), qoff));
            text.clear();
        }

        // add remaining
        if (text.length() > 0) l.addAll(splitQuotation(text, qoff));
        return l;
    }

    /**
     * transfer until a specified pattern is found; everything but the pattern is transfered so far
     * the function returns true, if the pattern is found
     */
    private static boolean transferUntil(PushbackInputStream i, OutputStream o, byte[] pattern) throws IOException {
        int b, bb;
        boolean equal;
        while ((b = i.read()) > 0) {
            if ((b & 0xFF) == pattern[0]) {
                // read the whole pattern
                equal = true;
                for (int n = 1; n < pattern.length; n++) {
                    if (((bb = i.read()) & 0xFF) != pattern[n]) {
                        // push back all
                        i.unread(bb);
                        equal = false;
                        for (int nn = n - 1; nn > 0; nn--) i.unread(pattern[nn]);
                        break;
                    }
                }
                if (equal) return true;
            }
            o.write(b);
        }
        return false;
    }

    public static void writeTemplate(InputStream in, OutputStream out, HashMap<String, String> pattern, byte[] dflt) throws IOException {
        writeTemplate(in, out, pattern, dflt, new byte[0]);
    }

    /**
     * Reads a input stream, and writes the data with replaced templates on a output stream
     */
    public static byte[] writeTemplate(InputStream in, OutputStream out, HashMap<String, String> pattern, byte[] dflt, byte[] prefix) throws IOException {
        PushbackInputStream pis = new PushbackInputStream(in, 100);
        ByteArrayOutputStream keyStream;
        byte[] key;
        byte[] multi_key;
        byte[] replacement;
        int bb;
        serverByteBuffer structure=new serverByteBuffer();

        while (transferUntil(pis, out, hasha)) {
            bb = pis.read();
            keyStream = new ByteArrayOutputStream();

            if( (bb & 0xFF) == lcbr ){ //multi
                if( transferUntil(pis, keyStream, mClose) ){ //close tag
                    //multi_key =  "_" + keyStream.toString(); //for _Key
                    bb = pis.read();
                    if( (bb & 0xFF) != 10){ //kill newline
                        pis.unread(bb);
                    }
                    multi_key = keyStream.toByteArray(); //IMPORTANT: no prefix here
                    keyStream = new ByteArrayOutputStream(); //reset stream

                    /* DEBUG - print key + value
			try{
				System.out.println("Key: "+prefix+multi_key+ "; Value: "+pattern.get(prefix+multi_key));
			}catch(NullPointerException e){
				System.out.println("Key: "+prefix+multi_key);
			}
                     */

                    //this needs multi_key without prefix
                    if( transferUntil(pis, keyStream, appendBytes(mOpen,slash,multi_key,mClose))){
                        bb = pis.read();
                        if((bb & 0xFF) != 10){ //kill newline
                            pis.unread(bb);
                        }

                        byte[] text=keyStream.toByteArray(); //text between #{key}# an #{/key}#
                        int num=0;
                        String patternKey = getPatternKey(prefix, multi_key);
                        if(pattern.containsKey(patternKey) && pattern.get(patternKey) != null){
                            try{
                                num=Integer.parseInt(pattern.get(patternKey)); // Key contains the iteration number as string
                            }catch(NumberFormatException e){
                                num=0;
                            }
                            //System.out.println(multi_key + ": " + num); //DEBUG
                        }

                        //Enumeration enx = pattern.keys(); while (enx.hasMoreElements()) System.out.println("KEY=" + enx.nextElement()); // DEBUG
                        structure.append("<".getBytes("UTF-8"))
                                 .append(multi_key)
                                 .append(" type=\"multi\" num=\"".getBytes("UTF-8"))
                                 .append(Integer.toString(num).getBytes("UTF-8"))
                                 .append("\">\n".getBytes("UTF-8"));
                        for(int i=0;i < num;i++ ){
                            PushbackInputStream pis2 = new PushbackInputStream(new ByteArrayInputStream(text));
                            //System.out.println("recursing with text(prefix="+ multi_key + "_" + i + "_" +"):"); //DEBUG
                            //System.out.println(text);
                            structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,multi_key,i)));
                        }//for
                        structure.append("</".getBytes("UTF-8")).append(multi_key).append(">\n".getBytes("UTF-8"));
                    }else{//transferUntil
                        serverLog.logSevere("TEMPLATE", "No Close Key found for #{"+new String(multi_key)+"}#"); //prefix here?
                    }
                }
            }else if( (bb & 0xFF) == lrbr ){ //alternatives
                int others=0;
                serverByteBuffer text= new serverByteBuffer();
                PushbackInputStream pis2;

                transferUntil(pis, keyStream, aClose);
                key = keyStream.toByteArray(); //Caution: Key does not contain prefix

                /* DEBUG - print key + value
		try{
			System.out.println("Key: "+prefix+key+ "; Value: "+pattern.get(prefix+key));
		}catch(NullPointerException e){
			System.out.println("Key: "+prefix+key);
		}
                 */

                keyStream=new ByteArrayOutputStream(); //clear

                boolean byName=false;
                int whichPattern=0;
                byte[] patternName = new byte[0];
                String patternKey = getPatternKey(prefix, key);
                if(pattern.containsKey(patternKey) && pattern.get(patternKey) != null){
                    String patternId=pattern.get(patternKey);
                    try{
                        whichPattern=Integer.parseInt(patternId); //index
                    }catch(NumberFormatException e){
                        whichPattern=0;
                        byName=true;
                        patternName=patternId.getBytes("UTF-8");
                    }
                }

                int currentPattern=0;
                boolean found=false;
                keyStream = new ByteArrayOutputStream(); //reset stream
                if(byName){
                    //TODO: better Error Handling
                    transferUntil(pis, keyStream,appendBytes("%%".getBytes("UTF-8"),patternName,null,null));
                    if(pis.available()==0){
                        serverLog.logSevere("TEMPLATE", "No such Template: %%"+patternName);
                        return structure.getBytes();
                    }
                    keyStream=new ByteArrayOutputStream();
                    transferUntil(pis, keyStream, "::".getBytes());
                    pis2 = new PushbackInputStream(new ByteArrayInputStream(keyStream.toByteArray()));
                    structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,key)));
                    transferUntil(pis, keyStream, appendBytes("#(/".getBytes("UTF-8"),key,")#".getBytes("UTF-8"),null));
                    if(pis.available()==0){
                        serverLog.logSevere("TEMPLATE", "No Close Key found for #("+new String(key)+")# (by Name)");
                    }
                }else{
                    while(!found){
                        bb=pis.read();
                        if( (bb & 0xFF) == hash){
                            bb=pis.read();
                            if( (bb & 0xFF) == lrbr){
                                transferUntil(pis, keyStream, aClose);

                                //reached the end. output last string.                                
                                if (java.util.Arrays.equals(keyStream.toByteArray(),appendBytes(slash, key, null,null))) {
                                    pis2 = new PushbackInputStream(new ByteArrayInputStream(text.getBytes()));
                                    //this maybe the wrong, but its the last
                                    structure.append("<".getBytes("UTF-8")).append(key).append(" type=\"alternative\" which=\"".getBytes("UTF-8")).append(Integer.toString(whichPattern).getBytes("UTF-8")).append("\" found=\"0\">\n".getBytes("UTF-8"));
                                    structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,key)));
                                    structure.append("</".getBytes("UTF-8")).append(key).append(">\n".getBytes("UTF-8"));
                                    found=true;
                                }else if(others >0 && keyStream.toString().startsWith("/")){ //close nested
                                    others--;
                                    text.append("#(".getBytes("UTF-8")).append(keyStream.toByteArray()).append(")#".getBytes("UTF-8"));
                                }else{ //nested
                                    others++;
                                    text.append("#(".getBytes("UTF-8")).append(keyStream.toByteArray()).append(")#".getBytes("UTF-8"));
                                }
                                keyStream = new ByteArrayOutputStream(); //reset stream
                                continue;
                            }else{ //is not #(
                                pis.unread(bb);//is processed in next loop
                                bb = (hash);//will be added to text this loop
                                //text += "#";
                            }
                        }else if( (bb & 0xFF) == ':' && others==0){//ignore :: in nested Expressions
                            bb=pis.read();
                            if( (bb & 0xFF) == ':'){
                                if(currentPattern == whichPattern){ //found the pattern
                                    pis2 = new PushbackInputStream(new ByteArrayInputStream(text.getBytes()));
                                    structure.append("<".getBytes("UTF-8")).append(key).append(" type=\"alternative\" which=\"".getBytes("UTF-8")).append(Integer.toString(whichPattern).getBytes("UTF-8")).append("\" found=\"0\">\n".getBytes("UTF-8"));
                                    structure.append(writeTemplate(pis2, out, pattern, dflt, newPrefix(prefix,key)));
                                    structure.append("</".getBytes("UTF-8")).append(key).append(">\n".getBytes("UTF-8"));

                                    transferUntil(pis, keyStream, appendBytes("#(/".getBytes("UTF-8"),key,")#".getBytes("UTF-8"),null));//to #(/key)#.

                                    found=true;
                                }
                                currentPattern++;
                                text.clear();
                                continue;
                            }else{
                                text.append(":".getBytes("UTF-8"));
                            }
                        }
                        if(!found){
                            text.append((byte)bb);
                            if(pis.available()==0){
                                serverLog.logSevere("TEMPLATE", "No Close Key found for #("+new String(key)+")# (by Index)");
                                found=true;
                            }
                        }
                    }//while
                }//if(byName) (else branch)
            }else if( (bb & 0xFF) == lbr ){ //normal
                if (transferUntil(pis, keyStream, pClose)) {
                    // pattern detected, write replacement
                    key = keyStream.toByteArray();
                    String patternKey = getPatternKey(prefix, key);
                    replacement = replacePattern(patternKey, pattern, dflt); //replace
                    structure.append("<".getBytes("UTF-8")).append(key).append(" type=\"normal\">\n".getBytes("UTF-8"));
                    structure.append(replacement);
                    structure.append("</".getBytes("UTF-8")).append(key).append(">\n".getBytes("UTF-8"));

                    /* DEBUG
			try{
				System.out.println("Key: "+key+ "; Value: "+pattern.get(key));
			}catch(NullPointerException e){
				System.out.println("Key: "+key);
			}
                     */

                    serverFileUtils.copy(replacement, out);
                } else {
                    // inconsistency, simply finalize this
                    serverFileUtils.copy(pis, out);
                    return structure.getBytes();
                }
            }else if( (bb & 0xFF) == ps){ //include
                serverByteBuffer include = new serverByteBuffer();                
                keyStream = new ByteArrayOutputStream(); //reset stream
                if(transferUntil(pis, keyStream, iClose)){
                    byte[] filename = keyStream.toByteArray();
                    //if(filename.startsWith( Character.toString((char)lbr) ) && filename.endsWith( Character.toString((char)rbr) )){ //simple pattern for filename
                    if((filename[0] == lbr) && (filename[filename.length-1] == rbr)){ //simple pattern for filename
                        byte[] newFilename = new byte[filename.length-2];
                        System.arraycopy(filename, 1, newFilename, 0, newFilename.length);
                        String patternkey = getPatternKey(prefix, newFilename);
                        filename= replacePattern(patternkey, pattern, dflt);
                    }
                    if (filename.length > 0 && !java.util.Arrays.equals(filename, dflt)) {
                        BufferedReader br = null;
                        try{
                            //br = new BufferedReader(new InputStreamReader(new FileInputStream( filename ))); //Simple Include
                            br = new BufferedReader( new InputStreamReader(new FileInputStream( httpdFileHandler.getLocalizedFile(new String(filename,"UTF-8"))),"UTF-8") ); //YaCy (with Locales)
                            //Read the Include
                            String line = "";
                            while( (line = br.readLine()) != null ){
                                include.append(line.getBytes("UTF-8")).append(de.anomic.server.serverCore.CRLF_STRING.getBytes("UTF-8"));
                            }
                        } catch (IOException e) {
                            //file not found?                    
                            serverLog.logSevere("FILEHANDLER","Include Error with file " + new String(filename, "UTF-8") + ": " + e.getMessage());
                        } finally {
                            if (br != null) try { br.close(); br=null; } catch (Exception e) {}
                        }
                        PushbackInputStream pis2 = new PushbackInputStream(new ByteArrayInputStream(include.getBytes()));
                        structure.append("<fileinclude file=\"".getBytes("UTF-8")).append(filename).append(">\n".getBytes("UTF-8"));
                        structure.append(writeTemplate(pis2, out, pattern, dflt, prefix));
                        structure.append("</fileinclude>\n".getBytes("UTF-8"));
                    }
                }
            }else{ //no match, but a single hash (output # + bb)
                byte[] tmp=new byte[2];
                tmp[0]=hash;
                tmp[1]=(byte)bb;
                serverFileUtils.copy(tmp, out);
            }
        }
        //System.out.println(structure.toString()); //DEBUG
        return structure.getBytes();
    }

    public static byte[] replacePattern(String key, HashMap<String, String> pattern, byte dflt[]) {
        byte[] replacement;
        Object value;
        if (pattern.containsKey(key)) {
            value = pattern.get(key);
            try {
                if (value instanceof byte[]) {
                    replacement = (byte[]) value;
                } else if (value instanceof String) {
                    //replacement = ((String) value).getBytes();
                    replacement = ((String) value).getBytes("UTF-8");
                } else {
                    //replacement = value.toString().getBytes();
                    replacement = value.toString().getBytes("UTF-8");
                }
            } catch (UnsupportedEncodingException e) {
                replacement = dflt;
            }
        } else {
            replacement = dflt;
        }
        return replacement;
    }

    public static void main(String[] args) {
        // arg1 = test input; arg2 = replacement for pattern 'test'; arg3 = default replacement
        try {
            InputStream i = new ByteArrayInputStream(args[0].getBytes());
            HashMap<String, String> h = new HashMap<String, String>();
            h.put("test", args[1]);
            writeTemplate(new PushbackInputStream(i, 100), System.out, h, args[2].getBytes());
            System.out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /*
     * loads all Files from path into a filename->content HashMap
     */
    public static HashMap<String, String> loadTemplates(File path) {
        // reads all templates from a path
        // we use only the folder from the given file path
        HashMap<String, String> result = new HashMap<String, String>();
        if (path == null) return result;
        if (!(path.isDirectory())) path = path.getParentFile();
        if ((path == null) || (!(path.isDirectory()))) return result;
        String[] templates = path.list();
        for (int i = 0; i < templates.length; i++) {
            if (templates[i].endsWith(".template")) 
                try {
                    //System.out.println("TEMPLATE " + templates[i].substring(0, templates[i].length() - 9) + ": " + new String(buf, 0, c));
                    result.put(templates[i].substring(0, templates[i].length() - 9),
                            new String(serverFileUtils.read(new File(path, templates[i]))));
                } catch (Exception e) {}
        }
        return result;
    }

    public static byte[] newPrefix(byte[] oldPrefix, byte[] key) {
        serverByteBuffer newPrefix = new serverByteBuffer();
        newPrefix.append(oldPrefix)
        .append(key)
        .append("_".getBytes());
        return newPrefix.getBytes();
    }

    public static byte[] newPrefix(byte[] oldPrefix, byte[] multi_key, int i) {
        serverByteBuffer newPrefix = new serverByteBuffer();
        try {        
            newPrefix.append(oldPrefix)
            .append(multi_key)
            .append("_".getBytes())
            .append(Integer.toString(i).getBytes("UTF-8"))
            .append("_".getBytes());

        } catch (UnsupportedEncodingException e) {}
        return newPrefix.getBytes();
    }

    public static String getPatternKey(byte[] prefix, byte[] key) {
        serverByteBuffer patternKey = new serverByteBuffer();
        patternKey.append(prefix)
                  .append(key);
        try {
            return new String(patternKey.getBytes(),"UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }
    
    public static byte[] appendBytes(byte[] b1, byte[] b2, byte[] b3, byte[] b4) {
        serverByteBuffer byteArray = new serverByteBuffer();
        byteArray.append(b1)
                 .append(b2);
        if (b3 != null) byteArray.append(b3);
        if (b4 != null) byteArray.append(b4);
        return byteArray.getBytes();
    }
    
}
