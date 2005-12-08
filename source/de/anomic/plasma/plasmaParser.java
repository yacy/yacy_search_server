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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.http.httpc;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.plasma.parser.ParserInfo;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;

public final class plasmaParser {
    public static final String PARSER_MODE_PROXY   = "PROXY";
    public static final String PARSER_MODE_CRAWLER = "CRAWLER";
    public static final String PARSER_MODE_URLREDIRECTOR = "URLREDIRECTOR";
    public static final String PARSER_MODE_ICAP = "ICAP";
    public static final HashSet PARSER_MODE = new HashSet(Arrays.asList(new String[]{
            PARSER_MODE_PROXY,
            PARSER_MODE_CRAWLER,
            PARSER_MODE_ICAP,
            PARSER_MODE_URLREDIRECTOR
    }));
    
    private static final HashMap parserConfigList = new HashMap();
    
    /**
     * A list containing all installed parsers and the mimeType that they support
     * @see #loadAvailableParserList()
     */
    static final Properties availableParserList = new Properties();
    
    /**
     * A list of file extensions that are supported by the html-parser and can
     * be parsed in realtime.
     */
    static final HashSet supportedRealtimeFileExt = new HashSet();
    
    /**
     * A list of mimeTypes that can be parsed in Realtime (on the fly)
     */
    static final HashSet realtimeParsableMimeTypes = new HashSet();    
    
    private static final Properties mimeTypeLookupByFileExt = new Properties();
    static {
        // loading a list of extensions from file
        BufferedInputStream bufferedIn = null;
        try {            
            mimeTypeLookupByFileExt.load(bufferedIn = new BufferedInputStream(new FileInputStream(new File("httpd.mime"))));
        } catch (IOException e) {
            System.err.println("ERROR: httpd.mime not found in settings path");
        } finally {
            if (bufferedIn != null) try{bufferedIn.close();}catch(Exception e){}
        }    
    }
    
    /**
     * A pool of parsers.
     * @see plasmaParserPool
     * @see plasmaParserFactory
     */
    static plasmaParserPool theParserPool;

    /**
     * A list of media extensions that should <b>not</b> be handled by the plasmaParser
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
        initMediaExt(extString2extList("swf,wmv,jpg,jpeg,jpe,rm,mov,mpg,mpeg,mp3,asf,gif,png,avi,zip,rar," +
        "sit,hqx,img,dmg,tar,gz,ps,xls,ppt,ram,bz2,arj"));
        
        /* ===================================================
         * initializing the parser object pool
         * =================================================== */
        GenericKeyedObjectPool.Config config = new GenericKeyedObjectPool.Config();
        
        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        config.maxActive = 0;
        
        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        config.maxIdle = 5;    
        
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        config.minEvictableIdleTimeMillis = 30000; 
        
        plasmaParser.theParserPool = new plasmaParserPool(new plasmaParserFactory(),config);    
        
        /* ===================================================
         * loading a list of available parsers
         * =================================================== */        
        loadAvailableParserList();      
    }
    
    private serverLog theLogger = new serverLog("PARSER");
    
    public static HashMap getParserConfigList() {
        return parserConfigList;
    }
    
    /**
     * This function is used to initialize the realtimeParsableMimeTypes List.
     * This list contains a list of mimeTypes that can be parsed in realtime by
     * the yacy html-Parser
     * @param realtimeParsableMimeTypes a list of mimetypes that can be parsed by the 
     * yacy html parser
     */
    public static void initRealtimeParsableMimeTypes(String realtimeParsableMimeTypes) {
        LinkedList mimeTypes = new LinkedList();
        if ((realtimeParsableMimeTypes == null) || (realtimeParsableMimeTypes.length() == 0)) {
            // Nothing todo here
        } else {            
            String[] realtimeParsableMimeTypeList = realtimeParsableMimeTypes.split(",");        
            for (int i = 0; i < realtimeParsableMimeTypeList.length; i++) mimeTypes.add(realtimeParsableMimeTypeList[i].toLowerCase().trim());
        }
        initRealtimeParsableMimeTypes(mimeTypes);
    }
    
    /**
     * This function is used to initialize the realtimeParsableMimeTypes List.
     * This list contains a list of mimeTypes that can be parsed in realtime by
     * the yacy html-Parser
     * @param realtimeParsableMimeTypes a list of mimetypes that can be parsed by the 
     * yacy html parser
     */    
    public static void initRealtimeParsableMimeTypes(List mimeTypesList) {
        synchronized (realtimeParsableMimeTypes) {
            realtimeParsableMimeTypes.clear();
            realtimeParsableMimeTypes.addAll(mimeTypesList);
        }        
    }
    

    
    public static List extString2extList(String extString) {
        LinkedList extensions = new LinkedList();
        if ((extString == null) || (extString.length() == 0)) {
            return extensions;
        } else {
            String[] xs = extString.split(",");
            for (int i = 0; i < xs.length; i++) extensions.add(xs[i].toLowerCase().trim());
        }
        return extensions;
    }
    
    public static void initMediaExt(List mediaExtList) {
        synchronized (mediaExtSet) {
            mediaExtSet.clear();
            mediaExtSet.addAll(mediaExtList);
        }
    }
    
    public static String getMediaExtList() {
        synchronized (mediaExtSet) {
            return mediaExtSet.toString();
        }        
    }
    
    public static void initSupportedRealtimeFileExt(List supportedRealtimeFileExtList) {
        synchronized (supportedRealtimeFileExt) {
            supportedRealtimeFileExt.clear();
            supportedRealtimeFileExt.addAll(supportedRealtimeFileExtList);
        }
    }
        
    public static boolean realtimeParsableMimeTypesContains(String mimeType) {
        mimeType = getRealMimeType(mimeType);
        synchronized (realtimeParsableMimeTypes) {
            return realtimeParsableMimeTypes.contains(mimeType);
        }
    }
    
    public static boolean supportedRealTimeContent(URL url, String mimeType) {
        return realtimeParsableMimeTypesContains(mimeType) && supportedRealtimeFileExtContains(url);
    }    
    
    public static boolean supportedRealtimeFileExtContains(URL url) {
        String fileExt = getFileExt(url);
        synchronized (supportedRealtimeFileExt) {
            return supportedRealtimeFileExt.contains(fileExt);
        }   
    }

    
    public static String getFileExt(URL url) {
        // getting the file path
        String name = url.getFile();
        
        // chopping http parameters from the url
        int p = name.lastIndexOf('?');
        if (p != -1) {
            name = name.substring(0,p);
        }
        
        // tetermining last position of / in the file path
        p = name.lastIndexOf('/');
        if (p != -1) {
            name = name.substring(p);
        }
            
        // termining last position of . in file path
        p = name.lastIndexOf('.');
        if (p < 0) return ""; 
        return name.substring(p + 1);        
    }

    
    public static boolean mediaExtContains(String mediaExt) {
        if (mediaExt == null) return false;
        mediaExt = mediaExt.trim().toLowerCase();
        
        synchronized (supportedRealtimeFileExt) {
            if (supportedRealtimeFileExt.contains(mediaExt)) return false;
        }        
        
        synchronized (mediaExtSet) {
			return mediaExtSet.contains(mediaExt);
		}
    }

    public static String getRealMimeType(String mimeType) {
        //if (mimeType == null) doMimeTypeAnalysis
        if (mimeType == null) mimeType = "application/octet-stream";
        mimeType = mimeType.trim().toLowerCase();
        
        int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType : mimeType.substring(0, pos));              
    }
    
    public static String getMimeTypeByFileExt(String fileExt) {        
        return mimeTypeLookupByFileExt.getProperty(fileExt,"application/octet-stream");
    }

    public Hashtable getAvailableParserList() {
        return plasmaParser.availableParserList;
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
            serverLog.logFine("PARSER", "Parser directory is " + packageURI);
            
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
                serverLog.logFine("PARSER", "Searching in directory " + currentDir.toString());
                
                String[] parserClasses = currentDir.list(parserFileNameFilter);
                if (parserClasses == null) continue;
                
                for (int parserNr=0; parserNr<parserClasses.length; parserNr++) {
                    serverLog.logFine("PARSER", "Testing parser class " + parserClasses[parserNr]);
                    String className = parserClasses[parserNr].substring(0,parserClasses[parserNr].indexOf(".class"));
                    String fullClassName = plasmaParserPkgName + "." + currentDir.getName() + "." + className;
                    try {
                        // trying to load the parser class by its name
                        Class parserClass = Class.forName(fullClassName);
                        Object theParser = parserClass.newInstance();
                        if (!(theParser instanceof Parser)) continue;
                        
                        // testing if all needed libx libraries are available
                        String[] neededLibx = ((Parser)theParser).getLibxDependences();
                        StringBuffer neededLibxBuf = new StringBuffer();
                        if (neededLibx != null) {
                            for (int libxId=0; libxId < neededLibx.length; libxId++) {
                                if (javaClassPath.indexOf(neededLibx[libxId]) == -1) {
                                    throw new ParserException("Missing dependency detected: '" + neededLibx[libxId] + "'.");
                                }
                                neededLibxBuf.append(neededLibx[libxId])
                                             .append(",");
                            }
                            if (neededLibxBuf.length()>0) neededLibxBuf.deleteCharAt(neededLibxBuf.length()-1);
                        }
                        
                        // loading the list of mime-types that are supported by this parser class
                        Hashtable supportedMimeTypes = ((Parser)theParser).getSupportedMimeTypes();
                        
                        // creating a parser info object
                        ParserInfo parserInfo = new ParserInfo();
                        parserInfo.parserClass = parserClass;
                        parserInfo.parserClassName = fullClassName;
                        parserInfo.libxDependencies = neededLibx;
                        parserInfo.supportedMimeTypes = supportedMimeTypes;
                        parserInfo.parserVersionNr = ((Parser)theParser).getVersion();
                        parserInfo.parserName = ((Parser)theParser).getName();
                        
                        Iterator mimeTypeIterator = supportedMimeTypes.keySet().iterator();
                        while (mimeTypeIterator.hasNext()) {
                            String mimeType = (String) mimeTypeIterator.next();
                            availableParserList.put(mimeType,parserInfo );
                            serverLog.logInfo("PARSER", "Found functional parser for mimeType '" + mimeType + "'." +
                                              "\n\tName:    " + parserInfo.parserName + 
                                              "\n\tVersion: " + parserInfo.parserVersionNr + 
                                              "\n\tClass:   " + parserInfo.parserClassName +
                                              ((neededLibxBuf.length()>0)?"\n\tDependencies: " + neededLibxBuf.toString():""));
                        }
                        
                    } catch (Exception e) { /* we can ignore this for the moment */
                        serverLog.logWarning("PARSER", "Parser '" + className + "' doesn't work correctly and will be ignored.\n [" + e.getClass().getName() + "]: " + e.getMessage());
                    } catch (Error e) { /* we can ignore this for the moment */
                        serverLog.logWarning("PARSER", "Parser '" + className + "' doesn't work correctly and will be ignored.\n [" + e.getClass().getName() + "]: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            serverLog.logSevere("PARSER", "Unable to determine all installed parsers. " + e.getMessage());
        }
    }
    
    public void close() {
        // clearing the parser list
        Iterator configs = parserConfigList.values().iterator();
        while (configs.hasNext()) {
            plasmaParserConfig currentConfig = (plasmaParserConfig) configs.next();
            synchronized (currentConfig.enabledParserList) {
                currentConfig.enabledParserList.clear();
            }
        }
        
        // closing the parser object pool
        try {
            theParserPool.close();
        } catch (Exception e) { }
    }    
    
    public plasmaParserDocument parseSource(URL location, String mimeType, byte[] source) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("parseSource", ".tmp");
            serverFileUtils.write(source, tempFile);
            return parseSource(location, mimeType, tempFile);
        } catch (Exception e) {   
            return null;
        } finally {
            if (tempFile != null) try { tempFile.delete(); } catch (Exception ex){}
        }
        
    }

    public plasmaParserDocument parseSource(URL location, String mimeType, File sourceFile) {

        Parser theParser = null;
        try {
            mimeType = getRealMimeType(mimeType);
            String fileExt = getFileExt(location);
            
            if (this.theLogger.isFine())
                this.theLogger.logFine("Parsing " + location + " with mimeType '" + mimeType + 
                                       "' and file extension '" + fileExt + "'.");
            
            /*
             * There are some problematic mimeType - fileExtension combination where we have to enforce
             * a mimeType detection to get the proper parser for the content
             * 
             * - application/zip + .odt
             * - text/plain + .odt
             * - text/plain + .vcf
             * - text/xml + .rss
             * - text/xml + .atom
             * 
             * In all these cases we can trust the fileExtension and have to determine the proper mimeType.
             * 
             */
            
//            // Handling of not trustable mimeTypes
//            // - text/plain
//            // - text/xml
//            // - application/octet-stream
//            // - application/zip
//            if (
//                    (mimeType.equalsIgnoreCase("text/plain") && !fileExt.equalsIgnoreCase("txt")) || 
//                    (mimeType.equalsIgnoreCase("text/xml")   && !fileExt.equalsIgnoreCase("txt")) 
//            ) {
//                if (this.theLogger.isFine())
//                    this.theLogger.logFine("Document " + location + " has an mimeType '" + mimeType + 
//                                           "' that seems not to be correct for file extension '" + fileExt + "'.");                
//                
//                if (enabledParserList.containsKey("application/octet-stream")) {
//                    theParser = this.getParser("application/octet-stream");
//                    Object newMime = theParser.getClass().getMethod("getMimeType", new Class[]{File.class}).invoke(theParser, sourceFile);
//                    if (newMime == null)
//                    if (newMime instanceof String) {
//                        String newMimeType = (String)newMime;
//                        if ((newMimeType.equals("application/octet-stream")) {
//                            return null;
//                        }
//                        mimeType = newMimeType;
//                    }
//                } else {
//                    return null;
//                }
//            } else if (mimeType.equalsIgnoreCase("application/zip") && fileExt.equalsIgnoreCase("odt")){
//                if (enabledParserList.containsKey("application/vnd.oasis.opendocument.text")) {
//                    mimeType = "application/vnd.oasis.opendocument.text";
//                } else {
//                    return null;
//                }
//            }        
            
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
                hfos.close();
                return transformScraper(location, mimeType, scraper);
            } else {
                return null;
            }
        } catch (Exception e) {
            //e.printStackTrace();
            return null;
        } finally {
            if (theParser != null) {
                try { plasmaParser.theParserPool.returnObject(mimeType, theParser); } catch (Exception e) { }
            }
        }
    }
    
    public plasmaParserDocument transformScraper(URL location, String mimeType, htmlFilterContentScraper scraper) {
        try {
            plasmaParserDocument ppd =  new plasmaParserDocument(new URL(htmlFilterContentScraper.urlNormalform(location)),
                                mimeType, null, null, scraper.getHeadline(),
                                null, null,
                                scraper.getText(), scraper.getAnchors(), scraper.getImages());
            //scraper.close();
            return ppd;
        } catch (MalformedURLException e) {
            //e.printStackTrace();
            return null;
        }
    }
    
    /**
     * This function is used to determine the parser class that should be used for a given
     * mimetype ...
     * @param mimeType
     * @return
     */
    private Parser getParser(String mimeType) {

        mimeType = getRealMimeType(mimeType);        
        try {
            
            // determining the proper parser class name for the mimeType
            String parserClassName = null;
            ParserInfo parserInfo = null;
            synchronized (plasmaParser.availableParserList) {
    	        if (plasmaParser.availableParserList.containsKey(mimeType)) {
                    parserInfo = (ParserInfo)plasmaParser.availableParserList.get(mimeType);
    	            parserClassName = parserInfo.parserClassName;
    	        } else {
                    return null;
    	        }
			}
            
            // fetching a new parser object from pool  
			Parser theParser = (Parser) theParserPool.borrowObject(parserClassName);
            
            // checking if the created parser really supports the given mimetype 
            Hashtable supportedMimeTypes = theParser.getSupportedMimeTypes();
            if ((supportedMimeTypes != null) && (supportedMimeTypes.containsKey(mimeType))) {
                parserInfo.incUsageCounter();
				return theParser;
            }
            theParserPool.returnObject(parserClassName,theParser);
            
        } catch (Exception e) {
            System.err.println("ERROR: Unable to load the correct parser for type " + mimeType);
        }
        
        return null;
        
    }
    
    /*
    public static String urlNormalform(URL url) {
        if (url == null) return null;
        return urlNormalform(url.toString());
    }
    
    public static String urlNormalform(String us) {
        return htmlFilterContentScraper.urlNormalform(us);
    }   
    */
    
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
            File contentFile = null;
            URL contentURL = null;
            String contentMimeType = "application/octet-stream";
            
            if (args.length < 2) {
                System.err.println("Usage: java de.anomic.plasma.plasmaParser (-f filename|-u URL) [-m mimeType]");
            }            
                        
            String mode = args[0];
            if (mode.equalsIgnoreCase("-f")) {
                contentFile = new File(args[1]);
                contentURL = contentFile.toURL();
            } else if (mode.equalsIgnoreCase("-u")) {
                contentURL = new URL(args[1]);
                
                // downloading the document content
                byte[] contentBytes = httpc.singleGET(contentURL, 10000, null, null, null);
                
                contentFile = File.createTempFile("content",".tmp");
                contentFile.deleteOnExit();
                serverFileUtils.write(contentBytes, contentFile);
            }
            
            if ((args.length == 4)&&(args[2].equalsIgnoreCase("-m"))) {
                contentMimeType = args[3];
            }
            
            // creating a plasma parser
            plasmaParser theParser = new plasmaParser();
            
            // configuring the realtime parsable mimeTypes
            plasmaParser.initRealtimeParsableMimeTypes("application/xhtml+xml,text/html,text/plain");
            
            // configure all other supported mimeTypes
            plasmaParser.enableAllParsers(PARSER_MODE_PROXY);

            // parsing the content
            plasmaParserDocument document = theParser.parseSource(contentURL, contentMimeType, contentFile);

            // printing out all parsed sentences
            if (document != null) {
                // found text
                String[] sentences = document.getSentences();
                if (sentences != null) for (int i = 0; i < sentences.length; i++) System.out.println("line " + i + ":" + sentences[i]);
                
                // found links
                int anchorNr = 0;
                Map anchors = document.getAnchors();
                Iterator anchorIter = anchors.keySet().iterator();
                while (anchorIter.hasNext()) {
                    String key = (String) anchorIter.next();
                    System.out.println("URL " + anchorNr + ":\t" + key + " | " + anchors.get(key));
                    anchorNr++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void enableAllParsers(String parserMode) {
        if (!PARSER_MODE.contains(parserMode)) throw new IllegalArgumentException();
        
        plasmaParserConfig config = (plasmaParserConfig) parserConfigList.get(parserMode);
        if (config == null) {
            config = new plasmaParserConfig(parserMode);
            parserConfigList.put(parserMode, config);
        }
        config.enableAllParsers();        
    }
    
    public static boolean supportedContent(URL url, String mimeType) {
        if (url == null) throw new NullPointerException();
        
        Iterator configs = parserConfigList.values().iterator();
        while (configs.hasNext()) {
            plasmaParserConfig currentConfig = (plasmaParserConfig) configs.next();
            synchronized (currentConfig.enabledParserList) {
                if (currentConfig.supportedContent(url, mimeType)) return true;
            }
        }        
        
        return false;
    }    

    public static boolean supportedContent(String parserMode, URL url, String mimeType) {
        if (!PARSER_MODE.contains(parserMode)) throw new IllegalArgumentException();
        if (url == null) throw new NullPointerException();
        
        plasmaParserConfig config = (plasmaParserConfig) parserConfigList.get(parserMode);
        return (config == null)?false:config.supportedContent(url, mimeType);
    }

    public static void initParseableMimeTypes(String parserMode, String configStr) {
        if (!PARSER_MODE.contains(parserMode)) throw new IllegalArgumentException();
        
        plasmaParserConfig config = (plasmaParserConfig) parserConfigList.get(parserMode);
        if (config == null) {
            config = new plasmaParserConfig(parserMode);
            parserConfigList.put(parserMode, config);
        }
        config.initParseableMimeTypes(configStr);
    }

    public static String[] setEnabledParserList(String parserMode, Set mimeTypeSet) {
        if (!PARSER_MODE.contains(parserMode)) throw new IllegalArgumentException();
        
        plasmaParserConfig config = (plasmaParserConfig) parserConfigList.get(parserMode);
        if (config == null) {
            config = new plasmaParserConfig(parserMode);
            parserConfigList.put(parserMode, config);
        }
        return config.setEnabledParserList(mimeTypeSet);        
    }
    
    public static boolean supportedFileExtContains(String fileExt) {
        Iterator configs = parserConfigList.values().iterator();
        while (configs.hasNext()) {
            plasmaParserConfig currentConfig = (plasmaParserConfig) configs.next();
            synchronized (currentConfig.enabledParserList) {
                if (currentConfig.supportedFileExtContains(fileExt)) return true;
            }
        }        
        
        return false;
    }

    public static boolean supportedMimeTypesContains(String mimeType) {
        Iterator configs = parserConfigList.values().iterator();
        while (configs.hasNext()) {
            plasmaParserConfig currentConfig = (plasmaParserConfig) configs.next();
            synchronized (currentConfig.enabledParserList) {
                if (currentConfig.supportedMimeTypesContains(mimeType)) return true;
            }
        }        
        
        return false;
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
    	/*
        if (obj instanceof Parser) {
            Parser theParser = (Parser) obj;
        }
    */
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object key, Object obj) {
        if (obj instanceof Parser) {
            //Parser theParser = (Parser) obj;
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
