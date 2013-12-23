// HTTPDemon.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.server.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;



/**
 * Instances of this class can be passed as argument to the serverCore.
 * The generic server dispatches HTTP commands and calls the
 * method GET, HEAD or POST in this class
 * these methods parse the command line and decide wether to call
 * a proxy servlet or a file server servlet
 */
public final class HTTPDemon {


    private static final int ERRORCASE_MESSAGE = 4;
    private static final int ERRORCASE_FILE = 5;
    private static final File TMPDIR = new File(System.getProperty("java.io.tmpdir"));
    private static final int SIZE_FILE_THRESHOLD = 20 * 1024 * 1024;
    private static final FileItemFactory DISK_FILE_ITEM_FACTORY = new DiskFileItemFactory(SIZE_FILE_THRESHOLD, TMPDIR);

    private static AlternativeDomainNames alternativeResolver = null;

    // static objects
    private static volatile Switchboard switchboard;

    public static boolean keepAliveSupport = false;

    /**
     * parses the message accordingly to RFC 1867 using "Commons FileUpload" (http://commons.apache.org/fileupload/)
     *
     * @author danielr
     * @since 07.08.2008
     * @param header
     *            hier muss ARGC gesetzt werden!
     * @param args
     * @param in the raw body
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public
    static Map<String, byte[]> parseMultipart(final RequestHeader header, final serverObjects args, final InputStream in) throws IOException {

        final InputStream body = prepareBody(header, in);

        final RequestContext request = new yacyContextRequest(header, body);

        // check information
        if (!FileUploadBase.isMultipartContent(request)) {
            throw new IOException("the request is not a multipart-message!");
        }

        // reject too large uploads
        if (request.getContentLength() > SIZE_FILE_THRESHOLD) throw new IOException("FileUploadException: uploaded file too large = " + request.getContentLength());

        // check if we have enough memory
        if (!MemoryControl.request(request.getContentLength() * 3, false)) {
        	throw new IOException("not enough memory available for request. request.getContentLength() = " + request.getContentLength() + ", MemoryControl.available() = " + MemoryControl.available());
        }

        // parse data in memory
        final List<FileItem> items;

        try {
            final FileUpload upload = new FileUpload(DISK_FILE_ITEM_FACTORY);
            items = upload.parseRequest(request);
        } catch (final FileUploadException e) {
            throw new IOException("FileUploadException " + e.getMessage());
        }

        // format information for further usage
        final Map<String, byte[]> files = new HashMap<String, byte[]>();
        byte[] fileContent;
        for (final FileItem item : items) {
            if (item.isFormField()) {
                // simple text
                if (item.getContentType() == null || !item.getContentType().contains("charset")) {
                    // old yacy clients use their local default charset, on most systems UTF-8 (I hope ;)
                    args.add(item.getFieldName(), item.getString("UTF-8"));
                } else {
                    // use default encoding (given as header or ISO-8859-1)
                    args.add(item.getFieldName(), item.getString());
                }
            } else {
                // file
                args.add(item.getFieldName(), item.getName());
                fileContent = FileUtils.read(item.getInputStream(), (int) item.getSize());
                item.getInputStream().close();
                files.put(item.getFieldName(), fileContent);
            }
        }
        header.put("ARGC", String.valueOf(items.size())); // store argument count

        return files;
    }

    /**
     * prepares the body so that it can be read as whole plain text
     * (uncompress if necessary and ensure correct ending)
     *
     * @param header
     * @param in
     * @return
     * @throws IOException
     */
    private static InputStream prepareBody(final RequestHeader header, final InputStream in) throws IOException {
        InputStream body = in;
        // data may be compressed
        final String bodyEncoding = header.get(HeaderFramework.CONTENT_ENCODING);
        if(HeaderFramework.CONTENT_ENCODING_GZIP.equalsIgnoreCase(bodyEncoding) && !(body instanceof GZIPInputStream)) {
            body = new GZIPInputStream(body);
            // length of uncompressed data is unknown
            header.remove(HeaderFramework.CONTENT_LENGTH);
        } else {
            // ensure the end of data (if client keeps alive the connection)
            final long clength = header.getContentLength();
            if (clength > 0) {
                body = new ContentLengthInputStream(body, clength);
            }
        }
        return body;
    }

    /**
     * wraps the request into a org.apache.commons.fileupload.RequestContext
     *
     * @author danielr
     * @since 07.08.2008
     */
    private static class yacyContextRequest extends RequestHeader implements RequestContext {

        private static final long serialVersionUID = -8936741958551376593L;

        private final InputStream inStream;

        /**
         * creates a new yacyContextRequest
         *
         * @param header
         * @param in
         */
        public yacyContextRequest(final Map<String, String> requestHeader, final InputStream in) {
            super(null, requestHeader);
            this.inStream = in;
        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.commons.fileupload.RequestContext#getInputStream()
         */
        // @Override
        @Override
        public InputStream getInputStream() throws IOException {
            return this.inStream;
        }

    }

    static final void sendRespondError(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final int errorcase,
            final int httpStatusCode,
            final String httpStatusText,
            final String detailedErrorMsg,
            final Throwable stackTrace
    ) throws IOException {
        sendRespondError(
                conProp,
                respond,
                errorcase,
                httpStatusCode,
                httpStatusText,
                detailedErrorMsg,
                null,
                null,
                stackTrace,
                null
        );
    }

    static final void sendRespondError(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final int httpStatusCode,
            final String httpStatusText,
            final File detailedErrorMsgFile,
            final serverObjects detailedErrorMsgValues,
            final Throwable stackTrace
    ) throws IOException {
        sendRespondError(
                conProp,
                respond,
                5,
                httpStatusCode,
                httpStatusText,
                null,
                detailedErrorMsgFile,
                detailedErrorMsgValues,
                stackTrace,
                null
        );
    }

    private static final void sendRespondError(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final int errorcase,
            final int httpStatusCode,
            String httpStatusText,
            final String detailedErrorMsgText,
            final Object detailedErrorMsgFile,
            final serverObjects detailedErrorMsgValues,
            final Throwable stackTrace,
            ResponseHeader header
    ) throws IOException {

        FileInputStream fis = null;
        ByteArrayOutputStream o = null;
        try {
            // setting the proper http status message
            String httpVersion = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = "HTTP/1.1";
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals("HTTP/1.0") && HeaderFramework.http1_0.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals("HTTP/1.1") && HeaderFramework.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }

            // generating the desired request url
            String host = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HOST);
            String path = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PATH); if (path == null) path = "/";
            final String args = (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS);
            final String method = (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD);

            final int port;
            final int pos = host.indexOf(':');
            if (pos != -1) {
                port = NumberTools.parseIntDecSubstring(host, pos + 1);
                host = host.substring(0, pos);
            } else {
                port = 80;
            }

            String urlString;
            try {
                urlString = (new DigestURL((method.equals(HeaderFramework.METHOD_CONNECT)?"https":"http"), host, port, (args == null) ? path : path + "?" + args)).toString();
            } catch (final MalformedURLException e) {
                urlString = "invalid URL";
            }

            // set rewrite values
            final serverObjects tp = new serverObjects();

            String clientIP = (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP); if (clientIP == null) clientIP = Domains.LOCALHOST;

            // check if ip is local ip address
            final InetAddress hostAddress = Domains.dnsResolve(clientIP);
            if (hostAddress == null) {
                tp.put("host", Domains.myPublicLocalIP().getHostAddress());
                tp.put("port", Integer.toString(serverCore.getPortNr(switchboard.getConfig("port", "8090"))));
            } else if (hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress()) {
                tp.put("host", Domains.myPublicLocalIP().getHostAddress());
                tp.put("port", Integer.toString(serverCore.getPortNr(switchboard.getConfig("port", "8090"))));
            } else {
                tp.put("host", switchboard.myPublicIP());
                tp.put("port", Integer.toString(serverCore.getPortNr(switchboard.getConfig("port", "8090"))));
            }

            tp.put("peerName", (getAlternativeResolver() == null) ? "" : getAlternativeResolver().myName());
            tp.put("errorMessageType", Integer.toString(errorcase));
            tp.put("httpStatus",       Integer.toString(httpStatusCode) + " " + httpStatusText);
            tp.put("requestMethod",    (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD));
            tp.put("requestURL",       urlString);

            switch (errorcase) {
                case ERRORCASE_FILE:
                    tp.put("errorMessageType_file", (detailedErrorMsgFile == null) ? "" : detailedErrorMsgFile.toString());
                    if ((detailedErrorMsgValues != null) && !detailedErrorMsgValues.isEmpty()) {
                        // rewriting the value-names and add the proper name prefix:
                        for (final Entry<String, String> entry: detailedErrorMsgValues.entrySet()) {
                            tp.put("errorMessageType_" + entry.getKey(), entry.getValue());
                        }
                    }
                    break;
                case ERRORCASE_MESSAGE:
                default:
                    tp.put("errorMessageType_detailedErrorMsg", (detailedErrorMsgText == null) ? "" : detailedErrorMsgText.replaceAll("\n", "<br />"));
                    break;
            }

            // building the stacktrace
            if (stackTrace != null) {
                tp.put("printStackTrace", "1");
                final ByteBuffer errorMsg = new ByteBuffer(100);
                stackTrace.printStackTrace(new PrintStream(errorMsg));
                tp.put("printStackTrace_exception", stackTrace.toString());
                tp.put("printStackTrace_stacktrace", UTF8.String(errorMsg.getBytes()));
            } else {
                tp.put("printStackTrace", "0");
            }

            // Generated Tue, 23 Aug 2005 11:19:14 GMT by brain.wg (squid/2.5.STABLE3)
            // adding some system information
            final String systemDate = HeaderFramework.formatRFC1123(new Date());
            tp.put("date", systemDate);

            // rewrite the file
            final File htRootPath = new File(switchboard.getAppPath(), switchboard.getConfig("htRootPath","htroot"));

            TemplateEngine.writeTemplate(
                    fis = new FileInputStream(new File(htRootPath, "/proxymsg/error.html")),
                    o = new ByteArrayOutputStream(512),
                    tp,
                    ASCII.getBytes("-UNRESOLVED_PATTERN-")
            );
            final byte[] result = o.toByteArray();
            o.close(); o = null;

            if (header == null) header = new ResponseHeader(httpStatusCode);
            header.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_STATUS, Integer.toString(httpStatusCode));
            header.put(HeaderFramework.DATE, systemDate);
            header.put(HeaderFramework.CONTENT_TYPE, "text/html");
            header.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(result.length));
            header.put(HeaderFramework.PRAGMA, "no-cache");
            sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,httpStatusText,header);

            if (! method.equals(HeaderFramework.METHOD_HEAD)) {
                // write the array to the client
                FileUtils.copy(result, respond);
            }
            respond.flush();
        } finally {
            if (fis != null) try { fis.close(); } catch (final Exception e) { ConcurrentLog.logException(e); }
            if (o != null)   try { o.close();   } catch (final Exception e) { ConcurrentLog.logException(e); }
        }
    }

    private static final void sendRespondHeader(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode,
            final String httpStatusText,
            String contentType,
            final long contentLength,
            Date moddate,
            final Date expires,
            ResponseHeader headers,
            final String contentEnc,
            final String transferEnc,
            final boolean nocache
    ) throws IOException {

        final String reqMethod = (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD);

        if ((transferEnc != null) && !httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1)) {
            throw new IllegalArgumentException("Transfer encoding is only supported for http/1.1 connections. The current connection version is " + httpVersion);
        }

        if (!reqMethod.equals(HeaderFramework.METHOD_HEAD)){
            if (!"close".equals(conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT)) &&
                    transferEnc == null && contentLength < 0) {
                throw new IllegalArgumentException("Message MUST contain a Content-Length or a non-identity transfer-coding header field.");
            }
            if (transferEnc != null && contentLength >= 0) {
                throw new IllegalArgumentException("Messages MUST NOT include both a Content-Length header field and a non-identity transfer-coding.");
            }
        }

        if (headers == null) headers = new ResponseHeader(httpStatusCode);
        final Date now = new Date(System.currentTimeMillis());

        headers.put(HeaderFramework.SERVER, "AnomicHTTPD (www.anomic.de)");
        headers.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(now));
        if (moddate.after(now)) {
            moddate = now;
        }
        headers.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(moddate));

        if (nocache) {
            headers.put(HeaderFramework.CACHE_CONTROL, "no-cache");
            headers.put(HeaderFramework.CACHE_CONTROL, "no-store");
            headers.put(HeaderFramework.PRAGMA, "no-cache");
        }

        if (contentType == null) contentType = "text/html; charset=UTF-8";
        if (headers.get(HeaderFramework.CONTENT_TYPE) == null) headers.put(HeaderFramework.CONTENT_TYPE, contentType);
        if (contentLength > 0)   headers.put(HeaderFramework.CONTENT_LENGTH, Long.toString(contentLength));
        //if (cookie != null)      headers.put(httpHeader.SET_COOKIE, cookie);
        if (expires != null)     headers.put(HeaderFramework.EXPIRES, HeaderFramework.formatRFC1123(expires));
        if (contentEnc != null)  headers.put(HeaderFramework.CONTENT_ENCODING, contentEnc);
        if (transferEnc != null) headers.put(HeaderFramework.TRANSFER_ENCODING, transferEnc);

        sendRespondHeader(conProp, respond, httpVersion, httpStatusCode, httpStatusText, headers);
    }

    static final void sendRespondHeader(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode,
            final ResponseHeader header
    ) throws IOException {
        sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,null,header);
    }

    static final void sendRespondHeader(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            String httpVersion,
            final int httpStatusCode,
            String httpStatusText,
            ResponseHeader responseHeader
    ) throws IOException {

        if (respond == null) throw new NullPointerException("The outputstream must not be null.");
        if (conProp == null) throw new NullPointerException("The connection property structure must not be null.");
        if (httpVersion == null) httpVersion = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = HeaderFramework.HTTP_VERSION_1_1;
        if (responseHeader == null) responseHeader = new ResponseHeader(httpStatusCode);

        try {
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals(HeaderFramework.HTTP_VERSION_1_0) && HeaderFramework.http1_0.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1) && HeaderFramework.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }

            final StringBuilder header = new StringBuilder(560);

            // "HTTP/0.9" does not have a status line or header in the response
            if (! httpVersion.toUpperCase().equals(HeaderFramework.HTTP_VERSION_0_9)) {
                // write status line
                header.append(httpVersion).append(" ")
                                  .append(Integer.toString(httpStatusCode)).append(" ")
                                  .append(httpStatusText).append("\r\n");

                // prepare header
                if (!responseHeader.containsKey(HeaderFramework.DATE))
                    responseHeader.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(new Date()));
                if (!responseHeader.containsKey(HeaderFramework.CONTENT_TYPE))
                    responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html; charset=UTF-8"); // fix this
                if (!responseHeader.containsKey(RequestHeader.CONNECTION) && conProp.containsKey(HeaderFramework.CONNECTION_PROP_PERSISTENT))
                    responseHeader.put(RequestHeader.CONNECTION, (String) conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT));
                if (!responseHeader.containsKey(RequestHeader.PROXY_CONNECTION) && conProp.containsKey(HeaderFramework.CONNECTION_PROP_PERSISTENT))
                    responseHeader.put(RequestHeader.PROXY_CONNECTION, (String) conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT));

                if (conProp.containsKey(HeaderFramework.CONNECTION_PROP_PERSISTENT) &&
                    conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT).equals("keep-alive") &&
                    !responseHeader.containsKey(HeaderFramework.TRANSFER_ENCODING) &&
                    !responseHeader.containsKey(HeaderFramework.CONTENT_LENGTH))
                    responseHeader.put(HeaderFramework.CONTENT_LENGTH, "0");

                // adding some yacy specific headers
                responseHeader.put(HeaderFramework.X_YACY_KEEP_ALIVE_REQUEST_COUNT,(String) conProp.get(HeaderFramework.CONNECTION_PROP_KEEP_ALIVE_COUNT));
                responseHeader.put(HeaderFramework.X_YACY_ORIGINAL_REQUEST_LINE,(String) conProp.get(HeaderFramework.CONNECTION_PROP_REQUESTLINE));
                //responseHeader.put(HeaderFramework.X_YACY_PREVIOUS_REQUEST_LINE,conProp.getProperty(HeaderFramework.CONNECTION_PROP_PREV_REQUESTLINE));

                //read custom headers
                final Iterator<ResponseHeader.Entry> it = responseHeader.getAdditionalHeaderProperties().iterator();
                ResponseHeader.Entry e;
                while(it.hasNext()) {
                        //Append user properties to the main String
                        //TODO: Should we check for user properites. What if they intersect properties that are already in header?
                    e = it.next();
                    header.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
                }

                // write header
                final Iterator<String> i = responseHeader.keySet().iterator();
                String key;
                char tag;
                int count;
                while (i.hasNext()) {
                    key = i.next();
                    tag = key.charAt(0);
                    if ((tag != '*') && (tag != '#')) { // '#' in key is reserved for proxy attributes as artificial header values
                        count = responseHeader.keyCount(key);
                        for (int j = 0; j < count; j++) {
                            header.append(key).append(": ").append(responseHeader.getSingle(key, j)).append("\r\n");
                        }
                    }
                }

                // end header
                header.append("\r\n");

                // sending headers to the client
                respond.write(UTF8.getBytes(header.toString()));

                // flush stream
                respond.flush();
            }

            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER,responseHeader);
            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_STATUS,Integer.toString(httpStatusCode));
        } catch (final Exception e) {
            // any interruption may be caused be network error or because the user has closed
            // the windows during transmission. We simply pass it as IOException
            throw new IOException(e.getMessage());
        }
    }

    private static boolean isThisSeedIP(final String hostName) {
        if ((hostName == null) || (hostName.isEmpty())) return false;

        // getting ip address and port of this seed
        if (getAlternativeResolver() == null) return false;

        // resolve ip addresses
        final InetAddress seedInetAddress = Domains.dnsResolve(getAlternativeResolver().myIP());
        final InetAddress hostInetAddress = Domains.dnsResolve(hostName);
        if (seedInetAddress == null || hostInetAddress == null) return false;

        // if it's equal, the hostname points to this seed
        return (seedInetAddress.equals(hostInetAddress));
    }

    /**
     * @param alternativeResolver the alternativeResolver to set
     */
    public static void setAlternativeResolver(final AlternativeDomainNames alternativeResolver) {
        HTTPDemon.alternativeResolver = alternativeResolver;
    }

    /**
     * @return the alternativeResolver
     */
    public static AlternativeDomainNames getAlternativeResolver() {
        return alternativeResolver;
    }

}
