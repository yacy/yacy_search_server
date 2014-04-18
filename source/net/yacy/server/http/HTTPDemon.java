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
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
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
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;


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

    // static objects
    private static volatile Switchboard switchboard = Switchboard.getSwitchboard();

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
                //http1_1 includes http1_0 messages
                if (HeaderFramework.http1_1.containsKey(Integer.toString(httpStatusCode)))
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
                tp.put("port", switchboard.getConfig("port", "8090"));
            } else if (hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress()) {
                tp.put("host", Domains.myPublicLocalIP().getHostAddress());
                tp.put("port", switchboard.getConfig("port", "8090"));
            } else {
                tp.put("host", switchboard.myPublicIP());
                tp.put("port", switchboard.getConfig("port", "8090"));
            }

            tp.put("peerName", (switchboard.peers == null) ? "" : switchboard.peers.myName());
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
                final PrintStream printStream = new PrintStream(errorMsg);
                stackTrace.printStackTrace(printStream);
                tp.put("printStackTrace_exception", stackTrace.toString());
                tp.put("printStackTrace_stacktrace", UTF8.String(errorMsg.getBytes()));
                printStream.close();
            } else {
                tp.put("printStackTrace", "0");
            }

            // Generated Tue, 23 Aug 2005 11:19:14 GMT by brain.wg (squid/2.5.STABLE3)
            // adding some system information
            final String systemDate = HeaderFramework.formatRFC1123(new Date());
            tp.put("date", systemDate);

            // rewrite the file
            final File htRootPath = new File(switchboard.getAppPath(), switchboard.getConfig(SwitchboardConstants.HTROOT_PATH,SwitchboardConstants.HTROOT_PATH_DEFAULT));

            TemplateEngine.writeTemplate(
                    "/proxymsg/error.html",
                    fis = new FileInputStream(new File(htRootPath, "/proxymsg/error.html")),
                    o = new ByteArrayOutputStream(512),
                    tp
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
                if (HeaderFramework.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    //http1_1 includes http1_0 messages
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
}
