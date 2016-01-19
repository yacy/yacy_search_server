package pt.tumba.parser.swf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

/**
 *  Base class for SAX2 Parsers
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public abstract class SaxParserBase implements XMLReader {
    /**
     *  Description of the Field
     */
    protected EntityResolver resolver;
    /**
     *  Description of the Field
     */
    protected DTDHandler dtdhandler;
    /**
     *  Description of the Field
     */
    protected ContentHandler contenthandler;
    /**
     *  Description of the Field
     */
    protected ErrorHandler errorhandler;

    /**
     *  Description of the Field
     */
    protected List elementStack = new ArrayList();

    /**
     *  Description of the Field
     */
    protected String namespace;


    /**
     *  Gets the namespace attribute of the SaxParserBase object
     *
     *@return    The namespace value
     */
    public String getNamespace() {
        return namespace;
    }


    /**
     *  Sets the namespace attribute of the SaxParserBase object
     *
     *@param  namespace  The new namespace value
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }


    /**
     *  Constructor for the SaxParserBase object
     *
     *@param  namespace  Description of the Parameter
     */
    protected SaxParserBase(String namespace) {
        this.namespace = namespace;
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    protected void startDoc() throws IOException {
        if (contenthandler == null) {
            return;
        }

        try {
            contenthandler.startDocument();
        } catch (SAXException saxex) {
            throw new IOException(saxex.toString());
        }
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    protected void endDoc() throws IOException {
        if (contenthandler == null) {
            return;
        }

        try {
            contenthandler.endDocument();
        } catch (SAXException saxex) {
            throw new IOException(saxex.toString());
        }
    }


    /**
     *  Description of the Method
     *
     *@param  text             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void text(String text) throws IOException {
        if (contenthandler == null) {
            return;
        }

        try {
            contenthandler.characters(text.toCharArray(), 0, text.length());
        } catch (SAXException saxex) {
            throw new IOException(saxex.toString());
        }
    }


    /**
     *  Description of the Method
     *
     *@param  name             Description of the Parameter
     *@param  attributes       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void element(String name, String[] attributes)
             throws IOException {
        if (contenthandler == null) {
            return;
        }

        AttributesImpl attrs = new AttributesImpl();

        if (attributes != null) {
            int topIndex = attributes.length - 1;
            for (int i = 0; i < topIndex; i += 2) {
                String attName = attributes[i];
                String value = attributes[i + 1];

                if (attName != null && value != null) {
                    attrs.addAttribute("", attName, attName, "CDATA", value);
                }
            }
        }

        try {
            contenthandler.startElement(namespace, name, name, attrs);
            contenthandler.endElement(namespace, name, name);
        } catch (SAXException saxex) {
            throw new IOException(saxex.toString());
        }
    }


    /**
     *  Description of the Method
     *
     *@param  name             Description of the Parameter
     *@param  attributes       Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void start(String name, String[] attributes)
             throws IOException {
        elementStack.add(name);
        if (contenthandler == null) {
            return;
        }

        AttributesImpl attrs = new AttributesImpl();

        if (attributes != null) {
            int topIndex = attributes.length - 1;
            for (int i = 0; i < topIndex; i += 2) {
                String attName = attributes[i];
                String value = attributes[i + 1];

                if (attName != null && value != null) {
                    attrs.addAttribute("", attName, attName, "CDATA", value);
                }
            }
        }

        try {
            contenthandler.startElement(namespace, name, name, attrs);
        } catch (SAXException saxex) {
            throw new IOException(saxex.toString());
        }
    }


    /**
     *  Description of the Method
     *
     *@exception  IOException  Description of the Exception
     */
    protected void end() throws IOException {
        if (elementStack.isEmpty()) {
            return;
        }
        if (contenthandler == null) {
            return;
        }

        String name = (String) elementStack.remove(elementStack.size() - 1);

        try {
            contenthandler.endElement(namespace, name, name);
        } catch (SAXException saxex) {
            throw new IOException(saxex.toString());
        }
    }


    //============ XMLReader interface follows: ================

    /**
     *  Gets the feature attribute of the SaxParserBase object
     *
     *@param  name                           Description of the Parameter
     *@return                                The feature value
     *@exception  SAXNotRecognizedException  Description of the Exception
     *@exception  SAXNotSupportedException   Description of the Exception
     */
    public boolean getFeature(String name)
             throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals("http://xml.org/sax/features/namespaces")) {
            return true;
        }

        if (name.equals("http://xml.org/sax/features/namespace-prefixes")) {
            return false;
        }

        throw new SAXNotRecognizedException(name);
    }


    /**
     *  Sets the feature attribute of the SaxParserBase object
     *
     *@param  name                           The new feature value
     *@param  value                          The new feature value
     *@exception  SAXNotRecognizedException  Description of the Exception
     *@exception  SAXNotSupportedException   Description of the Exception
     */
    public void setFeature(String name, boolean value)
             throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals("http://xml.org/sax/features/namespaces")
                || name.equals("http://xml.org/sax/features/namespace-prefixes")) {
            return;
        }

        throw new SAXNotRecognizedException(name);
    }


    /**
     *  Gets the property attribute of the SaxParserBase object
     *
     *@param  name                           Description of the Parameter
     *@return                                The property value
     *@exception  SAXNotRecognizedException  Description of the Exception
     *@exception  SAXNotSupportedException   Description of the Exception
     */
    public Object getProperty(String name)
             throws SAXNotRecognizedException, SAXNotSupportedException {
        throw new SAXNotRecognizedException(name);
    }


    /**
     *  Sets the property attribute of the SaxParserBase object
     *
     *@param  name                           The new property value
     *@param  value                          The new property value
     *@exception  SAXNotRecognizedException  Description of the Exception
     *@exception  SAXNotSupportedException   Description of the Exception
     */
    public void setProperty(String name, Object value)
             throws SAXNotRecognizedException, SAXNotSupportedException {
        throw new SAXNotRecognizedException(name);
    }


    /**
     *  Sets the entityResolver attribute of the SaxParserBase object
     *
     *@param  resolver  The new entityResolver value
     */
    public void setEntityResolver(EntityResolver resolver) {
        this.resolver = resolver;
    }


    /**
     *  Gets the entityResolver attribute of the SaxParserBase object
     *
     *@return    The entityResolver value
     */
    public EntityResolver getEntityResolver() {
        return resolver;
    }


    /**
     *  Sets the dTDHandler attribute of the SaxParserBase object
     *
     *@param  handler  The new dTDHandler value
     */
    public void setDTDHandler(DTDHandler handler) {
        this.dtdhandler = handler;
    }


    /**
     *  Gets the dTDHandler attribute of the SaxParserBase object
     *
     *@return    The dTDHandler value
     */
    public DTDHandler getDTDHandler() {
        return dtdhandler;
    }


    /**
     *  Sets the contentHandler attribute of the SaxParserBase object
     *
     *@param  handler  The new contentHandler value
     */
    public void setContentHandler(ContentHandler handler) {
        this.contenthandler = handler;
    }


    /**
     *  Gets the contentHandler attribute of the SaxParserBase object
     *
     *@return    The contentHandler value
     */
    public ContentHandler getContentHandler() {
        return contenthandler;
    }


    /**
     *  Sets the errorHandler attribute of the SaxParserBase object
     *
     *@param  handler  The new errorHandler value
     */
    public void setErrorHandler(ErrorHandler handler) {
        this.errorhandler = handler;
    }


    /**
     *  Gets the errorHandler attribute of the SaxParserBase object
     *
     *@return    The errorHandler value
     */
    public ErrorHandler getErrorHandler() {
        return errorhandler;
    }


    /**
     *  Description of the Method
     *
     *@param  input             Description of the Parameter
     *@exception  IOException   Description of the Exception
     *@exception  SAXException  Description of the Exception
     */
    public abstract void parse(InputSource input) throws IOException, SAXException;


    /**
     *  Description of the Method
     *
     *@param  systemId          Description of the Parameter
     *@exception  IOException   Description of the Exception
     *@exception  SAXException  Description of the Exception
     */
    public abstract void parse(String systemId) throws IOException, SAXException;
}
