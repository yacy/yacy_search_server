package pt.tumba.parser.swf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 *  Base class for SAX2 Content Handlers
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public abstract class SaxHandlerBase extends DefaultHandler {
    /**
     *  Description of the Field
     */
    protected Map elementTypes = new HashMap();
    /**
     *  Description of the Field
     */
    protected SaxHandlerBase.ElementType elemType;
    /**
     *  Description of the Field
     */
    protected List elems = new ArrayList();

    /**
     *  Description of the Field
     */
    protected boolean gatherMode = false;
    /**
     *  Description of the Field
     */
    protected List gatherBuffer;
    /**
     *  Description of the Field
     */
    protected SaxHandlerBase.GatheringElementType gatheringElement;


    //--Start gathering elements/chars for later dispatch
    /**
     *  Description of the Method
     *
     *@param  elem  Description of the Parameter
     */
    public void startGatherMode(SaxHandlerBase.GatheringElementType elem) {
        gatheringElement = elem;
        gatherBuffer = new ArrayList();
        gatherMode = true;
    }


    //--stop gathering and dispatch the gathered elements
    /**
     *  Description of the Method
     *
     *@exception  Exception  Description of the Exception
     */
    public void endGatherMode() throws Exception {
        gatherMode = false;
        gatheringElement = null;

        //--replay the elements
        for (Iterator it = gatherBuffer.iterator(); it.hasNext(); ) {
            Object[] elem = (Object[]) it.next();

            SaxHandlerBase.ElementType type = (SaxHandlerBase.ElementType) elem[0];
            if (type == null) {
                continue;
            }

            if (elem[1] == null) {
                //element end

                type.endElement();
            } else if (elem[1] instanceof String) {
                String charstring = (String) elem[1];
                char[] chars = charstring.toCharArray();
                type.characters(chars, 0, chars.length);
            } else {
                Attributes atts = (Attributes) elem[1];
                type.startElement(atts);
            }
        }

        gatherBuffer = null;
    }


    //--dispatch all the gathered elements that match the name
    /**
     *  Description of the Method
     *
     *@param  elemName       Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    public void dispatchAllMatchingGatheredElements(String elemName) throws Exception {
        SaxHandlerBase.ElementType dispelem =
                (SaxHandlerBase.ElementType) elementTypes.get(elemName);

        if (dispelem == null) {
            return;
        }
        boolean found = false;

        for (Iterator it = gatherBuffer.iterator(); it.hasNext(); ) {
            Object[] elem = (Object[]) it.next();

            SaxHandlerBase.ElementType type = (SaxHandlerBase.ElementType) elem[0];
            if (type == null) {
                continue;
            }

            if (type == dispelem) {
                found = true;
            }

            if (found) {
                it.remove();

                if (elem[1] == null) {
                    //element end

                    type.endElement();
                    if (type == dispelem) {
                        found = false;
                    }
                    //done dispatching this element
                } else if (elem[1] instanceof String) {
                    String charstring = (String) elem[1];
                    char[] chars = charstring.toCharArray();
                    type.characters(chars, 0, chars.length);
                } else {
                    Attributes atts = (Attributes) elem[1];
                    type.startElement(atts);
                }
            }
        }
    }


    //--dispatch the first gathered element that matches the name
    /**
     *  Description of the Method
     *
     *@param  elemName       Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    public void dispatchGatheredElement(String elemName) throws Exception {
        SaxHandlerBase.ElementType dispelem =
                (SaxHandlerBase.ElementType) elementTypes.get(elemName);

        if (dispelem == null) {
            return;
        }
        boolean found = false;

        for (Iterator it = gatherBuffer.iterator(); it.hasNext(); ) {
            Object[] elem = (Object[]) it.next();

            SaxHandlerBase.ElementType type = (SaxHandlerBase.ElementType) elem[0];
            if (type == null) {
                continue;
            }

            if (type == dispelem) {
                found = true;
            }

            if (found) {
                it.remove();

                if (elem[1] == null) {
                    //element end

                    type.endElement();
                    if (type == dispelem) {
                        return;
                    }
                    //done dispatching
                } else if (elem[1] instanceof String) {
                    String charstring = (String) elem[1];
                    char[] chars = charstring.toCharArray();
                    type.characters(chars, 0, chars.length);
                } else {
                    Attributes atts = (Attributes) elem[1];
                    type.startElement(atts);
                }
            }
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class ElementType {
        /**
         *  Description of the Method
         *
         *@param  atts           Description of the Parameter
         *@exception  Exception  Description of the Exception
         */
        public void startElement(Attributes atts) throws Exception { }


        /**
         *  Description of the Method
         *
         *@exception  Exception  Description of the Exception
         */
        public void endElement() throws Exception { }


        /**
         *  Description of the Method
         *
         *@param  ch             Description of the Parameter
         *@param  start          Description of the Parameter
         *@param  length         Description of the Parameter
         *@exception  Exception  Description of the Exception
         */
        public void characters(char[] ch, int start, int length)
                 throws Exception { }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class ContentElementType extends SaxHandlerBase.ElementType {
        /**
         *  Description of the Field
         */
        protected Attributes attrs;
        /**
         *  Description of the Field
         */
        protected StringBuffer buff;


        /**
         *  Description of the Method
         *
         *@param  atts           Description of the Parameter
         *@exception  Exception  Description of the Exception
         */
        public void startElement(Attributes atts) throws Exception {
            attrs = new AttributesImpl(atts);
            buff = new StringBuffer();
        }


        /**
         *  Description of the Method
         *
         *@param  ch             Description of the Parameter
         *@param  start          Description of the Parameter
         *@param  length         Description of the Parameter
         *@exception  Exception  Description of the Exception
         */
        public void characters(char[] ch, int start, int length)
                 throws Exception {
            buff.append(ch, start, length);
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class GatheringElementType extends SaxHandlerBase.ContentElementType {
        /**
         *  Description of the Method
         *
         *@param  atts           Description of the Parameter
         *@exception  Exception  Description of the Exception
         */
        public void startElement(Attributes atts) throws Exception {
            super.startElement(atts);
        }


        /**
         *  Description of the Method
         *
         *@param  localName  Description of the Parameter
         *@param  atts       Description of the Parameter
         *@return            Description of the Return Value
         */
        public boolean gatherElement(String localName, Attributes atts) {
            return true;
        }
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
            elemType = (SaxHandlerBase.ElementType) elementTypes.get(localName);

            if (gatherMode) {
                //gather the element for later processing

                if (gatheringElement.gatherElement(localName, atts)) {
                    gatherBuffer.add(new Object[]{elemType, new AttributesImpl(atts)});
                }
            } else {
                if (elemType == null) {
                    return;
                }
                elemType.startElement(atts);
            }

            elems.add(elemType);
        } catch (SAXException saxex) {
            throw saxex;
        } catch (Exception ex) {
            //ex.printStackTrace();
            throw new SAXException(ex);
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
            elemType = (SaxHandlerBase.ElementType) elementTypes.get(localName);
            if (elemType == null) {
                return;
            }

            if (elemType == gatheringElement) {
                gatherMode = false;
            }

            if (gatherMode) {
                //gather the element for later processing

                gatherBuffer.add(new Object[]{elemType, null});
            } else {
                elemType.endElement();
            }

            if (!elems.isEmpty()) {
                elemType = (SaxHandlerBase.ElementType) elems.remove(elems.size() - 1);
            } else {
                elemType = null;
            }
        } catch (SAXException saxex) {
            throw saxex;
        } catch (Exception ex) {
            throw new SAXException(ex);
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
    public void characters(char[] ch, int start, int length) throws SAXException {
        try {
            if (elemType == null) {
                return;
            }

            if (gatherMode) {
                //gather the element for later processing

                gatherBuffer.add(new Object[]{elemType, new String(ch, start, length)});
            } else {
                elemType.characters(ch, start, length);
            }
        } catch (SAXException saxex) {
            throw saxex;
        } catch (Exception ex) {
            throw new SAXException(ex);
        }
    }


    /**
     *  Gets the attr attribute of the SaxHandlerBase class
     *
     *@param  attrs         Description of the Parameter
     *@param  name          Description of the Parameter
     *@param  defaultValue  Description of the Parameter
     *@return               The attr value
     */
    public static String getAttr(Attributes attrs, String name, String defaultValue) {
        String value = attrs.getValue("", name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }


    /**
     *  Gets the attrInt attribute of the SaxHandlerBase class
     *
     *@param  attrs         Description of the Parameter
     *@param  name          Description of the Parameter
     *@param  defaultValue  Description of the Parameter
     *@return               The attrInt value
     */
    public static int getAttrInt(Attributes attrs, String name, int defaultValue) {
        String value = attrs.getValue("", name);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }


    /**
     *  Gets the attrDouble attribute of the SaxHandlerBase class
     *
     *@param  attrs         Description of the Parameter
     *@param  name          Description of the Parameter
     *@param  defaultValue  Description of the Parameter
     *@return               The attrDouble value
     */
    public static double getAttrDouble(Attributes attrs, String name, double defaultValue) {
        String value = attrs.getValue("", name);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }


    /**
     *  Gets the attrBool attribute of the SaxHandlerBase class
     *
     *@param  attrs         Description of the Parameter
     *@param  name          Description of the Parameter
     *@param  defaultValue  Description of the Parameter
     *@return               The attrBool value
     */
    public static boolean getAttrBool(Attributes attrs, String name, boolean defaultValue) {
        String value = attrs.getValue("", name);
        if (value == null) {
            return defaultValue;
        }

        if (value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        return false;
    }
}
