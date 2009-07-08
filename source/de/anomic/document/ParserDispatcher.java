
package de.anomic.document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import de.anomic.document.parser.bzipParser;
import de.anomic.document.parser.docParser;
import de.anomic.document.parser.gzipParser;
import de.anomic.document.parser.mimeTypeParser;
import de.anomic.document.parser.odtParser;
import de.anomic.document.parser.pdfParser;
import de.anomic.document.parser.pptParser;
import de.anomic.document.parser.psParser;
import de.anomic.document.parser.rpmParser;
import de.anomic.document.parser.rssParser;
import de.anomic.document.parser.rtfParser;
import de.anomic.document.parser.sevenzipParser;
import de.anomic.document.parser.swfParser;
import de.anomic.document.parser.tarParser;
import de.anomic.document.parser.vcfParser;
import de.anomic.document.parser.vsdParser;
import de.anomic.document.parser.xlsParser;
import de.anomic.document.parser.zipParser;
import de.anomic.document.parser.html.ContentScraper;
import de.anomic.document.parser.html.ImageEntry;
import de.anomic.document.parser.html.ScraperInputStream;
import de.anomic.document.parser.html.TransformerWriter;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public final class ParserDispatcher {
 
 private static final ParserConfig parserConfig = new ParserConfig();
 
 /**
  * A list containing all installed parsers and the mimeType that they support
  * @see #loadAvailableParserList()
  */
 public static final HashMap<String, Parser> availableParserList = new HashMap<String, Parser>();
 
 /**
  * A list of file extensions and mime types that are supported by the html-parser
  */
 public static final HashSet<String> supportedHTMLFileExt = new HashSet<String>();
 public static final HashSet<String> supportedHTMLMimeTypes = new HashSet<String>();    
 
 private static final Properties mimeTypeLookupByFileExt = new Properties();
 static {
     // loading a list of extensions from file
     BufferedInputStream bufferedIn = null;
     try {            
         mimeTypeLookupByFileExt.load(bufferedIn = new BufferedInputStream(new FileInputStream(new File("httpd.mime"))));
     } catch (final IOException e) {
         System.err.println("ERROR: httpd.mime not found in settings path");
     } finally {
         if (bufferedIn != null) try{bufferedIn.close();}catch(final Exception e){}
     }    
 }

 /**
  * A list of media extensions that should <b>not</b> be handled by the Parser
  */
 private static final HashSet<String> mediaExtSet = new HashSet<String>();
 
 /**
  * A list of image, audio, video and application extensions
  */
 private static final HashSet<String> imageExtSet = new HashSet<String>();
 private static final HashSet<String> audioExtSet = new HashSet<String>();
 private static final HashSet<String> videoExtSet = new HashSet<String>();
 private static final HashSet<String> appsExtSet = new HashSet<String>();
 
 /**
  * This {@link FilenameFilter} is used to find all classes based on there filenames 
  * which seems to be additional content parsers.
  * Currently the filenames of all content parser classes must end with <code>Parser.class</code> 
  */
 /*
 private static final FilenameFilter parserFileNameFilter = new FilenameFilter() {
     public boolean accept(File dir, String name) {
         return name.endsWith("Parser.class");
     }
 };
 */
 
 /**
  * This {@link FileFilter} is used to get all subpackages
  * of the parser package.
  */
 /*
 private static final FileFilter parserDirectoryFilter = new FileFilter() {
     public boolean accept(File file) {
         return file.isDirectory();
     }
 };
 */    
 
 /**
  * Initializing the 
  * @see #initMediaExt(String)
  */
 static {
     final String apps = "sit,hqx,img,dmg,exe,com,bat,sh,vbs,zip,jar";
     final String audio = "mp2,mp3,ogg,aac,aif,aiff,wav";
     final String video = "swf,avi,wmv,rm,mov,mpg,mpeg,ram,m4v";
     final String image = "jpg,jpeg,jpe,gif,png,ico,bmp";
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
      * loading a list of available parsers
      * =================================================== */        
     loadAvailableParserList();      
 }
 
 private static final Log theLogger = new Log("PARSER");
 
 public Log getLogger() {
     return theLogger;
 }
 
 /**
  * This function is used to initialize the HTMLParsableMimeTypes List.
  * This list contains a list of mimeTypes that can be parsed in realtime by
  * the yacy html-Parser
  * @param htmlParsableMimeTypes a list of mimetypes that can be parsed by the 
  * yacy html parser
  */
 public static void initHTMLParsableMimeTypes(final String htmlParsableMimeTypes) {
     final LinkedList<String> mimeTypes = new LinkedList<String>();
     if ((htmlParsableMimeTypes == null) || (htmlParsableMimeTypes.length() == 0)) {
         return;
     }
     final String[] realtimeParsableMimeTypeList = htmlParsableMimeTypes.split(",");        
     for (int i = 0; i < realtimeParsableMimeTypeList.length; i++) {
         mimeTypes.add(realtimeParsableMimeTypeList[i].toLowerCase().trim());
     }
     synchronized (supportedHTMLMimeTypes) {
         supportedHTMLMimeTypes.addAll(mimeTypes);
     }        
 }
 
 public static List<String> extString2extList(final String extString) {
     final LinkedList<String> extensions = new LinkedList<String>();
     if ((extString == null) || (extString.length() == 0)) {
         return extensions;
     }
     final String[] xs = extString.split(",");
     for (int i = 0; i < xs.length; i++) extensions.add(xs[i].toLowerCase().trim());
     return extensions;
 }
 
 public static void initMediaExt(final List<String> mediaExtList) {
     synchronized (mediaExtSet) {
         mediaExtSet.addAll(mediaExtList);
     }
 }
 
 public static void initImageExt(final List<String> imageExtList) {
     synchronized (imageExtSet) {
         imageExtSet.addAll(imageExtList);
     }
 }
 
 public static void initAudioExt(final List<String> audioExtList) {
     synchronized (audioExtSet) {
         audioExtSet.addAll(audioExtList);
     }
 }
 
 public static void initVideoExt(final List<String> videoExtList) {
     synchronized (videoExtSet) {
         videoExtSet.addAll(videoExtList);
     }
 }
 
 public static void initAppsExt(final List<String> appsExtList) {
     synchronized (appsExtSet) {
         appsExtSet.addAll(appsExtList);
     }
 }
 
 public static String getMediaExtList() {
     synchronized (mediaExtSet) {
         return mediaExtSet.toString();
     }        
 }
 
 public static void initSupportedHTMLFileExt(final List<String> supportedRealtimeFileExtList) {
     synchronized (supportedHTMLFileExt) {
         supportedHTMLFileExt.addAll(supportedRealtimeFileExtList);
     }
 }
     
 public static boolean HTMLParsableMimeTypesContains(String mimeType) {
     mimeType = normalizeMimeType(mimeType);
     synchronized (supportedHTMLMimeTypes) {
         return supportedHTMLMimeTypes.contains(mimeType);
     }
 }
 
 public static boolean supportedHTMLContent(final yacyURL url, final String mimeType) {
     return HTMLParsableMimeTypesContains(mimeType) && supportedHTMLFileExtContains(url);
 }    
 
 public static boolean supportedHTMLFileExtContains(final yacyURL url) {
     final String fileExt = getFileExt(url);
     synchronized (supportedHTMLFileExt) {
         return supportedHTMLFileExt.contains(fileExt);
     }   
 }

 public static String getFileExt(final yacyURL url) {
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
     
     synchronized (supportedHTMLFileExt) {
         if (supportedHTMLFileExt.contains(mediaExt)) return false;
     }        
     
     if (supportedFileExtContains(mediaExt)) return false;
     
     synchronized (mediaExtSet) {
         return mediaExtSet.contains(mediaExt);
     }
 }

 public static boolean imageExtContains(final String imageExt) {
     if (imageExt == null) return false;
     synchronized (imageExtSet) {
         return imageExtSet.contains(imageExt.trim().toLowerCase());
     }
 }

 public static boolean audioExtContains(final String audioExt) {
     if (audioExt == null) return false;
     synchronized (audioExtSet) {
         return audioExtSet.contains(audioExt.trim().toLowerCase());
     }
 }

 public static boolean videoExtContains(final String videoExt) {
     if (videoExt == null) return false;
     synchronized (videoExtSet) {
         return videoExtSet.contains(videoExt.trim().toLowerCase());
     }
 }

 public static boolean appsExtContains(final String appsExt) {
     if (appsExt == null) return false;
     synchronized (appsExtSet) {
         return appsExtSet.contains(appsExt.trim().toLowerCase());
     }
 }

 /**
  * some html authors use wrong encoding names, either because they don't know exactly what they
  * are doing or they produce a type. Many times, the upper/downcase scheme of the name is fuzzy
  * This method patches wrong encoding names. The correct names are taken from
  * http://www.iana.org/assignments/character-sets
  * @param encoding
  * @return patched encoding name
  */
 public static String patchCharsetEncoding(String encoding) {
     
     // return the system default encoding
     if ((encoding == null) || (encoding.length() < 3)) return Charset.defaultCharset().name();
     
     // trim encoding string
     encoding = encoding.trim();

     // fix upper/lowercase
     encoding = encoding.toUpperCase();
     if (encoding.startsWith("SHIFT")) return "Shift_JIS";
     if (encoding.startsWith("BIG")) return "Big5";
     // all other names but such with "windows" use uppercase
     if (encoding.startsWith("WINDOWS")) encoding = "windows" + encoding.substring(7);
     if (encoding.startsWith("MACINTOSH")) encoding = "MacRoman";
     
     // fix wrong fill characters
     encoding = encoding.replaceAll("_", "-");

     if (encoding.matches("GB[_-]?2312([-_]80)?")) return "GB2312";
     if (encoding.matches(".*UTF[-_]?8.*")) return "UTF-8";
     if (encoding.startsWith("US")) return "US-ASCII";
     if (encoding.startsWith("KOI")) return "KOI8-R";
     
     // patch missing '-'
     if (encoding.startsWith("windows") && encoding.length() > 7) {
         final char c = encoding.charAt(7);
         if ((c >= '0') && (c <= '9')) {
             encoding = "windows-" + encoding.substring(7);
         }
     }
     
     if (encoding.startsWith("ISO")) {
         // patch typos
         if (encoding.length() > 3) {
             final char c = encoding.charAt(3);
             if ((c >= '0') && (c <= '9')) {
                 encoding = "ISO-" + encoding.substring(3);
             }
         }
         if (encoding.length() > 8) {
             final char c = encoding.charAt(8);
             if ((c >= '0') && (c <= '9')) {
                 encoding = encoding.substring(0, 8) + "-" + encoding.substring(8);           
             } 
         }
     }
     
     // patch wrong name
     if (encoding.startsWith("ISO-8559")) {
         // popular typo
         encoding = "ISO-8859" + encoding.substring(8);
     }

     // converting cp\d{4} -> windows-\d{4}
     if (encoding.matches("CP([_-])?125[0-8]")) {
         final char c = encoding.charAt(2);
         if ((c >= '0') && (c <= '9')) {
             encoding = "windows-" + encoding.substring(2);
         } else {
             encoding = "windows" + encoding.substring(2);
         }
     }

     return encoding;
 }
 
 public static String normalizeMimeType(String mimeType) {
     //if (mimeType == null) doMimeTypeAnalysis
     if (mimeType == null) mimeType = "application/octet-stream";
     mimeType = mimeType.trim().toLowerCase();
     
     final int pos = mimeType.indexOf(';');
     return ((pos < 0) ? mimeType : mimeType.substring(0, pos));              
 }
 
 public static String getMimeTypeByFileExt(final String fileExt) {        
     return mimeTypeLookupByFileExt.getProperty(fileExt,"application/octet-stream");
 }

 public static HashMap<String, Parser> getAvailableParserList() {
     return availableParserList;
 }    
 
 private static void loadAvailableParserList() {
     initParser(new bzipParser());
     initParser(new docParser());
     initParser(new gzipParser());
     initParser(new mimeTypeParser());
     initParser(new odtParser());
     initParser(new pdfParser());
     initParser(new pptParser());
     initParser(new psParser());
     initParser(new rpmParser());
     initParser(new rssParser());
     initParser(new rtfParser());
     initParser(new sevenzipParser());
     initParser(new swfParser());
     initParser(new tarParser());
     initParser(new vcfParser());
     initParser(new vsdParser());
     initParser(new xlsParser());
     initParser(new zipParser());
 }
 
 private static void initParser(Parser theParser) {
  // loading the list of mime-types that are supported by this parser class
     final Hashtable<String, String> supportedMimeTypes = theParser.getSupportedMimeTypes();
     
     final Iterator<String> mimeTypeIterator = supportedMimeTypes.keySet().iterator();
     while (mimeTypeIterator.hasNext()) {
         final String mimeType = mimeTypeIterator.next();
         availableParserList.put(mimeType, theParser);
         Log.logInfo("PARSER", "Found parser for mimeType '" + mimeType + "'." +
                   "\n\tName:    " + theParser.getName());
     }
 }
 
 public static Document parseSource(final yacyURL location, final String mimeType, final String charset, final byte[] sourceArray) 
 throws InterruptedException, ParserException {
     ByteArrayInputStream byteIn = null;
     try {
         if (theLogger.isFine())
             theLogger.logFine("Parsing '" + location + "' from byte-array");
         
         // testing if the resource is not empty
         if (sourceArray == null || sourceArray.length == 0) {
             final String errorMsg = "No resource content available (1) " + (((sourceArray == null) ? "source == null" : "source.length() == 0") + ", url = " + location.toNormalform(true, false));
             theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
             throw new ParserException(errorMsg,location, errorMsg);
         }              
         
         // creating an InputStream
         byteIn = new ByteArrayInputStream(sourceArray);
         
         // parsing the temp file
         return parseSource(location, mimeType, charset, sourceArray.length, byteIn);
         
     } catch (final Exception e) {
         // Interrupted- and Parser-Exceptions should pass through
         if (e instanceof InterruptedException) throw (InterruptedException) e;
         if (e instanceof ParserException) throw (ParserException) e;
         
         // log unexpected error
         theLogger.logSevere("Unexpected exception in parseSource from byte-array: " + e.getMessage(), e);
         throw new ParserException("Unexpected exception while parsing " + location,location, e);
     } finally {
         if (byteIn != null) try { byteIn.close(); } catch (final Exception ex){/* ignore this */}
     }
     
 }

 public static Document parseSource(final yacyURL location, final String theMimeType, final String theDocumentCharset, final File sourceFile) throws InterruptedException, ParserException {
     
     BufferedInputStream sourceStream = null;
     try {
         if (theLogger.isFine())
             theLogger.logFine("Parsing '" + location + "' from file");
         
         // testing if the resource is not empty
         if (!(sourceFile.exists() && sourceFile.canRead() && sourceFile.length() > 0)) {
             final String errorMsg = sourceFile.exists() ? "Empty resource file." : "No resource content available (2).";
             theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
             throw new ParserException(errorMsg,location, "document has no content");
         }        
         
         // create a new InputStream
         sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
         
         // parsing the data
         return parseSource(location, theMimeType, theDocumentCharset, sourceFile.length(),  sourceStream);
         
     } catch (final Exception e) {
         // Interrupted- and Parser-Exceptions should pass through
         if (e instanceof InterruptedException) throw (InterruptedException) e;
         if (e instanceof ParserException) throw (ParserException) e;

         // log unexpected error
         theLogger.logSevere("Unexpected exception in parseSource from File: " + e.getMessage(), e);
         throw new ParserException("Unexpected exception while parsing " + location,location, e);
     } finally {
         if (sourceStream != null) try { sourceStream.close(); } catch (final Exception ex){/* ignore this */}
     }
 }
 
 /**
  * To parse a resource from an {@link InputStream}
  * @param location the URL of the resource
  * @param theMimeType the resource mimetype (<code>null</code> if unknown)
  * @param theDocumentCharset the charset of the resource (<code>null</code> if unknown)
  * @param contentLength the content length of the resource (<code>-1</code> if unknown)
  * @param sourceStream an {@link InputStream} containing the resource body 
  * @return the parsed {@link ParserDocument document}
  * @throws InterruptedException
  * @throws ParserException
  */
 public static Document parseSource(final yacyURL location, final String theMimeType, final String theDocumentCharset, final long contentLength, final InputStream sourceStream) throws InterruptedException, ParserException {        
     Parser theParser = null;
     String mimeType = null;
     try {
         if (theLogger.isFine())
             theLogger.logFine("Parsing '" + location + "' from stream");            
         
         // getting the mimetype of the document
         mimeType = normalizeMimeType(theMimeType);
         
         // getting the file extension of the document
         final String fileExt = getFileExt(location);
         
         // getting the charset of the document
         // TODO: do a charset detection here ....
         final String documentCharset = patchCharsetEncoding(theDocumentCharset);
         
         // testing if parsing is supported for this resource
         if (!supportedContent(location,mimeType)) {
             final String errorMsg = "No parser available to parse mimetype '" + mimeType + "' (1)";
             theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
             throw new ParserException(errorMsg,location, "wrong mime type or wrong extension");
         }
         
         if (theLogger.isFine())
             theLogger.logInfo("Parsing " + location + " with mimeType '" + mimeType + 
                                    "' and file extension '" + fileExt + "'.");                
         
         // getting the correct parser for the given mimeType
         theParser = getParser(mimeType);
         
         // if a parser was found we use it ...
         Document doc = null;
         if (theParser != null) {
             // set the content length of the resource
             theParser.setContentLength(contentLength);
             // parse the resource
             doc = theParser.parse(location, mimeType,documentCharset,sourceStream);
         } else if (HTMLParsableMimeTypesContains(mimeType)) {
             doc = parseHtml(location, mimeType, documentCharset, sourceStream);
         } else {
             final String errorMsg = "No parser available to parse mimetype '" + mimeType + "' (2)";
             theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
             throw new ParserException(errorMsg,location, "wrong mime type or wrong extension");                
         }
         
         // check result
         if (doc == null) {
             final String errorMsg = "Unexpected error. Parser returned null.";
             theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
             throw new ParserException(errorMsg,location);                
         }
         return doc;
         
     } catch (final UnsupportedEncodingException e) {
         final String errorMsg = "unsupported charset encoding: " + e.getMessage();
         theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg, e);
         throw new ParserException(errorMsg,location, errorMsg);                 
     } catch (final IOException e) {
         // IOExceptions may occur during html parsing when a server closes the connection during reading.
         // This may happen here, because the html parser is a streaming parser
         // that produces surrogates while the connection is active
         final String errorMsg = "IOException - server may have closed the connection. " + e.getMessage();
         theLogger.logWarning("Unable to parse '" + location + "'. " + errorMsg);
         throw new ParserException(errorMsg, location, errorMsg);
     } catch (final Exception e) {
         // Interrupted- and Parser-Exceptions should pass through
         if (e instanceof InterruptedException) throw (InterruptedException) e;
         if (e instanceof ParserException) throw (ParserException) e;
         
         // log unexpected error
         final String errorMsg = "Unexpected exception. " + e.getMessage();
         theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg, e);
         throw new ParserException(errorMsg,location,e);            
         
     } finally {
         if (theParser != null) {
             theParser = null; // delete object
         }
     }        
 }
 
 private static Document parseHtml(
         final yacyURL location, 
         final String mimeType, 
         final String documentCharset, 
         final InputStream sourceStream) throws IOException, ParserException {
     
     // make a scraper and transformer
     final ScraperInputStream htmlFilter = new ScraperInputStream(sourceStream,documentCharset,location,null,false);
     String charset = htmlFilter.detectCharset();
     if (charset == null) {
         charset = documentCharset;
     } else {
         charset = patchCharsetEncoding(charset);
     }
     
     if (!documentCharset.equalsIgnoreCase(charset)) {
         theLogger.logInfo("Charset transformation needed from '" + documentCharset + "' to '" + charset + "' for URL = " + location.toNormalform(true, true));
     }
     
     Charset c;
     try {
         c = Charset.forName(charset);
     } catch (IllegalCharsetNameException e) {
         c = Charset.defaultCharset();
     } catch (UnsupportedCharsetException e) {
         c = Charset.defaultCharset();
     }
     
     // parsing the content
     final ContentScraper scraper = new ContentScraper(location);        
     final TransformerWriter writer = new TransformerWriter(null,null,scraper,null,false);
     FileUtils.copy(htmlFilter, writer, c);
     writer.close();
     //OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);            
     //serverFileUtils.copy(sourceFile, hfos);
     //hfos.close();
     if (writer.binarySuspect()) {
         final String errorMsg = "Binary data found in resource";
         theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg);
         throw new ParserException(errorMsg,location);    
     }
     return transformScraper(location, mimeType, documentCharset, scraper);
 }
 
 public static Document transformScraper(final yacyURL location, final String mimeType, final String charSet, final ContentScraper scraper) {
     final String[] sections = new String[scraper.getHeadlines(1).length + scraper.getHeadlines(2).length + scraper.getHeadlines(3).length + scraper.getHeadlines(4).length];
     int p = 0;
     for (int i = 1; i <= 4; i++) for (int j = 0; j < scraper.getHeadlines(i).length; j++) sections[p++] = scraper.getHeadlines(i)[j];
     final Document ppd =  new Document(
             location,
             mimeType,
             charSet,
             scraper.getContentLanguages(),
             scraper.getKeywords(),
             scraper.getTitle(),
             scraper.getAuthor(),
             sections,
             scraper.getDescription(),
             scraper.getText(),
             scraper.getAnchors(),
             scraper.getImages());
     //scraper.close();            
     ppd.setFavicon(scraper.getFavicon());
     return ppd;
 }
 
 /**
  * This function is used to determine the parser class that should be used for a given
  * mimetype ...
  * @param mimeType MIME-Type of the resource
  * @return the {@link Parser}-class that is supposed to parse the resource of
  * the given MIME-Type
  */
 private static Parser getParser(String mimeType) {

     mimeType = normalizeMimeType(mimeType);        

     // determining the proper parser class name for the mimeType
     return availableParserList.get(mimeType);
 }
 
 public static Map<yacyURL, String> allReflinks(final Collection<?> links) {
     // links is either a Set of Strings (with urls) or htmlFilterImageEntries
     // we find all links that are part of a reference inside a url
     final HashMap<yacyURL, String> v = new HashMap<yacyURL, String>();
     final Iterator<?> i = links.iterator();
     Object o;
     yacyURL url;
     String u;
     int pos;
     loop: while (i.hasNext()) try {
         o = i.next();
         if (o instanceof yacyURL) url = (yacyURL) o;
         else if (o instanceof String) url = new yacyURL((String) o, null);
         else if (o instanceof ImageEntry) url = ((ImageEntry) o).url();
         else {
             assert false;
             continue;
         }
         u = url.toNormalform(true, true);
         if ((pos = u.toLowerCase().indexOf("http://",7)) > 0) {
             i.remove();
             u = u.substring(pos);
             while ((pos = u.toLowerCase().indexOf("http://",7)) > 0) u = u.substring(pos);
             url = new yacyURL(u, null);
             if (!(v.containsKey(url))) v.put(url, "ref");
             continue loop;
         }
         if ((pos = u.toLowerCase().indexOf("/www.",7)) > 0) {
             i.remove();
             u = "http:/" + u.substring(pos);
             while ((pos = u.toLowerCase().indexOf("/www.",7)) > 0) u = "http:/" + u.substring(pos);
             url = new yacyURL(u, null);
             if (!(v.containsKey(url))) v.put(url, "ref");
             continue loop;
         }
     } catch (final MalformedURLException e) {}
     return v;
 }
 
 static Map<yacyURL, String> allSubpaths(final Collection<?> links) {
     // links is either a Set of Strings (urls) or a Set of htmlFilterImageEntries
     final HashSet<String> h = new HashSet<String>();
     Iterator<?> i = links.iterator();
     Object o;
     yacyURL url;
     String u;
     int pos;
     int l;
     while (i.hasNext()) try {
         o = i.next();
         if (o instanceof yacyURL) url = (yacyURL) o;
         else if (o instanceof String) url = new yacyURL((String) o, null);
         else if (o instanceof ImageEntry) url = ((ImageEntry) o).url();
         else {
             assert false;
             continue;
         }
         u = url.toNormalform(true, true);
         if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
         pos = u.lastIndexOf('/');
         while (pos > 8) {
             l = u.length();
             u = u.substring(0, pos + 1);
             h.add(u);
             u = u.substring(0, pos);
             assert (u.length() < l) : "u = " + u;
             pos = u.lastIndexOf('/');
         }
     } catch (final MalformedURLException e) {}
     // now convert the strings to yacyURLs
     i = h.iterator();
     final HashMap<yacyURL, String> v = new HashMap<yacyURL, String>();
     while (i.hasNext()) {
         u = (String) i.next();
         try {
             url = new yacyURL(u, null);
             v.put(url, "sub");
         } catch (final MalformedURLException e) {}
     }
     return v;
 }
 
 public static boolean supportedContent(final yacyURL url, final String mimeType) {
     if (url == null) throw new NullPointerException();
     
     if (parserConfig.supportedContent(url, mimeType)) return true;
     
     return false;
 }    

 public static void addParseableMimeTypes(final String configStr) {
     parserConfig.addParseableMimeTypes(configStr);
 }

 public static String[] setEnabledParserList(final Set<String> mimeTypeSet) {
     return parserConfig.setEnabledParserList(mimeTypeSet);        
 }
 
 public static boolean supportedFileExtContains(final String fileExt) {
     return parserConfig.supportedFileExtContains(fileExt);
 }

 public static boolean supportedMimeTypesContains(final String mimeType) {
     return parserConfig.supportedMimeTypesContains(mimeType);
 }

}
