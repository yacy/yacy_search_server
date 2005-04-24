// plasmaParser.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 12.04.2005
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


package de.anomic.plasma;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.plasma.parser.Parser;
import de.anomic.server.serverFileUtils;
import de.anomic.htmlFilter.*;

public final class plasmaParser {
    
    public static String mediaExt =
        "swf,wmv,jpg,jpeg,jpe,rm,mov,mpg,mpeg,mp3,asf,gif,png,avi,zip,rar," +
        "sit,hqx,img,dmg,tar,gz,ps,xls,ppt,ram,bz2,arj";
    
    private final Properties parserList;

	private final plasmaParserPool theParserPool;

    public plasmaParser(File parserDispatcherPropertyFile) {
        
        /* ===================================================
         * loading a list of availabe parser from file
         * =================================================== */ 
    	Properties prop = new Properties();
    	try {
    	    prop.load(new FileInputStream(parserDispatcherPropertyFile));
    	} catch (IOException e) {
    	    System.err.println("ERROR: " + parserDispatcherPropertyFile.toString() + " not found in settings path");
    	}    	
        this.parserList = prop;
        
        /* ===================================================
         * initializing the parser object pool
         * =================================================== */
        GenericKeyedObjectPool.Config config = new GenericKeyedObjectPool.Config();
        
        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        config.maxActive = 0;
        
        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        config.maxIdle = 10;    
        
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        config.minEvictableIdleTimeMillis = 30000; 
        
        this.theParserPool = new plasmaParserPool(new plasmaParserFactory(),config);           
        
        /* ===================================================
         * testing if all parsers could be loaded properly.
         * This is done now to avoid surprises at runtime. 
         * =================================================== */
        if (this.parserList.size() > 0) {
			Iterator parserIterator = this.parserList.values().iterator();
            while (parserIterator.hasNext()) {
				String className = (String) parserIterator.next();
                try {
					Class.forName(className);
                } catch (Exception e) {
                    // if we could not load the parser we remove it from the parser list ...
                    this.parserList.remove(className);
                }
            }
        }        
    }
    
    public void close() {
        // release resources 
        try {        
	        // clearing the parser list
	        this.parserList.clear();
	        
	        // closing the parser object pool
	        this.theParserPool.close();
        } catch (Exception e) {
            //
        }
    }    
    
    public plasmaParserDocument parseSource(URL location, String mimeType, byte[] source) {
        
        Parser theParser = null;
        try {
            
            if ((mimeType != null) && (mimeType.indexOf(";") != -1)) {
                mimeType = mimeType.substring(0,mimeType.indexOf(";"));
            }                        
            
            // getting the correct parser for the given mimeType
            theParser = this.getParser(mimeType);
            
            // if a parser was found we use it ...
            if (theParser != null) {
                return theParser.parse(location, mimeType,source);
            }
        
            // ...otherwise we make a html scraper and transformer
            htmlFilterContentScraper scraper = new htmlFilterContentScraper(location);
            OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);

            hfos.write(source);
            return transformScraper(location, mimeType, scraper);
        } catch (Exception e) {
            return null;
        } finally {
            if (theParser != null) {
                try {
                    this.theParserPool.returnObject(mimeType, theParser);
                } catch (Exception e) {
                }
            }
        }
    }

    public plasmaParserDocument parseSource(URL location, String mimeType, File sourceFile) {

        Parser theParser = null;
        try {
            if ((mimeType != null) && (mimeType.indexOf(";") != -1)) {
                mimeType = mimeType.substring(0,mimeType.indexOf(";"));
            }            
            
            // getting the correct parser for the given mimeType
            theParser = this.getParser(mimeType);
            
            // if a parser was found we use it ...
            if (theParser != null) {
                return theParser.parse(location, mimeType,sourceFile);
            }    
            
            // ...otherwise we make a scraper and transformer
            htmlFilterContentScraper scraper = new htmlFilterContentScraper(location);
            OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);            
            
			serverFileUtils.copy(sourceFile, hfos);
            return transformScraper(location, mimeType, scraper);
        } catch (Exception e) {
            return null;
        } finally {
            if (theParser != null) {
                try {
                    this.theParserPool.returnObject(mimeType, theParser);
                } catch (Exception e) {
                }
            }
        }
    }
    
    public plasmaParserDocument transformScraper(URL location, String mimeType, htmlFilterContentScraper scraper) {
        try {
            return new plasmaParserDocument(new URL(urlNormalform(location)),
                                mimeType, null, null, scraper.getHeadline(),
                                null, null,
                                scraper.getText(), scraper.getAnchors(), scraper.getImages());
        } catch (MalformedURLException e) {
            return null;
        }
    }
    
    /**
     * This function is used to determine the parser class that should be used for a given
     * mimetype ...
     * @param mimeType
     * @return
     */
    public Parser getParser(String mimeType) {
        
        if (mimeType == null) {            
            // TODO: do automatic mimetype detection
            return null;
        }
        
        try {
	        if (this.parserList.containsKey(mimeType)) {
	            String parserClassName = (String)this.parserList.get(mimeType);
	            
                // fetching a new parser object from pool  
				Parser theParser = (Parser) this.theParserPool.borrowObject(parserClassName);
                
                // checking if the created parser really supports the given mimetype 
                HashSet supportedMimeTypes = theParser.getSupportedMimeTypes();
                if ((supportedMimeTypes != null) && (supportedMimeTypes.contains(mimeType))) {
					return theParser;
                }
                this.theParserPool.returnObject(parserClassName,theParser);
	        }
        } catch (Exception e) {
            System.err.println("ERROR: Unable to load the correct parser for type " + mimeType);
        }
        
        return null;
        
    }
    
    public static String urlNormalform(URL url) {
        if (url == null) return null;
        return urlNormalform(url.toString());
    }
    
    public static String urlNormalform(String us) {
        if (us == null) return null;
        if (us.length() == 0) return null;
        int p;
        if ((p = us.indexOf("#")) >= 0) us = us.substring(0, p);
        if (us.endsWith(":80")) us = us.substring(0, us.length() - 3);
        if (((us.endsWith("/")) && (us.lastIndexOf('/', us.length() - 2) < 8))) us = us.substring(0, us.length() - 1);
        return us;
    }   
    
    static Map allReflinks(Map links) {
        // we find all links that are part of a reference inside a url
        HashMap v = new HashMap();
        Iterator i = links.keySet().iterator();
        String s;
        int pos;
        loop: while (i.hasNext()) {
            s = (String) i.next();
            if ((pos = s.toLowerCase().indexOf("http://",7)) > 0) {
                i.remove();
                s = s.substring(pos);
                while ((pos = s.toLowerCase().indexOf("http://",7)) > 0) s = s.substring(pos);
                if (!(v.containsKey(s))) v.put(s, "ref");
                continue loop;
            }
            if ((pos = s.toLowerCase().indexOf("/www.",7)) > 0) {
                i.remove();
                s = "http:/" + s.substring(pos);
                while ((pos = s.toLowerCase().indexOf("/www.",7)) > 0) s = "http:/" + s.substring(pos);
                if (!(v.containsKey(s))) v.put(s, "ref");
                continue loop;
            }
        }
        return v;
    }
    
    static Map allSubpaths(Map links) {
        HashMap v = new HashMap();
        Iterator i = links.keySet().iterator();
        String s;
        int pos;
        while (i.hasNext()) {
            s = (String) i.next();
            if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
            pos = s.lastIndexOf("/");
            while (pos > 8) {
                s = s.substring(0, pos + 1);
                if (!(v.containsKey(s))) v.put(s, "sub");
                s = s.substring(0, pos);
                pos = s.lastIndexOf("/");
            }
        }
        return v;
    }
    
    public static void main(String[] args) {
		try {            
			plasmaParser theParser = new plasmaParser(new File("yacy.parser"));
            FileInputStream theInput = new FileInputStream(new File("Y:/public_html/test.pdf"));
			ByteArrayOutputStream theOutput = new ByteArrayOutputStream();
            serverFileUtils.copy(theInput, theOutput);
            
            theParser.parseSource(new URL("http://brain"),"application/pdf",theOutput.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
    
}

final class plasmaParserFactory implements KeyedPoolableObjectFactory {
    
    public plasmaParserFactory() {
        super();  
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
     */
    public Object makeObject(Object key) throws Exception {
        
        if (!(key instanceof String))
            throw new IllegalArgumentException("The object key must be of type string.");
        
        Class moduleClass = Class.forName((String)key);
        return moduleClass.newInstance();
    }          
    
     /**
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object key, Object obj) {
        if (obj instanceof Parser) {
            Parser theParser = (Parser) obj;
        }
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object key, Object obj) {
        if (obj instanceof Parser) {
            Parser theParser = (Parser) obj;
            return true;
        }
        return true;
    }
    
    /**
     * @param obj 
     * 
     */
    public void activateObject(Object key, Object obj)  {
        //log.debug(" activateObject...");
    }

    /**
     * @param obj 
     * 
     */
    public void passivateObject(Object key, Object obj) { 
        //log.debug(" passivateObject..." + obj);
        if (obj instanceof Parser)  {
            Parser theParser = (Parser) obj;
            theParser.reset();
        }
    }
}    

final class plasmaParserPool extends GenericKeyedObjectPool {

    public plasmaParserPool(plasmaParserFactory objFactory,
            GenericKeyedObjectPool.Config config) {
        super(objFactory, config);
    }
    

    public Object borrowObject(Object key) throws Exception  {
       return super.borrowObject(key);
    }

    public void returnObject(Object key, Object borrowed) throws Exception  {
        super.returnObject(key,borrowed);
    }        
}   
