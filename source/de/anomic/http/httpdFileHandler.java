// httpdFileHandler.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 05.10.2005
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


/*
 Class documentation:
 this class provides a file servlet and CGI interface
 for the httpd server.
 Whenever this server is addressed to load a local file,
 this class searches for the file in the local path as
 configured in the setting property 'rootPath'
 The servlet loads the file and returns it to the client.
 Every file can also act as an template for the built-in
 CGI interface. There is no specific path for CGI functions.
 CGI functionality is triggered, if for the file to-be-served
 'template.html' also a file 'template.class' exists. Then,
 the class file is called with the GET/POST properties that
 are attached to the http call.
 Possible variable hand-over are:
 - form method GET
 - form method POST, enctype text/plain
 - form method POST, enctype multipart/form-data
 The class that creates the CGI respond must have at least one
 static method of the form
 public static java.util.Hashtable respond(java.util.HashMap, serverSwitch)
 In the HashMap, the GET/POST variables are handed over.
 The return value is a Property object that contains replacement
 key/value pairs for the patterns in the template file.
 The templates must have the form
 either '#['<name>']#' for single attributes, or
 '#{'<enumname>'}#' and '#{/'<enumname>'}#' for enumerations of
 values '#['<value>']#'.
 A single value in repetitions/enumerations in the template has
 the property key '_'<enumname><count>'_'<value>
 Please see also the example files 'test.html' and 'test.java'
 */

package de.anomic.http;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import de.anomic.data.userDB;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverClassLoader;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.ymage.ymageMatrixPainter;
import de.anomic.ymage.ymagePNGEncoderAWT;
import de.anomic.ymage.ymagePainter;

public final class httpdFileHandler extends httpdAbstractHandler implements httpdHandler {
    
    // class variables   
    private static final Properties mimeTable = new Properties();
    private static final serverClassLoader provider;
    private static final HashMap templates = new HashMap();
    private static serverSwitch switchboard;
    private static plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
    
    private static File htRootPath = null;
    private static File htDocsPath = null;
    private static File htTemplatePath = null;
    private static String[] defaultFiles = null;
    private static File htDefaultPath = null;
    private static File htLocalePath = null;
    
    private MessageDigest md5Digest = null;
    
    /**
     * Template Cache
     * @param switchboard
     */
    private static final HashMap templateCache;
    
    private static final HashMap templateMethodCache;
    
    public static boolean useTemplateCache = false;
    
    static {
        useTemplateCache = plasmaSwitchboard.getSwitchboard().getConfig("enableTemplateCache","true").equalsIgnoreCase("true");
        templateCache = (useTemplateCache)? new HashMap() : new HashMap(0);
        templateMethodCache = (useTemplateCache) ? new HashMap() : new HashMap(0);
        
        // create a class loader
        provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
    }
    
    public httpdFileHandler(serverSwitch switchboard) {
        
        // creating a logger
        this.theLogger = new serverLog("FILEHANDLER");
        
        if (httpdFileHandler.switchboard == null) {
            httpdFileHandler.switchboard = switchboard;
            
            if (mimeTable.size() == 0) {
                // load the mime table
                String mimeTablePath = switchboard.getConfig("mimeConfig","");
                BufferedInputStream mimeTableInputStream = null;
                try {
                    serverLog.logConfig("HTTPDFiles", "Loading mime mapping file " + mimeTablePath);
                    mimeTableInputStream = new BufferedInputStream(new FileInputStream(new File(switchboard.getRootPath(), mimeTablePath)));
                    mimeTable.load(mimeTableInputStream);
                } catch (Exception e) {                
                    serverLog.logSevere("HTTPDFiles", "ERROR: path to configuration file or configuration invalid\n" + e);
                    System.exit(1);
                } finally {
                    if (mimeTableInputStream != null) try { mimeTableInputStream.close(); } catch (Exception e1) {}                
                }
            }
            
            // create default files array
            defaultFiles = switchboard.getConfig("defaultFiles","index.html").split(",");
            if (defaultFiles.length == 0) defaultFiles = new String[] {"index.html"};
            
            // create a htRootPath: system pages
            if (htRootPath == null) {
                htRootPath = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath","htroot"));
                if (!(htRootPath.exists())) htRootPath.mkdir();
            }
            
            // create a htDocsPath: user defined pages
            if (htDocsPath == null) {
                htDocsPath = new File(switchboard.getRootPath(), switchboard.getConfig("htDocsPath", "htdocs"));
                if (!(htDocsPath.exists())) htDocsPath.mkdir();
            }
            
            // create a htTemplatePath
            if (htTemplatePath == null) {
                htTemplatePath = new File(switchboard.getRootPath(), switchboard.getConfig("htTemplatePath","htroot/env/templates"));
                if (!(htTemplatePath.exists())) htTemplatePath.mkdir();
            }
            //This is now handles by #%env/templates/foo%#
            //if (templates.size() == 0) templates.putAll(httpTemplate.loadTemplates(htTemplatePath));
            
            // create htLocaleDefault, htLocalePath
            if (htDefaultPath == null) htDefaultPath = new File(switchboard.getRootPath(), switchboard.getConfig("htDefaultPath","htroot"));
            if (htLocalePath == null) htLocalePath = new File(switchboard.getConfig("htLocalePath","DATA/HTDOCS/locale"));
            //htLocaleSelection = switchboard.getConfig("htLocaleSelection","default");
        }
        
        // initialise an message digest for Content-MD5 support ...
        try {
            this.md5Digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            serverLog.logWarning("HTTPDFileHandler", "Content-MD5 support not availabel ...");
        }
    }
    
    /*
     * Returns the path of a (existing) localized File, or the english one, if not availible.
     * @param path is relative from htroot
     * @return the function returns a Filehandle to the translated file, or if not availitble to the english one.
     */
	public static File getLocalizedFile(String path){
        plasmaSwitchboard switchboard=plasmaSwitchboard.getSwitchboard();
        // create htLocaleDefault, htLocalePath
        if (htDefaultPath == null) htDefaultPath = new File(switchboard.getRootPath(), switchboard.getConfig("htDefaultPath","htroot"));
        if (htLocalePath == null) htLocalePath = new File(switchboard.getRootPath(), switchboard.getConfig("htLocalePath","htroot/locale"));
        //htLocaleSelection = switchboard.getConfig("htLocaleSelection","default");

        // find locales or alternatives in htDocsPath
        String htLocaleSelection = switchboard.getConfig("htLocaleSelection","default");
        // look if we have a localization of that file
        if (!(htLocaleSelection.equals("default"))) {
            File localePath = new File(htLocalePath, htLocaleSelection + "/" + path);
            if (localePath.exists())
                return localePath;
        }
        return new File(htDefaultPath, path);
	}
    
//    private void textMessage(OutputStream out, int retcode, String body) throws IOException {
//        httpd.sendRespondHeader(
//                this.connectionProperties,  // the connection properties 
//                out,                        // the output stream
//                "HTTP/1.1",                 // the http version that should be used
//                retcode,                    // the http status code
//                null,                       // the http status message
//                "text/plain",               // the mimetype
//                body.length(),              // the content length
//                httpc.nowDate(),            // the modification date
//                null,                       // the expires date
//                null,                       // cookies
//                null,                       // content encoding
//                null);                      // transfer encoding
//        out.write(body.getBytes());
//        out.flush();
//    }
    
    private httpHeader getDefaultHeaders(String path) {
        httpHeader headers = new httpHeader();
		String ext;
		int pos;
    	if ((pos = path.lastIndexOf('.')) < 0) {
            ext = "";
        } else {
            ext = path.substring(pos + 1).toLowerCase();
        }
        headers.put(httpHeader.SERVER, "AnomicHTTPD (www.anomic.de)");
        headers.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));
        if(!(plasmaParser.mediaExtContains(ext))){
            headers.put(httpHeader.PRAGMA, "no-cache");         
        }
        return headers;
    }
    
    public void doGet(Properties conProp, httpHeader requestHeader, OutputStream response) throws IOException {
        doResponse(conProp, requestHeader, response, null);
    }
    
    public void doHead(Properties conProp, httpHeader requestHeader, OutputStream response) throws IOException {
        doResponse(conProp, requestHeader, response, null);
    }
    
    public void doPost(Properties conProp, httpHeader requestHeader, OutputStream response, PushbackInputStream body) throws IOException {
        doResponse(conProp, requestHeader, response, body);
    }
    
    public void doResponse(Properties conProp, httpHeader requestHeader, OutputStream out, InputStream body) throws IOException {
        
        this.connectionProperties = conProp;
        
        // getting some connection properties
        String method     = conProp.getProperty(httpHeader.CONNECTION_PROP_METHOD);
        String path       = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
        String argsString = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS); // is null if no args were given
        String httpVersion= conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
        
        // check hack attacks in path
        if (path.indexOf("..") >= 0) {
            httpd.sendRespondError(conProp,out,4,403,null,"Access not allowed",null);
            return;
        }
        
        // url decoding of path
        try {
            path = URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // This should never occur
            assert(false) : "UnsupportedEncodingException: " + e.getMessage();
        }
        
        // check permission/granted access
        String authorization = (String) requestHeader.get(httpHeader.AUTHORIZATION);
        String adminAccountBase64MD5 = switchboard.getConfig("adminAccountBase64MD5", "");
        
        int pos = path.lastIndexOf(".");
        
        if ((path.substring(0,(pos==-1)?path.length():pos)).endsWith("_p") && (adminAccountBase64MD5.length() != 0)) {
            // authentication required
            if(authorization != null){
                //TODO: One (switchboard? httpd?) method to check it all, without too much userDB in the other classes.
                if(sb.userDB.hasAdminRight(authorization, conProp.getProperty("CLIENTIP"), requestHeader.getHeaderCookies())|| sb.staticAdminAuthenticated(authorization.trim().substring(6))==4){
                    //Authentication successful. remove brute-force flag
                    serverCore.bfHost.remove(conProp.getProperty("CLIENTIP"));
                }
            }else if (authorization == null) {
                // no authorization given in response. Ask for that
                httpHeader headers = getDefaultHeaders(path);
                headers.put(httpHeader.WWW_AUTHENTICATE,"Basic realm=\"admin log-in\"");
                //httpd.sendRespondHeader(conProp,out,httpVersion,401,headers);
                serverObjects tp=new serverObjects();
                tp.put("returnto", path);
                httpd.sendRespondError(conProp, out, 5, 401, "Wrong Authentication", "", new File("proxymsg/authfail.inc"), tp, null, headers);
                return;
            } else {
                // a wrong authentication was given or the userDB user does not have admin access. Ask again
                String clientIP = conProp.getProperty("CLIENTIP", "unknown-host");
                serverLog.logInfo("HTTPD", "Wrong log-in for account 'admin' in http file handler for path '" + path + "' from host '" + clientIP + "'");
                Integer attempts = (Integer) serverCore.bfHost.get(clientIP);
                if (attempts == null)
                    serverCore.bfHost.put(clientIP, new Integer(1));
                else
                    serverCore.bfHost.put(clientIP, new Integer(attempts.intValue() + 1));

                httpHeader headers = getDefaultHeaders(path);
                headers.put(httpHeader.WWW_AUTHENTICATE,"Basic realm=\"admin log-in\"");
                httpd.sendRespondHeader(conProp,out,httpVersion,401,headers);
                return;
            }
        }
        
        
        // parse arguments
        serverObjects args = new serverObjects();
        int argc;
        if (argsString == null) {
            // no args here, maybe a POST with multipart extension
            int length = 0;
            //System.out.println("HEADER: " + requestHeader.toString()); // DEBUG
            if (method.equals(httpHeader.METHOD_POST)) {

                GZIPInputStream gzipBody = null;
                if (requestHeader.containsKey(httpHeader.CONTENT_LENGTH)) {
                    length = Integer.parseInt((String) requestHeader.get(httpHeader.CONTENT_LENGTH));
                } else if (requestHeader.gzip()) {
                    length = -1;
                    gzipBody = new GZIPInputStream(body);
                }
//                } else {
//                    httpd.sendRespondError(conProp,out,4,403,null,"bad post values",null); 
//                    return;
//                }
                
                // if its a POST, it can be either multipart or as args in the body
                if ((requestHeader.containsKey(httpHeader.CONTENT_TYPE)) &&
                        (((String) requestHeader.get(httpHeader.CONTENT_TYPE)).toLowerCase().startsWith("multipart"))) {
                    // parse multipart
                    HashMap files = httpd.parseMultipart(requestHeader, args, (gzipBody!=null)?gzipBody:body, length);
                    // integrate these files into the args
                    if (files != null) {
                        Iterator fit = files.entrySet().iterator();
                        Map.Entry entry;
                        while (fit.hasNext()) {
                            entry = (Map.Entry) fit.next();
                            args.put(((String) entry.getKey()) + "$file", entry.getValue());
                        }
                    }
                    argc = Integer.parseInt((String) requestHeader.get("ARGC"));
                } else {
                    // parse args in body
                    argc = httpd.parseArgs(args, (gzipBody!=null)?gzipBody:body, length);
                }
            } else {
                // no args
                argsString = null;
                args = null;
                argc = 0;
            }
        } else {
            // simple args in URL (stuff after the "?")
            argc = httpd.parseArgs(args, argsString);
        }
        
        // check for cross site scripting - attacks in request arguments
        if (argc > 0) {
            // check all values for occurrences of script values
            Enumeration e = args.elements(); // enumeration of values
            Object val;
            while (e.hasMoreElements()) {
                val = e.nextElement();
                if ((val != null) && (val instanceof String) && (((String) val).indexOf("<script") >= 0)) {
                    // deny request
                    httpd.sendRespondError(conProp,out,4,403,null,"bad post values",null);
                    return;
                }
            }
        }
        
        // we are finished with parsing
        // the result of value hand-over is in args and argc
        if (path.length() == 0) {
            httpd.sendRespondError(conProp,out,4,400,null,"Bad Request",null);
            out.flush();
            return;
        }
        File targetClass=null;
        try {
            // locate the file
            if (!(path.startsWith("/"))) path = "/" + path; // attach leading slash
            File   targetFile  = getLocalizedFile(path);
            String targetExt   = conProp.getProperty("EXT","");
            targetClass = rewriteClassFile(new File(htDefaultPath, path));
            if (path.endsWith("/")) {
                String testpath;
                // attach default file name
                for (int i = 0; i < defaultFiles.length; i++) {
                    testpath = path + defaultFiles[i];
                    targetFile = getLocalizedFile(testpath);
                    targetClass = rewriteClassFile(new File(htDefaultPath, testpath));
                    if (!(targetFile.exists())){
                        targetFile = new File(htDocsPath, testpath);
                        targetClass = rewriteClassFile(targetFile);
                    }
                    if (targetFile.exists()) {
                        path = testpath;
                        break;
                    }
                }
            }else{
                    //XXX: you cannot share a .png/.gif file with a name like a class in htroot.
                    if ( !(targetFile.exists()) && !((path.endsWith("png")||path.endsWith("gif")||path.endsWith(".stream"))&&targetClass!=null ) ){
                        targetFile = new File(htDocsPath, path);
                        targetClass = rewriteClassFile(new File(htDocsPath, path));
                    }
                
            }
            
            //File targetClass = rewriteClassFile(targetFile);
            //We need tp here
            serverObjects tp = new serverObjects();
            Date targetDate;
            boolean nocache = false;
            
            if ((targetClass != null) && (path.endsWith("png"))) {
                // call an image-servlet to produce an on-the-fly - generated image
                Object img = null;
                try {
                    requestHeader.put("CLIENTIP", conProp.getProperty("CLIENTIP"));
                    requestHeader.put("PATH", path);
                    // in case that there are no args given, args = null or empty hashmap
                    img = rewriteMethod(targetClass).invoke(null, new Object[] {requestHeader, args, switchboard});
                } catch (InvocationTargetException e) {
                    this.theLogger.logSevere("INTERNAL ERROR: " + e.toString() + ":" +
                    e.getMessage() +
                    " target exception at " + targetClass + ": " +
                    e.getTargetException().toString() + ":" +
                    e.getTargetException().getMessage() +
                    "; java.awt.graphicsenv='" + System.getProperty("java.awt.graphicsenv","") + "'",e);
                    targetClass = null;
                }
                if (img == null) {
                    // error with image generation; send file-not-found
                    httpd.sendRespondError(this.connectionProperties,out,3,404,"File not Found",null,null);
                } else {
                    if (img instanceof ymagePainter) {
                        ymagePainter yp = (ymagePainter) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        String mimeType = mimeTable.getProperty(targetExt, "text/html");

                        // generate an byte array from the generated image
                        serverByteBuffer baos = new serverByteBuffer();
                        // ymagePNGEncoderJDE jde = new
                        // ymagePNGEncoderJDE((ymageMatrixPainter) yp,
                        // ymagePNGEncoderJDE.FILTER_NONE, 0);
                        // byte[] result = jde.pngEncode();
                        ImageIO.write(ymagePNGEncoderAWT.toImage(
                                        (ymageMatrixPainter) yp, true),
                                        targetExt, baos);
                        byte[] result = baos.toByteArray();
                        baos.close();
                        baos = null;

                        // write the array to the client
                        httpd.sendRespondHeader(this.connectionProperties, out,
                                httpVersion, 200, null, mimeType,
                                result.length, targetDate, null, null, null,
                                null, nocache);
                        if (!method.equals(httpHeader.METHOD_HEAD)) {
                            Thread.sleep(200); // see below
                            serverFileUtils.write(result, out);
                        }
                    }
                    if (img instanceof Image) {
                        Image i = (Image) img;
                        // send an image to client
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                        String mimeType = mimeTable.getProperty(targetExt, "text/html");

                        // generate an byte array from the generated image
                        int width = i.getWidth(null);
                        int height = i.getHeight(null);
                        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        bi.createGraphics().drawImage(i, 0, 0, width, height, null); 
                        serverByteBuffer baos = new serverByteBuffer();
                        ImageIO.write(bi, targetExt, baos);

                        byte[] result = baos.toByteArray();
                        baos.close();
                        baos = null;

                        // write the array to the client
                        httpd.sendRespondHeader(this.connectionProperties, out,
                                httpVersion, 200, null, mimeType,
                                result.length, targetDate, null, null, null,
                                null, nocache);
                        if (!method.equals(httpHeader.METHOD_HEAD)) {
                            Thread.sleep(200); // see below
                            serverFileUtils.write(result, out);
                        }
                    }
                }
            } else if ((targetClass != null) && (path.endsWith(".stream"))) {
                // call rewrite-class
                requestHeader.put("CLIENTIP", conProp.getProperty("CLIENTIP"));
                requestHeader.put("PATH", path);
                requestHeader.put("INPUTSTREAM", body);
                requestHeader.put("OUTPUTSTREAM", out);
             
                httpd.sendRespondHeader(this.connectionProperties, out, httpVersion, 200, null);                
                
                // in case that there are no args given, args = null or empty hashmap
                /* serverObjects tp = (serverObjects) */ rewriteMethod(targetClass).invoke(null, new Object[] {requestHeader, args, switchboard});
             
                this.forceConnectionClose();
                return;                
            } else if ((targetFile.exists()) && (targetFile.canRead())) {
                // we have found a file that can be written to the client
                // if this file uses templates, then we use the template
                // re-write - method to create an result
                String mimeType = mimeTable.getProperty(targetExt,"text/html");
                byte[] result;
                boolean zipContent = requestHeader.acceptGzip() && httpd.shallTransportZipped("." + conProp.getProperty("EXT",""));
                if (path.endsWith("html") || 
                        path.endsWith("xml") || 
                        path.endsWith("rss") || 
                        path.endsWith("csv") ||
                        path.endsWith("pac") ||
                        path.endsWith("src")) {
                            
                    targetFile = getLocalizedFile(path);
					if (!(targetFile.exists())) {
		                // try to find that file in the htDocsPath
				        File trialFile = new File(htDocsPath, path);
						if (trialFile.exists()) targetFile = trialFile;
		            }
            
                    
                    // call rewrite-class
                   
                    if (targetClass == null) {
                        targetDate = new Date(targetFile.lastModified());
                    } else {
                        // CGI-class: call the class to create a property for rewriting
                        try {
                            requestHeader.put("CLIENTIP", conProp.getProperty("CLIENTIP"));
                            requestHeader.put("PATH", path);
                            // in case that there are no args given, args = null or empty hashmap
                            tp = (serverObjects) rewriteMethod(targetClass).invoke(null, new Object[] {requestHeader, args, switchboard});
                            // if no args given , then tp will be an empty Hashtable object (not null)
                            if (tp == null) tp = new serverObjects();
                            // check if the servlets requests authentification
                            if (tp.containsKey("AUTHENTICATE")) {
                                // handle brute-force protection
                                if (authorization != null) {
                                    String clientIP = conProp.getProperty("CLIENTIP", "unknown-host");
                                    serverLog.logInfo("HTTPD", "dynamic log-in for account 'admin' in http file handler for path '" + path + "' from host '" + clientIP + "'");
                                    Integer attempts = (Integer) serverCore.bfHost.get(clientIP);
                                    if (attempts == null)
                                        serverCore.bfHost.put(clientIP, new Integer(1));
                                    else
                                        serverCore.bfHost.put(clientIP, new Integer(attempts.intValue() + 1));
                                }
                                // send authentication request to browser
                                httpHeader headers = getDefaultHeaders(path);
                                headers.put(httpHeader.WWW_AUTHENTICATE,"Basic realm=\"" + tp.get("AUTHENTICATE", "") + "\"");
                                httpd.sendRespondHeader(conProp,out,httpVersion,401,headers);
                                return;
                            } else if (tp.containsKey("LOCATION")) {
                                String location = tp.get("LOCATION","");
                                if (location.length() == 0) location = path;
                                
                                httpHeader headers = getDefaultHeaders(path);
                                headers.put(httpHeader.LOCATION,location);
                                httpd.sendRespondHeader(conProp,out,httpVersion,302,headers);
                                return;
                            }
                            // add the application version, the uptime and the client name to every rewrite table
                            tp.put("version", switchboard.getConfig("version", ""));
                            tp.put("uptime", ((System.currentTimeMillis() - Long.parseLong(switchboard.getConfig("startupTime","0"))) / 1000) / 60); // uptime in minutes
                            tp.put("clientname", switchboard.getConfig("peerName", "anomic"));
                            //System.out.println("respond props: " + ((tp == null) ? "null" : tp.toString())); // debug
                        } catch (InvocationTargetException e) {
                            if (e.getCause() instanceof InterruptedException) {
                                throw new InterruptedException(e.getCause().getMessage());
                            }                            
                            
                            this.theLogger.logSevere("INTERNAL ERROR: " + e.toString() + ":" +
                                    e.getMessage() +
                                    " target exception at " + targetClass + ": " +
                                    e.getTargetException().toString() + ":" +
                                    e.getTargetException().getMessage(),e);
                            targetClass = null;
                            throw e;
                        }
                        targetDate = new Date(System.currentTimeMillis());
                        nocache = true;
                    }
                    // read templates
                    tp.putAll(templates);
                    
                    // rewrite the file
                    serverByteBuffer o = null;
                    InputStream fis = null;
                    GZIPOutputStream zippedOut = null;
                    try {
                        // do fileCaching here
                        byte[] templateContent = null;
                        if (useTemplateCache) {
                            long fileSize = targetFile.length();
                            if (fileSize <= 512*1024) {
                                SoftReference ref = (SoftReference) templateCache.get(targetFile);
                                if (ref != null) {
                                    templateContent = (byte[]) ref.get();
                                    if (templateContent == null) 
                                        templateCache.remove(targetFile);                               
                                }
                                
                                if (templateContent == null) {
                                    // loading the content of the template file into a byte array
                                    templateContent = serverFileUtils.read(targetFile);
                                    
                                    // storing the content into the cache
                                    ref = new SoftReference(templateContent);
                                    templateCache.put(targetFile,ref);
                                    if (this.theLogger.isLoggable(Level.FINEST))
                                        this.theLogger.logFinest("Cache MISS for file " + targetFile);
                                } else {
                                    if (this.theLogger.isLoggable(Level.FINEST))
                                        this.theLogger.logFinest("Cache HIT for file " + targetFile);
                                }
                                
                                // creating an inputstream needed by the template rewrite function
                                fis = new ByteArrayInputStream(templateContent);                            
                                templateContent = null;
                            } else {
                                fis = new BufferedInputStream(new FileInputStream(targetFile));
                            }
                        } else {
                            fis = new BufferedInputStream(new FileInputStream(targetFile));
                        }

                        o = new serverByteBuffer();
                        if (zipContent) zippedOut = new GZIPOutputStream(o);
                        httpTemplate.writeTemplate(fis, (zipContent) ? (OutputStream)zippedOut: (OutputStream)o, tp, "-UNRESOLVED_PATTERN-".getBytes());
                        if (zipContent) {
                            zippedOut.finish();
                            zippedOut.flush();
                            zippedOut.close();
                            zippedOut = null;
                        }
                        result = o.toByteArray();
                        
                        if (this.md5Digest != null) {
                            this.md5Digest.reset();
                            this.md5Digest.update(result);
                            byte[] digest = this.md5Digest.digest();
                            StringBuffer digestString = new StringBuffer();
                            for ( int i = 0; i < digest.length; i++ )
                                digestString.append(Integer.toHexString( digest[i]&0xff));

                        }                        
                    } finally {
                        if (zippedOut != null) try {zippedOut.close();} catch(Exception e) {}
                        if (o != null) try {o.close(); o = null;} catch(Exception e) {}
                        if (fis != null) try {fis.close(); fis=null;} catch(Exception e) {}
                    }
                    
                    // write the array to the client
                    long contentLength     = result.length;
                    String contentEncoding = (zipContent)?"gzip":null;
                    httpd.sendRespondHeader(this.connectionProperties, out, httpVersion, 200, null, mimeType, contentLength, targetDate, null, tp.getOutgoingHeader(), contentEncoding, null, nocache);
                    if (! method.equals(httpHeader.METHOD_HEAD)) {
                        Thread.sleep(200); // this solved the message problem (!!)
                        serverFileUtils.write(result, out);
                    }                    
                    
                } else { // no html
                    
                    int statusCode = 200;
                    int rangeStartOffset = 0;
                    httpHeader header = new httpHeader();
                    
                    // adding the accept ranges header
                    header.put(httpHeader.ACCEPT_RANGES, "bytes");
                    
                    // reading the files md5 hash if availabe and use it as ETAG of the resource
                    String targetMD5 = null;
                    File targetMd5File = new File(targetFile + ".md5");
                    try {
                        if (targetMd5File.exists()) {
                            //String description = null;
                            targetMD5 = new String(serverFileUtils.read(targetMd5File));
                            pos = targetMD5.indexOf('\n');
                           if (pos >= 0) {
                               //description = targetMD5.substring(pos + 1);
                               targetMD5 = targetMD5.substring(0, pos);
                           }         
                           
                           // using the checksum as ETAG header
                           header.put(httpHeader.ETAG, targetMD5);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }                        
                    
                    if (requestHeader.containsKey(httpHeader.RANGE)) {
                        Object ifRange = requestHeader.ifRange();
                        if ((ifRange == null)||
                            (ifRange instanceof Date && targetFile.lastModified() == ((Date)ifRange).getTime()) ||
                            (ifRange instanceof String && ifRange.equals(targetMD5))) {
                            String rangeHeaderVal = ((String) requestHeader.get(httpHeader.RANGE)).trim();
                            if (rangeHeaderVal.startsWith("bytes=")) {
                                String rangesVal = rangeHeaderVal.substring("bytes=".length());
                                String[] ranges = rangesVal.split(",");
                                if ((ranges.length == 1)&&(ranges[0].endsWith("-"))) {
                                    rangeStartOffset = Integer.valueOf(ranges[0].substring(0,ranges[0].length()-1)).intValue();
                                    statusCode = 206;
                                    if (header == null) header = new httpHeader();
                                    header.put(httpHeader.CONTENT_RANGE, "bytes " + rangeStartOffset + "-" + (targetFile.length()-1) + "/" + targetFile.length());
                                }
                            }
                        }
                    }
                    
                    // write the file to the client
                    targetDate = new Date(targetFile.lastModified());
                    long   contentLength    = (zipContent)?-1:targetFile.length()-rangeStartOffset;
                    String contentEncoding  = (zipContent)?"gzip":null;
                    String transferEncoding = (!httpVersion.equals(httpHeader.HTTP_VERSION_1_1))?null:(zipContent)?"chunked":null;
                    if (!httpVersion.equals(httpHeader.HTTP_VERSION_1_1) && zipContent) forceConnectionClose();
                    
                    httpd.sendRespondHeader(this.connectionProperties, out, httpVersion, statusCode, null, mimeType, contentLength, targetDate, null, header, contentEncoding, transferEncoding, nocache);
                
                    if (!method.equals(httpHeader.METHOD_HEAD)) {                        
                        httpChunkedOutputStream chunkedOut = null;
                        GZIPOutputStream zipped = null;
                        OutputStream newOut = out;
                        
                        if (transferEncoding != null) {
                            chunkedOut = new httpChunkedOutputStream(newOut);
                            newOut = chunkedOut;
                        }
                        if (contentEncoding != null) {
                            zipped = new GZIPOutputStream(newOut);
                            newOut = zipped;
                        }
                        
                        serverFileUtils.copyRange(targetFile,newOut,rangeStartOffset);
                        
                        if (zipped != null) {
                            zipped.flush();
                            zipped.finish();
                        }
                        if (chunkedOut != null) {
                            chunkedOut.finish();
                        }
                    }   
                    
                    // check mime type again using the result array: these are 'magics'
//                    if (serverByteBuffer.equals(result, 1, "PNG".getBytes())) mimeType = mimeTable.getProperty("png","text/html");
//                    else if (serverByteBuffer.equals(result, 0, "GIF89".getBytes())) mimeType = mimeTable.getProperty("gif","text/html");
//                    else if (serverByteBuffer.equals(result, 6, "JFIF".getBytes())) mimeType = mimeTable.getProperty("jpg","text/html");
                    //System.out.print("MAGIC:"); for (int i = 0; i < 10; i++) System.out.print(Integer.toHexString((int) result[i]) + ","); System.out.println();
                }
            } else {
                httpd.sendRespondError(conProp,out,3,404,"File not Found",null,null);
                return;
            }
        } catch (Exception e) {     
            try {
                // doing some errorhandling ...
                int httpStatusCode = 400; 
                String httpStatusText = null; 
                StringBuffer errorMessage = new StringBuffer(); 
                Exception errorExc = null;            
                
                String errorMsg = e.getMessage();
                if (
                        (e instanceof InterruptedException) ||
                        ((errorMsg != null) && (errorMsg.startsWith("Socket closed")) && (Thread.currentThread().isInterrupted()))
                   ) {
                    errorMessage.append("Interruption detected while processing query.");
                    httpStatusCode = 503;
                } else {
                    if ((errorMsg != null) && 
                        (
                           errorMsg.startsWith("Broken pipe") || 
                           errorMsg.startsWith("Connection reset") ||
                           errorMsg.startsWith("Software caused connection abort")                           
                       )) {
                        // client closed the connection, so we just end silently
                        errorMessage.append("Client unexpectedly closed connection while processing query.");
                    } else if ((errorMsg != null) && (errorMsg.startsWith("Connection timed out"))) {
                        errorMessage.append("Connection timed out.");
                    } else {
                        errorMessage.append("Unexpected error while processing query.");
                        httpStatusCode = 500;
                        errorExc = e;
                    }
                }
                
                errorMessage.append("\nSession: ").append(Thread.currentThread().getName())
                            .append("\nQuery:   ").append(path)
                            .append("\nClient:  ").append(conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP,"unknown")) 
                            .append("\nReason:  ").append(e.toString());    
                
                if (!conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                    // sending back an error message to the client 
                    // if we have not already send an http header
                    httpd.sendRespondError(conProp,out, 4, httpStatusCode, httpStatusText, errorMessage.toString(),errorExc);
                } else {
                    // otherwise we close the connection
                    this.forceConnectionClose();
                }    
                
                // if it is an unexpected error we log it 
                if (httpStatusCode == 500) {
                    this.theLogger.logWarning(errorMessage.toString(),e);
                }
                
            } catch (Exception ee) {
                this.forceConnectionClose();
            }            
            
        } finally {
            try {out.flush();}catch (Exception e) {}
            if (!(requestHeader.get(httpHeader.CONNECTION, "close").equals("keep-alive"))) {
                // wait a little time until everything closes so that clients can read from the streams/sockets
                try {Thread.sleep(1000);} catch (InterruptedException e) {}
            }
        }
    }
    
    private void forceConnectionClose() {
        if (this.connectionProperties != null) {
            this.connectionProperties.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");            
        }
    }

    private File rewriteClassFile(File template) {
        try {
            String f = template.getCanonicalPath();
            int p = f.lastIndexOf(".");
            if (p < 0) return null;
            f = f.substring(0, p) + ".class";
            //System.out.println("constructed class path " + f);
            File cf = new File(f);
            if (cf.exists()) return cf;
            return null;
        } catch (IOException e) {
            return null;
        }
    }
    
    private final Method rewriteMethod(File classFile) {                
        Method m = null;
        // now make a class out of the stream
        try {
            if (useTemplateCache) {
                
                SoftReference ref = (SoftReference) templateMethodCache.get(classFile);
                if (ref != null) {
                    m = (Method) ref.get();
                    if (m == null) {
                        templateMethodCache.remove(classFile);
                    } else {
                        this.theLogger.logFine("Cache HIT for file " + classFile);
                        return m;
                    }
                    
                }          
            } 
            
            //System.out.println("**DEBUG** loading class file " + classFile);
            Class c = provider.loadClass(classFile);
            Class[] params = new Class[] {
                    Class.forName("de.anomic.http.httpHeader"),
                    Class.forName("de.anomic.server.serverObjects"),
                    Class.forName("de.anomic.server.serverSwitch")};
            m = c.getMethod("respond", params);
            
            if (useTemplateCache) {
                // storing the method into the cache
                SoftReference ref = new SoftReference(m);
                templateMethodCache.put(classFile,ref);
                this.theLogger.logFine("Cache MISS for file " + classFile);
            }
            
        } catch (ClassNotFoundException e) {
            System.out.println("INTERNAL ERROR: class " + classFile + " is missing:" + e.getMessage()); 
        } catch (NoSuchMethodException e) {
            System.out.println("INTERNAL ERROR: method respond not found in class " + classFile + ": " + e.getMessage());
        }
        //System.out.println("found method: " + m.toString());
        return m;
    }
    
    public void doConnect(Properties conProp, httpHeader requestHeader, InputStream clientIn, OutputStream clientOut) {
        throw new UnsupportedOperationException();
    }
    
}
