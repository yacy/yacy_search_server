package net.yacy.http;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;

import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import de.anomic.server.serverClassLoader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class TemplateHandler extends AbstractHandler {

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
            Log.logSevere("HTTPDFileHandler", "class " + classFile + " is missing:" + e.getMessage());
            throw new InvocationTargetException(e, "class " + classFile + " is missing:" + e.getMessage());
        } catch (final NoSuchMethodException e) {
            Log.logSevere("HTTPDFileHandler", "method 'respond' not found in class " + classFile + ": " + e.getMessage());
            throw new InvocationTargetException(e, "method 'respond' not found in class " + classFile + ": " + e.getMessage());
        }
        return m;
    }
    
    
    private final Object invokeServlet(final File targetClass, final HttpServletRequest request, final serverObjects args) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return rewriteMethod(targetClass).invoke(null, new Object[] {request, args, null}); // add switchboard
    }

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		String localeSelection = "default";
        File targetFile = getLocalizedFile(target, localeSelection);
        File targetClass = rewriteClassFile(new File(htDefaultPath, target));
        
        if ((targetClass != null)) {
        	serverObjects args = new serverObjects(request.getParameterMap());
            Object tmp;
			try {
				tmp = invokeServlet(targetClass, request, args);
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
            servletProperties templatePatterns = null;
            if (tmp == null) {
                // if no args given, then tp will be an empty Hashtable object (not null)
                templatePatterns = new servletProperties();
            } else if (tmp instanceof servletProperties) {
                templatePatterns = (servletProperties) tmp;
            } else {
                templatePatterns = new servletProperties((serverObjects) tmp);
            }
            
            response.setContentType("text/html");
    		response.setStatus(HttpServletResponse.SC_OK);
    		response.getWriter().println("<h1>Hello OneHandler</h1>");
    		
    		Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
    		base_request.setHandled(true);
        }
	}

    protected final class TemplateCacheEntry {
        Date lastModified;
        byte[] content;
    }

}
