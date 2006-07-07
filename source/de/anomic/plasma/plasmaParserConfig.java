/**
 * 
 */
package de.anomic.plasma;

import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserInfo;
import de.anomic.server.logging.serverLog;

public class plasmaParserConfig {
    /**
     * A list containing all enabled parsers and the mimeType that they can handle
     * @see #loadEnabledParserList()
     * @see #setEnabledParserList(Enumeration)
     */
    final HashSet enabledParserList = new HashSet();    
    
    /**
     * A list of file extensions that are supported by all enabled parsers
     */
    final HashSet supportedFileExt = new HashSet();
    
    /**
     * Parsermode this configuration belongs to
     */
    public String parserMode = null;
    
    public plasmaParserConfig(String theParserMode) {
        if (!plasmaParser.PARSER_MODE.contains(theParserMode)) {
            throw new IllegalArgumentException("Unknown parser mode " + theParserMode);
        }
        
        this.parserMode = theParserMode;            
    }
    
    public boolean supportedContent(URL url, String mimeType) {
        // TODO: we need some exceptions here to index URLs like this
        //       http://www.musicabona.com/respighi/12668/cd/index.html.fr
        mimeType = plasmaParser.getRealMimeType(mimeType);
        if (
                mimeType.equals("text/html") ||
                mimeType.equals("application/xhtml+xml") ||
                mimeType.equals("text/plain")
            ) {
            return supportedMimeTypesContains(mimeType);
        }
        return supportedMimeTypesContains(mimeType) && supportedFileExt(url);
    }        
    
    public boolean supportedMimeTypesContains(String mimeType) {
        mimeType = plasmaParser.getRealMimeType(mimeType);
        
        synchronized (plasmaParser.realtimeParsableMimeTypes) {
            if (plasmaParser.realtimeParsableMimeTypes.contains(mimeType)) return true;
        }        

        synchronized (this.enabledParserList) { 
            return this.enabledParserList.contains(mimeType);
        }
    }        
    
    
    public boolean supportedFileExt(URL url) {
        if (url == null) throw new NullPointerException();
        
        // getting the file path
        String name = plasmaParser.getFileExt(url);
        return supportedFileExtContains(name);
    }
    
    public boolean supportedFileExtContains(String fileExt) {
        if (fileExt == null) return false;        
        fileExt = fileExt.trim().toLowerCase();

        synchronized (plasmaParser.supportedRealtimeFileExt) {
            if (plasmaParser.supportedRealtimeFileExt.contains(fileExt)) return true;
        }        

        synchronized(this.supportedFileExt) {
            return this.supportedFileExt.contains(fileExt);
        }
    }        
    
    public void initParseableMimeTypes(String enabledMimeTypes) {
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
    
    public void enableAllParsers() {
        Set availableMimeTypes = plasmaParser.availableParserList.keySet();
        setEnabledParserList(availableMimeTypes);
    }
    
    public  String[] setEnabledParserList(Set mimeTypeSet) {
        
        HashSet newEnabledParsers = new HashSet();
        HashSet newSupportedFileExt = new HashSet();
        
        if (mimeTypeSet != null) {
            Iterator mimeTypes = mimeTypeSet.iterator();
            while (mimeTypes.hasNext()) {
                String mimeType = (String) mimeTypes.next();
                if (plasmaParser.availableParserList.containsKey(mimeType)) {
                    Parser theParser = null;
                    try {
                        // getting the parser
                        theParser = (Parser) plasmaParser.theParserPool.borrowObject(((ParserInfo)plasmaParser.availableParserList.get(mimeType)).parserClassName);
                        
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
                        newEnabledParsers.add(mimeType);
                        
                    } catch (Exception e) {
                        serverLog.logSevere("PARSER", "error in setEnabledParserList", e);
                    } finally {
                        if (theParser != null)
                            try { plasmaParser.theParserPool.returnObject(mimeType,theParser); } catch (Exception e) {}
                    }
                }
            }
        }
        
        synchronized (this.enabledParserList) {
            this.enabledParserList.clear();
            this.enabledParserList.addAll(newEnabledParsers);
        }
        
        
        synchronized (this.supportedFileExt) {
            this.supportedFileExt.clear();
            this.supportedFileExt.addAll(newSupportedFileExt);
        }

        return (String[])newEnabledParsers.toArray(new String[newEnabledParsers.size()]);
    }
    
    public HashSet getEnabledParserList() {
        synchronized (this.enabledParserList) {
            return (HashSet) this.enabledParserList.clone();
        }        
    }
}