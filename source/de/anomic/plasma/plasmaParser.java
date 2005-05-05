// plasmaParser.java 
// ------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// last major change: 02.05.2005 by Martin Thelian
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

// compile: javac -classpath lib/commons-collections.jar:lib/commons-pool-1.2.jar -sourcepath source source/de/anomic/plasma/plasmaParser.java


package de.anomic.plasma;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.plasma.parser.Parser;
import de.anomic.server.serverFileUtils;

public final class plasmaParser {

    /**
     * A list containing all installed parsers and the mimeType that they support
     * @see #loadAvailableParserList()
     */
    private final Properties availableParserList = new Properties();
    
    /**
     * A list containing all enabled parsers and the mimeType that they can handle
     * @see #loadEnabledParserList()
     * @see #setEnabledParserList(Enumeration)
     */
    private final Properties enabledParserList = new Properties();    
    
    /**
     * A list of file extensions that are supported by all enabled parsers
     */
    private static final HashSet supportedFileExt = new HashSet();
    
    /**
     * A pool of parsers.
     * @see plasmaParserPool
     * @see plasmaParserFactory
     */
    private final plasmaParserPool theParserPool;

    /**
     * The configuration file containing a list of enabled parsers
     * @see plasmaParser#plasmaParser(File)
     */
    private final File parserDispatcherPropertyFile;
    
    /**
     * A list of media extensions that should not be handled by the plasmaParser
     */
    private static final HashSet mediaExtSet = new HashSet(28);
    
    /**
     * This {@link FilenameFilter} is used to find all classes based on there filenames 
     * which seems to be additional content parsers.
     * Currently the filenames of all content parser classes must end with <code>Parser.class</code> 
     */
    private final FilenameFilter parserFileNameFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith("Parser.class");
        }
    };
    
    /**
     * This {@link FileFilter} is used to get all subpackages
     * of the parser package.
     */
    private final FileFilter parserDirectoryFilter = new FileFilter() {
        public boolean accept(File file) {
            return file.isDirectory();
        }
    };    
    
    /**
     * Initializing the 
     * @see #initMediaExt(String)
     */
    static {
		initMediaExt("swf,wmv,jpg,jpeg,jpe,rm,mov,mpg,mpeg,mp3,asf,gif,png,avi,zip,rar," +
			"sit,hqx,img,dmg,tar,gz,ps,xls,ppt,ram,bz2,arj");
    }
    
    public static void initMediaExt(String mediaExtString) {
		String[] xs = mediaExtString.split(",");
        initMediaExt(Arrays.asList(xs));
    }
    
    public static void initMediaExt(List mediaExtList) {
        synchronized (mediaExtSet) {
            mediaExtSet.clear();
    		mediaExtSet.addAll(mediaExtList);
		}
    }
    
    public static boolean mediaExtContains(String mediaExt) {
        
        synchronized (supportedFileExt) {
			if (supportedFileExt.contains(mediaExt)) return false;
		}
        
        synchronized (mediaExtSet) {
			return mediaExtSet.contains(mediaExtSet);
		}
    }


    public plasmaParser(File parserDispatcherPropertyFile) {
        
        this.parserDispatcherPropertyFile = parserDispatcherPropertyFile;

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
         * loading a list of available parsers
         * =================================================== */        
		loadAvailableParserList();
        
        /* ===================================================
         * loading a list of activated parsers
         * =================================================== */         
		loadEnabledParserList();
        
    }
    
    public boolean setEnabledParserList(Enumeration mimeTypes) {
        
        Properties newEnabledParsers = new Properties();
        HashSet newSupportedFileExt = new HashSet();
        
        if (mimeTypes != null) {
	        while (mimeTypes.hasMoreElements()) {
	            String mimeType = (String) mimeTypes.nextElement();
				if (this.availableParserList.containsKey(mimeType)) {
                    Parser theParser = null;
                    try {
                        // getting the parser
                        theParser = (Parser) this.theParserPool.borrowObject(this.availableParserList.get(mimeType));
                        
                        // getting a list of mimeTypes that the parser supports
                        Hashtable parserSupportsMimeTypes = theParser.getSupportedMimeTypes();                        
                        if (parserSupportsMimeTypes != null) {
                            Object supportedExtensions = parserSupportsMimeTypes.get(mimeType);                        
                            if ((supportedExtensions != null) && (supportedExtensions instanceof String)) {
                        		String[] extArray = ((String)supportedExtensions).split(",");
                                newSupportedFileExt.addAll(Arrays.asList(extArray));
                            }
                        }
						newEnabledParsers.put(mimeType,this.availableParserList.get(mimeType));
                        
                    } catch (Exception e) { 
                        e.printStackTrace();
                    } finally {
                        if (theParser != null) 
                            try { this.theParserPool.returnObject(mimeType,theParser); } catch (Exception e) {}
                    }
				}
	        }
        }
        
        synchronized (this.enabledParserList) {
			this.enabledParserList.clear();
            this.enabledParserList.putAll(newEnabledParsers);
		}
        
        
        synchronized (supportedFileExt) {
			supportedFileExt.clear();
            supportedFileExt.addAll(supportedFileExt);
		}
        
        return true;
    }
    
    public Hashtable getEnabledParserList() {
        synchronized (this.enabledParserList) {
            return (Hashtable) this.enabledParserList.clone();
		}        
    }
    
    public Hashtable getAvailableParserList() {
        return this.availableParserList;
    }
    
    private void loadEnabledParserList() {
        // loading a list of availabe parser from file
    	Properties prop = new Properties();
    	try {
    	    prop.load(new FileInputStream(this.parserDispatcherPropertyFile));
    	} catch (IOException e) {
    	    System.err.println("ERROR: " + this.parserDispatcherPropertyFile.toString() + " not found in settings path");
    	}    	

        // enable them ...
        this.setEnabledParserList(prop.keys());
	}

	private void loadAvailableParserList() {
        try {
            this.availableParserList.clear();
            
            // getting the current package name
			String plasmaParserPkgName = this.getClass().getPackage().getName() + ".parser";
			System.out.println("INFO: Searching for additional content parsers in package " + plasmaParserPkgName);
 
            // getting an uri to the parser subpackage
	        String packageURI = this.getClass().getResource("/"+plasmaParserPkgName.replace('.','/')).toString();
			System.out.println("INFO: Parser directory is " + packageURI);           
 
            // open the parser directory
	        File parserDir = new File(new URI(packageURI));
            if ((parserDir == null) || (!parserDir.exists()) || (!parserDir.isDirectory())) return;
            
            /* 
             * loop through all subdirectories and test if we can 
             * find an additional parser class
             */
            File[] parserDirectories = parserDir.listFiles(this.parserDirectoryFilter);
            if (parserDirectories == null) return;
			for (int parserDirNr=0; parserDirNr< parserDirectories.length; parserDirNr++) {
                File currentDir = parserDirectories[parserDirNr];
				System.out.println("INFO: Searching in directory " + currentDir.toString());
                String[] parserClasses = currentDir.list(this.parserFileNameFilter);
                if (parserClasses == null) continue;
                
                for (int parserNr=0; parserNr<parserClasses.length; parserNr++) {
                	System.out.println("INFO: Testing parser class " + parserClasses[parserNr]);
                    String className = parserClasses[parserNr].substring(0,parserClasses[parserNr].indexOf(".class"));
                    String fullClassName = plasmaParserPkgName + "." + currentDir.getName() + "." + className;
	                try {
						Class parserClass = Class.forName(fullClassName);
                        Object theParser = parserClass.newInstance();
                        if (!(theParser instanceof Parser)) continue;
                        Hashtable supportedMimeTypes = ((Parser)theParser).getSupportedMimeTypes();
                        Iterator mimeTypeIterator = supportedMimeTypes.keySet().iterator();
                        while (mimeTypeIterator.hasNext()) {
                            String mimeType = (String) mimeTypeIterator.next();
                            this.availableParserList.put(mimeType,fullClassName);
							System.out.println("INFO: Found parser for mimeType " + mimeType);
                        }
                            
	                } catch (Exception e) { /* we can ignore this for the moment */ }
                }
			}
            
        } catch (Exception e) {
            System.err.println("ERROR: while trying to determine all installed parsers. " + e.getMessage());
        }		
	}

	public void close() {        
        // clearing the parser list
        synchronized (this.enabledParserList) {
        	try {
        	    this.enabledParserList.store(new FileOutputStream(this.parserDispatcherPropertyFile),"plasmaParser configuration file");
        	} catch (IOException e) {
        	    System.err.println("ERROR: " + this.parserDispatcherPropertyFile.toString() + " can not be stored.");
        	} 
            
	        this.enabledParserList.clear();
		}
        
        // closing the parser object pool
        try {        
	        this.theParserPool.close();
        } catch (Exception e) { }        
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
            
            // determining the proper parser class name for the mimeType
            String parserClassName = null;
            synchronized (this.enabledParserList) {
    	        if (this.enabledParserList.containsKey(mimeType)) {
    	            parserClassName = (String)this.enabledParserList.get(mimeType);
    	        } else {
                    return null;
    	        }
			}
            
            // fetching a new parser object from pool  
			Parser theParser = (Parser) this.theParserPool.borrowObject(parserClassName);
            
            // checking if the created parser really supports the given mimetype 
            Hashtable supportedMimeTypes = theParser.getSupportedMimeTypes();
            if ((supportedMimeTypes != null) && (supportedMimeTypes.contains(mimeType))) {
				return theParser;
            }
            this.theParserPool.returnObject(parserClassName,theParser);
            
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
	//javac -classpath lib/commons-collections.jar:lib/commons-pool-1.2.jar -sourcepath source source/de/anomic/plasma/plasmaParser.java
	//java -cp source:lib/commons-collections.jar:lib/commons-pool-1.2.jar de.anomic.plasma.plasmaParser bug.html bug.out
		try {
			File in = new File(args[0]);
			File out = new File(args[1]);
			plasmaParser theParser = new plasmaParser(new File("yacy.parser"));
			FileInputStream theInput = new FileInputStream(in);
			ByteArrayOutputStream theOutput = new ByteArrayOutputStream();
			serverFileUtils.copy(theInput, theOutput);
			plasmaParserDocument document = theParser.parseSource(new URL("http://brain.yacy"), "text/html", theOutput.toByteArray());
			//plasmaParserDocument document = theParser.parseSource(new URL("http://brain.yacy"), "application/pdf", theOutput.toByteArray());
			byte[] theText = document.getText();
			serverFileUtils.write(theText, out);
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


