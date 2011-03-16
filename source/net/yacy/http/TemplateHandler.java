//
//  TemplateHandler
//  Copyright 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//  Copyright 2011 by Florian Richter
//  First released 13.04.2011 at http://yacy.net
//  
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//  
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//  
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program in the file lgpl21.txt
//  If not, see <http://www.gnu.org/licenses/>.
//

package net.yacy.http;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.visualization.RasterPlotter;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import de.anomic.data.MimeTable;
import de.anomic.http.server.TemplateEngine;
import de.anomic.search.Switchboard;
import de.anomic.server.serverClassLoader;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;
import de.anomic.yacy.yacyBuildProperties;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.graphics.EncodedImage;

/**
 * jetty http handler:
 * 
 * Handles classic yacy servlets with templates
 */
public class TemplateHandler extends AbstractHandler implements Handler {

	private String htLocalePath = "DATA/LOCALE/htroot";
	private String htDefaultPath = "htroot";
	private String htDocsPath = "DATA/HTDOCS";

	private static final serverClassLoader provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);

    boolean useTemplateCache = false;
    private ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>> templateCache = null;
    private ConcurrentHashMap<File, SoftReference<Method>> templateMethodCache = null;
    
	@Override
	protected void doStart() throws Exception {
		super.doStart();
		// TODO create template cache
        templateCache = (useTemplateCache)? new ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>>() : new ConcurrentHashMap<File, SoftReference<TemplateCacheEntry>>(0);
        templateMethodCache = (useTemplateCache) ? new ConcurrentHashMap<File, SoftReference<Method>>() : new ConcurrentHashMap<File, SoftReference<Method>>(0);
	}

	@Override
	protected void doStop() throws Exception {
		super.doStop();
		// TODO destroy template cache?
	}

    /** Returns a path to the localized or default file according to the parameter localeSelection
     * @param path relative from htroot
     * @param localeSelection language of localized file; locale.language from switchboard is used if localeSelection.equals("") */
    public File getLocalizedFile(final String path, final String localeSelection){
    	if (!(localeSelection.equals("default"))) {
            final File localePath = new File(htLocalePath, localeSelection + '/' + path);
            if (localePath.exists()) return localePath;  // avoid "NoSuchFile" troubles if the "localeSelection" is misspelled
        }

        File docsPath  = new File(htDocsPath, path);
        if (docsPath.exists()) return docsPath;
        return new File(htDefaultPath, path);
    }
    
    private final File rewriteClassFile(final File template) {
        try {
            String f = template.getCanonicalPath();
            final int p = f.lastIndexOf('.');
            if (p < 0) return null;
            f = f.substring(0, p) + ".class";
            //System.out.println("constructed class path " + f);
            final File cf = new File(f);
            if (cf.exists()) return cf;
            return null;
        } catch (final IOException e) {
            return null;
        }
    }
    
    private final Method rewriteMethod(final File classFile) throws InvocationTargetException {                
        Method m = null;
        // now make a class out of the stream
        try {
            if (useTemplateCache) {                
                final SoftReference<Method> ref = templateMethodCache.get(classFile);
                if (ref != null) {
                    m = ref.get();
                    if (m == null) {
                        templateMethodCache.remove(classFile);
                    } else {
                        return m;
                    }
                }          
            }
            
            final Class<?> c = provider.loadClass(classFile);
            final Class<?>[] params = new Class[] {
                    RequestHeader.class,
                    serverObjects.class,
                    serverSwitch.class };
            m = c.getMethod("respond", params);
            
            if (useTemplateCache) {
                // storing the method into the cache
                final SoftReference<Method> ref = new SoftReference<Method>(m);
                templateMethodCache.put(classFile, ref);
            }
            
        } catch (final ClassNotFoundException e) {
            Log.logSevere("TemplateHandler", "class " + classFile + " is missing:" + e.getMessage());
            throw new InvocationTargetException(e, "class " + classFile + " is missing:" + e.getMessage());
        } catch (final NoSuchMethodException e) {
            Log.logSevere("TemplateHandler", "method 'respond' not found in class " + classFile + ": " + e.getMessage());
            throw new InvocationTargetException(e, "method 'respond' not found in class " + classFile + ": " + e.getMessage());
        }
        return m;
    }
    
    
    private final Object invokeServlet(final File targetClass, final RequestHeader request, final serverObjects args) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return rewriteMethod(targetClass).invoke(null, new Object[] {request, args, Switchboard.getSwitchboard()}); // add switchboard
    }
    
    private RequestHeader generateLegacyRequestHeader(HttpServletRequest request, String target, String targetExt) {
    	RequestHeader legacyRequestHeader = new RequestHeader();
		@SuppressWarnings("unchecked")
		Enumeration<String> headers = request.getHeaderNames();
    	while (headers.hasMoreElements()) {
        	String headerName = headers.nextElement();
    		@SuppressWarnings("unchecked")
			Enumeration<String> header = request.getHeaders(headerName);
    		while(header.hasMoreElements())
    			legacyRequestHeader.add(headerName, header.nextElement());
    	}
    	
    	legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, request.getRemoteAddr());
    	legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, target);
    	legacyRequestHeader.put(HeaderFramework.CONNECTION_PROP_EXT, targetExt);
    	
    	return legacyRequestHeader;
    }
    
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
    	Switchboard sb = Switchboard.getSwitchboard();
    	
		System.err.println("Page: " + target);
		
		String localeSelection = "default";
        File targetFile = getLocalizedFile(target, localeSelection);
        File targetClass = rewriteClassFile(new File(htDefaultPath, target));
        String targetExt = target.substring(target.lastIndexOf('.') + 1, target.length());
        
        if ((targetClass != null)) {
			serverObjects args = new serverObjects();
        	@SuppressWarnings("unchecked")
			Enumeration<String> argNames = request.getParameterNames();
        	while (argNames.hasMoreElements()) {
        		String argName = argNames.nextElement();
        		args.put(argName, request.getParameter(argName));
        	}
        	RequestHeader legacyRequestHeader = generateLegacyRequestHeader(request, target, targetExt);
        	
            Object tmp;
			try {
				tmp = invokeServlet(targetClass, legacyRequestHeader, args);
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new ServletException();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new ServletException();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new ServletException();
			}
			
			if ( tmp instanceof RasterPlotter || tmp instanceof EncodedImage || tmp instanceof Image) {
				
				ByteBuffer result = null;
			
				if (tmp instanceof RasterPlotter) {
	                final RasterPlotter yp = (RasterPlotter) tmp;
	                // send an image to client
	                result = RasterPlotter.exportImage(yp.getImage(), "png");
	            }
				if (tmp instanceof EncodedImage) {
					final EncodedImage yp = (EncodedImage) tmp;
	                result = yp.getImage();
				}
				
	            if (tmp instanceof Image) {
	                final Image i = (Image) tmp;
	
	                // generate an byte array from the generated image
	                int width = i.getWidth(null); if (width < 0) width = 96; // bad hack
	                int height = i.getHeight(null); if (height < 0) height = 96; // bad hack
	                final BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	                bi.createGraphics().drawImage(i, 0, 0, width, height, null); 
	                result = RasterPlotter.exportImage(bi, targetExt);
	            }
                
                final String mimeType = MimeTable.ext2mime(targetExt, "text/html");
                response.setContentType(targetExt);
                response.setContentLength(result.length());
                response.setStatus(HttpServletResponse.SC_OK);
                
                result.writeTo(response.getOutputStream());
                
                // we handled this request, break out of handler chain
        		Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
        		base_request.setHandled(true);

        		return;
			}

			
            servletProperties templatePatterns = null;
            if (tmp == null) {
                // if no args given, then tp will be an empty Hashtable object (not null)
                templatePatterns = new servletProperties();
            } else if (tmp instanceof servletProperties) {
                templatePatterns = (servletProperties) tmp;
            } else {
                templatePatterns = new servletProperties((serverObjects) tmp);
            }
            // add the application version, the uptime and the client name to every rewrite table
            templatePatterns.put(servletProperties.PEER_STAT_VERSION, yacyBuildProperties.getVersion());
            templatePatterns.put(servletProperties.PEER_STAT_UPTIME, ((System.currentTimeMillis() -  serverCore.startupTime) / 1000) / 60); // uptime in minutes
            templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTNAME, sb.peers.mySeed().getName());
            templatePatterns.putHTML(servletProperties.PEER_STAT_CLIENTID, sb.peers.myID());
            templatePatterns.put(servletProperties.PEER_STAT_MYTIME, GenericFormatter.SHORT_SECOND_FORMATTER.format());
            yacySeed myPeer = sb.peers.mySeed();
            templatePatterns.put("newpeer", myPeer.getAge() >= 1 ? 0 : 1); 
            templatePatterns.putHTML("newpeer_peerhash", myPeer.hash);
            
            if(targetFile.exists() && targetFile.isFile() && targetFile.canRead()) {
            	String mimeType = MimeTable.ext2mime(targetExt, "text/html");
            	
                InputStream fis = null;
                long fileSize = targetFile.length();

            	if (fileSize <= Math.min(4 * 1024 * 1204, MemoryControl.available() / 100)) {
                    // read file completely into ram, avoid that too many files are open at the same time
                    fis = new ByteArrayInputStream(FileUtils.read(targetFile));
                } else {
                    fis = new BufferedInputStream(new FileInputStream(targetFile));
                }
            	
            	// set response header
                response.setContentType(mimeType);
                response.setStatus(HttpServletResponse.SC_OK);
                
                // apply templates
                TemplateEngine.writeTemplate(fis, response.getOutputStream(), templatePatterns, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));
                fis.close();
                
                // we handled this request, break out of handler chain
        		Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
        		base_request.setHandled(true);
            }
        }
	}

    protected final class TemplateCacheEntry {
        Date lastModified;
        byte[] content;
    }

}
