/*
 * ====================================================================
 * About Sarissa: http://dev.abiss.gr/sarissa
 * ====================================================================
 * Sarissa is an ECMAScript library acting as a cross-browser wrapper for native XML APIs.
 * The library supports Gecko based browsers like Mozilla and Firefox,
 * Internet Explorer (5.5+ with MSXML3.0+), Konqueror, Safari and Opera
 * @version 0.9.9.4
 * @author: Copyright 2004-2008 Emmanouil Batsis, mailto: mbatsis at users full stop sourceforge full stop net
 * ====================================================================
 * Licence
 * ====================================================================
 * Sarissa is free software distributed under the GNU GPL version 2 (see <a href="gpl.txt">gpl.txt</a>) or higher, 
 * GNU LGPL version 2.1 (see <a href="lgpl.txt">lgpl.txt</a>) or higher and Apache Software License 2.0 or higher 
 * (see <a href="asl.txt">asl.txt</a>). This means you can choose one of the three and use that if you like. If 
 * you make modifications under the ASL, i would appreciate it if you submitted those.
 * In case your copy of Sarissa does not include the license texts, you may find
 * them online in various formats at <a href="http://www.gnu.org">http://www.gnu.org</a> and 
 * <a href="http://www.apache.org">http://www.apache.org</a>.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY 
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE 
 * WARRANTIES OF MERCHANTABILITY,FITNESS FOR A PARTICULAR PURPOSE 
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR 
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR 
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE 
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
/**
 * <p>Sarissa is a utility class. Provides "static" methods for DOMDocument, 
 * DOM Node serialization to XML strings and other utility goodies.</p>
 * @constructor
 * @static
 */
function Sarissa(){}
Sarissa.VERSION = "0.9.9.4";
Sarissa.PARSED_OK = "Document contains no parsing errors";
Sarissa.PARSED_EMPTY = "Document is empty";
Sarissa.PARSED_UNKNOWN_ERROR = "Not well-formed or other error";
Sarissa.IS_ENABLED_TRANSFORM_NODE = false;
Sarissa.REMOTE_CALL_FLAG = "gr.abiss.sarissa.REMOTE_CALL_FLAG";
/** @private */
Sarissa._lastUniqueSuffix = 0;
/** @private */
Sarissa._getUniqueSuffix = function(){
	return Sarissa._lastUniqueSuffix++;
};
/** @private */
Sarissa._SARISSA_IEPREFIX4XSLPARAM = "";
/** @private */
Sarissa._SARISSA_HAS_DOM_IMPLEMENTATION = document.implementation && true;
/** @private */
Sarissa._SARISSA_HAS_DOM_CREATE_DOCUMENT = Sarissa._SARISSA_HAS_DOM_IMPLEMENTATION && document.implementation.createDocument;
/** @private */
Sarissa._SARISSA_HAS_DOM_FEATURE = Sarissa._SARISSA_HAS_DOM_IMPLEMENTATION && document.implementation.hasFeature;
/** @private */
Sarissa._SARISSA_IS_MOZ = Sarissa._SARISSA_HAS_DOM_CREATE_DOCUMENT && Sarissa._SARISSA_HAS_DOM_FEATURE;
/** @private */
Sarissa._SARISSA_IS_SAFARI = navigator.userAgent.toLowerCase().indexOf("safari") != -1 || navigator.userAgent.toLowerCase().indexOf("konqueror") != -1;
/** @private */
Sarissa._SARISSA_IS_SAFARI_OLD = Sarissa._SARISSA_IS_SAFARI && (parseInt((navigator.userAgent.match(/AppleWebKit\/(\d+)/)||{})[1], 10) < 420);
/** @private */
Sarissa._SARISSA_IS_IE = document.all && window.ActiveXObject && navigator.userAgent.toLowerCase().indexOf("msie") > -1  && navigator.userAgent.toLowerCase().indexOf("opera") == -1;
/** @private */
Sarissa._SARISSA_IS_OPERA = navigator.userAgent.toLowerCase().indexOf("opera") != -1;
if(!window.Node || !Node.ELEMENT_NODE){
    Node = {ELEMENT_NODE: 1, ATTRIBUTE_NODE: 2, TEXT_NODE: 3, CDATA_SECTION_NODE: 4, ENTITY_REFERENCE_NODE: 5,  ENTITY_NODE: 6, PROCESSING_INSTRUCTION_NODE: 7, COMMENT_NODE: 8, DOCUMENT_NODE: 9, DOCUMENT_TYPE_NODE: 10, DOCUMENT_FRAGMENT_NODE: 11, NOTATION_NODE: 12};
}

//This breaks for(x in o) loops in the old Safari
if(Sarissa._SARISSA_IS_SAFARI_OLD){
	HTMLHtmlElement = document.createElement("html").constructor;
	Node = HTMLElement = {};
	HTMLElement.prototype = HTMLHtmlElement.__proto__.__proto__;
	HTMLDocument = Document = document.constructor;
	var x = new DOMParser();
	XMLDocument = x.constructor;
	Element = x.parseFromString("<Single />", "text/xml").documentElement.constructor;
	x = null;
}
if(typeof XMLDocument == "undefined" && typeof Document !="undefined"){ XMLDocument = Document; } 

// IE initialization
if(Sarissa._SARISSA_IS_IE){
    // for XSLT parameter names, prefix needed by IE
    Sarissa._SARISSA_IEPREFIX4XSLPARAM = "xsl:";
    // used to store the most recent ProgID available out of the above
    var _SARISSA_DOM_PROGID = "";
    var _SARISSA_XMLHTTP_PROGID = "";
    var _SARISSA_DOM_XMLWRITER = "";
    /**
     * Called when the sarissa.js file is parsed, to pick most recent
     * ProgIDs for IE, then gets destroyed.
     * @memberOf Sarissa
     * @private
     * @param idList an array of MSXML PROGIDs from which the most recent will be picked for a given object
     * @param enabledList an array of arrays where each array has two items; the index of the PROGID for which a certain feature is enabled
     */
    Sarissa.pickRecentProgID = function (idList){
        // found progID flag
        var bFound = false, e;
        var o2Store;
        for(var i=0; i < idList.length && !bFound; i++){
            try{
                var oDoc = new ActiveXObject(idList[i]);
                o2Store = idList[i];
                bFound = true;
            }catch (objException){
                // trap; try next progID
                e = objException;
            }
        }
        if (!bFound) {
            throw "Could not retrieve a valid progID of Class: " + idList[idList.length-1]+". (original exception: "+e+")";
        }
        idList = null;
        return o2Store;
    };
    // pick best available MSXML progIDs
    _SARISSA_DOM_PROGID = null;
    _SARISSA_THREADEDDOM_PROGID = null;
    _SARISSA_XSLTEMPLATE_PROGID = null;
    _SARISSA_XMLHTTP_PROGID = null;
    // commenting the condition out; we need to redefine XMLHttpRequest 
    // anyway as IE7 hardcodes it to MSXML3.0 causing version problems 
    // between different activex controls 
    //if(!window.XMLHttpRequest){
    /**
     * Emulate XMLHttpRequest
     * @constructor
     */
    XMLHttpRequest = function() {
        if(!_SARISSA_XMLHTTP_PROGID){
            _SARISSA_XMLHTTP_PROGID = Sarissa.pickRecentProgID(["Msxml2.XMLHTTP.6.0", "MSXML2.XMLHTTP.3.0", "MSXML2.XMLHTTP", "Microsoft.XMLHTTP"]);
        }
        return new ActiveXObject(_SARISSA_XMLHTTP_PROGID);
    };
    //}
    // we dont need this anymore
    //============================================
    // Factory methods (IE)
    //============================================
    // see non-IE version
    Sarissa.getDomDocument = function(sUri, sName){
        if(!_SARISSA_DOM_PROGID){
            _SARISSA_DOM_PROGID = Sarissa.pickRecentProgID(["Msxml2.DOMDocument.6.0", "Msxml2.DOMDocument.3.0", "MSXML2.DOMDocument", "MSXML.DOMDocument", "Microsoft.XMLDOM"]);
        }
        var oDoc = new ActiveXObject(_SARISSA_DOM_PROGID);
        // if a root tag name was provided, we need to load it in the DOM object
        if (sName){
            // create an artifical namespace prefix 
            // or reuse existing prefix if applicable
            var prefix = "";
            if(sUri){
                if(sName.indexOf(":") > 1){
                    prefix = sName.substring(0, sName.indexOf(":"));
                    sName = sName.substring(sName.indexOf(":")+1); 
                }else{
                    prefix = "a" + Sarissa._getUniqueSuffix();
                }
            }
            // use namespaces if a namespace URI exists
            if(sUri){
                oDoc.loadXML('<' + prefix+':'+sName + " xmlns:" + prefix + "=\"" + sUri + "\"" + " />");
            } else {
                oDoc.loadXML('<' + sName + " />");
            }
        }
        return oDoc;
    };
    // see non-IE version   
    Sarissa.getParseErrorText = function (oDoc) {
        var parseErrorText = Sarissa.PARSED_OK;
        if(oDoc && oDoc.parseError && oDoc.parseError.errorCode && oDoc.parseError.errorCode != 0){
            parseErrorText = "XML Parsing Error: " + oDoc.parseError.reason + 
                "\nLocation: " + oDoc.parseError.url + 
                "\nLine Number " + oDoc.parseError.line + ", Column " + 
                oDoc.parseError.linepos + 
                ":\n" + oDoc.parseError.srcText +
                "\n";
            for(var i = 0;  i < oDoc.parseError.linepos;i++){
                parseErrorText += "-";
            }
            parseErrorText +=  "^\n";
        }
        else if(oDoc.documentElement === null){
            parseErrorText = Sarissa.PARSED_EMPTY;
        }
        return parseErrorText;
    };
    // see non-IE version
    Sarissa.setXpathNamespaces = function(oDoc, sNsSet) {
        oDoc.setProperty("SelectionLanguage", "XPath");
        oDoc.setProperty("SelectionNamespaces", sNsSet);
    };
    /**
     * A class that reuses the same XSLT stylesheet for multiple transforms.
     * @constructor
     */
    XSLTProcessor = function(){
        if(!_SARISSA_XSLTEMPLATE_PROGID){
            _SARISSA_XSLTEMPLATE_PROGID = Sarissa.pickRecentProgID(["Msxml2.XSLTemplate.6.0", "MSXML2.XSLTemplate.3.0"]);
        }
        this.template = new ActiveXObject(_SARISSA_XSLTEMPLATE_PROGID);
        this.processor = null;
    };
    /**
     * Imports the given XSLT DOM and compiles it to a reusable transform
     * <b>Note:</b> If the stylesheet was loaded from a URL and contains xsl:import or xsl:include elements,it will be reloaded to resolve those
     * @param {DOMDocument} xslDoc The XSLT DOMDocument to import
     */
    XSLTProcessor.prototype.importStylesheet = function(xslDoc){
        if(!_SARISSA_THREADEDDOM_PROGID){
            _SARISSA_THREADEDDOM_PROGID = Sarissa.pickRecentProgID(["MSXML2.FreeThreadedDOMDocument.6.0", "MSXML2.FreeThreadedDOMDocument.3.0"]);
        }
        xslDoc.setProperty("SelectionLanguage", "XPath");
        xslDoc.setProperty("SelectionNamespaces", "xmlns:xsl='http://www.w3.org/1999/XSL/Transform'");
        // convert stylesheet to free threaded
        var converted = new ActiveXObject(_SARISSA_THREADEDDOM_PROGID);
        // make included/imported stylesheets work if exist and xsl was originally loaded from url
        try{
            converted.resolveExternals = true; 
            converted.setProperty("AllowDocumentFunction", true); 
        }
        catch(e){
            // Ignore. "AllowDocumentFunction" is only supported in MSXML 3.0 SP4 and later.
        } 
        if(xslDoc.url && xslDoc.selectSingleNode("//xsl:*[local-name() = 'import' or local-name() = 'include']") != null){
            converted.async = false;
            converted.load(xslDoc.url);
        } 
        else {
            converted.loadXML(xslDoc.xml);
        }
        converted.setProperty("SelectionNamespaces", "xmlns:xsl='http://www.w3.org/1999/XSL/Transform'");
        var output = converted.selectSingleNode("//xsl:output");
        //this.outputMethod = output ? output.getAttribute("method") : "html";
        if(output) {
            this.outputMethod = output.getAttribute("method");
        } 
        else {
            delete this.outputMethod;
        } 
        this.template.stylesheet = converted;
        this.processor = this.template.createProcessor();
        // for getParameter and clearParameters
        this.paramsSet = [];
    };

    /**
     * Transform the given XML DOM and return the transformation result as a new DOM document
     * @param {DOMDocument} sourceDoc The XML DOMDocument to transform
     * @return {DOMDocument} The transformation result as a DOM Document
     */
    XSLTProcessor.prototype.transformToDocument = function(sourceDoc){
        // fix for bug 1549749
        var outDoc;
        if(_SARISSA_THREADEDDOM_PROGID){
            this.processor.input=sourceDoc;
            outDoc=new ActiveXObject(_SARISSA_DOM_PROGID);
            this.processor.output=outDoc;
            this.processor.transform();
            return outDoc;
        }
        else{
            if(!_SARISSA_DOM_XMLWRITER){
                _SARISSA_DOM_XMLWRITER = Sarissa.pickRecentProgID(["Msxml2.MXXMLWriter.6.0", "Msxml2.MXXMLWriter.3.0", "MSXML2.MXXMLWriter", "MSXML.MXXMLWriter", "Microsoft.XMLDOM"]);
            }
            this.processor.input = sourceDoc;
            outDoc = new ActiveXObject(_SARISSA_DOM_XMLWRITER);
            this.processor.output = outDoc; 
            this.processor.transform();
            var oDoc = new ActiveXObject(_SARISSA_DOM_PROGID);
            oDoc.loadXML(outDoc.output+"");
            return oDoc;
        }
    };
    
    /**
     * Transform the given XML DOM and return the transformation result as a new DOM fragment.
     * <b>Note</b>: The xsl:output method must match the nature of the owner document (XML/HTML).
     * @param {DOMDocument} sourceDoc The XML DOMDocument to transform
     * @param {DOMDocument} ownerDoc The owner of the result fragment
     * @return {DOMDocument} The transformation result as a DOM Document
     */
    XSLTProcessor.prototype.transformToFragment = function (sourceDoc, ownerDoc) {
        this.processor.input = sourceDoc;
        this.processor.transform();
        var s = this.processor.output;
        var f = ownerDoc.createDocumentFragment();
        var container;
        if (this.outputMethod == 'text') {
            f.appendChild(ownerDoc.createTextNode(s));
        } else if (ownerDoc.body && ownerDoc.body.innerHTML) {
            container = ownerDoc.createElement('div');
            container.innerHTML = s;
            while (container.hasChildNodes()) {
                f.appendChild(container.firstChild);
            }
        }
        else {
            var oDoc = new ActiveXObject(_SARISSA_DOM_PROGID);
            if (s.substring(0, 5) == '<?xml') {
                s = s.substring(s.indexOf('?>') + 2);
            }
            var xml = ''.concat('<my>', s, '</my>');
            oDoc.loadXML(xml);
            container = oDoc.documentElement;
            while (container.hasChildNodes()) {
                f.appendChild(container.firstChild);
            }
        }
        return f;
    };
    
    /**
     * Set global XSLT parameter of the imported stylesheet
     * @param {String} nsURI The parameter namespace URI
     * @param {String} name The parameter base name
     * @param {String} value The new parameter value
     */
     XSLTProcessor.prototype.setParameter = function(nsURI, name, value){
         // make value a zero length string if null to allow clearing
         value = value ? value : "";
         // nsURI is optional but cannot be null
         if(nsURI){
             this.processor.addParameter(name, value, nsURI);
         }else{
             this.processor.addParameter(name, value);
         }
         // update updated params for getParameter
         nsURI = "" + (nsURI || "");
         if(!this.paramsSet[nsURI]){
             this.paramsSet[nsURI] = [];
         }
         this.paramsSet[nsURI][name] = value;
     };
    /**
     * Gets a parameter if previously set by setParameter. Returns null
     * otherwise
     * @param {String} name The parameter base name
     * @param {String} value The new parameter value
     * @return {String} The parameter value if reviously set by setParameter, null otherwise
     */
    XSLTProcessor.prototype.getParameter = function(nsURI, name){
        nsURI = "" + (nsURI || "");
        if(this.paramsSet[nsURI] && this.paramsSet[nsURI][name]){
            return this.paramsSet[nsURI][name];
        }else{
            return null;
        }
    };
    
    /**
     * Clear parameters (set them to default values as defined in the stylesheet itself)
     */
    XSLTProcessor.prototype.clearParameters = function(){
        for(var nsURI in this.paramsSet){
            for(var name in this.paramsSet[nsURI]){
                if(nsURI!=""){
                    this.processor.addParameter(name, "", nsURI);
                }else{
                    this.processor.addParameter(name, "");
                }
            }
        }
        this.paramsSet = [];
    };
}else{ /* end IE initialization, try to deal with real browsers now ;-) */
    if(Sarissa._SARISSA_HAS_DOM_CREATE_DOCUMENT){
        /**
         * <p>Ensures the document was loaded correctly, otherwise sets the
         * parseError to -1 to indicate something went wrong. Internal use</p>
         * @private
         */
        Sarissa.__handleLoad__ = function(oDoc){
            Sarissa.__setReadyState__(oDoc, 4);
        };
        /**
        * <p>Attached by an event handler to the load event. Internal use.</p>
        * @private
        */
        _sarissa_XMLDocument_onload = function(){
            Sarissa.__handleLoad__(this);
        };
        /**
         * <p>Sets the readyState property of the given DOM Document object.
         * Internal use.</p>
         * @memberOf Sarissa
         * @private
         * @param oDoc the DOM Document object to fire the
         *          readystatechange event
         * @param iReadyState the number to change the readystate property to
         */
        Sarissa.__setReadyState__ = function(oDoc, iReadyState){
            oDoc.readyState = iReadyState;
            oDoc.readystate = iReadyState;
            if (oDoc.onreadystatechange != null && typeof oDoc.onreadystatechange == "function") {
                oDoc.onreadystatechange();
            }
        };
        
        Sarissa.getDomDocument = function(sUri, sName){
            var oDoc = document.implementation.createDocument(sUri?sUri:null, sName?sName:null, null);
            if(!oDoc.onreadystatechange){
            
                /**
                * <p>Emulate IE's onreadystatechange attribute</p>
                */
                oDoc.onreadystatechange = null;
            }
            if(!oDoc.readyState){
                /**
                * <p>Emulates IE's readyState property, which always gives an integer from 0 to 4:</p>
                * <ul><li>1 == LOADING,</li>
                * <li>2 == LOADED,</li>
                * <li>3 == INTERACTIVE,</li>
                * <li>4 == COMPLETED</li></ul>
                */
                oDoc.readyState = 0;
            }
            oDoc.addEventListener("load", _sarissa_XMLDocument_onload, false);
            return oDoc;
        };
        if(window.XMLDocument){
            // do nothing
        }// TODO: check if the new document has content before trying to copynodes, check  for error handling in DOM 3 LS
        else if(Sarissa._SARISSA_HAS_DOM_FEATURE && window.Document && !Document.prototype.load && document.implementation.hasFeature('LS', '3.0')){
    		//Opera 9 may get the XPath branch which gives creates XMLDocument, therefore it doesn't reach here which is good
            /**
            * <p>Factory method to obtain a new DOM Document object</p>
            * @memberOf Sarissa
            * @param {String} sUri the namespace of the root node (if any)
            * @param {String} sUri the local name of the root node (if any)
            * @returns {DOMDOcument} a new DOM Document
            */
            Sarissa.getDomDocument = function(sUri, sName){
                var oDoc = document.implementation.createDocument(sUri?sUri:null, sName?sName:null, null);
                return oDoc;
            };
        }
        else {
            Sarissa.getDomDocument = function(sUri, sName){
                var oDoc = document.implementation.createDocument(sUri?sUri:null, sName?sName:null, null);
                // looks like safari does not create the root element for some unknown reason
                if(oDoc && (sUri || sName) && !oDoc.documentElement){
                    oDoc.appendChild(oDoc.createElementNS(sUri, sName));
                }
                return oDoc;
            };
        }
    }//if(Sarissa._SARISSA_HAS_DOM_CREATE_DOCUMENT)
}
//==========================================
// Common stuff
//==========================================
if(!window.DOMParser){
    if(Sarissa._SARISSA_IS_SAFARI){
        /**
         * DOMParser is a utility class, used to construct DOMDocuments from XML strings
         * @constructor
         */
        DOMParser = function() { };
        /** 
        * Construct a new DOM Document from the given XMLstring
        * @param {String} sXml the given XML string
        * @param {String} contentType the content type of the document the given string represents (one of text/xml, application/xml, application/xhtml+xml). 
        * @return {DOMDocument} a new DOM Document from the given XML string
        */
        DOMParser.prototype.parseFromString = function(sXml, contentType){
            var xmlhttp = new XMLHttpRequest();
            xmlhttp.open("GET", "data:text/xml;charset=utf-8," + encodeURIComponent(sXml), false);
            xmlhttp.send(null);
            return xmlhttp.responseXML;
        };
    }else if(Sarissa.getDomDocument && Sarissa.getDomDocument() && Sarissa.getDomDocument(null, "bar").xml){
        DOMParser = function() { };
        DOMParser.prototype.parseFromString = function(sXml, contentType){
            var doc = Sarissa.getDomDocument();
            doc.loadXML(sXml);
            return doc;
        };
    }
}

if((typeof(document.importNode) == "undefined") && Sarissa._SARISSA_IS_IE){
    try{
        /**
        * Implementation of importNode for the context window document in IE.
        * If <code>oNode</code> is a TextNode, <code>bChildren</code> is ignored.
        * @param {DOMNode} oNode the Node to import
        * @param {boolean} bChildren whether to include the children of oNode
        * @returns the imported node for further use
        */
        document.importNode = function(oNode, bChildren){
            var tmp;
            if (oNode.nodeName=='#text') {
                return document.createTextNode(oNode.data);
            }
            else {
                if(oNode.nodeName == "tbody" || oNode.nodeName == "tr"){
                    tmp = document.createElement("table");
                }
                else if(oNode.nodeName == "td"){
                    tmp = document.createElement("tr");
                }
                else if(oNode.nodeName == "option"){
                    tmp = document.createElement("select");
                }
                else{
                    tmp = document.createElement("div");
                }
                if(bChildren){
                    tmp.innerHTML = oNode.xml ? oNode.xml : oNode.outerHTML;
                }else{
                    tmp.innerHTML = oNode.xml ? oNode.cloneNode(false).xml : oNode.cloneNode(false).outerHTML;
                }
                return tmp.getElementsByTagName("*")[0];
            }
        };
    }catch(e){ }
}
if(!Sarissa.getParseErrorText){
    /**
     * <p>Returns a human readable description of the parsing error. Usefull
     * for debugging. Tip: append the returned error string in a &lt;pre&gt;
     * element if you want to render it.</p>
     * <p>Many thanks to Christian Stocker for the initial patch.</p>
     * @memberOf Sarissa
     * @param {DOMDocument} oDoc The target DOM document
     * @returns {String} The parsing error description of the target Document in
     *          human readable form (preformated text)
     */
    Sarissa.getParseErrorText = function (oDoc){
        var parseErrorText = Sarissa.PARSED_OK;
        if((!oDoc) || (!oDoc.documentElement)){
            parseErrorText = Sarissa.PARSED_EMPTY;
        } else if(oDoc.documentElement.tagName == "parsererror"){
            parseErrorText = oDoc.documentElement.firstChild.data;
            parseErrorText += "\n" +  oDoc.documentElement.firstChild.nextSibling.firstChild.data;
        } else if(oDoc.getElementsByTagName("parsererror").length > 0){
            var parsererror = oDoc.getElementsByTagName("parsererror")[0];
            parseErrorText = Sarissa.getText(parsererror, true)+"\n";
        } else if(oDoc.parseError && oDoc.parseError.errorCode != 0){
            parseErrorText = Sarissa.PARSED_UNKNOWN_ERROR;
        }
        return parseErrorText;
    };
}
/**
 * Get a string with the concatenated values of all string nodes under the given node
 * @param {DOMNode} oNode the given DOM node
 * @param {boolean} deep whether to recursively scan the children nodes of the given node for text as well. Default is <code>false</code>
 * @memberOf Sarissa 
 */
Sarissa.getText = function(oNode, deep){
    var s = "";
    var nodes = oNode.childNodes;
    for(var i=0; i < nodes.length; i++){
        var node = nodes[i];
        var nodeType = node.nodeType;
        if(nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE){
            s += node.data;
        } else if(deep === true && (nodeType == Node.ELEMENT_NODE || nodeType == Node.DOCUMENT_NODE || nodeType == Node.DOCUMENT_FRAGMENT_NODE)){
            s += Sarissa.getText(node, true);
        }
    }
    return s;
};
if(!window.XMLSerializer && Sarissa.getDomDocument && Sarissa.getDomDocument("","foo", null).xml){
    /**
     * Utility class to serialize DOM Node objects to XML strings
     * @constructor
     */
    XMLSerializer = function(){};
    /**
     * Serialize the given DOM Node to an XML string
     * @param {DOMNode} oNode the DOM Node to serialize
     */
    XMLSerializer.prototype.serializeToString = function(oNode) {
        return oNode.xml;
    };
}

/**
 * Strips tags from the given markup string. If the given string is 
 * <code>undefined</code>, <code>null</code> or empty, it is returned as is. 
 * @memberOf Sarissa
 * @param {String} s the string to strip the tags from
 */
Sarissa.stripTags = function (s) {
    return s?s.replace(/<[^>]+>/g,""):s;
};
/**
 * <p>Deletes all child nodes of the given node</p>
 * @memberOf Sarissa
 * @param {DOMNode} oNode the Node to empty
 */
Sarissa.clearChildNodes = function(oNode) {
    // need to check for firstChild due to opera 8 bug with hasChildNodes
    while(oNode.firstChild) {
        oNode.removeChild(oNode.firstChild);
    }
};
/**
 * <p> Copies the childNodes of nodeFrom to nodeTo</p>
 * <p> <b>Note:</b> The second object's original content is deleted before 
 * the copy operation, unless you supply a true third parameter</p>
 * @memberOf Sarissa
 * @param {DOMNode} nodeFrom the Node to copy the childNodes from
 * @param {DOMNode} nodeTo the Node to copy the childNodes to
 * @param {boolean} bPreserveExisting whether to preserve the original content of nodeTo, default is false
 */
Sarissa.copyChildNodes = function(nodeFrom, nodeTo, bPreserveExisting) {
    if(Sarissa._SARISSA_IS_SAFARI && nodeTo.nodeType == Node.DOCUMENT_NODE){ // SAFARI_OLD ??
    	nodeTo = nodeTo.documentElement; //Apparently there's a bug in safari where you can't appendChild to a document node
    }
    
    if((!nodeFrom) || (!nodeTo)){
        throw "Both source and destination nodes must be provided";
    }
    if(!bPreserveExisting){
        Sarissa.clearChildNodes(nodeTo);
    }
    var ownerDoc = nodeTo.nodeType == Node.DOCUMENT_NODE ? nodeTo : nodeTo.ownerDocument;
    var nodes = nodeFrom.childNodes;
    var i;
    if(typeof(ownerDoc.importNode) != "undefined")  {
        for(i=0;i < nodes.length;i++) {
            nodeTo.appendChild(ownerDoc.importNode(nodes[i], true));
        }
    } else {
        for(i=0;i < nodes.length;i++) {
            nodeTo.appendChild(nodes[i].cloneNode(true));
        }
    }
};

/**
 * <p> Moves the childNodes of nodeFrom to nodeTo</p>
 * <p> <b>Note:</b> The second object's original content is deleted before 
 * the move operation, unless you supply a true third parameter</p>
 * @memberOf Sarissa
 * @param {DOMNode} nodeFrom the Node to copy the childNodes from
 * @param {DOMNode} nodeTo the Node to copy the childNodes to
 * @param {boolean} bPreserveExisting whether to preserve the original content of nodeTo, default is
 */ 
Sarissa.moveChildNodes = function(nodeFrom, nodeTo, bPreserveExisting) {
    if((!nodeFrom) || (!nodeTo)){
        throw "Both source and destination nodes must be provided";
    }
    if(!bPreserveExisting){
        Sarissa.clearChildNodes(nodeTo);
    }
    var nodes = nodeFrom.childNodes;
    // if within the same doc, just move, else copy and delete
    if(nodeFrom.ownerDocument == nodeTo.ownerDocument){
        while(nodeFrom.firstChild){
            nodeTo.appendChild(nodeFrom.firstChild);
        }
    } else {
        var ownerDoc = nodeTo.nodeType == Node.DOCUMENT_NODE ? nodeTo : nodeTo.ownerDocument;
        var i;
        if(typeof(ownerDoc.importNode) != "undefined") {
           for(i=0;i < nodes.length;i++) {
               nodeTo.appendChild(ownerDoc.importNode(nodes[i], true));
           }
        }else{
           for(i=0;i < nodes.length;i++) {
               nodeTo.appendChild(nodes[i].cloneNode(true));
           }
        }
        Sarissa.clearChildNodes(nodeFrom);
    }
};

/** 
 * <p>Serialize any <strong>non</strong> DOM object to an XML string. All properties are serialized using the property name
 * as the XML element name. Array elements are rendered as <code>array-item</code> elements, 
 * using their index/key as the value of the <code>key</code> attribute.</p>
 * @memberOf Sarissa
 * @param {Object} anyObject the object to serialize
 * @param {String} objectName a name for that object, to be used as the root element name
 * @return {String} the XML serialization of the given object as a string
 */
Sarissa.xmlize = function(anyObject, objectName, indentSpace){
    indentSpace = indentSpace?indentSpace:'';
    var s = indentSpace  + '<' + objectName + '>';
    var isLeaf = false;
    if(!(anyObject instanceof Object) || anyObject instanceof Number || anyObject instanceof String || anyObject instanceof Boolean || anyObject instanceof Date){
        s += Sarissa.escape(""+anyObject);
        isLeaf = true;
    }else{
        s += "\n";
        var isArrayItem = anyObject instanceof Array;
        for(var name in anyObject){
            s += Sarissa.xmlize(anyObject[name], (isArrayItem?"array-item key=\""+name+"\"":name), indentSpace + "   ");
        }
        s += indentSpace;
    }
    return (s += (objectName.indexOf(' ')!=-1?"</array-item>\n":"</" + objectName + ">\n"));
};

/** 
 * Escape the given string chacters that correspond to the five predefined XML entities
 * @memberOf Sarissa
 * @param {String} sXml the string to escape
 */
Sarissa.escape = function(sXml){
    return sXml.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");
};

/** 
 * Unescape the given string. This turns the occurences of the predefined XML 
 * entities to become the characters they represent correspond to the five predefined XML entities
 * @memberOf Sarissa
 * @param  {String}sXml the string to unescape
 */
Sarissa.unescape = function(sXml){
    return sXml.replace(/&apos;/g,"'").replace(/&quot;/g,"\"").replace(/&gt;/g,">").replace(/&lt;/g,"<").replace(/&amp;/g,"&");
};

/** @private */
Sarissa.updateCursor = function(oTargetElement, sValue) {
    if(oTargetElement && oTargetElement.style && oTargetElement.style.cursor != undefined ){
        oTargetElement.style.cursor = sValue;
    }
};

/**
 * Asynchronously update an element with response of a GET request on the given URL.  Passing a configured XSLT 
 * processor will result in transforming and updating oNode before using it to update oTargetElement.
 * You can also pass a callback function to be executed when the update is finished. The function will be called as 
 * <code>functionName(oNode, oTargetElement);</code>
 * @memberOf Sarissa
 * @param {String} sFromUrl the URL to make the request to
 * @param {DOMElement} oTargetElement the element to update
 * @param {XSLTProcessor} xsltproc (optional) the transformer to use on the returned
 *                  content before updating the target element with it
 * @param {Function} callback (optional) a Function object to execute once the update is finished successfuly, called as <code>callback(sFromUrl, oTargetElement)</code>. 
 *        In case an exception is thrown during execution, the callback is called as called as <code>callback(sFromUrl, oTargetElement, oException)</code>
 * @param {boolean} skipCache (optional) whether to skip any cache
 */
Sarissa.updateContentFromURI = function(sFromUrl, oTargetElement, xsltproc, callback, skipCache) {
    try{
        Sarissa.updateCursor(oTargetElement, "wait");
        var xmlhttp = new XMLHttpRequest();
        xmlhttp.open("GET", sFromUrl, true);
        xmlhttp.onreadystatechange = function() {
            if (xmlhttp.readyState == 4) {
            	try{
            		var oDomDoc = xmlhttp.responseXML;
	            	if(oDomDoc && Sarissa.getParseErrorText(oDomDoc) == Sarissa.PARSED_OK){
		                Sarissa.updateContentFromNode(xmlhttp.responseXML, oTargetElement, xsltproc);
        				if(callback){
		                	callback(sFromUrl, oTargetElement);
		                }
	            	}
	            	else{
	            		throw Sarissa.getParseErrorText(oDomDoc);
	            	}
            	}
            	catch(e){
            		if(callback){
			        	callback(sFromUrl, oTargetElement, e);
			        }
			        else{
			        	throw e;
			        }
            	}
            }
        };
        if (skipCache) {
             var oldage = "Sat, 1 Jan 2000 00:00:00 GMT";
             xmlhttp.setRequestHeader("If-Modified-Since", oldage);
        }
        xmlhttp.send("");
    }
    catch(e){
        Sarissa.updateCursor(oTargetElement, "auto");
        if(callback){
        	callback(sFromUrl, oTargetElement, e);
        }
        else{
        	throw e;
        }
    }
};

/**
 * Update an element's content with the given DOM node. Passing a configured XSLT 
 * processor will result in transforming and updating oNode before using it to update oTargetElement.
 * You can also pass a callback function to be executed when the update is finished. The function will be called as 
 * <code>functionName(oNode, oTargetElement);</code>
 * @memberOf Sarissa
 * @param {DOMNode} oNode the URL to make the request to
 * @param {DOMElement} oTargetElement the element to update
 * @param {XSLTProcessor} xsltproc (optional) the transformer to use on the given 
 *                  DOM node before updating the target element with it
 */
Sarissa.updateContentFromNode = function(oNode, oTargetElement, xsltproc) {
    try {
        Sarissa.updateCursor(oTargetElement, "wait");
        Sarissa.clearChildNodes(oTargetElement);
        // check for parsing errors
        var ownerDoc = oNode.nodeType == Node.DOCUMENT_NODE?oNode:oNode.ownerDocument;
        if(ownerDoc.parseError && ownerDoc.parseError.errorCode != 0) {
            var pre = document.createElement("pre");
            pre.appendChild(document.createTextNode(Sarissa.getParseErrorText(ownerDoc)));
            oTargetElement.appendChild(pre);
        }
        else {
            // transform if appropriate
            if(xsltproc) {
                oNode = xsltproc.transformToDocument(oNode);
            }
            // be smart, maybe the user wants to display the source instead
            if(oTargetElement.tagName.toLowerCase() == "textarea" || oTargetElement.tagName.toLowerCase() == "input") {
                oTargetElement.value = new XMLSerializer().serializeToString(oNode);
            }
            else {
                // ok that was not smart; it was paranoid. Keep up the good work by trying to use DOM instead of innerHTML
                try{
                    oTargetElement.appendChild(oTargetElement.ownerDocument.importNode(oNode, true));
                }
                catch(e){
                    oTargetElement.innerHTML = new XMLSerializer().serializeToString(oNode);
                }
            }
        }
    }
    catch(e) {
    	throw e;
    }
    finally{
        Sarissa.updateCursor(oTargetElement, "auto");
    }
};


/**
 * Creates an HTTP URL query string from the given HTML form data
 * @memberOf Sarissa
 * @param {HTMLFormElement} oForm the form to construct the query string from
 */
Sarissa.formToQueryString = function(oForm){
    var qs = "";
    for(var i = 0;i < oForm.elements.length;i++) {
        var oField = oForm.elements[i];
        var sFieldName = oField.getAttribute("name") ? oField.getAttribute("name") : oField.getAttribute("id"); 
        // ensure we got a proper name/id and that the field is not disabled
        if(sFieldName && 
            ((!oField.disabled) || oField.type == "hidden")) {
            switch(oField.type) {
                case "hidden":
                case "text":
                case "textarea":
                case "password":
                    qs += sFieldName + "=" + encodeURIComponent(oField.value) + "&";
                    break;
                case "select-one":
                    qs += sFieldName + "=" + encodeURIComponent(oField.options[oField.selectedIndex].value) + "&";
                    break;
                case "select-multiple":
                    for (var j = 0; j < oField.length; j++) {
                        var optElem = oField.options[j];
                        if (optElem.selected === true) {
                            qs += sFieldName + "[]" + "=" + encodeURIComponent(optElem.value) + "&";
                        }
                     }
                     break;
                case "checkbox":
                case "radio":
                    if(oField.checked) {
                        qs += sFieldName + "=" + encodeURIComponent(oField.value) + "&";
                    }
                    break;
            }
        }
    }
    // return after removing last '&'
    return qs.substr(0, qs.length - 1); 
};


/**
 * Asynchronously update an element with response of an XMLHttpRequest-based emulation of a form submission. <p>The form <code>action</code> and 
 * <code>method</code> attributess will be followed. Passing a configured XSLT processor will result in 
 * transforming and updating the server response before using it to update the target element.
 * You can also pass a callback function to be executed when the update is finished. The function will be called as 
 * <code>functionName(oNode, oTargetElement);</code></p>
 * <p>Here is an example of using this in a form element:</p>
 * <pre name="code" class="xml">
 * &lt;div id="targetId"&gt; this content will be updated&lt;/div&gt;
 * &lt;form action="/my/form/handler" method="post" 
 *     onbeforesubmit="return Sarissa.updateContentFromForm(this, document.getElementById('targetId'));"&gt;<pre>
 * <p>If JavaScript is supported, the form will not be submitted. Instead, Sarissa will
 * scan the form and make an appropriate AJAX request, also adding a parameter 
 * to signal to the server that this is an AJAX call. The parameter is 
 * constructed as <code>Sarissa.REMOTE_CALL_FLAG = "=true"</code> so you can change the name in your webpage
 * simply by assigning another value to Sarissa.REMOTE_CALL_FLAG. If JavaScript is not supported
 * the form will be submitted normally.
 * @memberOf Sarissa
 * @param {HTMLFormElement} oForm the form submition to emulate
 * @param {DOMElement} oTargetElement the element to update
 * @param {XSLTProcessor} xsltproc (optional) the transformer to use on the returned
 *                  content before updating the target element with it
 * @param {Function} callback (optional) a Function object to execute once the update is finished successfuly, called as <code>callback(oNode, oTargetElement)</code>. 
 *        In case an exception occurs during excecution and a callback function was provided, the exception is cought and the callback is called as 
 *        <code>callback(oForm, oTargetElement, exception)</code>
 */
Sarissa.updateContentFromForm = function(oForm, oTargetElement, xsltproc, callback) {
    try{
    	Sarissa.updateCursor(oTargetElement, "wait");
        // build parameters from form fields
        var params = Sarissa.formToQueryString(oForm) + "&" + Sarissa.REMOTE_CALL_FLAG + "=true";
        var xmlhttp = new XMLHttpRequest();
        var bUseGet = oForm.getAttribute("method") && oForm.getAttribute("method").toLowerCase() == "get"; 
        if(bUseGet) {
            xmlhttp.open("GET", oForm.getAttribute("action")+"?"+params, true);
        }
        else{
            xmlhttp.open('POST', oForm.getAttribute("action"), true);
            xmlhttp.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
            xmlhttp.setRequestHeader("Content-length", params.length);
            xmlhttp.setRequestHeader("Connection", "close");
        }
        xmlhttp.onreadystatechange = function() {
        	try{
	            if (xmlhttp.readyState == 4) {
	            	var oDomDoc = xmlhttp.responseXML;
	            	if(oDomDoc && Sarissa.getParseErrorText(oDomDoc) == Sarissa.PARSED_OK){
		                Sarissa.updateContentFromNode(xmlhttp.responseXML, oTargetElement, xsltproc);
        				if(callback){
		                	callback(oForm, oTargetElement);
		                }
	            	}
	            	else{
	            		throw Sarissa.getParseErrorText(oDomDoc);
	            	}
	            }
        	}
        	catch(e){
        		if(callback){
        			callback(oForm, oTargetElement, e);
        		}
        		else{
        			throw e;
        		}
        	}
        };
        xmlhttp.send(bUseGet?"":params);
    }
    catch(e){
        Sarissa.updateCursor(oTargetElement, "auto");
        if(callback){
        	callback(oForm, oTargetElement, e);
        }
        else{
        	throw e;
        }
    }
    return false;
};
Sarissa.FUNCTION_NAME_REGEXP = new RegExp("");//new RegExp("function\\s+(\\S+)\\s*\\((.|\\n)*?\\)\\s*{");
/**
 * Get the name of a function created like:
 * <pre>function functionName(){}</pre>
 * If a name is not found and the bForce parameter is true,
 * attach the function to the window object with a new name and
 * return that
 * @param {Function} oFunc the function object
 * @param {boolean} bForce whether to force a name under the window context if none is found
 */
Sarissa.getFunctionName = function(oFunc, bForce){
	//alert("Sarissa.getFunctionName oFunc: "+oFunc);
	var name;
	if(!name){
		if(bForce){
			// attach to window object under a new name
			name = "SarissaAnonymous" + Sarissa._getUniqueSuffix();
			window[name] = oFunc;
		}
		else{
			name = null;
		}
	}
	
	//alert("Sarissa.getFunctionName returns: "+name);
	if(name){
		window[name] = oFunc;
	}
	return name;
};

/**
 *
 */
Sarissa.setRemoteJsonCallback = function(url, callback, callbackParam) {
	if(!callbackParam){
		callbackParam = "callback";
	}
	var callbackFunctionName = Sarissa.getFunctionName(callback, true);
	var id = "sarissa_json_script_id_" + Sarissa._getUniqueSuffix(); 
	var oHead = document.getElementsByTagName("head")[0];
	var scriptTag = document.createElement('script');
	scriptTag.type = 'text/javascript';
	scriptTag.id = id;
	scriptTag.onload = function(){
		// cleanUp
		// document.removeChild(scriptTag);
	};
	if(url.indexOf("?") != -1){
		url += ("&" + callbackParam + "=" + callbackFunctionName);
	}
	else{
		url += ("?" + callbackParam + "=" + callbackFunctionName);
	}
	scriptTag.src = url;
  	oHead.appendChild(scriptTag);
  	return id;
};

//   EOF
