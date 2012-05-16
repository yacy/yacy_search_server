package net.yacy.interaction;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.Charset;

import net.yacy.yacy;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.document.Document;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;

import org.htmlparser.Tag;
import org.htmlparser.Text;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.visitors.NodeVisitor;

import de.anomic.http.server.ServerSideIncludes;


public class AugmentHtmlStream {

	static RequestHeader globalrequestHeader;

	/**
	 * creates a NodeVisitor which assigns a unique ID to every node
	 *
	 * @return customized NodeVisitor
	 */
	private static class VisitorAddUniqueID extends NodeVisitor {

		private int counter;

		public VisitorAddUniqueID() {
			this.setCounter(0);
		}

		@Override
        public void visitTag(Tag tag) {
			if (tag.getAttribute("id") == null) {
				this.setCounter(this.getCounter() + 1);
				tag.setAttribute("id", "\"sci" + this.getCounter() + "\"");
			}

			if (tag instanceof org.htmlparser.tags.LinkTag) {
				// Link
				Log.logInfo("AUGMENTATION", tag.getAttribute("href"));

				LinkTag lt = (LinkTag)tag;

			}

		}

		@Override
        public void visitStringNode(Text string) {

		}

		public void setCounter(int counter) {
			this.counter = counter;
		}

		public int getCounter() {
			return this.counter;
		}

	}

	/**
	 * creates a NodeVisitor which inspects the element if it contains useful
	 * text
	 *
	 * @return customized NodeVisitor
	 */
	private static class VisitorText extends NodeVisitor {

		private int counter;

		public VisitorText() {
			this.setCounter(0);
		}

		@Override
        public void visitTag(Tag tag) {

//			tag.setText(tag.getText()+" <span>augmented</span>");

//			Node node = new org.htmlparser.nodes.TextNode(loadInternal("interactionparts/scibutton.html", globalrequestHeader));
//			NodeList nl = tag.getChildren();
//			nl.add (node);
//			tag.setChildren(nl);

		}

		@Override
        public void visitStringNode(Text string) {

//			if (string.getParent() != null) {
//
//				string.setText(string
//						.getText()
//						.replaceAll("und",
//								"<a href=\"http://www.kit.edu/\" target=\"_blank\">KIT</a>"));
//
//
//			}
		}

		public void setCounter(int counter) {
			this.counter = counter;
		}

		public int getCounter() {
			return this.counter;
		}

	}

	/**
	 * send web page to external REFLECT web service
	 *
	 * @return the web page with integrated REFLECT elements
	 */
	private static String processExternal(String url, String fieldname,
			String data) throws IOException {
		final HTTPClient client = new HTTPClient();
		try {
			StringBuilder postdata = new StringBuilder();
			postdata.append("document=");
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
		ServerSideIncludes.writeContent(path, buffer, realmProp, "127.0.0.1", requestHeader); // TODO: ip
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
		} catch (IOException e1) {

		}

		return result;

	}

	/**
	 * load snippet from resource text file
	 *
	 * @return text from resource text file
	 */
	private static String loadPart(String part) {
		String result = "";
	try {
	    BufferedReader in = new BufferedReader(new FileReader(yacy.homedir + File.separatorChar + "htroot"
				+ File.separatorChar + "interaction" + File.separatorChar
				+ "parts" + File.separatorChar + part));
	    String str;
	    while ((str = in.readLine()) != null) {
	        result += str;
	    }
	    in.close();
	} catch (IOException e) {
	}

	return result;
	}

	public static StringBuffer process (StringBuffer data, Charset charset, DigestURI url, RequestHeader requestHeader) {

		globalrequestHeader = requestHeader;

		Switchboard sb = Switchboard.getSwitchboard();

		boolean augmented = false;
		
		try {
			Log.logInfo("AUGMENTATION", url.getName());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		String Doc = data.toString();

		// Send document to REFLECT (http://www.reflect.ws/REST_API.html)
		if (sb.getConfigBool("augmentation.reflect", false) == true) {
			try {

				Doc = processExternal("http://reflect.ws/REST/GetHTML",
						"document", Doc);
				Log.logInfo("AUGMENTATION", "reflected " + url);
				augmented = true;
			} catch (Exception e) {

			}
		}

		// Add DOCTYPE if not present.
		// This is required for IE to render position:absolute correctly.

		if (sb.getConfigBool("augmentation.addDoctype", true) == true) {
			Doc = processAddDoctype(Doc);
			augmented = true;
		}


		if (sb.getConfigBool("augmentation.reparse", true) == true) {

			NodeList list = new NodeList();

			// Fill NodeList with parsed Document
			try {

				org.htmlparser.Parser par = new org.htmlparser.Parser();

				par.setInputHTML(Doc);

				list = par.parse(null);

				Log.logInfo ("AUGMENTATION", url.toString());

			} catch (Exception e) {
			}

			// Add Unique ID to every node element which has no id yet.
			// This allows consistent interaction between client (browser) and
			// back-end (data store) by providing "position awareness" in the
			// document.
			if (sb.getConfigBool("augmentation.reparse.adduniqueid", true) == true) {
				try {

					NodeVisitor visitorAddUniqueID = new AugmentHtmlStream.VisitorAddUniqueID();
					list.visitAllNodesWith(visitorAddUniqueID);

				} catch (Exception e) {
				}
			}

			// Inspect on text tags

			try {

				NodeVisitor visitorText = new AugmentHtmlStream.VisitorText();
				list.visitAllNodesWith(visitorText);

			} catch (Exception e) {
			}

			String SCI_GUID = "";

			String SCI_GUID_DOI = "";
			String SCI_GUID_PMID = "";

			String SCI_TITLE = "";
			String SCI_CREATOR = "";
			String SCI_DESCRIPTION = "";
			String SCI_IDENTIFIER = "";

			String SCI_WHITELIST = "";

			String SCI_URL = "";

			String SCI_HASH = "";

			SCI_URL = url.toString();

			// System.out.println("Starting augmentation for " + url);
			// System.out.println("Content: " + Doc);

			if (!(list == null)) {

				// DOCUMENT IS MANIPULABLE BY HTML REWRITER

				// SO SEND IT TO YACY PARSER

				Document document = null;

				try {
					final StringReader stringReader = new StringReader(Doc);
					InputStream inputStream = new InputStream() {

						@Override
						public int read() throws IOException {
							return stringReader.read();
						}
					};

					document = Document.mergeDocuments(
							url,
							"text/html",
							TextParser.parseSource(url, "text/html", null,
									data.length(), inputStream));

				} catch (Exception e) {

				}

				if (document != null) {

					if (document.dc_format() == "text/html") {

						SCI_TITLE = document.dc_title();
						SCI_CREATOR = document.dc_creator();
						SCI_DESCRIPTION = document.dc_description();
						SCI_IDENTIFIER = document.dc_identifier();

					}

				}

				SCI_HASH = "" + url.hashCode();

				// ADD AUGMENTED HEADER INFORMATION

				NodeList header = list.extractAllNodesThatMatch(
						new org.htmlparser.filters.NodeClassFilter(
								org.htmlparser.tags.HeadTag.class), true);

				org.htmlparser.util.SimpleNodeIterator iterHeader = header
						.elements();

				while (iterHeader.hasMoreNodes()) {
					org.htmlparser.tags.HeadTag ht = ((org.htmlparser.tags.HeadTag) iterHeader
							.nextNode());

					NodeList headchildren = ht.getChildren();

					headchildren.add(new org.htmlparser.nodes.TextNode(loadInternal("interactionparts/interaction.html", requestHeader)));

					augmented = true;

					ht.setChildren(headchildren);
				}

				// ADD AUGMENTED BODY INFORMATION

				NodeList body = list.extractAllNodesThatMatch(
						new org.htmlparser.filters.NodeClassFilter(
								org.htmlparser.tags.BodyTag.class), true);

				org.htmlparser.util.SimpleNodeIterator iterBody = body
						.elements();

				while (iterBody.hasMoreNodes()) {

					org.htmlparser.tags.BodyTag bt = ((org.htmlparser.tags.BodyTag) iterBody
							.nextNode());

					NodeList bodychildren = bt.getChildren();

					bodychildren.add(new org.htmlparser.nodes.TextNode(loadInternal("interaction/Footer.html", requestHeader)));

					bodychildren.add(new org.htmlparser.nodes.TextNode(loadInternal("interaction/OverlayInteraction.html?link="+url.toNormalform(true, false), requestHeader)));

					// ADD AUGMENTED INFO

					org.htmlparser.tags.Div sci_aug = new org.htmlparser.tags.Div();

					sci_aug.setTagName("div");

					sci_aug.setAttribute("id", "sciety_augmented");
					sci_aug.setAttribute("style",
							"visibility: hidden; position: absolute; overflow: hidden;");

					org.htmlparser.util.NodeList childr = new org.htmlparser.util.NodeList();


					sci_aug.setChildren(childr);

					org.htmlparser.tags.Div sci_aug_endtag = new org.htmlparser.tags.Div();

					sci_aug_endtag.setTagName("/div");

					sci_aug.setEndTag(sci_aug_endtag);

					bodychildren.add(sci_aug);

					bt.setChildren(bodychildren);

					augmented = true;

				}

				Doc = list.toHtml(true);

				augmented = true;

			} // not list = null

		} // reparse

		if (augmented) {

			return (new StringBuffer (Doc));
		} else {
			return (data);
		}
	}

}
