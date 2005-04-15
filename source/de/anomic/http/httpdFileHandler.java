// httpdFileHandler.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 22.06.2004
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

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import de.anomic.server.*;

public final class httpdFileHandler extends httpdAbstractHandler implements httpdHandler {

    // class variables   
    private Properties mimeTable = null;
    private serverClassLoader provider = null;
    private File htRootPath = null;
    private File htDocsPath = null;
    private File htTemplatePath = null;
    private HashMap templates = null;
    private String[] defaultFiles = null;

    private serverSwitch switchboard;
    private String adminAccountBase64MD5;

    public httpdFileHandler(serverSwitch switchboard) {
	this.switchboard = switchboard;

	if (mimeTable == null) {
	    // load the mime table
	    mimeTable = new Properties();
            String mimeTablePath = switchboard.getConfig("mimeConfig","");
            try {
		serverLog.logSystem("HTTPDFiles", "Loading mime mapping file " + mimeTablePath);
		mimeTable.load(new FileInputStream(new File(switchboard.getRootPath(), mimeTablePath)));
	    } catch (Exception e) {
		serverLog.logError("HTTPDFiles", "ERROR: path to configuration file or configuration invalid\n" + e);
		System.exit(1);
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
        
        if (templates == null) templates = loadTemplates(htTemplatePath);

	// create a class loader
        if (provider == null) {
	    provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
	    // debug
	    /*
	    Package[] ps = ((cachedClassLoader) provider).packages();
	    for (int i = 0; i < ps.length; i++) System.out.println("PACKAGE IN PROVIDER: " + ps[i].toString());
	    */
	}
	adminAccountBase64MD5 = null;
        
        serverLog.logSystem("HTTPDFileHandler", "File Handler Initialized");
    }
 
    private void respondHeader(OutputStream out, int retcode,
			       String conttype, long contlength,
			       Date moddate, Date expires,
			       String cookie) throws IOException {
	try {
	    out.write(("HTTP/1.1 " + retcode + " OK\r\n").getBytes());
	    out.write(("Server: AnomicHTTPD (www.anomic.de)\r\n").getBytes());
	    out.write(("Date: " + httpc.dateString(httpc.nowDate()) + "\r\n").getBytes());
	    if (expires != null) out.write(("Expires: " + httpc.dateString(expires) + "\r\n").getBytes()); 
	    out.write(("Content-type: " + conttype /* "image/gif", "text/html" */ + "\r\n").getBytes());
	    out.write(("Last-modified: " + httpc.dateString(moddate) + "\r\n").getBytes()); 
	    out.write(("Content-length: " + contlength +"\r\n").getBytes());
	    out.write(("Pragma: no-cache\r\n").getBytes());
	    //    out.write(("Accept-ranges: bytes\r\n").getBytes());
	    if (cookie != null) out.write(("Set-Cookie: " + cookie + "\r\n").getBytes());
	    out.write(("\r\n").getBytes());
	    out.flush();
	} catch (Exception e) {
	    // any interruption may be caused be network error or because the user has closed
	    // the windows during transmission. We simply pass it as IOException
	    throw new IOException(e.getMessage());
	}
    }
    
    private void textMessage(OutputStream out, int retcode, String body) throws IOException {
	respondHeader(out, retcode, "text/plain", body.length(), httpc.nowDate(), null, null);
	out.write(body.getBytes());
	out.flush();
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

    public void doResponse(Properties conProp, httpHeader requestHeader, OutputStream out, PushbackInputStream body) throws IOException {

	String userAgent = (String) requestHeader.get("USER-AGENT");
	if (userAgent == null) userAgent = "";
	userAgent = userAgent.trim().toLowerCase();
	//userAgent = "portalmmm n400i"; // debug
	//boolean iMode = (userAgent.startsWith("portalmmm"));
	//if (iMode) System.out.println("DETECTED IMODE");

	//System.out.println("HTTPD-REQUEST FROM CLIENT: " + userAgent); // DEBUG

	// prepare response
	String method = conProp.getProperty("METHOD");
	String path = conProp.getProperty("PATH");
	String argsString = conProp.getProperty("ARGS"); // is null if no args were given

	// check hack attacks in path
	if (path.indexOf("..") >= 0) {
	    out.write(("HTTP/1.0 403 bad path\r\n").getBytes());
	    out.write(("\r\n").getBytes());
	    out.flush();
	    return;
	}

        // check permission/granted access
        if ((path.endsWith("_p.html")) &&
            ((adminAccountBase64MD5 = switchboard.getConfig("adminAccountBase64MD5", "")).length() != 0)) {
            // authentication required
            String auth = (String) requestHeader.get("Authorization");
            if (auth == null) {
                // no authorization given in response. Ask for that
                out.write(("HTTP/1.1 401 log-in required\r\n").getBytes());
                out.write(("WWW-Authenticate: Basic realm=\"admin log-in\"\r\n").getBytes());
                out.write(("\r\n").getBytes());
                out.flush();
                return;
            } else if (!(adminAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(auth.trim().substring(6))))) {
                // a wrong authentication was given. Ask again
                serverLog.logInfo("HTTPD", "Wrong log-in for account 'admin' in http file handler for path '" + path + "' from host '" + conProp.getProperty("CLIENTIP", "unknown-IP") + "'");
                try {Thread.currentThread().sleep(3000);} catch (InterruptedException e) {} // add a delay to make brute-force harder
                out.write(("HTTP/1.1 401 log-in required\r\n").getBytes());
                out.write(("WWW-Authenticate: Basic realm=\"admin log-in\"\r\n").getBytes());
                out.write(("\r\n").getBytes());
                out.flush();
                return;
            }
	}

	// parse arguments
	serverObjects args = new serverObjects();
	int argc;
	if (argsString == null) {
	    // no args here, maybe a POST with multipart extension
	    int length;
	    //System.out.println("HEADER: " + requestHeader.toString()); // DEBUG
	    if ((method.equals("POST")) &&
		(requestHeader.containsKey("CONTENT-LENGTH"))) {
		// if its a POST, it can be either multipart or as args in the body
		length = Integer.parseInt((String) requestHeader.get("CONTENT-LENGTH"));
		if ((requestHeader.containsKey("CONTENT-TYPE")) &&
		    (((String) requestHeader.get("CONTENT-TYPE")).toLowerCase().startsWith("multipart"))) {
		    // parse multipart
                    HashMap files = httpd.parseMultipart(requestHeader, args, body, length);
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
		    argc = httpd.parseArgs(args, body, length);
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
	
	//if (args != null) System.out.println("***ARGS=" + args.toString()); // DEBUG

	// check for cross site scripting - attacks in request arguments
	if (argc > 0) {
	    // check all values for occurrences of script values
	    Enumeration e = args.elements(); // enumeration of values
	    Object val;
	    while (e.hasMoreElements()) {
		val = e.nextElement();
		if ((val != null) && (val instanceof String) && (((String) val).indexOf("<script") >= 0)) {
		    // deny request
		    out.write(("HTTP/1.0 403 bad post values\r\n").getBytes());
		    out.write(("\r\n").getBytes());
		    out.flush();
		    return;
		}
	    }
	}

	// we are finished with parsing
	// the result of value hand-over is in args and argc
	if (path.length() == 0) {
	    textMessage(out, 400, "Bad Request\r\n");
	    out.flush();
	    return;
	}

	Date filedate;
	long filelength;
	File rc = null;
	try {
	    // locate the file
	    if (!(path.startsWith("/"))) {
		// attach leading slash
		path = "/" + path;
	    }
            
            // find defaults
            File file = null;
            String testpath = path;
            if (path.endsWith("/")) {
		// attach default file name
                for (int i = 0; i < defaultFiles.length; i++) {
                    testpath = path + defaultFiles[i];
                    file = new File(htRootPath, testpath);
                    if (!(file.exists())) file = new File(htDocsPath, testpath);
                    if (file.exists()) {path = testpath; break;}
                }
	    } else {
               file = new File(htRootPath, path);
               if (!(file.exists())) file = new File(htDocsPath, path);
            }
            
            /*
	    if ((iMode) && (path.endsWith(".html"))) {
		file = new File(htRootPath, path.substring(0, path.length() - 4) + "ihtml");
		if (!(file.exists())) file = new File(htDocsPath, path.substring(0, path.length() - 4) + "ihtml");
		if (!(file.exists())) file = new File(htRootPath, path);
		if (!(file.exists())) file = new File(htDocsPath, path);
		//System.out.println("IMODE PATH = " + file.toString());
	    }
            */
            
	    if ((file.exists()) && (file.canRead())) {
		// we have found a file that can be written to the client
		// if this file uses templates, then we use the template
		// re-write - method to create an result
		serverObjects tp = new serverObjects();
		filedate = new Date(file.lastModified());
                String mimeType = mimeTable.getProperty(conProp.getProperty("EXT",""),"text/html");
                byte[] result;
		if (path.endsWith("html") || 
            path.endsWith("xml") || 
            path.endsWith("rss") || 
            path.endsWith("csv") ||
            path.endsWith("pac")) {
		    rc = rewriteClassFile(file);
		    if (rc != null) {
			// CGI-class: call the class to create a property for rewriting
			try {
			    requestHeader.put("CLIENTIP", conProp.getProperty("CLIENTIP"));
                            requestHeader.put("PATH", path);
			    // in case that there are no args given, args = null or empty hashmap
			    tp = (serverObjects) rewriteMethod(rc).invoke(null, new Object[] {requestHeader, args, switchboard});
			    // if no args given , then tp will be an empty Hashtable object (not null)
			    if (tp == null) tp = new serverObjects();
                            // check if the servlets requests authentification
                            if (tp.containsKey("AUTHENTICATE")) {
                                String account = tp.get("AUTHENTICATE", "");
                                out.write(("HTTP/1.1 401 log-in required\r\n").getBytes());
                                out.write(("WWW-Authenticate: Basic realm=\"" + account + "\"\r\n").getBytes());
                                out.write(("\r\n").getBytes());
                                out.flush();
                                return;
                            }
			    // add the application version to every rewrite table
			    tp.put("version", switchboard.getConfig("version", ""));
			    tp.put("uptime", ((System.currentTimeMillis() - Long.parseLong(switchboard.getConfig("startupTime","0"))) / 1000) / 60); // uptime in minutes
			    //System.out.println("respond props: " + ((tp == null) ? "null" : tp.toString())); // debug
			} catch (InvocationTargetException e) {
			    System.out.println("INTERNAL ERROR: " + e.toString() + ":" +
					       e.getMessage() +
					       " target exception at " + rc + ": " +
					       e.getTargetException().toString() + ":" +
					       e.getTargetException().getMessage());
			    e.printStackTrace();
			    rc = null;
			}
                        filedate = new Date(System.currentTimeMillis());
		    }
		    // read templates
		    tp.putAll(templates);
		    // rewrite the file
		    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    FileInputStream fis = new FileInputStream(file);
		    httpTemplate.writeTemplate(fis, o, tp, "-UNRESOLVED_PATTERN-".getBytes());
		    o.close();
                    result = o.toByteArray();
		} else { // no html
                    // write the file to the client
                    result = serverFileUtils.read(file);
                }
                // check mime type again using the result array: these are 'magics'
                if (serverByteBuffer.equals(result, 1, "PNG".getBytes())) mimeType = mimeTable.getProperty("png","text/html");
                else if (serverByteBuffer.equals(result, 0, "GIF89".getBytes())) mimeType = mimeTable.getProperty("gif","text/html");
                else if (serverByteBuffer.equals(result, 6, "JFIF".getBytes())) mimeType = mimeTable.getProperty("jpg","text/html");
                //System.out.print("MAGIC:"); for (int i = 0; i < 10; i++) System.out.print(Integer.toHexString((int) result[i]) + ","); System.out.println();
                // write the array to the client
                respondHeader(out, 200, mimeType, result.length, filedate, null, null);
                Thread.currentThread().sleep(200); // this solved the message problem (!!)
                serverFileUtils.write(result, out);
	    } else {
		textMessage(out, 404, "404 File not Found\r\n"); // would be a possible vuln to return original the original path
	    }
	} catch (Exception e) {
	    //textMessage(out, 503, "Exception with query: " + path + "; '" + e.toString() + ":" + e.getMessage() + "'\r\n");
	    //e.printStackTrace();
	    System.out.println("ERROR: Exception with query: " + path + "; '" + e.toString() + ":" + e.getMessage() + "'\r\n");
	}
	out.flush();
        if (!(requestHeader.get("Connection", "close").equals("keep-alive"))) {
          // wait a little time until everything closes so that clients can read from the streams/sockets
          try {Thread.currentThread().sleep(1000);} catch (InterruptedException e) {}
        }
    }

    private static HashMap loadTemplates(File path) {
	// reads all templates from a path
	// we use only the folder from the given file path
	HashMap result = new HashMap();
	if (path == null) return result;
	if (!(path.isDirectory())) path = path.getParentFile();
	if ((path == null) || (!(path.isDirectory()))) return result;
	String[] templates = path.list();
	int c;
	for (int i = 0; i < templates.length; i++) {
	    if (templates[i].endsWith(".template")) try {
		//System.out.println("TEMPLATE " + templates[i].substring(0, templates[i].length() - 9) + ": " + new String(buf, 0, c));
		result.put(templates[i].substring(0, templates[i].length() - 9),
                           new String(serverFileUtils.read(new File(path, templates[i]))));
	    } catch (Exception e) {}
	}
	return result;
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

    private Method rewriteMethod(File classFile) {
	Method m = null;
	// now make a class out of the stream
	try {
	    //System.out.println("**DEBUG** loading class file " + classFile);
	    Class c = provider.loadClass(classFile);
	    Class[] params = new Class[] {
		Class.forName("de.anomic.http.httpHeader"),
		Class.forName("de.anomic.server.serverObjects"),
		Class.forName("de.anomic.server.serverSwitch")};
	    m = c.getMethod("respond", params);
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
