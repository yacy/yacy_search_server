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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedUploader;

public final class plasmaParser {

    /**
     * A list containing all installed parsers and the mimeType that they support
     * @see #loadAvailableParserList()
     */
    private static final Properties availableParserList = new Properties();
    
    /**
     * A list containing all enabled parsers and the mimeType that they can handle
     * @see #loadEnabledParserList()
     * @see #setEnabledParserList(Enumeration)
     */
    private static final Properties enabledParserList = new Properties();    
    
    /**
     * A list of file extensions that are supported by all enabled parsers
     */
    private static final HashSet supportedFileExt = new HashSet();
    
    /**
     * A list of mimeTypes that can be parsed in Realtime (on the fly)
     */
    private static final HashSet realtimeParsableMimeTypes = new HashSet();    
    
    /**
     * A pool of parsers.
     * @see plasmaParserPool
     * @see plasmaParserFactory
     */
    private static plasmaParserPool theParserPool;

    /**
     * A list of media extensions that should not be handled by the plasmaParser
     */
    private static final HashSet mediaExtSet = new HashSet(28);
    
    /**
     * This {@link FilenameFilter} is used to find all classes based on there filenames 
     * which seems to be additional content parsers.
     * Currently the filenames of all content parser classes must end with <code>Parser.class</code> 
     */
    private static final FilenameFilter parserFileNameFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith("Parser.class");
        }
    };
    
    /**
     * This {@link FileFilter} is used to get all subpackages
     * of the parser package.
     */
    private static final FileFilter parserDirectoryFilter = new FileFilter() {
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
        
        plasmaParser.theParserPool = new plasmaParserPool(new plasmaParserFactory(),config);    
        
        /* ===================================================
         * loading a list of available parsers
         * =================================================== */        
        loadAvailableParserList();      
    }
    
    public static void initRealtimeParsableMimeTypes(String realtimeParsableMimeTypes) {
        LinkedList mimeTypes = new LinkedList();
        if ((realtimeParsableMimeTypes == null) || (realtimeParsableMimeTypes.length() == 0)) {
            
        } else {            
            String[] realtimeParsableMimeTypeList = realtimeParsableMimeTypes.split(",");        
            for (int i = 0; i < realtimeParsableMimeTypeList.length; i++) mimeTypes.add(realtimeParsableMimeTypeList[i].toLowerCase().trim());
        }
        initRealtimeParsableMimeTypes(mimeTypes);
    }
    
    public static void initRealtimeParsableMimeTypes(List mimeTypesList) {
        synchronized (realtimeParsableMimeTypes) {
            realtimeParsableMimeTypes.clear();
            realtimeParsableMimeTypes.addAll(mimeTypesList);
        }        
    }
    
    public static void initParseableMimeTypes(String enabledMimeTypes) {
        HashSet mimeTypes = null;
        if ((enabledMimeTypes == null) || (enabledMimeTypes.length() == 0)) {
            mimeTypes = new HashSet();
        } else {            
            String[] enabledMimeTypeList = enabledMimeTypes.split(",");
            mimeTypes = new HashSet(enabledMimeTypeList.length);
            for (int i = 0; i < enabledMimeTypeList.length; i++) mimeTypes.add(enabledMimeTypeList[i].toLowerCase().trim());
        }
        setEnabledParserList(mimeTypes);
    }
    
    public static void initMediaExt(String mediaExtString) {
        LinkedList extensions = new LinkedList();
        if ((mediaExtString == null) || (mediaExtString.length() == 0)) {
            
        } else {
            
            String[] xs = mediaExtString.split(",");
            for (int i = 0; i < xs.length; i++) extensions.add(xs[i].toLowerCase().trim());
        }
        initMediaExt(extensions);
    }
    
    public static void initMediaExt(List mediaExtList) {
        synchronized (mediaExtSet) {
            mediaExtSet.clear();
    		mediaExtSet.addAll(mediaExtList);
		}
    }
    
    public static boolean realtimeParsableMimeTypesContains(String mimeType) {
        mimeType = getRealMimeType(mimeType);
        synchronized (realtimeParsableMimeTypes) {
            return realtimeParsableMimeTypes.contains(mimeType);
        }
    }
    
    public static boolean supportedMimeTypesContains(String mimeType) {
        mimeType = getRealMimeType(mimeType);
        
        synchronized (realtimeParsableMimeTypes) {
            if (realtimeParsableMimeTypes.contains(mimeType)) return true;
        }        
        
        synchronized (enabledParserList) { 
            return enabledParserList.containsKey(mimeType);
        }
    }
    
    public static boolean mediaExtContains(String mediaExt) {
        if (mediaExt == null) return false;
        
        synchronized (supportedFileExt) {
			if (supportedFileExt.contains(mediaExt)) return false;
		}
        
        synchronized (mediaExtSet) {
			return mediaExtSet.contains(mediaExt);
		}
    }

    public static String getRealMimeType(String mimeType) {
        //if (mimeType == null) doMimeTypeAnalysis
        if (mimeType == null) mimeType = "application/octet-stream";
        
        int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType : mimeType.substring(0, pos)).toLowerCase();              
    }
    
    public static String getMimeTypeByFileExt(String fileExt) {
        // loading a list of extensions from file
        Properties prop = new Properties();
        BufferedInputStream bufferedIn = null;
        try {            
            prop.load(bufferedIn = new BufferedInputStream(new FileInputStream(new File("httpd.mime"))));
        } catch (IOException e) {
            System.err.println("ERROR: httpd.mime not found in settings path");
        } finally {
            if (bufferedIn != null) try{bufferedIn.close();}catch(Exception e){}
        }
        
        return prop.getProperty(fileExt,"application/octet-stream");
    }
    
    public plasmaParser() {
        // nothing todo here at the moment
    }
    
    public static String[] setEnabledParserList(Set mimeTypeSet) {
        
        Properties newEnabledParsers = new Properties();
        HashSet newSupportedFileExt = new HashSet();
        
        if (mimeTypeSet != null) {
            Iterator mimeTypes = mimeTypeSet.iterator();
	        while (mimeTypes.hasNext()) {
	            String mimeType = (String) mimeTypes.next();
				if (availableParserList.containsKey(mimeType)) {
                    Parser theParser = null;
                    try {
                        // getting the parser
                        theParser = (Parser) plasmaParser.theParserPool.borrowObject(availableParserList.get(mimeType));
                        
                        // getting a list of mimeTypes that the parser supports
                        Hashtable parserSupportsMimeTypes = theParser.getSupportedMimeTypes();                        
                        if (parserSupportsMimeTypes != null) {
                            Object supportedExtensions = parserSupportsMimeTypes.get(mimeType);                        
                            if ((supportedExtensions != null) && 
                                (supportedExtensions instanceof String) && 
                                (((String)supportedExtensions).length() > 0)) {
                        		String[] extArray = ((String)supportedExtensions).split(",");
                                newSupportedFileExt.addAll(Arrays.asList(extArray));
                            }
                        }
						newEnabledParsers.put(mimeType,availableParserList.get(mimeType));
                        
                    } catch (Exception e) { 
                        e.printStackTrace();
                    } finally {
                        if (theParser != null) 
                            try { plasmaParser.theParserPool.returnObject(mimeType,theParser); } catch (Exception e) {}
                    }
				}
	        }
        }
        
        synchronized (enabledParserList) {
			enabledParserList.clear();
            enabledParserList.putAll(newEnabledParsers);            
		}
        
        
        synchronized (supportedFileExt) {
			supportedFileExt.clear();
            supportedFileExt.addAll(newSupportedFileExt);
		}
        
        return (String[])newEnabledParsers.keySet().toArray(new String[newEnabledParsers.size()]);
    }
    
    public Hashtable getEnabledParserList() {
        synchronized (plasmaParser.enabledParserList) {
            return (Hashtable) plasmaParser.enabledParserList.clone();
		}        
    }
    
    public Hashtable getAvailableParserList() {
        return plasmaParser.availableParserList;
    }
    
    private static void loadEnabledParserList() {
        // loading a list of availabe parser from file
    	Properties prop = new Properties();
        BufferedInputStream bufferedIn = null;
    	try {
    	    prop.load(bufferedIn = new BufferedInputStream(new FileInputStream(new File("yacy.parser"))));
    	} catch (IOException e) {
    	    System.err.println("ERROR: yacy.parser not found in settings path");
    	} finally {
            if (bufferedIn != null) try{ bufferedIn.close(); }catch(Exception e){}
        }

        // enable them ...
        setEnabledParserList(prop.keySet());
	}

	private static void loadAvailableParserList() {
        try {
            plasmaParser.availableParserList.clear();
            
            // getting the current java classpath
            String javaClassPath = System.getProperty("java.class.path");
            
            // getting the current package name
			String plasmaParserPkgName = plasmaParser.class.getPackage().getName() + ".parser";
            serverLog.logInfo("PARSER","Searching for additional content parsers in package " + plasmaParserPkgName);
 
            // getting an uri to the parser subpackage
	        String packageURI = plasmaParser.class.getResource("/"+plasmaParserPkgName.replace('.','/')).toString();
			serverLog.logDebug("PARSER", "Parser directory is " + packageURI);           
 
            // open the parser directory
	        File parserDir = new File(new URI(packageURI));
            if ((parserDir == null) || (!parserDir.exists()) || (!parserDir.isDirectory())) return;
            
            /* 
             * loop through all subdirectories and test if we can 
             * find an additional parser class
             */
            File[] parserDirectories = parserDir.listFiles(parserDirectoryFilter);
            if (parserDirectories == null) return;
			for (int parserDirNr=0; parserDirNr< parserDirectories.length; parserDirNr++) {
                File currentDir = parserDirectories[parserDirNr];
                serverLog.logDebug("PARSER", "Searching in directory " + currentDir.toString());
                String[] parserClasses = currentDir.list(parserFileNameFilter);
                if (parserClasses == null) continue;
                
                for (int parserNr=0; parserNr<parserClasses.length; parserNr++) {
                    serverLog.logDebug("PARSER", "Testing parser class " + parserClasses[parserNr]);
                    String className = parserClasses[parserNr].substring(0,parserClasses[parserNr].indexOf(".class"));
                    String fullClassName = plasmaParserPkgName + "." + currentDir.getName() + "." + className;
	                try {
                        // trying to load the parser class by its name
						Class parserClass = Class.forName(fullClassName);
                        Object theParser = parserClass.newInstance();
                        if (!(theParser instanceof Parser)) continue;
                        
                        // testing if all needed libx libraries are available
                        String[] neededLibx = ((Parser)theParser).getLibxDependences();
                        if (neededLibx != null) {
                            for (int libxId=0; libxId < neededLibx.length; libxId++) {
                                if (javaClassPath.indexOf(neededLibx[libxId]) == -1) {
                                    throw new ParserException("Missing dependency detected: '" + neededLibx[libxId] + "'.");
                                }
                            }
                        }                        
                        
                        // loading the list of mime-types that are supported by this parser class
                        Hashtable supportedMimeTypes = ((Parser)theParser).getSupportedMimeTypes();
                        Iterator mimeTypeIterator = supportedMimeTypes.keySet().iterator();
                        while (mimeTypeIterator.hasNext()) {
                            String mimeType = (String) mimeTypeIterator.next();
                            availableParserList.put(mimeType,fullClassName);
                            serverLog.logInfo("PARSER", "Found functional parser for mimeType '" + mimeType + "'.");
                        }
                            
	                } catch (Exception e) { /* we can ignore this for the moment */ 
                        serverLog.logWarning("PARSER", "Parser '" + className + "' doesn't work correctly and will be ignored.\n [" + e.getClass().getName() + "]: " + e.getMessage());
                    } catch (Error e) { /* we can ignore this for the moment */ 
                        serverLog.logWarning("PARSER", "Parser '" + className + "' doesn't work correctly and will be ignored.\n [" + e.getClass().getName() + "]: " + e.getMessage());
                    }
                }
			}
            
        } catch (Exception e) {
            serverLog.logError("PARSER", "Unable to determine all installed parsers. " + e.getMessage());
        }		
	}

	public void close() {        
        // clearing the parser list
        synchronized (this.enabledParserList) {
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
            mimeType = getRealMimeType(mimeType);
            
            // getting the correct parser for the given mimeType
            theParser = this.getParser(mimeType);
            
            // if a parser was found we use it ...
            if (theParser != null) {
                return theParser.parse(location, mimeType,source);
            } else if (realtimeParsableMimeTypesContains(mimeType)) {        
                // ... otherwise we make a html scraper and transformer
                htmlFilterContentScraper scraper = new htmlFilterContentScraper(location);
                OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);
    
                hfos.write(source);
                return transformScraper(location, mimeType, scraper);
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        } finally {
            if ((theParser != null) && (supportedMimeTypesContains(mimeType))) {
                try { plasmaParser.theParserPool.returnObject(mimeType, theParser); } catch (Exception e) {}
            }
        }
    }

    public plasmaParserDocument parseSource(URL location, String mimeType, File sourceFile) {

        Parser theParser = null;
        try {
            mimeType = getRealMimeType(mimeType);
            
            // getting the correct parser for the given mimeType
            theParser = this.getParser(mimeType);
            
            // if a parser was found we use it ...
            if (theParser != null) {
                return theParser.parse(location, mimeType,sourceFile);
            } else if (realtimeParsableMimeTypesContains(mimeType)) {                      
                // ...otherwise we make a scraper and transformer
                htmlFilterContentScraper scraper = new htmlFilterContentScraper(location);
                OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);            
                
                serverFileUtils.copy(sourceFile, hfos);
                return transformScraper(location, mimeType, scraper);
            } else {
                return null;
            }
        } catch (Exception e) {
            // e.printStackTrace();
            return null;
        } finally {
            if ((theParser != null) && (supportedMimeTypesContains(mimeType))) {
                try { plasmaParser.theParserPool.returnObject(mimeType, theParser); } catch (Exception e) { }
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

        mimeType = getRealMimeType(mimeType);        
        try {
            
            // determining the proper parser class name for the mimeType
            String parserClassName = null;
            synchronized (plasmaParser.enabledParserList) {
    	        if (plasmaParser.enabledParserList.containsKey(mimeType)) {
    	            parserClassName = (String)plasmaParser.enabledParserList.get(mimeType);
    	        } else {
                    return null;
    	        }
			}
            
            // fetching a new parser object from pool  
			Parser theParser = (Parser) this.theParserPool.borrowObject(parserClassName);
            
            // checking if the created parser really supports the given mimetype 
            Hashtable supportedMimeTypes = theParser.getSupportedMimeTypes();
            if ((supportedMimeTypes != null) && (supportedMimeTypes.containsKey(mimeType))) {
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
			plasmaParser theParser = new plasmaParser();
            theParser.initRealtimeParsableMimeTypes("application/xhtml+xml,text/html,text/plain");
            theParser.initParseableMimeTypes("application/atom+xml,application/gzip,application/java-archive,application/msword,application/octet-stream,application/pdf,application/rdf+xml,application/rss+xml,application/rtf,application/x-gzip,application/x-tar,application/xml,application/zip,text/rss,text/rtf,text/xml,application/x-bzip2");
			FileInputStream theInput = new FileInputStream(in);
			ByteArrayOutputStream theOutput = new ByteArrayOutputStream();
			serverFileUtils.copy(theInput, theOutput);
			plasmaParserDocument document = theParser.parseSource(new URL("http://brain/~theli/test.pdf"), null, theOutput.toByteArray());
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
        
        // loading class by name
        Class moduleClass = Class.forName((String)key);
        
        // instantiating class
        Parser theParser = (Parser) moduleClass.newInstance();
        
        // setting logger that should by used
        String parserShortName = ((String)key).substring("de.anomic.plasma.parser.".length(),((String)key).lastIndexOf("."));
        
        serverLog theLogger = new serverLog("PARSER." + parserShortName.toUpperCase());
        theParser.setLogger(theLogger);
        
        return theParser;
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


