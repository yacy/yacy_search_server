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

// compile: javac -classpath lib/commons-collections.jar:lib/commons-pool.jar -sourcepath source source/de/anomic/plasma/plasmaParser.java

package de.anomic.plasma;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
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

import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.htmlFilter.htmlFilterInputStream;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.http.httpc;
import de.anomic.net.URL;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.plasma.parser.ParserInfo;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

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
    private static final HashSet mediaExtSet = new HashSet();
    
    /**
     * A list of image, audio, video and application extensions
     */
    private static final HashSet imageExtSet = new HashSet();
    private static final HashSet audioExtSet = new HashSet();
    private static final HashSet videoExtSet = new HashSet();
    private static final HashSet appsExtSet = new HashSet();
    
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
        String apps = "sit,hqx,img,dmg,exe,com,bat,sh,zip,jar";
        String audio = "mp2,mp3,ogg,aac,aif,aiff,wav,ogg";
        String video = "swf,avi,wmv,rm,mov,mpg,mpeg,ram,m4v";
        String image = "jpg,jpeg,jpe,gif,png";
        initMediaExt(extString2extList(
                apps + "," +  // application container
                "tar,gz,bz2,arj,zip,rar," + // archive formats
                "ps,xls,ppt,asf," +         // text formats without support
                audio + "," +               // audio formats
                video + "," +               // video formats
                image                       // image formats
                ));
        initImageExt(extString2extList(image));  // image formats
        initAudioExt(extString2extList(audio));  // audio formats
        initVideoExt(extString2extList(video));  // video formats
        initAppsExt(extString2extList(apps));    // application formats
                
        
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
    
    public serverLog getLogger() {
        return this.theLogger;
    }
    
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
    
    public static void initImageExt(List imageExtList) {
        synchronized (imageExtSet) {
            imageExtSet.clear();
            imageExtSet.addAll(imageExtList);
        }
    }
    
    public static void initAudioExt(List audioExtList) {
        synchronized (audioExtSet) {
            audioExtSet.clear();
            audioExtSet.addAll(audioExtList);
        }
    }
    
    public static void initVideoExt(List videoExtList) {
        synchronized (videoExtSet) {
            videoExtSet.clear();
            videoExtSet.addAll(videoExtList);
        }
    }
    
    public static void initAppsExt(List appsExtList) {
        synchronized (appsExtSet) {
            appsExtSet.clear();
            appsExtSet.addAll(appsExtList);
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
        String name = url.getPath();
        
        // tetermining last position of / in the file path
        int p = name.lastIndexOf('/');
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
        
        if (supportedFileExtContains(mediaExt)) return false;
        
        synchronized (mediaExtSet) {
			return mediaExtSet.contains(mediaExt);
		}
    }

    public static boolean imageExtContains(String imageExt) {
        if (imageExt == null) return false;
        synchronized (imageExtSet) {
            return imageExtSet.contains(imageExt.trim().toLowerCase());
        }
    }

    public static boolean audioExtContains(String audioExt) {
        if (audioExt == null) return false;
        synchronized (audioExtSet) {
            return audioExtSet.contains(audioExt.trim().toLowerCase());
        }
    }

    public static boolean videoExtContains(String videoExt) {
        if (videoExt == null) return false;
        synchronized (videoExtSet) {
            return videoExtSet.contains(videoExt.trim().toLowerCase());
        }
    }

    public static boolean appsExtContains(String appsExt) {
        if (appsExt == null) return false;
        synchronized (appsExtSet) {
            return appsExtSet.contains(appsExt.trim().toLowerCase());
        }
    }

    public static String getRealCharsetEncoding(String encoding) {
    	if ((encoding == null) || (encoding.length() == 0)) return "ISO-8859-1";
    	
    	// trim encoding string
    	encoding = encoding.trim();
    	
    	if (encoding.toLowerCase().startsWith("windows") && encoding.length() > 7) {
    		char c = encoding.charAt(7);
    		if (c == '_') encoding = "windows-" + encoding.substring(8);
    		else if ((c >= '0') && (c <= '9')) encoding = "windows-" + encoding.substring(7);
    	}
    	
    	if (encoding.toLowerCase().startsWith("iso") && encoding.length() > 3) {
    		char c = encoding.charAt(3);
    		if (c == '_') encoding = "ISO-" + encoding.substring(4);
    		else if ((c >= '0') && (c <= '9')) encoding = "ISO-" + encoding.substring(3);    		
    	}
    	
    	if (encoding.toLowerCase().startsWith("iso") && encoding.length() > 8) {
    		char c = encoding.charAt(8);
    		if (c == '_') encoding = encoding.substring(0,8) + "-" + encoding.substring(9);
    		else if ((c >= '0') && (c <= '9')) encoding = encoding.substring(0,8) + "-" + encoding.substring(8);    		
    	}    	

    	
    	// converting cp\d{4} -> windows-\d{4}
    	if (encoding.toLowerCase().matches("cp([_-])?125[0-8]")) {
    		char c = encoding.charAt(2);
    		if (c == '_' || c == '-') encoding = "windows-" + encoding.substring(3);
    		else if ((c >= '0') && (c <= '9')) encoding = "windows-" + encoding.substring(2);    		
    	}    	
    	
    	if (encoding.toLowerCase().matches("gb[_-]?2312([-_]80)?")) {
    		encoding = "x-EUC-CN";
    	}
    	
    	if (encoding.toLowerCase().matches(".*utf[-_]?8.*")) {
    		encoding = "UTF-8";
    	}
    	
    	
    	return encoding;
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
                                    throw new Exception("Missing dependency detected: '" + neededLibx[libxId] + "'.");
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
        } catch (Exception e) {/* ignore this */}
    }    
    
    public plasmaParserDocument parseSource(URL location, String mimeType, String charset, byte[] sourceArray) 
    throws InterruptedException, ParserException {
        ByteArrayInputStream byteIn = null;
        try {
            if (this.theLogger.isFine())
                this.theLogger.logFine("Parsing '" + location + "' from byte-array");
            
            // testing if the resource is not empty
            if (sourceArray == null || sourceArray.length == 0) {
                String errorMsg = "No resource content available.";
                this.theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg,location,plasmaCrawlEURL.DENIED_NOT_PARSEABLE_NO_CONTENT);
            }              
            
            // creating an InputStream
            byteIn = new ByteArrayInputStream(sourceArray);
            
            // parsing the temp file
            return parseSource(location, mimeType, charset, sourceArray.length, byteIn);
            
        } catch (Exception e) {
            // Interrupted- and Parser-Exceptions should pass through
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            // log unexpected error
            this.theLogger.logSevere("Unexpected exception in parseSource from byte-array: " + e.getMessage(), e);
            throw new ParserException("Unexpected exception while parsing " + location,location, e);
        } finally {
            if (byteIn != null) try { byteIn.close(); } catch (Exception ex){/* ignore this */}
        }
        
    }

    public plasmaParserDocument parseSource(URL location, String theMimeType, String theDocumentCharset, File sourceFile) throws InterruptedException, ParserException {
        
        BufferedInputStream sourceStream = null;
        try {
            if (this.theLogger.isFine())
                this.theLogger.logFine("Parsing '" + location + "' from file");
            
            // testing if the resource is not empty
            if (!(sourceFile.exists() && sourceFile.canRead() && sourceFile.length() > 0)) {
                String errorMsg = sourceFile.exists() ? "Empty resource file." : "No resource content available.";
                this.theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg,location,plasmaCrawlEURL.DENIED_NOT_PARSEABLE_NO_CONTENT);
            }        
            
            // create a new InputStream
            sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
            
            // parsing the data
            return this.parseSource(location, theMimeType, theDocumentCharset, sourceFile.length(),  sourceStream);
            
        } catch (Exception e) {
            // Interrupted- and Parser-Exceptions should pass through
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;

            // log unexpected error
            this.theLogger.logSevere("Unexpected exception in parseSource from File: " + e.getMessage(), e);
            throw new ParserException("Unexpected exception while parsing " + location,location, e);
        } finally {
            if (sourceStream != null) try { sourceStream.close(); } catch (Exception ex){/* ignore this */}
        }
    }
    
    /**
     * To parse a resource from an {@link InputStream}
     * @param location the URL of the resource
     * @param theMimeType the resource mimetype (<code>null</code> if unknown)
     * @param theDocumentCharset the charset of the resource (<code>null</code> if unknown)
     * @param contentLength the content length of the resource (<code>-1</code> if unknown)
     * @param sourceStream an {@link InputStream} containing the resource body 
     * @return the parsed {@link plasmaParserDocument document}
     * @throws InterruptedException
     * @throws ParserException
     */
    public plasmaParserDocument parseSource(URL location, String theMimeType, String theDocumentCharset, long contentLength, InputStream sourceStream) throws InterruptedException, ParserException {        
        Parser theParser = null;
        String mimeType = null;
        try {
            if (this.theLogger.isFine())
                this.theLogger.logFine("Parsing '" + location + "' from stream");            
            
            // getting the mimetype of the document
            mimeType = getRealMimeType(theMimeType);
            
            // getting the file extension of the document
            String fileExt = getFileExt(location);
            
            // getting the charset of the document
            // TODO: do a charset detection here ....
            String documentCharset = getRealCharsetEncoding(theDocumentCharset);
            
            // testing if parsing is supported for this resource
            if (!plasmaParser.supportedContent(location,mimeType)) {
                String errorMsg = "No parser available to parse mimetype '" + mimeType + "'";
                this.theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg,location,plasmaCrawlEURL.DENIED_WRONG_MIMETYPE_OR_EXT);
            }
            
            if (this.theLogger.isFine())
                this.theLogger.logInfo("Parsing " + location + " with mimeType '" + mimeType + 
                                       "' and file extension '" + fileExt + "'.");                
            
            // getting the correct parser for the given mimeType
            theParser = this.getParser(mimeType);
            
            // if a parser was found we use it ...
            plasmaParserDocument doc = null;
            if (theParser != null) {
                // set the content length of the resource
                theParser.setContentLength(contentLength);
                // parse the resource
                doc = theParser.parse(location, mimeType,documentCharset,sourceStream);
            } else if (realtimeParsableMimeTypesContains(mimeType)) {                      
                doc = parseHtml(location, mimeType, documentCharset, sourceStream);
            } else {
                String errorMsg = "No parser available to parse mimetype '" + mimeType + "'";
                this.theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg,location,plasmaCrawlEURL.DENIED_WRONG_MIMETYPE_OR_EXT);                
            }
            
            // check result
            if (doc == null) {
                String errorMsg = "Unexpected error. Parser returned null.";
                this.theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg,location);                
            }
            return doc;
            
        } catch (UnsupportedEncodingException e) {
            String errorMsg = "Unsupported charset encoding: " + e.getMessage();
            this.theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg);
            throw new ParserException(errorMsg,location,plasmaCrawlEURL.DENIED_UNSUPPORTED_CHARSET);                	
        } catch (Exception e) {
            // Interrupted- and Parser-Exceptions should pass through
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            // log unexpected error
            String errorMsg = "Unexpected exception. " + e.getMessage();
            this.theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg, e);
            throw new ParserException(errorMsg,location,e);            
            
        } finally {
            if (theParser != null) {
                try { plasmaParser.theParserPool.returnObject(mimeType, theParser); } catch (Exception e) { /* ignore this */}
            }
        }        
    }
    
    private plasmaParserDocument parseHtml(URL location, String mimeType, String documentCharset, InputStream sourceStream) throws IOException, ParserException {
        
        // ...otherwise we make a scraper and transformer
        htmlFilterInputStream htmlFilter = new htmlFilterInputStream(sourceStream,documentCharset,location,null,false);
        String charset = htmlFilter.detectCharset();
        if (charset == null) {
            charset = documentCharset;
        } else {
        	charset = getRealCharsetEncoding(charset);
        }
        
        if (!documentCharset.equalsIgnoreCase(charset)) {
            this.theLogger.logInfo("Charset transformation needed from '" + documentCharset + "' to '" + charset + "'");
        }
        
        // parsing the content
        htmlFilterContentScraper scraper = new htmlFilterContentScraper(location);        
        htmlFilterWriter writer = new htmlFilterWriter(null,null,scraper,null,false);
        serverFileUtils.copy(htmlFilter, writer, charset);
        writer.close();
        //OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);            
        //serverFileUtils.copy(sourceFile, hfos);
        //hfos.close();
        if (writer.binarySuspect()) {
            String errorMsg = "Binary data found in resource";
            this.theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg);
            throw new ParserException(errorMsg,location);    
        }
        return transformScraper(location, mimeType, documentCharset, scraper);        
    }
    
    public plasmaParserDocument transformScraper(URL location, String mimeType, String charSet, htmlFilterContentScraper scraper) {
        try {
            String[] sections = new String[scraper.getHeadlines(1).length + scraper.getHeadlines(2).length + scraper.getHeadlines(3).length + scraper.getHeadlines(4).length];
            int p = 0;
            for (int i = 1; i <= 4; i++) for (int j = 0; j < scraper.getHeadlines(i).length; j++) sections[p++] = scraper.getHeadlines(i)[j];
            plasmaParserDocument ppd =  new plasmaParserDocument(
                    new URL(location.toNormalform()),
                    mimeType,
                    charSet,
                    scraper.getKeywords(),
                    scraper.getTitle(),
                    scraper.getAuthor(),
                    sections,
                    scraper.getDescription(),
                    scraper.getText(),
                    scraper.getAnchors(),
                    scraper.getImages());
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
     * @param mimeType MIME-Type of the resource
     * @return the {@link Parser}-class that is supposed to parse the resource of
     * the given MIME-Type
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
    
    static Map allReflinks(Set links) {
        // links is either a Set of Strings (with urls) or htmlFilterImageEntries
        // we find all links that are part of a reference inside a url
        HashMap v = new HashMap();
        Iterator i = links.iterator();
        Object o;
        String url;
        int pos;
        loop: while (i.hasNext()) {
            o = i.next();
            if (o instanceof String) url = (String) o;
            else if (o instanceof htmlFilterImageEntry) url = ((htmlFilterImageEntry) o).url().toNormalform();
            else {
                assert false;
                continue;
            }
            if ((pos = url.toLowerCase().indexOf("http://",7)) > 0) {
                i.remove();
                url = url.substring(pos);
                while ((pos = url.toLowerCase().indexOf("http://",7)) > 0) url = url.substring(pos);
                if (!(v.containsKey(url))) v.put(url, "ref");
                continue loop;
            }
            if ((pos = url.toLowerCase().indexOf("/www.",7)) > 0) {
                i.remove();
                url = "http:/" + url.substring(pos);
                while ((pos = url.toLowerCase().indexOf("/www.",7)) > 0) url = "http:/" + url.substring(pos);
                if (!(v.containsKey(url))) v.put(url, "ref");
                continue loop;
            }
        }
        return v;
    }
    
    static Map allSubpaths(Set links) {
        // links is either a Set of Strings (urls) or a Set of htmlFilterImageEntries
        HashMap v = new HashMap();
        Iterator i = links.iterator();
        Object o;
        String url;
        int pos;
        while (i.hasNext()) {
            o = i.next();
            if (o instanceof String) url = (String) o;
            else if (o instanceof htmlFilterImageEntry) url = ((htmlFilterImageEntry) o).url().toNormalform();
            else {
                assert false;
                continue;
            }
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            pos = url.lastIndexOf("/");
            while (pos > 8) {
                url = url.substring(0, pos + 1);
                if (!(v.containsKey(url))) v.put(url, "sub");
                url = url.substring(0, pos);
                pos = url.lastIndexOf("/");
            }
        }
        return v;
    }
    
    public static void main(String[] args) {
        //javac -classpath lib/commons-collections.jar:lib/commons-pool-1.2.jar -sourcepath source source/de/anomic/plasma/plasmaParser.java
        //java -cp source:lib/commons-collections.jar:lib/commons-pool-1.2.jar de.anomic.plasma.plasmaParser bug.html bug.out
    	httpc remote = null;
        try {
            Object content = null;
            URL contentURL = null;
            long contentLength = -1;
            String contentMimeType = "application/octet-stream";
            String charSet = "UTF-8";
            
            if (args.length < 2) {
                System.err.println("Usage: java de.anomic.plasma.plasmaParser (-f filename|-u URL) [-m mimeType]");
            }            
                        
            String mode = args[0];
            if (mode.equalsIgnoreCase("-f")) {
                content = new File(args[1]);
                contentURL = new URL((File)content);
            } else if (mode.equalsIgnoreCase("-u")) {
                contentURL = new URL(args[1]);
                
                // downloading the document content
                remote = httpc.getInstance(
                		contentURL.getHost(),
                		contentURL.getHost(),
                		contentURL.getPort(),
                		5000,
                		contentURL.getProtocol().equalsIgnoreCase("https"));
                
                httpc.response res = remote.GET(contentURL.getFile(), null);
                if (res.statusCode != 200) {
                	System.err.println("Unable to download " + contentURL + ". " + res.status);
                	return;
                }
				content = res.getContentInputStream();
                contentMimeType = res.responseHeader.mime();
                charSet = res.responseHeader.getCharacterEncoding();
                contentLength = res.responseHeader.contentLength();
            }
            
            if ((args.length >= 4)&&(args[2].equalsIgnoreCase("-m"))) {
                contentMimeType = args[3];
            }
            
            if ((args.length >= 6)&&(args[4].equalsIgnoreCase("-c"))) {
                charSet = args[5];
            }            
            
            // creating a plasma parser
            plasmaParser theParser = new plasmaParser();
            
            // configuring the realtime parsable mimeTypes
            plasmaParser.initRealtimeParsableMimeTypes("application/xhtml+xml,text/html,text/plain");
            
            // configure all other supported mimeTypes
            plasmaParser.enableAllParsers(PARSER_MODE_PROXY);

            // parsing the content
            plasmaParserDocument document = null;
            if (content instanceof byte[]) {
                document = theParser.parseSource(contentURL, contentMimeType, charSet, (byte[])content);
            } else if (content instanceof File) {
                document = theParser.parseSource(contentURL, contentMimeType, charSet, (File)content);
            } else if (content instanceof InputStream) {
            	document = theParser.parseSource(contentURL, contentMimeType, charSet, contentLength, (InputStream)content);
            }

            // printing out all parsed sentences
            if (document != null) {
                System.out.print("Document titel: ");
                System.out.println(document.getTitle());
                
                // found text
                final Iterator sentences = document.getSentences(false);
                int i = 0;
                if (sentences != null) while (sentences.hasNext()) {
                        System.out.print("line " + i + ": ");
                        System.out.println(((StringBuffer) sentences.next()).toString());
                        i++;
                }
                
                // found links
                int anchorNr = 0;
                Map anchors = document.getAnchors();
                Iterator anchorIter = anchors.keySet().iterator();
                while (anchorIter.hasNext()) {
                    String key = (String) anchorIter.next();
                    System.out.println("URL " + anchorNr + ":\t" + key + " | " + anchors.get(key));
                    anchorNr++;
                }
                document.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        	if (remote != null) try { httpc.returnInstance(remote); } catch (Exception e) {}
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

    public plasmaParserPool(plasmaParserFactory objFactory, GenericKeyedObjectPool.Config config) {
        super(objFactory, config);
    }
    

    public Object borrowObject(Object key) throws Exception  {
       return super.borrowObject(key);
    }

    public void returnObject(Object key, Object borrowed) throws Exception  {
        super.returnObject(key,borrowed);
    }        
}   
