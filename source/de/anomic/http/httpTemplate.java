// httpTemplate.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 16.01.2005
//
// extended for multi- and alternatives-templates by Alexander Schier
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
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.util.Hashtable;

import de.anomic.server.serverFileUtils;

final class httpTemplate {
    
    private static final byte hash = (byte)'#';
    private static final byte[] hasha = {hash};

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

    private static boolean transferUntil(PushbackInputStream i, OutputStream o, byte[] pattern) throws IOException {
        // returns true if pattern was found; everything but the pattern has then be transfered so far
        int ppos = 0;
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
    
    public static void writeTemplate(InputStream in, OutputStream out, Hashtable pattern, byte[] dflt) throws IOException {
	writeTemplate(in, out, pattern, dflt, "");
    }

    public static void writeTemplate(InputStream in, OutputStream out, Hashtable pattern, byte[] dflt, String prefix) throws IOException {
        PushbackInputStream pis = new PushbackInputStream(in, 100);
        ByteArrayOutputStream keyStream;
        String key;
	String multi_key;
        boolean consistent;
        byte[] replacement;
	int bb;
	
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
		    multi_key = keyStream.toString(); //IMPORTANT: no prefix here
		    keyStream = new ByteArrayOutputStream(); //reset stream

			/* DEBUG - print key + value
			try{
				System.out.println("Key: "+prefix+multi_key+ "; Value: "+pattern.get(prefix+multi_key));
			}catch(NullPointerException e){
				System.out.println("Key: "+prefix+multi_key);
			}
			*/
			
		    //this needs multi_key without prefix
		    if( transferUntil(pis, keyStream, (new String(mOpen) + "/" + multi_key + new String(mClose)).getBytes()) ){
			bb = pis.read();
			if((bb & 0xFF) != 10){ //kill newline
			    pis.unread(bb);
			}
			multi_key = prefix + multi_key; //OK, now add the prefix
			String text=keyStream.toString(); //text between #{key}# an #{/key}#
			int num=0;
			if(pattern.containsKey(multi_key) && pattern.get(multi_key) != null){
			    try{
				num=(int)Integer.parseInt((String)pattern.get(multi_key)); // Key contains the iteration number as string
			    }catch(NumberFormatException e){
				num=0;
			    }
			    //System.out.println(multi_key + ": " + num); //DEBUG
			}else{
			    //0 interations - no display
			    //System.out.println("_"+new String(multi_key)+" is null or does not exist"); //DEBUG
			}
			
                        //Enumeration enx = pattern.keys(); while (enx.hasMoreElements()) System.out.println("KEY=" + enx.nextElement()); // DEBUG
			for(int i=0;i < num;i++ ){
                            PushbackInputStream pis2 = new PushbackInputStream(new ByteArrayInputStream(text.getBytes()));
			    //System.out.println("recursing with text(prefix="+ multi_key + "_" + i + "_" +"):"); //DEBUG
			    //System.out.println(text);
			    writeTemplate(pis2, out, pattern, dflt, multi_key + "_" + i + "_");
			}//for
		    }else{//transferUntil
				System.out.println("TEMPLATE ERROR: No Close Key found for #{"+multi_key+"}#");
			}
		}
	    }else if( (bb & 0xFF) == lrbr ){ //alternatives
		int others=0;
		String text="";
		PushbackInputStream pis2;
		
		transferUntil(pis, keyStream, aClose);
		key = keyStream.toString(); //Caution: Key does not contain prefix
		
		/* DEBUG - print key + value
		try{
			System.out.println("Key: "+prefix+key+ "; Value: "+pattern.get(prefix+key));
		}catch(NullPointerException e){
			System.out.println("Key: "+prefix+key);
		}
		*/

		keyStream=new ByteArrayOutputStream(); //clear
		
		int whichPattern=0;
		if(pattern.containsKey(prefix + key) && pattern.get(prefix + key) != null){
		    try{
			whichPattern=(int)Integer.parseInt((String)pattern.get(prefix + key)); //which alternative(index)
		    }catch(NumberFormatException e){
			whichPattern=0;
		    }
		}else{
		    //System.out.println("Pattern \""+new String(prefix + key)+"\" is not set"); //DEBUG
		}
		
		int currentPattern=0;
		boolean found=false;
		keyStream = new ByteArrayOutputStream(); //reset stream
		while(!found){
		    bb=pis.read();
		    if( (bb & 0xFF) == hash){
			bb=pis.read();
			if( (bb & 0xFF) == lrbr){
			    transferUntil(pis, keyStream, aClose);

				//reached the end. output last string.
				if(keyStream.toString().equals("/" + key)){
					pis2 = new PushbackInputStream(new ByteArrayInputStream(text.getBytes()));
					//this maybe the wrong, but its the last
					writeTemplate(pis2, out, pattern, dflt, prefix + key + "_");
					found=true;
				}else if(others >0 && keyStream.toString().startsWith("/")){ //close nested
					others--;
					text += "#("+keyStream.toString()+")#";
				}else{ //nested
					others++;
					text += "#("+keyStream.toString()+")#";
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
				writeTemplate(pis2, out, pattern, dflt, prefix + key + "_");

				transferUntil(pis, keyStream, (new String("#(/"+key+")#")).getBytes());//to #(/key)#.

				found=true;
			    }
			    currentPattern++;
			    text="";
			    continue;
			}else{
			    text += ":";
			}
		    }
		    if(!found){
			text += (char)bb;
			if(pis.available()==0){
				System.out.println("TEMPLATE ERROR: No Close Key found for #("+key+")#");
				found=true;
			}
		    }
		}//while
	    }else if( (bb & 0xFF) == lbr ){ //normal
		if (transferUntil(pis, keyStream, pClose)) {
    	            // pattern detected, write replacement
		    key = prefix + keyStream.toString();
		    replacement = replacePattern(key, pattern, dflt); //replace

			/* DEBUG
			try{
				System.out.println("Key: "+key+ "; Value: "+pattern.get(key));
			}catch(NullPointerException e){
				System.out.println("Key: "+key);
			}
			*/

		    serverFileUtils.write(replacement, out);
    	        } else {
		    // inconsistency, simply finalize this
		    serverFileUtils.copy(pis, out);
            	    return;
		}
	    }else if( (bb & 0xFF) == ps){ //include
			String include = "";
			String line = "";
		    keyStream = new ByteArrayOutputStream(); //reset stream
			if(transferUntil(pis, keyStream, iClose)){
			String filename = keyStream.toString();
			if(filename.startsWith( Character.toString((char)lbr) ) && filename.endsWith( Character.toString((char)rbr) )){ //simple pattern for filename
				filename= new String(replacePattern( filename.substring(1, filename.length()-1), pattern, dflt));
			}
				try{
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream( new File("htroot", filename) )));
					//Read the Include
					while( (line = br.readLine()) != null ){
						include+=line+de.anomic.server.serverCore.crlfString;
					}
				}catch(IOException e){
					//file not found?
					System.err.println("Include Error with file: "+filename);
					//e.printStackTrace();
				}
				PushbackInputStream pis2 = new PushbackInputStream(new ByteArrayInputStream(include.getBytes()));
				writeTemplate(pis2, out, pattern, dflt, prefix);
			}

		}else{ //no match, but a single hash (output # + bb)
		byte[] tmp=new byte[2];
		tmp[0]=hash;
		tmp[1]=(byte)bb;
		serverFileUtils.write(tmp, out);
	    }
        }
    }

    public static byte[] replacePattern(String key, Hashtable pattern, byte dflt[]){
	byte[] replacement;
        Object value;
	if (pattern.containsKey(key)) {
	    value = pattern.get(key);
	    if (value instanceof byte[]) replacement = (byte[]) value;
	    else if (value instanceof String) replacement = ((String) value).getBytes();
    	    else replacement = value.toString().getBytes();
        } else {
	    replacement = dflt;
        }
	return replacement;
    }

    public static void main(String[] args) {
        // arg1 = test input; arg2 = replacement for pattern 'test'; arg3 = default replacement
        try {
            InputStream i = new ByteArrayInputStream(args[0].getBytes());
            Hashtable h = new Hashtable();
            h.put("test", args[1].getBytes());
            writeTemplate(new PushbackInputStream(i, 100), System.out, h, args[2].getBytes());
            System.out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
