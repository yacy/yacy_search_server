/**
 *  mp3Parser
 *  Copyright 2012 by Stefan Foerster, Norderstedt, Germany
 *  First released 01.10.2012 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.parser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

/**
 * this parser can parse id3 tags of mp3 audio files
 */
public class audioTagParser extends AbstractParser implements Parser {
	
	public static String EXTENSIONS 	= "mp3,ogg,oga,m4a,m4p,flac,wma";
	public static String MIME_TYPES 	= "audio/mpeg,audio/MPA,audio/mpa-robust,audio/mp4,audio/flac,audio/x-flac,audio/x-ms-wma,audio/x-ms-asf";
	public static String SEPERATOR 	= ",";
	
    public audioTagParser() {
        super("Audio File Meta-Tag Parser");
        final String[] extArray = EXTENSIONS.split(SEPERATOR);
        for (final String ext : extArray) {
        	this.SUPPORTED_EXTENSIONS.add(ext);
        }
        final String[] mimeArray = MIME_TYPES.split(SEPERATOR);
        for (final String mime : mimeArray) {
        	this.SUPPORTED_MIME_TYPES.add(mime);
        }
    }

    @Override
    public Document[] parse(final DigestURI location, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {

        String filename = location.getFileName();
        final String fileext = '.' + MultiProtocolURI.getFileExtension(filename);
        filename = filename.isEmpty() ? location.toTokens() : MultiProtocolURI.unescape(filename);
    	String mime = mimeType;
   	    
    	// fix mimeType
    	if(!this.SUPPORTED_MIME_TYPES.contains(mimeType)) {
    		if(fileext.equals("mp3")) {
    			mime = "audio/mpeg";
    		} else if(fileext.equals("ogg")) {
    			mime = "audio/ogg";
    		} else if(fileext.equals("flac")) {
    			mime = "audio/flac";
    		} else if(fileext.equals("wma")) {
    			mime = "audio/x-ms-wma";
    		} else if(fileext.startsWith("m4")) {
    			mime = "audio/mp4";
    		}
    	}
    	    	
    	Document[] docs;
        BufferedOutputStream fout = null;        
        File tempFile = null;
        AudioFile f;
        
        try {        	
        	if (location.isFile()) {
        		f = AudioFileIO.read(location.getFSFile());
        	} else {
            	// create a temporary file, as jaudiotagger requires a file rather than an input stream 
            	tempFile = File.createTempFile(filename,fileext);  
                tempFile.deleteOnExit();              
                fout = new BufferedOutputStream(new FileOutputStream(tempFile));  
                int c;  
                while ((c = source.read()) != -1) {  
                    fout.write(c);  
                }
                f = AudioFileIO.read(tempFile);
        	}
            
            Tag tag = f.getTag();
       
            final Set<String> lang = new HashSet<String>();
           	lang.add(tag.getFirst(FieldKey.LANGUAGE));
           	
            // title
            final List<String> titles = new ArrayList<String>();
            titles.add(tag.getFirst(FieldKey.TITLE));
            titles.add(tag.getFirst(FieldKey.ALBUM));
            titles.add(filename);
             
            // text
            final StringBuilder text = new StringBuilder(500);
            final char space = ' ';
            text.append(tag.getFirst(FieldKey.ARTIST));
            text.append(space);   
            text.append(tag.getFirst(FieldKey.ALBUM));
            text.append(space);
            text.append(tag.getFirst(FieldKey.TITLE));
            text.append(space);
            text.append(tag.getFirst(FieldKey.COMMENT));
            text.append(space);
            text.append(tag.getFirst(FieldKey.LYRICS));
            text.append(space);
            text.append(tag.getFirst(FieldKey.TAGS));
            text.append(space);
            text.append(tag.getFirst(FieldKey.GENRE));
            text.append(space);
            text.append(location.toTokens());
            
            // dc:subject
            final String[] subject = new String[1];
            subject[0] = tag.getFirst(FieldKey.GENRE);

            // description
            final StringBuilder desc = new StringBuilder(500);
            final String sep = " - ";
            int count = desc.length();
            desc.append(tag.getFirst(FieldKey.ARTIST));
            if(desc.length() > count) {
            	desc.append(sep);
            	count = desc.length();
            }
            desc.append(tag.getFirst(FieldKey.ALBUM));
            if(desc.length() > count) {
            	desc.append(sep);
            	count = desc.length();
            }
            desc.append(tag.getFirst(FieldKey.TITLE));
            
            docs = new Document[]{new Document(
                    location,
                    mime,
                    charset,
                    this,
                    lang, // languages
                    subject, // keywords, dc:subject
                    titles, // title
                    tag.getFirst(FieldKey.ARTIST), // author
                    location.getHost(), // publisher
                    null, // sections
                    desc.toString(), // abstrct
                    0.0f, 0.0f, // lon, lat
                    text.toString(), // text
                    null,
                    null,
                    null,
                    false)
            };            
            return docs;
        } catch (Exception e) {
			// return a generic document as default
	    	docs = new Document[]{new Document(
	                location,
	                mimeType,
	                charset,
	                this,
	                null,
	                null,
	                singleList(filename), // title
	                "", // author
	                location.getHost(),
	                null,
	                null,
	                0.0f, 0.0f,
	                location.toTokens(),
	                null,
	                null,
	                null,
	                false
	    	)};
		} finally {
            try {
				if (fout != null)
					fout.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.logException(e);
			}
            if (tempFile != null)
            	tempFile.delete();
		}
        return docs;
    }
}
