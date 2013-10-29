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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.http.TemplateEngine;
import net.yacy.server.serverClassLoader;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;
import net.yacy.visualization.RasterPlotter;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;


/**
 * jetty http handler:
 * 
 * Handles classic yacy servlets with templates
 */
public class TemplateHandler extends AbstractHandler implements Handler {

    private final String htLocalePath = "DATA/LOCALE/htroot";
    private final String htDefaultPath = "htroot";
    private String htDocsPath = "DATA/HTDOCS";
    
    private static final serverClassLoader provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
    
    private ConcurrentHashMap<File, SoftReference<Method>> templateMethodCache = null;
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        templateMethodCache = new ConcurrentHashMap<File, SoftReference<Method>>();
        htDocsPath = Switchboard.getSwitchboard().htDocsPath.getPath();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
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
            final SoftReference<Method> ref = templateMethodCache.get(classFile);
            if (ref != null) {
                m = ref.get();
                if (m == null) {
                    templateMethodCache.remove(classFile);
                } else {
                    return m;
                }
            }
            
            final Class<?> c = provider.loadClass(classFile);
            final Class<?>[] params = new Class[] {
                    RequestHeader.class,
                    serverObjects.class,
                    serverSwitch.class };
            m = c.getMethod("respond", params);
            
            // store the method into the cache
            templateMethodCache.put(classFile, new SoftReference<Method>(m));
            
        } catch (final ClassNotFoundException e) {
            ConcurrentLog.severe("HTTPDFileHandler", "class " + classFile + " is missing:" + e.getMessage());
            throw new InvocationTargetException(e, "class " + classFile + " is missing:" + e.getMessage());
        } catch (final NoSuchMethodException e) {
            ConcurrentLog.severe("HTTPDFileHandler", "method 'respond' not found in class " + classFile + ": " + e.getMessage());
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
		
	String localeSelection = Switchboard.getSwitchboard().getConfig("locale.language","default");
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
                //TODO: for SSI request, local parameters are added as attributes, put them back as parameter for the legacy request
                //      likely this should be implemented via httpservletrequestwrapper to supply complete parameters  
                @SuppressWarnings("unchecked")
                Enumeration<String> attNames = request.getAttributeNames();
                while (attNames.hasMoreElements()) {
                    String argName = attNames.nextElement();
                    args.put (argName,request.getAttribute(argName).toString());
                } 
                
                // add multipart-form fields to parameter
                if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
                    parseMultipart(request, args);
                }
                // eof modification to read attribute
        	RequestHeader legacyRequestHeader = generateLegacyRequestHeader(request, target, targetExt);
        	
            Object tmp;
			try {
				tmp = invokeServlet(targetClass, legacyRequestHeader, args);
			} catch (InvocationTargetException e) {
				ConcurrentLog.logException(e);
				throw new ServletException();
			} catch (IllegalArgumentException e) {
				ConcurrentLog.logException(e);
				throw new ServletException();
			} catch (IllegalAccessException e) {
				ConcurrentLog.logException(e);
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
                
                final String mimeType = Classification.ext2mime(targetExt, "text/html");
                response.setContentType(mimeType);
                response.setContentLength(result.length());
                response.setStatus(HttpServletResponse.SC_OK);
                
                result.writeTo(response.getOutputStream());
                
                // we handled this request, break out of handler chain 
                // is null on SSI template
                if (baseRequest != null)  baseRequest.setHandled(true);
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
            Seed myPeer = sb.peers.mySeed();
            templatePatterns.put("newpeer", myPeer.getAge() >= 1 ? 0 : 1); 
            templatePatterns.putHTML("newpeer_peerhash", myPeer.hash);
            templatePatterns.put("p2p", sb.getConfigBool(SwitchboardConstants.DHT_ENABLED, true) || !sb.isRobinsonMode() ? 1 : 0);
            
            if(targetFile.exists() && targetFile.isFile() && targetFile.canRead()) {
            	String mimeType = Classification.ext2mime(targetExt, "text/html");
            	
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
                // is null on SSI template
         	if (baseRequest != null) baseRequest.setHandled(true);
            }
        }
    }

    protected final class TemplateCacheEntry {
        Date lastModified;
        byte[] content;
    }
    
    /**
     * TODO: add same functionality & checks as in HTTPDemon.parseMultipart
     *
     * parse multi-part form data for formfields (only), see also original
     * implementation in HTTPDemon.parseMultipart
     *
     * @param request
     * @param args found fields/values are added to the map
     */
    public void parseMultipart(HttpServletRequest request, serverObjects args) {
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // maximum size that will be stored in memory
        factory.setSizeThreshold(4096 * 16);
        // Location to save data that is larger than maxMemSize.
        // factory.setRepository(new File("."));
        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setSizeMax(4096 * 8);
        try {
            // Parse the request to get form field items
            @SuppressWarnings("unchecked")
            List<FileItem> fileItems = upload.parseRequest(request);
            // Process the uploaded file items
            Iterator<FileItem> i = fileItems.iterator();
            while (i.hasNext()) {
                FileItem fi = i.next();
                if (fi.isFormField()) {
                    args.put(fi.getFieldName(), fi.getString());
                }
            }
        } catch (Exception ex) {
            ConcurrentLog.logException(ex);
        }
    }
}
