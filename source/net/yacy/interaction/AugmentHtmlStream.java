package net.yacy.interaction;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.server.http.ServerSideIncludes;

import org.jsoup.Jsoup;

public class AugmentHtmlStream {

    static RequestHeader globalrequestHeader;

    /**
     * send web page to external REFLECT web service
     *
     * @return the web page with integrated REFLECT elements
     */
    private static String processExternal(String url, String fieldname, String data) throws IOException {
        final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        try {
            StringBuilder postdata = new StringBuilder();
            postdata.append(fieldname);
            postdata.append('=');
            postdata.append(URLEncoder.encode(data, "UTF-8"));
            InputStream in = new ByteArrayInputStream(postdata.toString()
                    .getBytes());
            byte[] result = client.POSTbytes(url, in, postdata.length());
            if (result != null) {
                return new String(result);
            }
        } finally {
            client.finish();
        }
        return null;
    }

    private static String loadInternal(String path, RequestHeader requestHeader) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        String realmProp = requestHeader.get(RequestHeader.AUTHORIZATION);
        ServerSideIncludes.writeContent(path, buffer, realmProp, Domains.LOCALHOST, requestHeader); // TODO: ip
        return buffer.toString();
    }

    /**
     * add DOCTYPE if necessary
     *
     * @return the web page with a leading DOCTYPE definition
     */
    private static String processAddDoctype(String data) {

        String result = data;

        BufferedReader reader = new BufferedReader(new StringReader(data));

        try {
            String firstline = reader.readLine();

            if (firstline != null) {
                if (!firstline.startsWith("<!DOCTYPE")) {
                    result = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n"
                            + data;
                }
            }
        } catch (final IOException e1) {

        }

        return result;

    }

    public static StringBuilder process(StringBuilder data, DigestURL url, RequestHeader requestHeader) {

        String action =  requestHeader.get("YACYACTION");
        requestHeader.remove("YACYACTION");

        globalrequestHeader = requestHeader;

        Switchboard sb = Switchboard.getSwitchboard();

        boolean augmented = false;

        try {
            ConcurrentLog.info("AUGMENTATION", url.getName());
        } catch (final IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        String Doc = data.toString();

        // Send document to REFLECT (http://www.reflect.ws/REST_API.html)
        if (sb.getConfigBool("augmentation.reflect", false) == true) {
            try {

                Doc = processExternal("http://reflect.ws/REST/GetHTML", "document", Doc);
                ConcurrentLog.info("AUGMENTATION", "reflected " + url);
                augmented = true;
            } catch (final Exception e) {

            }
        }

        // Add DOCTYPE if not present.
        // This is required for IE to render position:absolute correctly.

        if (sb.getConfigBool("augmentation.addDoctype", false) == true) {
            Doc = processAddDoctype(Doc);
            augmented = true;
        }


        if (sb.getConfigBool("augmentation.reparse", false) == true) {

        	org.jsoup.nodes.Document d = Jsoup.parse(Doc);

        	d.title ("yacy - "+d.title());

        	if (sb.getConfigBool("interaction.overlayinteraction.enabled", false) == true) {

        		d.head().append (loadInternal("env/templates/jqueryheader.template", requestHeader));
	        	d.head().append ("<script type='text/javascript'>"+loadInternal("interaction_elements/interaction.js", requestHeader)+"</script>");
	        	d.head().append ("<script type='text/javascript'>"+loadInternal("interaction_elements/interaction_metadata.js", requestHeader)+"</script>");


	        	d.body().append (loadInternal("interaction_elements/OverlayInteraction.html?action="+action+"&urlhash="+ ASCII.String(url.hash()) +"&url="+url.toNormalform(false), requestHeader));
	        	d.body().append (loadInternal("interaction_elements/Footer.html?action="+action+"&urlhash="+ ASCII.String(url.hash()) +"&url="+url.toNormalform(false), requestHeader));

        	}

        	Doc = d.html();

        	augmented = true;
        }


        if (augmented) {
            return (new StringBuilder(Doc));
        }
        return (data);
    }

}