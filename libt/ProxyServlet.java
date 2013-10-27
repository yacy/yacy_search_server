// package de.spieleck.servlets;
// ProxyServlet - serving pages from foreign servers....
//

import java.io.*;
import java.net.*;
import java.lang.Integer;
import java.util.StringTokenizer;

import javax.servlet.*;
import javax.servlet.http.*;

/**
 *  Serves pages which are fetched from another HTTP-Server
 *  useful for going thru firewalls and other trickery...
 *  <P>
 *  The communication is somewhat this way:
 *  <UL>
 *  <LI>Client requests data from servlet
 *  <LI>Servlet interprets path and requests data from remote server
 *  <LI>Servlet obtains answer from remote server and forwards it to client
 *  <LI>Client obtains answer
 *  </UL>
 *  <P>
 *  XXX There is a problem with If-Modified and If-None-Match requests:
 *  the 304 Not Modified answer does not go thru the servelet in the
 *  backward direction. It could be that the HttpServletResponse does hava
 *  some sideeffects which are not helpfull in this special situation.
 *  This type of request is currently avoided by removing all "If-" requests.
 *  <br />
 *  <b>Note:</b> This servlet is actually buggy. It is buggy since it does
 *  not solve all problems, it only solves the problems I needed to solve.
 *  Many thanks to Thorsten Gast the creator of dirjack
 *  for pointing at least some bugs.
 *  @author <a href="mailto:frank -at- spieleck.de">Frank Nestel</a>.
 */

public class ProxyServlet extends HttpServlet
{
    /**
     * "Official" HTTP line end
     */
    public final static String CRLF = "\r\n";
    public final static String LF   = "\n";

    /**
     * remote path
     */
    protected String remotePath;

    /**
     * remote server
     */
    protected String remoteServer;

    /**
     * Port at remote server
     */
    protected int remotePort;

    /**
     * Debug mode?
     */
    protected boolean debugFlag;

    /** Init
     */
    public void init(ServletConfig config)
    throws ServletException
    {
    super.init(config);
    remotePath   = getInitParameter("remotePath");
    remoteServer = getInitParameter("remoteServer");
    String remotePortStr= getInitParameter("remotePort");
    if ( remotePath == null || remoteServer == null )
        throw new ServletException(
                "Servlet needs remotePath & remoteServer.");
        if ( remotePortStr != null )
        {
            try
            {
                 remotePort = Integer.parseInt(remotePortStr);
            }
            catch ( Exception e )
            {
                throw new ServletException("Port must be a number!");
            }
        }
        else
            remotePort = 80;
        if ( "".equals(remotePath) )
            remotePath = ""; // XXX ??? "/"
    else if ( remotePath.charAt(0) != '/' )
            remotePath = "/"+remotePath;
        debugFlag = "true".equals(getInitParameter("debug"));
        //
    log("remote="+remoteServer+" "+remotePort+" "+remotePath);
    }

    /// Returns a string containing information about the author, version, and
    // copyright of the servlet.
    public String getServletInfo()
    {
    return "Online redirecting content.";
    }

    /// Services a single request from the client.
    // @param req the servlet request
    // @param req the servlet response
    // @exception ServletException when an exception has occurred
    public void service( HttpServletRequest req, HttpServletResponse res )
    throws ServletException, IOException
    {
        //
        // Connect to "remote" server:
    Socket sock;
    OutputStream out;
    InputStream in;
    //
    try
    {
            sock = new Socket(remoteServer, remotePort); // !!!!!!!!
            out  = new BufferedOutputStream(sock.getOutputStream());
            in   = new BufferedInputStream(sock.getInputStream());
    }
    catch (IOException e)
    {
        res.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Socket opening: "+remoteServer+" "+remotePort);
        return;
    }
    try
    {
            //
            // Build up a HTTP request from pure strings:
            StringBuffer sb = new StringBuffer(200);
            sb.append(req.getMethod());
            sb.append(' ');
            String pi = req.getPathInfo();
            sb.append(remotePath);
            if ( pi != null )
            {
                appendCleaned(sb, pi);
            }
            else
                sb.append("/");
            if ( req.getQueryString() != null )
            {
                sb.append('?');
                appendCleaned(sb, req.getQueryString());
            }
            sb.append(' ');
            sb.append("HTTP/1.0");
            sb.append(CRLF);
            log(sb.toString());
            out.write(sb.toString().getBytes());
        java.util.Enumeration en = req.getHeaderNames();
        while ( en.hasMoreElements() )
        {
        String k = (String) en.nextElement();
                // Filter incoming headers:
                if ( "Host".equalsIgnoreCase(k) )
                {
                    sb.setLength(0);
                    sb.append(k);
                    sb.append(": ");
                    sb.append(remoteServer);
                    sb.append(":");
                    sb.append(remotePort);
                    sb.append(CRLF);
                    log("c["+k+"]: "+sb+" "+req.getHeader(k));
                    out.write(sb.toString().getBytes());
                }
                //
                // Throw away persistant connections between servers
                // Throw away request potentially causing a 304 response.
                else if (
                        ! "Connection".equalsIgnoreCase(k)
                      && ! "If-Modified-Since".equalsIgnoreCase(k)
                      && ! "If-None-Match".equalsIgnoreCase(k)
                   )
                {
                    sb.setLength(0);
                    sb.append(k);
                    sb.append(": ");
                    sb.append(req.getHeader(k));
                    sb.append(CRLF);
                    log("=["+k+"]: "+req.getHeader(k));
                    out.write(sb.toString().getBytes());
                }
                else
                {
                    log("*["+k+"]: "+req.getHeader(k));
                }
        }
            // Finish request header by an empty line
        out.write(CRLF.getBytes());
            // Copy post data
            InputStream inr = req.getInputStream();
            copyStream(inr, out);
        out.flush();
        log("Remote request finished. Reading answer.");

            // Now we have finished the outgoing request.
            // We'll now see, what is coming back from remote:

            // Get the answer, treat its header and copy the stream data:
        if ( treatHeader(in, req, res) )
            {
                log("+ copyStream");
                // if ( debugFlag ) res.setContentType("text/plain");
                out = res.getOutputStream();
                copyStream(in, out);
            }
            else
                log("- copyStream");
    }
    catch (IOException e)
    {
            log("out-in.open!");
        // res.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                // "out-in open!");
        return;
    }
    try
    {
        // out.close();
            in.close();
            sock.close();
        }
    catch (IOException ignore)
        {
            log("Exception "+ignore);
        }
    }

    public static void appendCleaned(StringBuffer sb, String str)
    {
        for(int i = 0; i < str.length(); i++)
        {
            char ch = str.charAt(i);
            if ( ch == ' ' )
                sb.append("%20");
            else
                sb.append(ch);
        }
    }


    /**
     * Forward and filter header from backend Request.
     */
    private boolean treatHeader(InputStream in,
                HttpServletRequest req,
                HttpServletResponse res)
    throws ServletException
    {
        boolean retval = true;
    byte[] lineBytes = new byte[4096];
    int len;
    String line;

        try
    {
            // Read the first line of the request.
            len = readLine(in, lineBytes );
            if ( len == -1 || len == 0 )
                throw new ServletException( "No Request found in Data." );
{
    String line2 = new String( lineBytes, 0, len );
    log("head: "+line2+" "+len);
}

        // We mainly skip the header by the foreign server
        // assuming, that we can handle protocoll mismatch or so!
        res.setHeader("viaJTTP","JTTP");

        // Some more headers require special care ....
        boolean firstline = true;
        // Shortcut evaluation skips the read on first time!
            while ( firstline || ((len=readLine(in,lineBytes)) > 0) )
            {
                line = new String( lineBytes, 0, len );
            int colonPos = line.indexOf( ":" );
        if ( firstline && colonPos == -1 )
        {
                // Special first line considerations ...
                String headl[] = wordStr(line);
log("head: "+line+" "+headl.length);
                try
                {
                        res.setStatus(Integer.parseInt(headl[1]));
                }
            catch ( NumberFormatException ignore )
                    {
                        log("ID exception: "+headl);
                    }
                catch ( Exception panik )
                {
                    log("First line invalid!");
                    return true;
             }
        }
        else if ( colonPos != -1 )
            {
            String head = line.substring(0,colonPos);
                    // XXX Skip LWS (what is LWS)
                    int i = colonPos + 1;
                    while ( isLWS(line.charAt(i)) ) i++;
            String value= line.substring(i);
log("<"+head+">=<"+ value+">");
            if ( head.equalsIgnoreCase("Location") )
                    {
            // res.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                        // res.setHeader(head, value );
                        log("Location cutted: "+value);
                    }
            else if ( head.equalsIgnoreCase( "Content-type" ) )
            res.setContentType( value );
            else if ( head.equalsIgnoreCase( "Content-length" ) )
            {
            try
            {
                            int cLen = Integer.parseInt( value );
                            retval = ( cLen > 0 );
                            res.setContentLength(cLen);
            }
            catch ( NumberFormatException ignore ) {}
            }
                    // Generically treat unknown headers
            else
                    {
                        log("^- generic.");
                        res.setHeader(head, value );
                    }
        }
                // XXX We do not treat multiline continuation Headers here
                // which have not occured anywhere yet.
        firstline = false;
        }
    }
    catch ( IOException e )
    {
            log("Header skip problem:");
        throw new ServletException("Header skip problem: "+e.getMessage());
    }
log( "--------------" );
        return retval;
    }

    /**
     * Read a RFC2616 line from an InputStream:
     */
    public int readLine(InputStream in, byte[] b )
    throws IOException
    {
    int off2 = 0;
    while ( off2 < b.length )
        {
        int r = in.read();
        if ( r == -1 )
        {
        if (off2 == 0 )
            return -1;
        break;
        }
        if ( r == 13 )
        continue;
        if ( r == 10 )
        break;
        b[off2] = (byte) r;
        ++off2;
        }
    return off2;
    }

    /** Copy a file from in to out.
     * Sub-classes can override this in order to do filtering of some sort.
     */
    public void copyStream( InputStream in, OutputStream out )
        throws IOException
    {
        BufferedInputStream bin = new BufferedInputStream(in);
        int b;
        while ( ( b = bin.read() ) != -1 )
            out.write(b);
    }

    /**
     * Split a blank separated string into
     */
    public String[] wordStr( String inp )
    {
        StringTokenizer tok = new StringTokenizer(inp, " ");
        int i, n = tok.countTokens();
        String[] res = new String[n];
        for(i = 0; i < n; i++ )
            res[i] = tok.nextToken();
        return res;
    }

    /**
     * XXX Should identify RFC2616 LWS
     */
    protected boolean isLWS(char c)
    {
        return c == ' ';
    }

    /**
     * Capture awaay the standard servlet log ..
     */
    public void log(String msg)
    {
        if ( debugFlag )
            System.err.println("## "+msg);
    }
}
