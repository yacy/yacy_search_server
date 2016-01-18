package pt.tumba.parser.swf;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *  Write XML text to an output stream
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class XMLWriter extends SaxHandlerBase {
    /**
     *  Description of the Field
     */
    protected Writer out;
    /**
     *  Description of the Field
     */
    protected boolean started = false;


    /**
     *  Constructor for the XMLWriter object
     *
     *@param  outstream  Description of the Parameter
     */
    public XMLWriter(OutputStream outstream) {
        out = new PrintWriter(outstream);
    }


    /**
     *  Constructor for the XMLWriter object
     *
     *@param  writer  Description of the Parameter
     */
    public XMLWriter(PrintWriter writer) {
        out = writer;
    }


    /**
     *  Description of the Method
     *
     *@exception  SAXException  Description of the Exception
     */
    public void startDocument() throws SAXException {
        try {
            out.write("<?xml version='1.0'?>");
        } catch (IOException ioe) {
            throw new SAXException(ioe);
        }
    }


    /**
     *  Description of the Method
     *
     *@exception  SAXException  Description of the Exception
     */
    public void endDocument() throws SAXException {
        try {
            out.flush();
        } catch (IOException ioe) {
            throw new SAXException(ioe);
        }
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    protected void completeElement() throws IOException {
        if (!started) {
            return;
        }

        out.write(" >");
        started = false;
    }


    /**
     *  Description of the Method
     *
     *@param  chars   Description of the Parameter
     *@param  start   Description of the Parameter
     *@param  length  Description of the Parameter
     *@return         Description of the Return Value
     */
    public static String normalize(char[] chars, int start, int length) {
        StringBuffer buff = new StringBuffer();

        for (int i = start; i < start + length; i++) {
            char c = chars[i];

            switch (c) {
                case '\'':
                    buff.append("&apos;");
                    break;
                case '"':
                    buff.append("&quot;");
                    break;
                case '&':
                    buff.append("&amp;");
                    break;
                case '<':
                    buff.append("&lt;");
                    break;
                case '>':
                    buff.append("&gt;");
                    break;
                default:
                    buff.append(""+c);
                    break;
            }
        }

        return buff.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  namespaceURI      Description of the Parameter
     *@param  localName         Description of the Parameter
     *@param  qName             Description of the Parameter
     *@param  atts              Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void startElement(String namespaceURI, String localName,
            String qName, Attributes atts)
             throws SAXException {
        try {
            completeElement();
            started = true;

            out.write("<" + qName);

            if (atts != null) {
                int count = atts.getLength();

                for (int i = 0; i < count; i++) {
                    String name = atts.getQName(i);
                    String value = atts.getValue(i);

                    out.write(" " + name + "='" +
                            normalize(value.toCharArray(), 0, value.length())
                            + "'");
                }
            }
        } catch (IOException ioe) {
            throw new SAXException(ioe);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  namespaceURI      Description of the Parameter
     *@param  localName         Description of the Parameter
     *@param  qName             Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void endElement(String namespaceURI, String localName, String qName)
             throws SAXException {
        try {
            if (started) {
                out.write(" />");
            } else {
                out.write("</" + qName + ">");
            }
            started = false;
        } catch (IOException ioe) {
            throw new SAXException(ioe);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  ch                Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  length            Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void characters(char ch[], int start, int length)
             throws SAXException {
        try {
            completeElement();
            out.write(normalize(ch, start, length));
        } catch (IOException ioe) {
            throw new SAXException(ioe);
        }
    }


    /**
     *  Description of the Method
     *
     *@param  target            Description of the Parameter
     *@param  data              Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void processingInstruction(String target, String data)
             throws SAXException {
        try {
            completeElement();
            out.write("<?" + target + " " + data + "?>");
        } catch (IOException ioe) {
            throw new SAXException(ioe);
        }
    }

}
