/*
 * $ URIs @VERSION
 * 
 * Copyright (c) 2008,2009 Jeni Tennison
 * Licensed under the MIT (MIT-LICENSE.txt)
 *
 */
/**
 * @fileOverview $ URIs
 * @author <a href="mailto:jeni@jenitennison.com">Jeni Tennison</a>
 * @copyright (c) 2008,2009 Jeni Tennison
 * @license MIT license (MIT-LICENSE.txt)
 * @version 1.0
 */
/**
 * @class
 * @name jQuery
 * @exports $ as jQuery
 * @description rdfQuery is a <a href="http://jquery.com/">jQuery</a> plugin. The only fields and methods listed here are those that come as part of the rdfQuery library.
 */
(function ($) {

  var
    mem = {},
    uriRegex = /^(([a-z][\-a-z0-9+\.]*):)?(\/\/([^\/?#]+))?([^?#]*)?(\?([^#]*))?(#(.*))?$/i,
    docURI,

    parseURI = function (u) {
      var m = u.match(uriRegex);
      if (m === null) {
        throw "Malformed URI: " + u;
      }
      return {
        scheme: m[1] ? m[2].toLowerCase() : undefined,
        authority: m[3] ? m[4] : undefined,
        path: m[5] || '',
        query: m[6] ? m[7] : undefined,
        fragment: m[8] ? m[9] : undefined
      };
    },

    removeDotSegments = function (u) {
      var r = '', m = [];
      if (/\./.test(u)) {
        while (u !== undefined && u !== '') {
          if (u === '.' || u === '..') {
            u = '';
          } else if (/^\.\.\//.test(u)) { // starts with ../
            u = u.substring(3);
          } else if (/^\.\//.test(u)) { // starts with ./
            u = u.substring(2);
          } else if (/^\/\.(\/|$)/.test(u)) { // starts with /./ or consists of /.
            u = '/' + u.substring(3);
          } else if (/^\/\.\.(\/|$)/.test(u)) { // starts with /../ or consists of /..
            u = '/' + u.substring(4);
            r = r.replace(/\/?[^\/]+$/, '');
          } else {
            m = u.match(/^(\/?[^\/]*)(\/.*)?$/);
            u = m[2];
            r = r + m[1];
          }
        }
        return r;
      } else {
        return u;
      }
    },

    merge = function (b, r) {
      if (b.authority !== '' && (b.path === undefined || b.path === '')) {
        return '/' + r;
      } else {
        return b.path.replace(/[^\/]+$/, '') + r;
      }
    };

  /**
   * Creates a new jQuery.uri object. This should be invoked as a method rather than constructed using new.
   * @class Represents a URI
   * @param {String} [relative='']
   * @param {String|jQuery.uri} [base] Defaults to the base URI of the page
   * @returns {jQuery.uri} The new jQuery.uri object.
   * @example uri = jQuery.uri('/my/file.html');
   */
  $.uri = function (relative, base) {
    var uri;
    relative = relative || '';
    if (mem[relative]) {
      return mem[relative];
    }
    base = base || $.uri.base();
    if (typeof base === 'string') {
      base = $.uri.absolute(base);
    }
    uri = new $.uri.fn.init(relative, base);
    if (mem[uri]) {
      return mem[uri];
    } else {
      mem[uri] = uri;
      return uri;
    }
  };

  $.uri.fn = $.uri.prototype = {
    /**
     * The scheme used in the URI
     * @type String
     */
    scheme: undefined,
    /**
     * The authority used in the URI
     * @type String
     */
    authority: undefined,
    /**
     * The path used in the URI
     * @type String
     */
    path: undefined,
    /**
     * The query part of the URI
     * @type String
     */
    query: undefined,
    /**
     * The fragment part of the URI
     * @type String
     */
    fragment: undefined,
    
    init: function (relative, base) {
      var r = {};
      base = base || {};
      $.extend(this, parseURI(relative));
      if (this.scheme === undefined) {
        this.scheme = base.scheme;
        if (this.authority !== undefined) {
          this.path = removeDotSegments(this.path);
        } else {
          this.authority = base.authority;
          if (this.path === '') {
            this.path = base.path;
            if (this.query === undefined) {
              this.query = base.query;
            }
          } else {
            if (!/^\//.test(this.path)) {
              this.path = merge(base, this.path);
            }
            this.path = removeDotSegments(this.path);
          }
        }
      }
      if (this.scheme === undefined) {
        throw "Malformed URI: URI is not an absolute URI and no base supplied: " + relative;
      }
      return this;
    },
  
    /**
     * Resolves a relative URI relative to this URI
     * @param {String} relative
     * @returns jQuery.uri
     */
    resolve: function (relative) {
      return $.uri(relative, this);
    },
    
    /**
     * Creates a relative URI giving the path from this URI to the absolute URI passed as a parameter
     * @param {String|jQuery.uri} absolute
     * @returns String
     */
    relative: function (absolute) {
      var aPath, bPath, i = 0, j, resultPath = [], result = '';
      if (typeof absolute === 'string') {
        absolute = $.uri(absolute, {});
      }
      if (absolute.scheme !== this.scheme || 
          absolute.authority !== this.authority) {
        return absolute.toString();
      }
      if (absolute.path !== this.path) {
        aPath = absolute.path.split('/');
        bPath = this.path.split('/');
        if (aPath[1] !== bPath[1]) {
          result = absolute.path;
        } else {
          while (aPath[i] === bPath[i]) {
            i += 1;
          }
          j = i;
          for (; i < bPath.length - 1; i += 1) {
            resultPath.push('..');
          }
          for (; j < aPath.length; j += 1) {
            resultPath.push(aPath[j]);
          }
          result = resultPath.join('/');
        }
        result = absolute.query === undefined ? result : result + '?' + absolute.query;
        result = absolute.fragment === undefined ? result : result + '#' + absolute.fragment;
        return result;
      }
      if (absolute.query !== undefined && absolute.query !== this.query) {
        return '?' + absolute.query + (absolute.fragment === undefined ? '' : '#' + absolute.fragment);
      }
      if (absolute.fragment !== undefined && absolute.fragment !== this.fragment) {
        return '#' + absolute.fragment;
      }
      return '';
    },
  
    /**
     * Returns the URI as an absolute string
     * @returns String
     */
    toString: function () {
      var result = '';
      if (this._string) {
        return this._string;
      } else {
        result = this.scheme === undefined ? result : (result + this.scheme + ':');
        result = this.authority === undefined ? result : (result + '//' + this.authority);
        result = result + this.path;
        result = this.query === undefined ? result : (result + '?' + this.query);
        result = this.fragment === undefined ? result : (result + '#' + this.fragment);
        this._string = result;
        return result;
      }
    }
  
  };

  $.uri.fn.init.prototype = $.uri.fn;

  /**
   * Creates a {@link jQuery.uri} from a known-to-be-absolute URI
   * @param {String}
   * @returns {jQuery.uri}
   */
  $.uri.absolute = function (uri) {
    return $.uri(uri, {});
  };

  /**
   * Creates a {@link jQuery.uri} from a relative URI and an optional base URI
   * @returns {jQuery.uri}
   * @see jQuery.uri
   */
  $.uri.resolve = function (relative, base) {
    return $.uri(relative, base);
  };
  
  /**
   * Creates a string giving the relative path from a base URI to an absolute URI
   * @param {String} absolute
   * @param {String} base
   * @returns {String}
   */
  $.uri.relative = function (absolute, base) {
    return $.uri(base, {}).relative(absolute);
  };
  
  /**
   * Returns the base URI of the page
   * @returns {jQuery.uri}
   */
  $.uri.base = function () {
    return $(document).base();
  };
  
  /**
   * Returns the base URI in scope for the first selected element
   * @methodOf jQuery#
   * @name jQuery#base
   * @returns {jQuery.uri}
   * @example baseURI = $('img').base();
   */
  $.fn.base = function () {
    var base = $(this).parents().andSelf().find('base').attr('href'),
      doc = $(this)[0].ownerDocument || document,
      docURI = $.uri.absolute(doc.location === null ? document.location.href : doc.location.href);
    return base === undefined ? docURI : $.uri(base, docURI);
  };

})(jQuery);
/*
 * jQuery CURIE @VERSION
 * 
 * Copyright (c) 2008,2009 Jeni Tennison
 * Licensed under the MIT (MIT-LICENSE.txt)
 *
 * Depends:
 *  jquery.uri.js
 */
/**
 * @fileOverview XML Namespace processing
 * @author <a href="mailto:jeni@jenitennison.com">Jeni Tennison</a>
 * @copyright (c) 2008,2009 Jeni Tennison
 * @license MIT license (MIT-LICENSE.txt)
 * @version 1.0
 * @requires jquery.uri.js
 */

/*global jQuery */
(function ($) {

  var 
    xmlnsRegex = /\sxmlns(?::([^ =]+))?\s*=\s*(?:"([^"]*)"|'([^']*)')/g;

/**
 * Returns the namespaces declared in the scope of the first selected element, or
 * adds a namespace declaration to all selected elements. Pass in no parameters
 * to return all namespaces bindings on the first selected element. If only 
 * the prefix parameter is specified, this method will return the namespace
 * URI that is bound to the specified prefix on the first element in the selection
 * If the prefix and uri parameters are both specified, this method will
 * add the binding of the specified prefix and namespace URI to all elements
 * in the selection.
 * @methodOf jQuery#
 * @name jQuery#xmlns
 * @param {String} [prefix] Restricts the namespaces returned to only the namespace with the specified namespace prefix.
 * @param {String|jQuery.uri} [uri] Adds a namespace declaration to the selected elements that maps the specified prefix to the specified namespace.
 * @param {Object} [inherited] A map of inherited namespace bindings.
 * @returns {Object|jQuery.uri|jQuery}
 * @example 
 * // Retrieve all of the namespace bindings on the HTML document element
 * var nsMap = $('html').xmlns();
 * @example
 * // Retrieve the namespace URI mapped to the 'dc' prefix on the HTML document element
 * var dcNamespace = $('html').xmlns('dc');
 * @example
 * // Create a namespace declaration that binds the 'dc' prefix to the URI 'http://purl.org/dc/elements/1.1/'
 * $('html').xmlns('dc', 'http://purl.org/dc/elements/1.1/');
 */
  $.fn.xmlns = function (prefix, uri, inherited) {
    var 
      elem = this.eq(0),
      ns = elem.data('xmlns'),
      e = elem[0], a, p, i,
      decl = prefix ? 'xmlns:' + prefix : 'xmlns',
      value,
      tag, found = false;
    if (uri === undefined) {
      if (prefix === undefined) { // get the in-scope declarations on the first element
        if (ns === undefined) {
          ns = {};
          if (e.attributes && e.attributes.getNamedItemNS) {
            for (i = 0; i < e.attributes.length; i += 1) {
              a = e.attributes[i];
              if (/^xmlns(:(.+))?$/.test(a.nodeName)) {
                prefix = /^xmlns(:(.+))?$/.exec(a.nodeName)[2] || '';
                value = a.nodeValue;
                if (prefix === '' || value !== '') {
                  ns[prefix] = $.uri(a.nodeValue);
                  found = true;
                }
              }
            }
          } else {
            tag = /<[^>]+>/.exec(e.outerHTML);
            a = xmlnsRegex.exec(tag);
            while (a !== null) {
              prefix = a[1] || '';
              value = a[2] || a[3];
              if (prefix === '' || value !== '') {
                ns[prefix] = $.uri(a[2] || a[3]);
                found = true;
              }
              a = xmlnsRegex.exec(tag);
            }
            xmlnsRegex.lastIndex = 0;
          }
          inherited = inherited || (e.parentNode.nodeType === 1 ? elem.parent().xmlns() : {});
          ns = found ? $.extend({}, inherited, ns) : inherited;
          elem.data('xmlns', ns);
        }
        return ns;
      } else if (typeof prefix === 'object') { // set the prefix mappings defined in the object
        for (p in prefix) {
          if (typeof prefix[p] === 'string') {
            this.xmlns(p, prefix[p]);
          }
        }
        this.find('*').andSelf().removeData('xmlns');
        return this;
      } else { // get the in-scope declaration associated with this prefix on the first element
        if (ns === undefined) {
          ns = elem.xmlns();
        }
        return ns[prefix];
      }
    } else { // set
      this.find('*').andSelf().removeData('xmlns');
      return this.attr(decl, uri);
    }
  };

/**
 * Removes one or more XML namespace bindings from the selected elements.
 * @methodOf jQuery#
 * @name jQuery#removeXmlns
 * @param {String|Object|String[]} prefix The prefix(es) of the XML namespace bindings that are to be removed from the selected elements.
 * @returns {jQuery} The original jQuery object.
 * @example
 * // Remove the foaf namespace declaration from the body element:
 * $('body').removeXmlns('foaf');
 * @example
 * // Remove the foo and bar namespace declarations from all h2 elements
 * $('h2').removeXmlns(['foo', 'bar']);
 * @example
 * // Remove the foo and bar namespace declarations from all h2 elements
 * var namespaces = { foo : 'http://www.example.org/foo', bar : 'http://www.example.org/bar' };
 * $('h2').removeXmlns(namespaces);
 */
  $.fn.removeXmlns = function (prefix) {
    var decl, p, i;
    if (typeof prefix === 'object') {
      if (prefix.length === undefined) { // assume an object representing namespaces
        for (p in prefix) {
          if (typeof prefix[p] === 'string') {
            this.removeXmlns(p);
          }
        }
      } else { // it's an array
        for (i = 0; i < prefix.length; i += 1) {
          this.removeXmlns(prefix[i]);
        }
      }
    } else {
      decl = prefix ? 'xmlns:' + prefix : 'xmlns';
      this.removeAttr(decl);
    }
    this.find('*').andSelf().removeData('xmlns');
    return this;
  };

  $.fn.qname = function (name) {
    var m, prefix, namespace;
    if (name === undefined) {
      if (this[0].outerHTML === undefined) {
        name = this[0].nodeName.toLowerCase();
      } else {
        name = /<([^ >]+)/.exec(this[0].outerHTML)[1].toLowerCase();
      }
    }
    if (name === '?xml:namespace') {
      // there's a prefix on the name, but we can't get at it
      throw "XMLinHTML: Unable to get the prefix to resolve the name of this element";
    }
    m = /^(([^:]+):)?([^:]+)$/.exec(name);
    prefix = m[2] || '';
    namespace = this.xmlns(prefix);
    if (namespace === undefined && prefix !== '') {
      throw "MalformedQName: The prefix " + prefix + " is not declared";
    }
    return {
      namespace: namespace,
      localPart: m[3],
      prefix: prefix,
      name: name
    };
  };

})(jQuery);
/*
 * jQuery CURIE @VERSION
 *
 * Copyright (c) 2008,2009 Jeni Tennison
 * Licensed under the MIT (MIT-LICENSE.txt)
 *
 * Depends:
 *  jquery.uri.js
 */
/**
 * @fileOverview XML Schema datatype handling
 * @author <a href="mailto:jeni@jenitennison.com">Jeni Tennison</a>
 * @copyright (c) 2008,2009 Jeni Tennison
 * @license MIT license (MIT-LICENSE.txt)
 * @version 1.0
 * @requires jquery.uri.js
 */

(function ($) {

  var strip = function (value) {
    return value.replace(/[ \t\n\r]+/, ' ').replace(/^ +/, '').replace(/ +$/, '');
  };

  /**
   * Creates a new jQuery.typedValue object. This should be invoked as a method
   * rather than constructed using new.
   * @class Represents a value with an XML Schema datatype
   * @param {String} value The string representation of the value
   * @param {String} datatype The XML Schema datatype URI
   * @returns {jQuery.typedValue}
   * @example intValue = jQuery.typedValue('42', 'http://www.w3.org/2001/XMLSchema#integer');
   */
  $.typedValue = function (value, datatype) {
    return $.typedValue.fn.init(value, datatype);
  };

  $.typedValue.fn = $.typedValue.prototype = {
    /**
     * The string representation of the value
     * @memberOf jQuery.typedValue#
     */
    representation: undefined,
    /**
     * The value as an object. The type of the object will
     * depend on the XML Schema datatype URI specified
     * in the constructor. The following table lists the mappings
     * currently supported:
     * <table>
     *   <tr>
     *   <th>XML Schema Datatype</th>
     *   <th>Value type</th>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#string</td>
     *     <td>string</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#boolean</td>
     *     <td>bool</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#decimal</td>
     *     <td>string</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#integer</td>
     *     <td>int</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#int</td>
     *     <td>int</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#float</td>
     *     <td>float</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#double</td>
     *     <td>float</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#dateTime</td>
     *     <td>string</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#date</td>
     *     <td>string</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#gMonthDay</td>
     *     <td>string</td>
     *   </tr>
     *   <tr>
     *     <td>http://www.w3.org/2001/XMLSchema#anyURI</td>
     *     <td>string</td>
     *   </tr>
     * </table>
     * @memberOf jQuery.typedValue#
     */
    value: undefined,
    /**
     * The XML Schema datatype URI for the value's datatype
     * @memberOf jQuery.typedValue#
     */
    datatype: undefined,

    init: function (value, datatype) {
      var d;
      if ($.typedValue.valid(value, datatype)) {
        d = $.typedValue.types[datatype];
        this.representation = value;
        this.datatype = datatype;
        this.value = d.value(d.strip ? strip(value) : value);
        return this;
      } else {
        throw {
          name: 'InvalidValue',
          message: value + ' is not a valid ' + datatype + ' value'
        };
      }
    }
  };

  $.typedValue.fn.init.prototype = $.typedValue.fn;

  /**
   * An object that holds the datatypes supported by the script. The properties of this object are the URIs of the datatypes, and each datatype has four properties:
   * <dl>
   *   <dt>strip</dt>
   *   <dd>A boolean value that indicates whether whitespace should be stripped from the value prior to testing against the regular expression or passing to the value function.</dd>
   *   <dt>regex</dt>
   *   <dd>A regular expression that valid values of the type must match.</dd>
   *   <dt>validate</dt>
   *   <dd>Optional. A function that performs further testing on the value.</dd>
   *   <dt>value</dt>
   *   <dd>A function that returns a Javascript object equivalent for the value.</dd>
   * </dl>
   * You can add to this object as necessary for your own datatypes, and {@link jQuery.typedValue} and {@link jQuery.typedValue.valid} will work with them.
   * @see jQuery.typedValue
   * @see jQuery.typedValue.valid
   */
  $.typedValue.types = {};

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#string'] = {
    regex: /^.*$/,
    strip: false,
    /** @ignore */
    value: function (v) {
      return v;
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#boolean'] = {
    regex: /^(?:true|false|1|0)$/,
    strip: true,
    /** @ignore */
    value: function (v) {
      return v === 'true' || v === '1';
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#decimal'] = {
    regex: /^[\-\+]?(?:[0-9]+\.[0-9]*|\.[0-9]+|[0-9]+)$/,
    strip: true,
    /** @ignore */
    value: function (v) {
      return v;
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#integer'] = {
    regex: /^[\-\+]?[0-9]+$/,
    strip: true,
    /** @ignore */
    value: function (v) {
      return parseInt(v, 10);
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#int'] = {
    regex: /^[\-\+]?[0-9]+$/,
    strip: true,
    /** @ignore */
    value: function (v) {
      return parseInt(v, 10);
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#float'] = {
    regex: /^(?:[\-\+]?(?:[0-9]+\.[0-9]*|\.[0-9]+|[0-9]+)(?:[eE][\-\+]?[0-9]+)?|[\-\+]?INF|NaN)$/,
    strip: true,
    /** @ignore */
    value: function (v) {
      if (v === '-INF') {
        return -1 / 0;
      } else if (v === 'INF' || v === '+INF') {
        return 1 / 0;
      } else {
        return parseFloat(v);
      }
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#double'] = {
    regex: $.typedValue.types['http://www.w3.org/2001/XMLSchema#float'].regex,
    strip: true,
    value: $.typedValue.types['http://www.w3.org/2001/XMLSchema#float'].value
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#duration'] = {
    regex: /^([\-\+])?P(?:([0-9]+)Y)?(?:([0-9]+)M)?(?:([0-9]+)D)?(?:T(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+(?:\.[0-9]+))?S)?)$/,
    /** @ignore */
    validate: function (v) {
      var m = this.regex.exec(v);
      return m[2] || m[3] || m[4] || m[5] || m[6] || m[7];
    },
    strip: true,
    /** @ignore */
    value: function (v) {
      return v;
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#dateTime'] = {
    regex: /^(-?[0-9]{4,})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):(([0-9]{2})(\.([0-9]+))?)((?:[\-\+]([0-9]{2}):([0-9]{2}))|Z)?$/,
    /** @ignore */
    validate: function (v) {
      var
        m = this.regex.exec(v),
        year = parseInt(m[1], 10),
        tz = m[10] === undefined || m[10] === 'Z' ? '+0000' : m[10].replace(/:/, ''),
        date;
      if (year === 0 ||
          parseInt(tz, 10) < -1400 || parseInt(tz, 10) > 1400) {
        return false;
      }
      try {
        year = year < 100 ? Math.abs(year) + 1000 : year;
        month = parseInt(m[2], 10);
        day = parseInt(m[3], 10);
        if (day > 31) {
          return false;
        } else if (day > 30 && !(month === 1 || month === 3 || month === 5 || month === 7 || month === 8 || month === 10 || month === 12)) {
          return false;
        } else if (month === 2) {
          if (day > 29) {
            return false;
          } else if (day === 29 && (year % 4 !== 0 || (year % 100 === 0 && year % 400 !== 0))) {
            return false;
          }
        }
        date = '' + year + '/' + m[2] + '/' + m[3] + ' ' + m[4] + ':' + m[5] + ':' + m[7] + ' ' + tz;
        date = new Date(date);
        return true;
      } catch (e) {
        return false;
      }
    },
    strip: true,
    /** @ignore */
    value: function (v) {
      return v;
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#date'] = {
    regex: /^(-?[0-9]{4,})-([0-9]{2})-([0-9]{2})((?:[\-\+]([0-9]{2}):([0-9]{2}))|Z)?$/,
    /** @ignore */
    validate: function (v) {
      var
        m = this.regex.exec(v),
        year = parseInt(m[1], 10),
        month = parseInt(m[2], 10),
        day = parseInt(m[3], 10),
        tz = m[10] === undefined || m[10] === 'Z' ? '+0000' : m[10].replace(/:/, '');
      if (year === 0 ||
          month > 12 ||
          day > 31 ||
          parseInt(tz, 10) < -1400 || parseInt(tz, 10) > 1400) {
        return false;
      } else {
        return true;
      }
    },
    strip: true,
    /** @ignore */
    value: function (v) {
      return v;
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#gMonthDay'] = {
    regex: /^--([0-9]{2})-([0-9]{2})((?:[\-\+]([0-9]{2}):([0-9]{2}))|Z)?$/,
    /** @ignore */
    validate: function (v) {
      var
        m = this.regex.exec(v),
        month = parseInt(m[1], 10),
        day = parseInt(m[2], 10),
        tz = m[3] === undefined || m[3] === 'Z' ? '+0000' : m[3].replace(/:/, '');
      if (month > 12 ||
          day > 31 ||
          parseInt(tz, 10) < -1400 || parseInt(tz, 10) > 1400) {
        return false;
      } else if (month === 2 && day > 29) {
        return false;
      } else if ((month === 4 || month === 6 || month === 9 || month === 11) && day > 30) {
        return false;
      } else {
        return true;
      }
    },
    strip: true,
    /** @ignore */
    value: function (v) {
      return v;
    }
  };

  $.typedValue.types['http://www.w3.org/2001/XMLSchema#anyURI'] = {
    regex: /^.*$/,
    strip: true,
    /** @ignore */
    value: function (v, options) {
      var opts = $.extend({}, $.typedValue.defaults, options);
      return $.uri.resolve(v, opts.base);
    }
  };

  $.typedValue.defaults = {
    base: $.uri.base(),
    namespaces: {}
  };

  /**
   * Checks whether a value is valid according to a given datatype. The datatype must be held in the {@link jQuery.typedValue.types} object.
   * @param {String} value The value to validate.
   * @param {String} datatype The URI for the datatype against which the value will be validated.
   * @returns {boolean} True if the value is valid.
   * @throws {String} Errors if the datatype has not been specified in the {@link jQuery.typedValue.types} object.
   * @example validDate = $.typedValue.valid(date, 'http://www.w3.org/2001/XMLSchema#date');
   */
  $.typedValue.valid = function (value, datatype) {
    var d = $.typedValue.types[datatype];
    if (d === undefined) {
      throw "InvalidDatatype: The datatype " + datatype + " can't be recognised";
    } else {
      value = d.strip ? strip(value) : value;
      if (d.regex.test(value)) {
        return d.validate === undefined ? true : d.validate(value);
      } else {
        return false;
      }
    }
  };

})(jQuery);
/*
 * jQuery CURIE @VERSION
 *
 * Copyright (c) 2008,2009 Jeni Tennison
 * Licensed under the MIT (MIT-LICENSE.txt)
 *
 * Depends:
 *  jquery.uri.js
 *  jquery.xmlns.js
 */

/**
 * @fileOverview jQuery CURIE handling
 * @author <a href="mailto:jeni@jenitennison.com">Jeni Tennison</a>
 * @copyright (c) 2008,2009 Jeni Tennison
 * @license MIT license (MIT-LICENSE.txt)
 * @version 1.0
 * @requires jquery.uri.js
 * @requires jquery.xmlns.js
 */
(function ($) {

   /**
    * Creates a {@link jQuery.uri} object by parsing a CURIE.
    * @methodOf jQuery
    * @param {String} curie The CURIE to be parsed
    * @param {String} uri The URI string to be converted to a CURIE.
    * @param {Object} [options] CURIE parsing options
    * @param {string} [options.reservedNamespace='http://www.w3.org/1999/xhtml/vocab#'] The namespace to apply to a CURIE that has no prefix and either starts with a colon or is in the list of reserved local names
    * @param {string} [options.defaultNamespace]  The namespace to apply to a CURIE with no prefix which is not mapped to the reserved namespace by the rules given above.
    * @param {Object} [options.namespaces] A map of namespace bindings used to map CURIE prefixes to URIs.
    * @param {string[]} [options.reserved=['alternate', 'appendix', 'bookmark', 'cite', 'chapter', 'contents', 'copyright', 'first', 'glossary', 'help', 'icon', 'index', 'last', 'license', 'meta', 'next', 'p3pv1', 'prev', 'role', 'section', 'stylesheet', 'subsection', 'start', 'top', 'up']] A list of local names that will always be mapped to the URI specified by reservedNamespace.
    * @param {string} [options.charcase='lower'] Specifies whether the curie's case is altered before it's interpreted. Acceptable values are:
    * <dl>
    * <dt>lower</dt><dd>Force the CURIE string to lower case.</dd>
    * <dt>upper</dt><dd>Force the CURIE string to upper case.</dd>
    * <dt>preserve</dt><dd>Preserve the original case of the CURIE. Note that this might not be possible if the CURIE has been taken from an HTML attribute value because of the case conversions performed automatically by browsers. For this reason, it's a good idea to avoid mixed-case CURIEs within RDFa.</dd>
    * </dl>
    * @returns {jQuery.uri} A new {@link jQuery.uri} object representing the full absolute URI specified by the CURIE.
    */
  $.curie = function (curie, options) {
    var
      opts = $.extend({}, $.curie.defaults, options || {}),
      m = /^(([^:]*):)?(.+)$/.exec(curie),
      prefix = m[2],
      local = m[3],
      ns = opts.namespaces[prefix];
    if (/^:.+/.test(curie)) { // This is the case of a CURIE like ":test"
      if (opts.reservedNamespace === undefined || opts.reservedNamespace === null) {
        throw "Malformed CURIE: No prefix and no default namespace for unprefixed CURIE " + curie;
      } else {
        ns = opts.reservedNamespace;
      }
    } else if (prefix) {
      if (ns === undefined) {
        throw "Malformed CURIE: No namespace binding for " + prefix + " in CURIE " + curie;
      }
    } else {
      if (opts.charcase === 'lower') {
        curie = curie.toLowerCase();
      } else if (opts.charcase === 'upper') {
        curie = curie.toUpperCase();
      }
      if (opts.reserved.length && $.inArray(curie, opts.reserved) >= 0) {
        ns = opts.reservedNamespace;
        local = curie;
      } else if (opts.defaultNamespace === undefined || opts.defaultNamespace === null) {
        // the default namespace is provided by the application; it's not clear whether
        // the default XML namespace should be used if there's a colon but no prefix
        throw "Malformed CURIE: No prefix and no default namespace for unprefixed CURIE " + curie;
      } else {
        ns = opts.defaultNamespace;
      }
    }
    return $.uri(ns + local);
  };

  $.curie.defaults = {
    namespaces: {},
    reserved: [],
    reservedNamespace: undefined,
    defaultNamespace: undefined,
    charcase: 'preserve'
  };

   /**
    * Creates a {@link jQuery.uri} object by parsing a safe CURIE string (a CURIE
    * contained within square brackets). If the input safeCurie string does not
    * start with '[' and end with ']', the entire string content will be interpreted
    * as a URI string.
    * @methodOf jQuery
    * @param {String} safeCurie The safe CURIE string to be parsed.
    * @param {Object} [options] CURIE parsing options
    * @param {string} [options.reservedNamespace='http://www.w3.org/1999/xhtml/vocab#'] The namespace to apply to a CURIE that has no prefix and either starts with a colon or is in the list of reserved local names
    * @param {string} [options.defaultNamespace]  The namespace to apply to a CURIE with no prefix which is not mapped to the reserved namespace by the rules given above.
    * @param {Object} [options.namespaces] A map of namespace bindings used to map CURIE prefixes to URIs.
    * @param {string[]} [options.reserved=['alternate', 'appendix', 'bookmark', 'cite', 'chapter', 'contents', 'copyright',
      'first', 'glossary', 'help', 'icon', 'index', 'last', 'license', 'meta', 'next',
      'p3pv1', 'prev', 'role', 'section', 'stylesheet', 'subsection', 'start', 'top', 'up']]
                        A list of local names that will always be mapped to the URI specified by reservedNamespace.
    * @param {string} [options.charcase='lower'] Specifies whether the curie's case is altered before it's interpreted. Acceptable values are:
    * <dl>
    * <dt>lower</dt><dd>Force the CURIE string to lower case.</dd>
    * <dt>upper</dt><dd>Force the CURIE string to upper case.</dd>
    * <dt>preserve</dt><dd>Preserve the original case of the CURIE. Note that this might not be possible if the CURIE has been taken from an HTML attribute value because of the case conversions performed automatically by browsers. For this reason, it's a good idea to avoid mixed-case CURIEs within RDFa.</dd>
    * </dl>
    * @returns {jQuery.uri} A new {@link jQuery.uri} object representing the full absolute URI specified by the CURIE.
    */
  $.safeCurie = function (safeCurie, options) {
    var m = /^\[([^\]]+)\]$/.exec(safeCurie);
    return m ? $.curie(m[1], options) : $.uri(safeCurie);
  };

   /**
    * Creates a CURIE string from a URI string.
    * @methodOf jQuery
    * @param {String} uri The URI string to be converted to a CURIE.
    * @param {Object} [options] CURIE parsing options
    * @param {string} [options.reservedNamespace='http://www.w3.org/1999/xhtml/vocab#']
    *        If the input URI starts with this value, the generated CURIE will
    *        have no namespace prefix and will start with a colon character (:),
    *        unless the local part of the CURIE is one of the reserved names specified
    *        by the reservedNames option (see below), in which case the generated
    *        CURIE will have no namespace prefix and will not start with a colon
    *        character.
    * @param {string} [options.defaultNamespace]  If the input URI starts with this value, the generated CURIE will have no namespace prefix and will not start with a colon.
    * @param {Object} [options.namespaces] A map of namespace bindings used to map CURIE prefixes to URIs.
    * @param {string[]} [options.reserved=['alternate', 'appendix', 'bookmark', 'cite', 'chapter', 'contents', 'copyright',
      'first', 'glossary', 'help', 'icon', 'index', 'last', 'license', 'meta', 'next',
      'p3pv1', 'prev', 'role', 'section', 'stylesheet', 'subsection', 'start', 'top', 'up']]
                        A list of local names that will always be mapped to the URI specified by reservedNamespace.
    * @param {string} [options.charcase='lower'] Specifies the case normalisation done to the CURIE. Acceptable values are:
    * <dl>
    * <dt>lower</dt><dd>Normalise the CURIE to lower case.</dd>
    * <dt>upper</dt><dd>Normalise the CURIE to upper case.</dd>
    * <dt>preserve</dt><dd>Preserve the original case of the CURIE. Note that this might not be possible if the CURIE has been taken from an HTML attribute value because of the case conversions performed automatically by browsers. For this reason, it's a good idea to avoid mixed-case CURIEs within RDFa.</dd>
    * </dl>
    * @returns {jQuery.uri} A new {@link jQuery.uri} object representing the full absolute URI specified by the CURIE.
    */
  $.createCurie = function (uri, options) {
    var opts = $.extend({}, $.curie.defaults, options || {}),
      ns = opts.namespaces,
      curie;
    uri = $.uri(uri).toString();
    if (opts.reservedNamespace !== undefined && 
        uri.substring(0, opts.reservedNamespace.toString().length) === opts.reservedNamespace.toString()) {
      curie = uri.substring(opts.reservedNamespace.toString().length);
      if ($.inArray(curie, opts.reserved) === -1) {
        curie = ':' + curie;
      }
    } else {
      $.each(ns, function (prefix, namespace) {
        if (uri.substring(0, namespace.toString().length) === namespace.toString()) {
          curie = prefix + ':' + uri.substring(namespace.toString().length);
          return null;
        }
      });
    }
    if (curie === undefined) {
      throw "No Namespace Binding: There's no appropriate namespace binding for generating a CURIE from " + uri;
    } else {
      return curie;
    }
  };

   /**
    * Creates a {@link jQuery.uri} object by parsing the specified
    * CURIE string in the context of the namespaces defined by the
    * jQuery selection.
    * @methodOf jQuery#
    * @name jQuery#curie
    * @param {String} curie The CURIE string to be parsed
    * @param {Object} options The CURIE parsing options.
    *        See {@link jQuery.curie} for details of the supported options.
    *        The namespace declarations declared on the current jQuery
    *        selection (and inherited from any ancestor elements) will automatically
    *        be included in the options.namespaces property.
    * @returns {jQuery.uri}
    * @see jQuery.curie
    */
  $.fn.curie = function (curie, options) {
    var opts = $.extend({}, $.fn.curie.defaults, { namespaces: this.xmlns() }, options || {});
    return $.curie(curie, opts);
  };

   /**
    * Creates a {@link jQuery.uri} object by parsing the specified
    * safe CURIE string in the context of the namespaces defined by
    * the jQuery selection.
    *
    * @methodOf jQuery#
    * @name jQuery#safeCurie
    * @param {String} safeCurie The safe CURIE string to be parsed. See {@link jQuery.safeCurie} for details on how safe CURIE strings are processed.
    * @param {Object} options   The CURIE parsing options.
    *        See {@link jQuery.safeCurie} for details of the supported options.
    *        The namespace declarations declared on the current jQuery
    *        selection (and inherited from any ancestor elements) will automatically
    *        be included in the options.namespaces property.
    * @returns {jQuery.uri}
    * @see jQuery.safeCurie
    */
  $.fn.safeCurie = function (safeCurie, options) {
    var opts = $.extend({}, $.fn.curie.defaults, { namespaces: this.xmlns() }, options || {});
    return $.safeCurie(safeCurie, opts);
  };

   /**
    * Creates a CURIE string from a URI string using the namespace
    * bindings in the context of the current jQuery selection.
    *
    * @methodOf jQuery#
    * @name jQuery#createCurie
    * @param {String|jQuery.uri} uri The URI string to be converted to a CURIE
    * @param {Object} options the CURIE parsing options.
    *        See {@link jQuery.createCurie} for details of the supported options.
    *        The namespace declarations declared on the current jQuery
    *        selection (and inherited from any ancestor elements) will automatically
    *        be included in the options.namespaces property.
    * @returns {String}
    * @see jQuery.createCurie
    */
  $.fn.createCurie = function (uri, options) {
    var opts = $.extend({}, $.fn.curie.defaults, { namespaces: this.xmlns() }, options || {});
    return $.createCurie(uri, opts);
  };

  $.fn.curie.defaults = {
    reserved: [
      'alternate', 'appendix', 'bookmark', 'cite', 'chapter', 'contents', 'copyright',
      'first', 'glossary', 'help', 'icon', 'index', 'last', 'license', 'meta', 'next',
      'p3pv1', 'prev', 'role', 'section', 'stylesheet', 'subsection', 'start', 'top', 'up'
    ],
    reservedNamespace: 'http://www.w3.org/1999/xhtml/vocab#',
    defaultNamespace: undefined,
    charcase: 'lower'
  };

})(jQuery);
/*
 * jQuery RDF @VERSION
 *
 * Copyright (c) 2008,2009 Jeni Tennison
 * Licensed under the MIT (MIT-LICENSE.txt)
 *
 * Depends:
 *  jquery.uri.js
 *  jquery.xmlns.js
 *  jquery.datatype.js
 *  jquery.curie.js
 *  jquery.json.js
 */
/**
 * @fileOverview jQuery RDF
 * @author <a href="mailto:jeni@jenitennison.com">Jeni Tennison</a>
 * @copyright (c) 2008,2009 Jeni Tennison
 * @license MIT license (MIT-LICENSE.txt)
 * @version 1.0
 */
/**
 * @exports $ as jQuery
 */
/**
 * @ignore
 */
(function ($) {

  var
    memResource = {},
    memBlank = {},
    memLiteral = {},
    memTriple = {},
    memPattern = {},
    xsdNs = "http://www.w3.org/2001/XMLSchema#",
    rdfNs = "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    rdfsNs = "http://www.w3.org/2000/01/rdf-schema#",
    uriRegex = /^<(([^>]|\\>)*)>$/,
    literalRegex = /^("""((\\"|[^"])*)"""|"((\\"|[^"])*)")(@([a-z]+(-[a-z0-9]+)*)|\^\^(.+))?$/,
    tripleRegex = /(("""((\\"|[^"])*)""")|("(\\"|[^"]|)*")|(<(\\>|[^>])*>)|\S)+/g,

    blankNodeSeed = databankSeed = new Date().getTime() % 1000,
    blankNodeID = function () {
      blankNodeSeed += 1;
      return 'b' + blankNodeSeed.toString(16);
    },

    databankID = function () {
      databankSeed += 1;
      return 'data' + databankSeed.toString(16);
    },

    subject = function (subject, opts) {
      if (typeof subject === 'string') {
        try {
          return $.rdf.resource(subject, opts);
        } catch (e) {
          try {
            return $.rdf.blank(subject, opts);
          } catch (f) {
            throw "Bad Triple: Subject " + subject + " is not a resource: " + f;
          }
        }
      } else {
        return subject;
      }
    },

    property = function (property, opts) {
      if (property === 'a') {
        return $.rdf.type;
      } else if (typeof property === 'string') {
        try {
          return $.rdf.resource(property, opts);
        } catch (e) {
          throw "Bad Triple: Property " + property + " is not a resource: " + e;
        }
      } else {
        return property;
      }
    },

    object = function (object, opts) {
      if (typeof object === 'string') {
        try {
          return $.rdf.resource(object, opts);
        } catch (e) {
          try {
            return $.rdf.blank(object, opts);
          } catch (f) {
            try {
              return $.rdf.literal(object, opts);
            } catch (g) {
              throw "Bad Triple: Object " + object + " is not a resource or a literal " + g;
            }
          }
        }
      } else {
        return object;
      }
    },

    testResource = function (resource, filter, existing) {
      var variable;
      if (typeof filter === 'string') {
        variable = filter.substring(1);
        if (existing[variable] && existing[variable] !== resource) {
          return null;
        } else {
          existing[variable] = resource;
          return existing;
        }
      } else if (filter === resource) {
        return existing;
      } else {
        return null;
      }
    },

    findMatches = function (triples, pattern) {
      return $.map(triples, function (triple) {
        var bindings = pattern.exec(triple);
        return bindings === null ? null : { bindings: bindings, triples: [triple] };
      });
    },

    mergeMatches = function (existingMs, newMs, optional) {
      return $.map(existingMs, function (existingM, i) {
        var compatibleMs = $.map(newMs, function (newM) {
          // For newM to be compatible with existingM, all the bindings
          // in newM must either be the same as in existingM, or not
          // exist in existingM
          var isCompatible = true;
          $.each(newM.bindings, function (k, b) {
            if (!(existingM.bindings[k] === undefined ||
                  existingM.bindings[k] === b)) {
              isCompatible = false;
              return false;
            }
          });
          return isCompatible ? newM : null;
        });
        if (compatibleMs.length > 0) {
          return $.map(compatibleMs, function (compatibleM) {
            return {
              bindings: $.extend({}, existingM.bindings, compatibleM.bindings),
              triples: $.unique(existingM.triples.concat(compatibleM.triples))
            };
          });
        } else {
          return optional ? existingM : null;
        }
      });
    },

    registerQuery = function (databank, query) {
      var s, p, o;
      if (query.filterExp !== undefined && !$.isFunction(query.filterExp)) {
        if (databank.union === undefined) {
          s = typeof query.filterExp.subject === 'string' ? '' : query.filterExp.subject;
          p = typeof query.filterExp.property === 'string' ? '' : query.filterExp.property;
          o = typeof query.filterExp.object === 'string' ? '' : query.filterExp.object;
          if (databank.queries[s] === undefined) {
            databank.queries[s] = {};
          }
          if (databank.queries[s][p] === undefined) {
            databank.queries[s][p] = {};
          }
          if (databank.queries[s][p][o] === undefined) {
            databank.queries[s][p][o] = [];
          }
          databank.queries[s][p][o].push(query);
        } else {
          $.each(databank.union, function (i, databank) {
            registerQuery(databank, query);
          });
        }
      }
    },

    resetQuery = function (query) {
      query.length = 0;
      query.matches = [];
      $.each(query.children, function (i, child) {
        resetQuery(child);
      });
      $.each(query.partOf, function (i, union) {
        resetQuery(union);
      });
    },

    updateQuery = function (query, matches) {
      if (matches.length > 0) {
        $.each(query.children, function (i, child) {
          leftActivate(child, matches);
        });
        $.each(query.partOf, function (i, union) {
          updateQuery(union, matches);
        });
        $.each(matches, function (i, match) {
          query.matches.push(match);
          Array.prototype.push.call(query, match.bindings);
        });
      }
    },

    leftActivate = function (query, matches) {
      var newMatches;
      if (query.union === undefined) {
        if (query.top || query.parent.top) {
          newMatches = query.alphaMemory;
        } else {
          matches = matches || query.parent.matches;
          if ($.isFunction(query.filterExp)) {
            newMatches = $.map(matches, function (match, i) {
              return query.filterExp.call(match.bindings, i, match.bindings, match.triples) ? match : null;
            });
          } else {
            newMatches = mergeMatches(matches, query.alphaMemory, query.filterExp.optional);
          }
        }
      } else {
        newMatches = $.map(query.union, function (q) {
          return q.matches;
        });
      }
      updateQuery(query, newMatches);
    },

    rightActivate = function (query, match) {
      var newMatches;
      if (query.filterExp.optional) {
        resetQuery(query);
        leftActivate(query);
      } else {
        if (query.top || query.parent.top) {
          newMatches = [match];
        } else {
          newMatches = mergeMatches(query.parent.matches, [match], false);
        }
        updateQuery(query, newMatches);
      }
    },

    addToQuery = function (query, triple) {
      var match,
        bindings = query.filterExp.exec(triple);
      if (bindings !== null) {
        match = { triples: [triple], bindings: bindings };
        query.alphaMemory.push(match);
        rightActivate(query, match);
      }
    },

    removeFromQuery = function (query, triple) {
      query.alphaMemory.splice($.inArray(triple, query.alphaMemory), 1);
      resetQuery(query);
      leftActivate(query);
    },

    addToQueries = function (queries, triple) {
      $.each(queries, function (i, query) {
        addToQuery(query, triple);
      });
    },

    removeFromQueries = function (queries, triple) {
      $.each(queries, function (i, query) {
        removeFromQuery(query, triple);
      });
    },

    addToDatabankQueries = function (databank, triple) {
      var s = triple.subject,
        p = triple.property,
        o = triple.object;
      if (databank.union === undefined) {
        if (databank.queries[s] !== undefined) {
          if (databank.queries[s][p] !== undefined) {
            if (databank.queries[s][p][o] !== undefined) {
              addToQueries(databank.queries[s][p][o], triple);
            }
            if (databank.queries[s][p][''] !== undefined) {
              addToQueries(databank.queries[s][p][''], triple);
            }
          }
          if (databank.queries[s][''] !== undefined) {
            if (databank.queries[s][''][o] !== undefined) {
              addToQueries(databank.queries[s][''][o], triple);
            }
            if (databank.queries[s][''][''] !== undefined) {
              addToQueries(databank.queries[s][''][''], triple);
            }
          }
        }
        if (databank.queries[''] !== undefined) {
          if (databank.queries[''][p] !== undefined) {
            if (databank.queries[''][p][o] !== undefined) {
              addToQueries(databank.queries[''][p][o], triple);
            }
            if (databank.queries[''][p][''] !== undefined) {
              addToQueries(databank.queries[''][p][''], triple);
            }
          }
          if (databank.queries[''][''] !== undefined) {
            if (databank.queries[''][''][o] !== undefined) {
              addToQueries(databank.queries[''][''][o], triple);
            }
            if (databank.queries[''][''][''] !== undefined) {
              addToQueries(databank.queries[''][''][''], triple);
            }
          }
        }
      } else {
        $.each(databank.union, function (i, databank) {
          addToDatabankQueries(databank, triple);
        });
      }
    },

    removeFromDatabankQueries = function (databank, triple) {
      var s = triple.subject,
        p = triple.property,
        o = triple.object;
      if (databank.union === undefined) {
        if (databank.queries[s] !== undefined) {
          if (databank.queries[s][p] !== undefined) {
            if (databank.queries[s][p][o] !== undefined) {
              removeFromQueries(databank.queries[s][p][o], triple);
            }
            if (databank.queries[s][p][''] !== undefined) {
              removeFromQueries(databank.queries[s][p][''], triple);
            }
          }
          if (databank.queries[s][''] !== undefined) {
            if (databank.queries[s][''][o] !== undefined) {
              removeFromQueries(databank.queries[s][''][o], triple);
            }
            if (databank.queries[s][''][''] !== undefined) {
              removeFromQueries(databank.queries[s][''][''], triple);
            }
          }
        }
        if (databank.queries[''] !== undefined) {
          if (databank.queries[''][p] !== undefined) {
            if (databank.queries[''][p][o] !== undefined) {
              removeFromQueries(databank.queries[''][p][o], triple);
            }
            if (databank.queries[''][p][''] !== undefined) {
              removeFromQueries(databank.queries[''][p][''], triple);
            }
          }
          if (databank.queries[''][''] !== undefined) {
            if (databank.queries[''][''][o] !== undefined) {
              removeFromQueries(databank.queries[''][''][o], triple);
            }
            if (databank.queries[''][''][''] !== undefined) {
              removeFromQueries(databank.queries[''][''][''], triple);
            }
          }
        }
      } else {
        $.each(databank.union, function (i, databank) {
          removeFromDatabankQueries(databank, triple);
        });
      }
    },

    createJson = function (triples) {
      var e = {},
        i, t, s, p;
      for (i = 0; i < triples.length; i += 1) {
        t = triples[i];
        s = t.subject.value.toString();
        p = t.property.value.toString();
        if (e[s] === undefined) {
          e[s] = {};
        }
        if (e[s][p] === undefined) {
          e[s][p] = [];
        }
        e[s][p].push(t.object.dump());
      }
      return e;
    },

    parseJson = function (data) {
      var s, subject, p, property, o, object, i, opts, triples = [];
      for (s in data) {
        subject = (s.substring(0, 2) === '_:') ? $.rdf.blank(s) : $.rdf.resource('<' + s + '>');
        for (p in data[s]) {
          property = $.rdf.resource('<' + p + '>');
          for (i = 0; i < data[s][p].length; i += 1) {
            o = data[s][p][i];
            if (o.type === 'uri') {
              object = $.rdf.resource('<' + o.value + '>');
            } else if (o.type === 'bnode') {
              object = $.rdf.blank(o.value);
            } else {
              // o.type === 'literal'
              if (o.datatype !== undefined) {
                object = $.rdf.literal(o.value, { datatype: o.datatype });
              } else {
                opts = {};
                if (o.lang !== undefined) {
                  opts.lang = o.lang;
                }
                object = $.rdf.literal('"' + o.value + '"', opts);
              }
            }
            triples.push($.rdf.triple(subject, property, object));
          }
        }
      }
      return triples;
    },

    addAttribute = function (parent, namespace, name, value) {
      var doc = parent.ownerDocument,
        a;
      if (namespace !== undefined && namespace !== null) {
        if (doc.createAttributeNS) {
          a = doc.createAttributeNS(namespace, name);
          a.nodeValue = value;
          parent.attributes.setNamedItemNS(a);
        } else {
          a = doc.createNode(2, name, namespace);
          a.nodeValue = value;
          parent.attributes.setNamedItem(a);
        }
      } else {
        a = doc.createAttribute(name);
        a.nodeValue = value;
        parent.attributes.setNamedItem(a);
      }
      return parent;
    },

    createXmlnsAtt = function (parent, namespace, prefix) {
      if (prefix) {
        addAttribute(parent, 'http://www.w3.org/2000/xmlns/', 'xmlns:' + prefix, namespace);
      } else {
        addAttribute(parent, undefined, 'xmlns', namespace);
      }
      return parent;
    },

    createDocument = function (namespace, name) {
      var doc, xmlns = '', prefix, addAttribute = false;
      if (namespace !== undefined && namespace !== null) {
        if (/:/.test(name)) {
          prefix = /([^:]+):/.exec(name)[1];
        }
        addAttribute = true;
      }
      if (document.implementation &&
          document.implementation.createDocument) {
        doc = document.implementation.createDocument(namespace, name, null);
        if (addAttribute) {
          createXmlnsAtt(doc.documentElement, namespace, prefix);
        }
        return doc;
      } else {
        doc = new ActiveXObject("Microsoft.XMLDOM");
        doc.async = "false";
        if (prefix === undefined) {
          xmlns = ' xmlns="' + namespace + '"';
        } else {
          xmlns = ' xmlns:' + prefix + '="' + namespace + '"';
        }
        doc.loadXML('<' + name + xmlns + '/>');
        return doc;
      }
    },

    appendElement = function (parent, namespace, name) {
      var doc = parent.ownerDocument,
        e;
      if (namespace !== undefined && namespace !== null) {
        e = doc.createElementNS ? doc.createElementNS(namespace, name) : doc.createNode(1, name, namespace);
      } else {
        e = doc.createElement(name);
      }
      parent.appendChild(e);
      return e;
    },

    appendText = function (parent, text) {
      var doc = parent.ownerDocument,
        t;
      t = doc.createTextNode(text);
      parent.appendChild(t);
      return parent;
    },

    appendXML = function (parent, xml) {
      var parser, doc, i, child;
      try {
        doc = new ActiveXObject('Microsoft.XMLDOM');
        doc.async = "false";
        doc.loadXML('<temp>' + xml + '</temp>');
      } catch(e) {
        parser = new DOMParser();
        doc = parser.parseFromString('<temp>' + xml + '</temp>', 'text/xml');
      }
      for (i = 0; i < doc.documentElement.childNodes.length; i += 1) {
        parent.appendChild(doc.documentElement.childNodes[i].cloneNode(true));
      }
      return parent;
    },

    createRdfXml = function (triples, options) {
      var doc = createDocument(rdfNs, 'rdf:RDF'),
        dump = createJson(triples),
        namespaces = options.namespaces || {},
        n, s, se, p, pe, i, v,
        m, local, ns, prefix;
      for (n in namespaces) {
        createXmlnsAtt(doc.documentElement, namespaces[n], n);
      }
      for (s in dump) {
        if (dump[s][$.rdf.type.value] !== undefined) {
          m = /(.+[#\/])([^#\/]+)/.exec(dump[s][$.rdf.type.value][0].value);
          ns = m[1];
          local = m[2];
          for (n in namespaces) {
            if (namespaces[n] === ns) {
              prefix = n;
              break;
            }
          }
          se = appendElement(doc.documentElement, ns, prefix + ':' + local);
        } else {
          se = appendElement(doc.documentElement, rdfNs, 'rdf:Description');
        }
        if (/^_:/.test(s)) {
          addAttribute(se, rdfNs, 'rdf:nodeID', s.substring(2));
        } else {
          addAttribute(se, rdfNs, 'rdf:about', s);
        }
        for (p in dump[s]) {
          if (p !== $.rdf.type.value.toString() || dump[s][p].length > 1) {
            m = /(.+[#\/])([^#\/]+)/.exec(p);
            ns = m[1];
            local = m[2];
            for (n in namespaces) {
              if (namespaces[n] === ns) {
                prefix = n;
                break;
              }
            }
            for (i = (p === $.rdf.type.value.toString() ? 1 : 0); i < dump[s][p].length; i += 1) {
              v = dump[s][p][i];
              pe = appendElement(se, ns, prefix + ':' + local);
              if (v.type === 'uri') {
                addAttribute(pe, rdfNs, 'rdf:resource', v.value);
              } else if (v.type === 'literal') {
                if (v.datatype !== undefined) {
                  if (v.datatype === 'http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral') {
                    addAttribute(pe, rdfNs, 'rdf:parseType', 'Literal');
                    appendXML(pe, v.value);
                  } else {
                    addAttribute(pe, rdfNs, 'rdf:datatype', v.datatype);
                    appendText(pe, v.value);
                  }
                } else if (v.lang !== undefined) {
                  addAttribute(pe, 'http://www.w3.org/XML/1998/namespace', 'xml:lang', v.lang);
                  appendText(pe, v.value);
                } else {
                  appendText(pe, v.value);
                }
              } else {
                // blank node
                addAttribute(pe, rdfNs, 'rdf:nodeID', v.value.substring(2));
              }
            }
          }
        }
      }
      return doc;
    },

    getDefaultNamespacePrefix = function(namespaceUri){
      switch (namespaceUri) {
        case 'http://www.w3.org/1999/02/22-rdf-syntax-ns':
          return 'rdf';
        case 'http://www.w3.org/XML/1998/namespace':
          return 'xml';
        case 'http://www.w3.org/2000/xmlns/':
          return 'xmlns';
        default:
          throw ('No default prefix mapped for namespace ' + namespaceUri);
      }
    },

    hasAttributeNS  = function(elem, namespace, name){
      var basename;
      if (elem.hasAttributeNS) {
        return elem.hasAttributeNS(namespace, name);
      } else {
        try {
          basename = /:/.test(name) ? /:(.+)$/.exec(name)[1] : name;
          return elem.attributes.getQualifiedItem(basename, namespace) !== null;
        } catch (e) {
          return elem.getAttribute(getDefaultNamespacePrefix(namespace) + ':' + name) !== null;
        }
      }
    },

    getAttributeNS = function(elem, namespace, name){
      var basename;
      if (elem.getAttributeNS) {
        return elem.getAttributeNS(namespace, name);
      } else {
        try {
          basename = /:/.test(name) ? /:(.+)$/.exec(name)[1] : name;
          return elem.attributes.getQualifiedItem(basename, namespace).nodeValue;
        } catch (e) {
          return elem.getAttribute(getDefaultNamespacePrefix(namespace) + ':' + name);
        }
      }
    },

    getLocalName = function(elem){
      return elem.localName || elem.baseName;
    },

    parseRdfXmlSubject = function (elem, base) {
      var s, subject;
      if (hasAttributeNS(elem, rdfNs, 'about')) {
        s = getAttributeNS(elem, rdfNs, 'about');
        subject = $.rdf.resource('<' + s + '>', { base: base });
      } else if (hasAttributeNS(elem, rdfNs, 'ID')) {
        s = getAttributeNS(elem, rdfNs, 'ID');
        subject = $.rdf.resource('<#' + s + '>', { base: base });
      } else if (hasAttributeNS(elem, rdfNs, 'nodeID')) {
        s = getAttributeNS(elem, rdfNs, 'nodeID');
        subject = $.rdf.blank('_:' + s);
      } else {
        subject = $.rdf.blank('[]');
      }
      return subject;
    },

    parseRdfXmlDescription = function (elem, isDescription, base, lang) {
      var subject, p, property, o, object, reified, lang, i, j, li = 1,
        collection1, collection2, collectionItem, collectionItems = [],
        parseType, serializer, literalOpts = {}, oTriples, triples = [];
      lang = getAttributeNS(elem, 'http://www.w3.org/XML/1998/namespace', 'lang') || lang;
      base = getAttributeNS(elem, 'http://www.w3.org/XML/1998/namespace', 'base') || base;
      if (lang !== null && lang !== undefined && lang !== '') {
        literalOpts = { lang: lang };
      }
      subject = parseRdfXmlSubject(elem, base);
      if (isDescription && (elem.namespaceURI !== rdfNs || getLocalName(elem) !== 'Description')) {
        property = $.rdf.type;
        object = $.rdf.resource('<' + elem.namespaceURI + getLocalName(elem) + '>');
        triples.push($.rdf.triple(subject, property, object));
      }
      for (i = 0; i < elem.attributes.length; i += 1) {
        p = elem.attributes.item(i);
        if (p.namespaceURI !== undefined &&
            p.namespaceURI !== 'http://www.w3.org/2000/xmlns/' &&
            p.namespaceURI !== 'http://www.w3.org/XML/1998/namespace' &&
            p.prefix !== 'xmlns' &&
            p.prefix !== 'xml') {
          if (p.namespaceURI !== rdfNs) {
            property = $.rdf.resource('<' + p.namespaceURI + getLocalName(p) + '>');
            object = $.rdf.literal('"' + p.nodeValue + '"', literalOpts);
            triples.push($.rdf.triple(subject, property, object));
          } else if (getLocalName(p) === 'type') {
            property = $.rdf.type;
            object = $.rdf.resource('<' + p.nodeValue + '>', { base: base });
            triples.push($.rdf.triple(subject, property, object));
          }
        }
      }
      for (i = 0; i < elem.childNodes.length; i += 1) {
        p = elem.childNodes[i];
        if (p.nodeType === 1) {
          if (p.namespaceURI === rdfNs && getLocalName(p) === 'li') {
            property = $.rdf.resource('<' + rdfNs + '_' + li + '>');
            li += 1;
          } else {
            property = $.rdf.resource('<' + p.namespaceURI + getLocalName(p) + '>');
          }
          lang = getAttributeNS(p, 'http://www.w3.org/XML/1998/namespace', 'lang') || lang;
          if (lang !== null && lang !== undefined && lang !== '') {
            literalOpts = { lang: lang };
          }
          if (hasAttributeNS(p, rdfNs, 'resource')) {
            o = getAttributeNS(p, rdfNs, 'resource');
            object = $.rdf.resource('<' + o + '>', { base: base });
          } else if (hasAttributeNS(p, rdfNs, 'nodeID')) {
            o = getAttributeNS(p, rdfNs, 'nodeID');
            object = $.rdf.blank('_:' + o);
          } else if (hasAttributeNS(p, rdfNs, 'parseType')) {
            parseType = getAttributeNS(p, rdfNs, 'parseType');
            if (parseType === 'Literal') {
              try {
                serializer = new XMLSerializer();
                o = serializer.serializeToString(p.getElementsByTagName('*')[0]);
              } catch (e) {
                o = "";
                for (j = 0; j < p.childNodes.length; j += 1) {
                  o += p.childNodes[j].xml;
                }
              }
              object = $.rdf.literal(o, { datatype: rdfNs + 'XMLLiteral' });
            } else if (parseType === 'Resource') {
              oTriples = parseRdfXmlDescription(p, false, base, lang);
              if (oTriples.length > 0) {
                object = oTriples[oTriples.length - 1].subject;
                triples = triples.concat(oTriples);
              } else {
                object = $.rdf.blank('[]');
              }
            } else if (parseType === 'Collection') {
              if (p.getElementsByTagName('*').length > 0) {
                for (j = 0; j < p.childNodes.length; j += 1) {
                  o = p.childNodes[j];
                  if (o.nodeType === 1) {
                    collectionItems.push(o);
                  }
                }
                collection1 = $.rdf.blank('[]');
                object = collection1;
                for (j = 0; j < collectionItems.length; j += 1) {
                  o = collectionItems[j];
                  oTriples = parseRdfXmlDescription(o, true, base, lang);
                  if (oTriples.length > 0) {
                    collectionItem = oTriples[oTriples.length - 1].subject;
                    triples = triples.concat(oTriples);
                  } else {
                    collectionItem = parseRdfXmlSubject(o);
                  }
                  triples.push($.rdf.triple(collection1, $.rdf.first, collectionItem));
                  if (j === collectionItems.length - 1) {
                    triples.push($.rdf.triple(collection1, $.rdf.rest, $.rdf.nil));
                  } else {
                    collection2 = $.rdf.blank('[]');
                    triples.push($.rdf.triple(collection1, $.rdf.rest, collection2));
                    collection1 = collection2;
                  }
                }
              } else {
                object = $.rdf.nil;
              }
            }
          } else if (hasAttributeNS(p, rdfNs, 'datatype')) {
            o = p.childNodes[0].nodeValue;
            object = $.rdf.literal(o, { datatype: getAttributeNS(p, rdfNs, 'datatype') });
          } else if (p.getElementsByTagName('*').length > 0) {
            for (j = 0; j < p.childNodes.length; j += 1) {
              o = p.childNodes[j];
              if (o.nodeType === 1) {
                oTriples = parseRdfXmlDescription(o, true, base, lang);
                if (oTriples.length > 0) {
                  object = oTriples[oTriples.length - 1].subject;
                  triples = triples.concat(oTriples);
                } else {
                  object = parseRdfXmlSubject(o);
                }
              }
            }
          } else if (p.childNodes.length > 0) {
            o = p.childNodes[0].nodeValue;
            object = $.rdf.literal('"' + o + '"', literalOpts);
          } else {
            oTriples = parseRdfXmlDescription(p, false, base, lang);
            if (oTriples.length > 0) {
              object = oTriples[oTriples.length - 1].subject;
              triples = triples.concat(oTriples);
            } else {
              object = $.rdf.blank('[]');
            }
          }
          triples.push($.rdf.triple(subject, property, object));
          if (hasAttributeNS(p, rdfNs, 'ID')) {
            reified = $.rdf.resource('<#' + getAttributeNS(p, rdfNs, 'ID') + '>', { base: base });
            triples.push($.rdf.triple(reified, $.rdf.subject, subject));
            triples.push($.rdf.triple(reified, $.rdf.property, property));
            triples.push($.rdf.triple(reified, $.rdf.object, object));
          }
        }
      }
      return triples;
    },

    parseRdfXml = function (doc) {
      var i, lang, d, triples = [];
      if (doc.documentElement.namespaceURI === rdfNs && getLocalName(doc.documentElement) === 'RDF') {
        lang = getAttributeNS(doc.documentElement, 'http://www.w3.org/XML/1998/namespace', 'lang');
        base = getAttributeNS(doc.documentElement, 'http://www.w3.org/XML/1998/namespace', 'base') || $.uri.base();
        for (i = 0; i < doc.documentElement.childNodes.length; i += 1) {
          d = doc.documentElement.childNodes[i];
          if (d.nodeType === 1) {
            triples = triples.concat(parseRdfXmlDescription(d, true, base, lang));
          }
        }
      } else {
        triples = parseRdfXmlDescription(doc.documentElement, true);
      }
      return triples;
    };

  $.typedValue.types['http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral'] = {
    regex: /^.*$/m,
    strip: false,
    value: function (v) {
      return v;
    }
  };

  /**
   * <p>Creates a new jQuery.rdf object. This should be invoked as a method rather than constructed using new; indeed you will usually want to generate these objects using a method such as {@link jQuery#rdf} or {@link jQuery.rdf#where}.</p>
   * @class <p>A jQuery.rdf object represents the results of a query over its {@link jQuery.rdf#databank}. The results of a query are a sequence of objects which represent the bindings of values to the variables used in filter expressions specified using {@link jQuery.rdf#where} or {@link jQuery.rdf#optional}. Each of the objects in this sequence has associated with it a set of triples that are the sources for the variable bindings, which you can get at using {@link jQuery.rdf#sources}.</p>
    * <p>The {@link jQuery.rdf} object itself is a lot like a {@link jQuery} object. It has a {@link jQuery.rdf#length} and the individual matches can be accessed using <code>[<var>n</var>]</code>, but you can also iterate through the matches using {@link jQuery.rdf#map} or {@link jQuery.rdf#each}.</p>
    * <p>{@link jQuery.rdf} is designed to mirror the functionality of <a href="http://www.w3.org/TR/rdf-sparql-query/">SPARQL</a> while providing an interface that's familiar and easy to use for jQuery programmers.</p>
   * @param {Object} [options]
   * @param {jQuery.rdf.databank} [options.databank] The databank that this query should operate over.
   * @param {jQuery.rdf.triple[]} [options.triples] A set of triples over which the query operates; this is only used if options.databank isn't specified, in which case a new databank with these triples is generated.
   * @param {Object} [options.namespaces] An object representing a set of namespace bindings. Rather than passing this in when you construct the {@link jQuery.rdf} instance, you will usually want to use the {@link jQuery.rdf#prefix} method.
   * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the query.
   * @returns {jQuery.rdf}
   * @example rdf = jQuery.rdf();
   * @see jQuery#rdf
   */
  $.rdf = function (options) {
    return new $.rdf.fn.init(options);
  };

  $.rdf.fn = $.rdf.prototype = {
    /**
     * The version of rdfQuery.
     * @type String
     */
    rdfquery: '0.9',

    init: function (options) {
      var databanks;
      options = options || {};
      /* must specify either a parent or a union, otherwise it's the top */
      this.parent = options.parent;
      this.union = options.union;
      this.top = this.parent === undefined && this.union === undefined;
      if (this.union === undefined) {
        if (options.databank === undefined) {
          /**
           * The databank over which this query operates.
           * @type jQuery.rdf.databank
           */
          this.databank = this.parent === undefined ? $.rdf.databank(options.triples, options) : this.parent.databank;
        } else {
          this.databank = options.databank;
        }
      } else {
        databanks = $.map(this.union, function (query) {
          return query.databank;
        });
        databanks = $.unique(databanks);
        if (databanks[1] !== undefined) {
          this.databank = $.rdf.databank(undefined, { union: databanks });
        } else {
          this.databank = databanks[0];
        }
      }
      this.children = [];
      this.partOf = [];
      this.filterExp = options.filter;
      this.alphaMemory = [];
      this.matches = [];
      /**
       * The number of matches represented by the {@link jQuery.rdf} object.
       * @type Integer
       */
      this.length = 0;
      if (this.filterExp !== undefined) {
        if (!$.isFunction(this.filterExp)) {
          registerQuery(this.databank, this);
          this.alphaMemory = findMatches(this.databank.triples(), this.filterExp);
        }
      }
      leftActivate(this);
      return this;
    },

    /**
     * Sets or returns the base URI of the {@link jQuery.rdf#databank}.
     * @param {String|jQuery.uri} [base]
     * @returns A {@link jQuery.uri} if no base URI is specified, otherwise returns this {@link jQuery.rdf} object.
     * @example baseURI = jQuery('html').rdf().base();
     * @example jQuery('html').rdf().base('http://www.example.org/');
     * @see jQuery.rdf.databank#base
     */
    base: function (base) {
      if (base === undefined) {
        return this.databank.base();
      } else {
        this.databank.base(base);
        return this;
      }
    },

    /**
     * Sets or returns a namespace binding on the {@link jQuery.rdf#databank}.
     * @param {String} [prefix]
     * @param {String} [namespace]
     * @returns {Object|jQuery.uri|jQuery.rdf} If no prefix or namespace is specified, returns an object providing all namespace bindings on the {@link jQuery.rdf.databank}. If a prefix is specified without a namespace, returns the {@link jQuery.uri} associated with that prefix. Otherwise returns this {@link jQuery.rdf} object after setting the namespace binding.
     * @example namespace = jQuery('html').rdf().prefix('foaf');
     * @example jQuery('html').rdf().prefix('foaf', 'http://xmlns.com/foaf/0.1/');
     * @see jQuery.rdf.databank#prefix
     */
    prefix: function (prefix, namespace) {
      if (namespace === undefined) {
        return this.databank.prefix(prefix);
      } else {
        this.databank.prefix(prefix, namespace);
        return this;
      }
    },

    /**
     * Adds a triple to the {@link jQuery.rdf#databank} or another {@link jQuery.rdf} object to create a union.
     * @param {String|jQuery.rdf.triple|jQuery.rdf.pattern|jQuery.rdf} triple The triple, {@link jQuery.rdf.pattern} or {@link jQuery.rdf} object to be added to this one. If the triple is a {@link jQuery.rdf} object, the two queries are unioned together. If the triple is a string, it's parsed as a {@link jQuery.rdf.pattern}. The pattern will be completed using the current matches on the {@link jQuery.rdf} object to create multiple triples, one for each set of bindings.
     * @param {Object} [options]
     * @param {Object} [options.namespaces] An object representing a set of namespace bindings used to interpret CURIEs within the triple. Defaults to the namespace bindings defined on the {@link jQuery.rdf#databank}.
     * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the triple. Defaults to the base URI defined on the {@link jQuery.rdf#databank}.
     * @returns {jQuery.rdf} This {@link jQuery.rdf} object.
     * @example
     * var rdf = $.rdf()
     *   .prefix('dc', ns.dc)
     *   .prefix('foaf', ns.foaf)
     *   .add('&lt;photo1.jpg> dc:creator &lt;http://www.blogger.com/profile/1109404> .')
     *   .add('&lt;http://www.blogger.com/profile/1109404> foaf:img &lt;photo1.jpg> .');
     * @example
     * var rdfA = $.rdf()
     *   .prefix('dc', ns.dc)
     *   .add('&lt;photo1.jpg> dc:creator "Jane"');
     * var rdfB = $.rdf()
     *   .prefix('foaf', ns.foaf)
     *   .add('&lt;photo1.jpg> foaf:depicts "Jane"');
     * var rdf = rdfA.add(rdfB);
     * @see jQuery.rdf.databank#add
     */
    add: function (triple, options) {
      var query, databank;
      if (triple.rdfquery !== undefined) {
        if (triple.top) {
          databank = this.databank.add(triple.databank);
          query = $.rdf({ parent: this.parent, databank: databank });
          return query;
        } else if (this.top) {
          databank = triple.databank.add(this.databank);
          query = $.rdf({ parent: triple.parent, databank: databank });
          return query;
        } else if (this.union === undefined) {
          query = $.rdf({ union: [this, triple] });
          this.partOf.push(query);
          triple.partOf.push(query);
          return query;
        } else {
          this.union.push(triple);
          triple.partOf.push(this);
        }
      } else {
        if (typeof triple === 'string') {
          options = $.extend({}, { base: this.base(), namespaces: this.prefix(), source: triple }, options);
          triple = $.rdf.pattern(triple, options);
        }
        if (triple.isFixed()) {
          this.databank.add(triple.triple(), options);
        } else {
          query = this;
          this.each(function (i, data) {
            var t = triple.triple(data);
            if (t !== null) {
              query.databank.add(t, options);
            }
          });
        }
      }
      return this;
    },

    /**
     * Removes a triple or several triples from the {@link jQuery.rdf#databank}.
     * @param {String|jQuery.rdf.triple|jQuery.rdf.pattern} triple The triple to be removed, or a {@link jQuery.rdf.pattern} that matches the triples that should be removed.
     * @param {Object} [options]
     * @param {Object} [options.namespaces] An object representing a set of namespace bindings used to interpret any CURIEs within the triple or pattern. Defaults to the namespace bindings defined on the {@link jQuery.rdf#databank}.
     * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the triple or pattern. Defaults to the base URI defined on the {@link jQuery.rdf#databank}.
     * @returns {jQuery.rdf} The {@link jQuery.rdf} object itself.
     * @example
     * var rdf = $('html').rdf()
     *   .prefix('foaf', ns.foaf)
     *   .where('?person foaf:givenname ?gname')
     *   .where('?person foaf:family_name ?fname')
     *   .remove('?person foaf:family_name ?fname');
     * @see jQuery.rdf.databank#remove
     */
    remove: function (triple, options) {
      if (typeof triple === 'string') {
        options = $.extend({}, { base: this.base(), namespaces: this.prefix() }, options);
        triple = $.rdf.pattern(triple, options);
      }
      if (triple.isFixed()) {
        this.databank.remove(triple.triple(), options);
      } else {
        query = this;
        this.each(function (i, data) {
          var t = triple.triple(data);
          if (t !== null) {
            query.databank.remove(t, options);
          }
        });
      }
      return this;
    },

    /**
     * Loads some data into the {@link jQuery.rdf#databank}
     * @param data
     * @param {Object} [options]
     * @see jQuery.rdf.databank#load
     */
    load: function (data, options) {
      this.databank.load(data, options);
      return this;
    },

    /**
     * Creates a new {@link jQuery.rdf} object whose databank contains all the triples in this object's databank except for those in the argument's databank.
     * @param {jQuery.rdf} query
     * @see jQuery.rdf.databank#except
     */
    except: function (query) {
      return $.rdf({ databank: this.databank.except(query.databank) });
    },

    /**
     * Creates a new {@link jQuery.rdf} object that is the result of filtering the matches on this {@link jQuery.rdf} object based on the filter that's passed into it.
     * @param {String|jQuery.rdf.pattern} filter An expression that filters the triples in the {@link jQuery.rdf#databank} to locate matches based on the matches on this {@link jQuery.rdf} object. If it's a string, the filter is parsed as a {@link jQuery.rdf.pattern}.
     * @param {Object} [options]
     * @param {Object} [options.namespaces] An object representing a set of namespace bindings used to interpret any CURIEs within the pattern. Defaults to the namespace bindings defined on the {@link jQuery.rdf#databank}.
     * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the pattern. Defaults to the base URI defined on the {@link jQuery.rdf#databank}.
     * @param {boolean} [options.optional] Not usually used (use {@link jQuery.rdf#optional} instead).
     * @returns {jQuery.rdf} A new {@link jQuery.rdf} object whose {@link jQuery.rdf#parent} is this {@link jQuery.rdf}.
     * @see jQuery.rdf#optional
     * @see jQuery.rdf#filter
     * @see jQuery.rdf#about
     * @example
     * var rdf = $.rdf()
     *   .prefix('foaf', ns.foaf)
     *   .add('_:a foaf:givenname   "Alice" .')
     *   .add('_:a foaf:family_name "Hacker" .')
     *   .add('_:b foaf:givenname   "Bob" .')
     *   .add('_:b foaf:family_name "Hacker" .')
     *   .where('?person foaf:family_name "Hacker"')
     *   .where('?person foaf:givenname "Bob");
     */ 
    where: function (filter, options) {
      var query, base, namespaces, optional;
      options = options || {};
      if (typeof filter === 'string') {
        base = options.base || this.base();
        namespaces = $.extend({}, this.prefix(), options.namespaces || {});
        optional = options.optional || false;
        filter = $.rdf.pattern(filter, { namespaces: namespaces, base: base, optional: optional });
      }
      query = $.rdf($.extend({}, options, { parent: this, filter: filter }));
      this.children.push(query);
      return query;
    },

    /**
     * Creates a new {@link jQuery.rdf} object whose set of bindings might optionally include those based on the filter pattern.
     * @param {String|jQuery.rdf.pattern} filter An pattern for a set of bindings that might be added to those in this {@link jQuery.rdf} object.
     * @param {Object} [options]
     * @param {Object} [options.namespaces] An object representing a set of namespace bindings used to interpret any CURIEs within the pattern. Defaults to the namespace bindings defined on the {@link jQuery.rdf#databank}.
     * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the pattern. Defaults to the base URI defined on the {@link jQuery.rdf#databank}.
     * @returns {jQuery.rdf} A new {@link jQuery.rdf} object whose {@link jQuery.rdf#parent} is this {@link jQuery.rdf}.
     * @see jQuery.rdf#where
     * @see jQuery.rdf#filter
     * @see jQuery.rdf#about
     * @example
     * var rdf = $.rdf()
     *   .prefix('foaf', 'http://xmlns.com/foaf/0.1/')
     *   .prefix('rdf', 'http://www.w3.org/1999/02/22-rdf-syntax-ns#')
     *   .add('_:a  rdf:type        foaf:Person .')
     *   .add('_:a  foaf:name       "Alice" .')
     *   .add('_:a  foaf:mbox       &lt;mailto:alice@example.com> .')
     *   .add('_:a  foaf:mbox       &lt;mailto:alice@work.example> .')
     *   .add('_:b  rdf:type        foaf:Person .')
     *   .add('_:b  foaf:name       "Bob" .')
     *   .where('?x foaf:name ?name')
     *   .optional('?x foaf:mbox ?mbox');
     */
    optional: function (filter, options) {
      return this.where(filter, $.extend({}, options || {}, { optional: true }));
    },

    /**
     * Creates a new {@link jQuery.rdf} object whose set of bindings include <code>property</code> and <code>value</code> for every triple that is about the specified resource.
     * @param {String|jQuery.rdf.resource} resource The subject of the matching triples.
     * @param {Object} [options]
     * @param {Object} [options.namespaces] An object representing a set of namespace bindings used to interpret the resource if it's a CURIE. Defaults to the namespace bindings defined on the {@link jQuery.rdf#databank}.
     * @param {String|jQuery.uri} [options.base] The base URI used to interpret the resource if it's a relative URI (wrapped in <code>&lt;</code> and <code>&gt;</code>). Defaults to the base URI defined on the {@link jQuery.rdf#databank}.
     * @returns {jQuery.rdf} A new {@link jQuery.rdf} object whose {@link jQuery.rdf#parent} is this {@link jQuery.rdf}.
     * @see jQuery.rdf#where
     * @see jQuery.rdf#optional
     * @see jQuery.rdf#filter
     * @example
     * var rdf = $.rdf()
     *   .prefix('dc', ns.dc)
     *   .prefix('foaf', ns.foaf)
     *   .add('&lt;photo1.jpg> dc:creator &lt;http://www.blogger.com/profile/1109404> .')
     *   .add('&lt;http://www.blogger.com/profile/1109404> foaf:img &lt;photo1.jpg> .')
     *   .add('&lt;photo2.jpg> dc:creator &lt;http://www.blogger.com/profile/1109404> .')
     *   .add('&lt;http://www.blogger.com/profile/1109404> foaf:img &lt;photo2.jpg> .')
     *   .about('&lt;http://www.blogger.com/profile/1109404>');
     */
    about: function (resource, options) {
      return this.where(resource + ' ?property ?value', options);
    },

    /**
     * Creates a new {@link jQuery.rdf} object whose set of bindings include only those that satisfy some arbitrary condition. There are two main ways to call this method: with two arguments in which case the first is a binding to be tested and the second represents a condition on the test, or with one argument which is a function that should return true for acceptable bindings.
     * @param {Function|String} property <p>In the two-argument version, this is the name of a property to be tested against the condition specified in the second argument. In the one-argument version, this is a function in which <code>this</code> is an object whose properties are a set of {@link jQuery.rdf.resource}, {@link jQuery.rdf.literal} or {@link jQuery.rdf.blank} objects and whose arguments are:</p>
     * <dl>
     *   <dt>i</dt>
     *   <dd>The index of the set of bindings amongst the other matches</dd>
     *   <dt>bindings</dt>
     *   <dd>An object representing the bindings (the same as <code>this</code>)</dd>
     *   <dt>triples</dt>
     *   <dd>The {@link jQuery.rdf.triple}s that underly this set of bindings</dd>
     * </dl>
     * @param {RegExp|String} condition In the two-argument version of this function, the condition that the property's must match. If it is a regular expression, the value must match the regular expression. If it is a {@link jQuery.rdf.literal}, the value of the literal must match the property's value. Otherwise, they must be the same resource.
     * @returns {jQuery.rdf} A new {@link jQuery.rdf} object whose {@link jQuery.rdf#parent} is this {@link jQuery.rdf}.
     * @see jQuery.rdf#where
     * @see jQuery.rdf#optional
     * @see jQuery.rdf#about
     * @example
     * var rdf = $.rdf()
     *   .prefix('foaf', 'http://xmlns.com/foaf/0.1/')
     *   .add('_:a foaf:surname "Jones" .')
     *   .add('_:b foaf:surname "Macnamara" .')
     *   .add('_:c foaf:surname "O\'Malley"')
     *   .add('_:d foaf:surname "MacFee"')
     *   .where('?person foaf:surname ?surname')
     *     .filter('surname', /^Ma?c/)
     *       .each(function () { scottish.push(this.surname.value); })
     *     .end()
     *     .filter('surname', /^O'/)
     *       .each(function () { irish.push(this.surname.value); })
     *     .end();
     * @example
     * var rdf = $.rdf()
     *   .prefix('foaf', 'http://xmlns.com/foaf/0.1/')
     *   .add('_:a foaf:surname "Jones" .')
     *   .add('_:b foaf:surname "Macnamara" .')
     *   .add('_:c foaf:surname "O\'Malley"')
     *   .add('_:d foaf:surname "MacFee"')
     *   .where('?person foaf:surname ?surname')
     *   .filter(function () { return this.surname !== "Jones"; })
     */
    filter: function (property, condition) {
      var func, query;
      if (typeof property === 'string') {
        if (condition.constructor === RegExp) {
          /** @ignore func */
          func = function () {
            return condition.test(this[property].value);
          };
        } else {
          func = function () {
            return this[property].type === 'literal' ? this[property].value === condition : this[property] === condition;
          };
        }
      } else {
        func = property;
      }
      query = $.rdf({ parent: this, filter: func });
      this.children.push(query);
      return query;
    },

    /**
     * Filters the variable bindings held by this {@link jQuery.rdf} object down to those listed in the bindings parameter. This mirrors the <a href="http://www.w3.org/TR/rdf-sparql-query/#select">SELECT</a> form in SPARQL.
     * @param {String[]} [bindings] The variables that you're interested in. The returned objects will only contain those variables. If bindings is undefined, you will get all the variable bindings in the returned objects.
     * @returns {Object[]} An array of objects with the properties named by the bindings parameter.
     * @example
     * var filtered = rdf
     *   .where('?photo dc:creator ?creator')
     *   .where('?creator foaf:img ?photo');
     * var selected = rdf.select(['creator']);
     */
    select: function (bindings) {
      var s = [], i, j;
      for (i = 0; i < this.length; i += 1) {
        if (bindings === undefined) {
          s[i] = this[i];
        } else {
          s[i] = {};
          for (j = 0; j < bindings.length; j += 1) {
            s[i][bindings[j]] = this[i][bindings[j]];
          }
        }
      }
      return s;
    },

    /**
     * Provides <a href="http://n2.talis.com/wiki/Bounded_Descriptions_in_RDF#Simple_Concise_Bounded_Description">simple concise bounded descriptions</a> of the resources or bindings that are passed in the argument. This mirrors the <a href="http://www.w3.org/TR/rdf-sparql-query/#describe">DESCRIBE</a> form in SPARQL.
     * @param {(String|jQuery.rdf.resource)[]} bindings An array that can contain strings, {@link jQuery.rdf.resource}s or a mixture of the two. Any strings that begin with a question mark (<code>?</code>) are taken as variable names; each matching resource is described by the function.
     * @returns {jQuery} A {@link jQuery} object that contains {@link jQuery.rdf.triple}s that describe the listed resources.
     * @see jQuery.rdf.databank#describe
     * @example
     * $.rdf.dump($('html').rdf().describe(['<photo1.jpg>']));
     * @example
     * $('html').rdf()
     *   .where('?person foaf:img ?picture')
     *   .describe(['?photo'])
     */
    describe: function (bindings) {
      var i, j, binding, resources = [];
      for (i = 0; i < bindings.length; i += 1) {
        binding = bindings[i];
        if (binding.substring(0, 1) === '?') {
          binding = binding.substring(1);
          for (j = 0; j < this.length; j += 1) {
            resources.push(this[j][binding]);
          }
        } else {
          resources.push(binding);
        }
      }
      return this.databank.describe(resources);
    },

    /**
     * Returns a new {@link jQuery.rdf} object that contains only one set of variable bindings. This is designed to mirror the <a href="http://docs.jquery.com/Traversing/eq#index">jQuery#eq</a> method.
     * @param {Integer} n The index number of the match that should be selected.
     * @returns {jQuery.rdf} A new {@link jQuery.rdf} object with just that match.
     * @example
     * var rdf = $.rdf()
     *   .prefix('foaf', 'http://xmlns.com/foaf/0.1/')
     *   .add('_:a  foaf:name       "Alice" .')
     *   .add('_:a  foaf:homepage   <http://work.example.org/alice/> .')
     *   .add('_:b  foaf:name       "Bob" .')
     *   .add('_:b  foaf:mbox       <mailto:bob@work.example> .')
     *   .where('?x foaf:name ?name')
     *   .eq(1);
     */
    eq: function (n) {
      return this.filter(function (i) {
        return i === n;
      });
    },

    /**
     * Returns a {@link jQuery.rdf} object that includes no filtering (and therefore has no matches) over the {@link jQuery.rdf#databank}.
     * @returns {jQuery.rdf} An empty {@link jQuery.rdf} object.
     * @example
     * $('html').rdf()
     *   .where('?person foaf:family_name "Hacker"')
     *   .where('?person foaf:givenname "Alice"')
     *   .each(...do something with Alice Hacker...)
     *   .reset()
     *   .where('?person foaf:family_name "Jones"')
     *   .where('?person foaf:givenname "Bob"')
     *   .each(...do something with Bob Jones...);
     */
    reset: function () {
      var query = this;
      while (query.parent !== undefined) {
        query = query.parent;
      }
      return query;
    },

    /**
     * Returns the parent {@link jQuery.rdf} object, which is equivalent to undoing the most recent filtering operation (such as {@link jQuery.rdf#where} or {@link jQuery.rdf#filter}). This is designed to mirror the <a href="http://docs.jquery.com/Traversing/end">jQuery#end</a> method.
     * @returns {jQuery.rdf}
     * @example
     * $('html').rdf()
     *   .where('?person foaf:family_name "Hacker"')
     *   .where('?person foaf:givenname "Alice"')
     *   .each(...do something with Alice Hacker...)
     *   .end()
     *   .where('?person foaf:givenname "Bob"')
     *   .each(...do something with Bob Hacker...);
     */
    end: function () {
      return this.parent;
    },

    /**
     * Returns the number of matches in this {@link jQuery.rdf} object (equivalent to {@link jQuery.rdf#length}).
     * @returns {Integer} The number of matches in this {@link jQuery.rdf} object.
     * @see jQuery.rdf#length
     */
    size: function () {
      return this.length;
    },

    /**
     * Gets the triples that form the basis of the variable bindings that are the primary product of {@link jQuery.rdf}. Getting hold of the triples can be useful for understanding the facts that form the basis of the variable bindings.
     * @returns {jQuery} A {@link jQuery} object containing arrays of {@link jQuery.rdf.triple} objects. A {@link jQuery} object is returned so that you can easily iterate over the contents.
     * @example
     * $('html').rdf()
     *   .where('?thing a foaf:Person')
     *   .sources()
     *   .each(function () {
     *     ...do something with the array of triples... 
     *   });
     */
    sources: function () {
      return $($.map(this.matches, function (match) {
        // return an array-of-an-array because arrays automatically get expanded by $.map()
        return [match.triples];
      }));
    },

    /**
     * Dumps the triples that form the basis of the variable bindings that are the primary product of {@link jQuery.rdf} into a format that can be shown to the user or sent to a server.
     * @param {Object} [options] Options that control the formatting of the triples. See {@link jQuery.rdf.dump} for details.
     * @see jQuery.rdf.dump
     */
    dump: function (options) {
      var triples = $.map(this.matches, function (match) {
        return match.triples;
      });
      options = $.extend({ namespaces: this.databank.namespaces, base: this.databank.base }, options || {});
      return $.rdf.dump(triples, options);
    },

    /**
     * Either returns the item specified by the argument or turns the {@link jQuery.rdf} object into an array. This mirrors the <a href="http://docs.jquery.com/Core/get">jQuery#get</a> method.
     * @param {Integer} [num] The number of the item to be returned.
     * @returns {Object[]|Object} Returns either a single Object representing variable bindings or an array of such.
     * @example
     * $('html').rdf()
     *   .where('?person a foaf:Person')
     *   .get(0)
     *   .subject
     *   .value;
     */
    get: function (num) {
      return (num === undefined) ? $.makeArray(this) : this[num];
    },

    /**
     * Iterates over the matches held by the {@link jQuery.rdf} object and performs a function on each of them. This mirrors the <a href="http://docs.jquery.com/Core/each">jQuery#each</a> method.
     * @param {Function} callback A function that is called for each match on the {@link jQuery.rdf} object. Within the function, <code>this</code> is set to the object representing the variable bindings. The function can take up to three parameters:
     * <dl>
     *   <dt>i</dt><dd>The index of the match amongst the other matches.</dd>
     *   <dt>bindings</dt><dd>An object representing the variable bindings for the match, the same as <code>this</code>.</dd>
     *   <dt>triples</dt><dd>An array of {@link jQuery.rdf.triple}s associated with the particular match.</dd>
     * </dl>
     * @returns {jQuery.rdf} The {@link jQuery.rdf} object.
     * @see jQuery.rdf#map
     * @example
     * var rdf = $('html').rdf()
     *   .where('?photo dc:creator ?creator')
     *   .where('?creator foaf:img ?photo')
     *   .each(function () {
     *     photos.push(this.photo.value);
     *   });
     */
    each: function (callback) {
      $.each(this.matches, function (i, match) {
        callback.call(match.bindings, i, match.bindings, match.triples);
      });
      return this;
    },

    /**
     * Iterates over the matches held by the {@link jQuery.rdf} object and creates a new {@link jQuery} object that holds the result of applying the passed function to each one. This mirrors the <a href="http://docs.jquery.com/Traversing/map">jQuery#map</a> method.
     * @param {Function} callback A function that is called for each match on the {@link jQuery.rdf} object. Within the function, <code>this</code> is set to the object representing the variable bindings. The function can take up to three parameters and should return some kind of value:
     * <dl>
     *   <dt>i</dt><dd>The index of the match amongst the other matches.</dd>
     *   <dt>bindings</dt><dd>An object representing the variable bindings for the match, the same as <code>this</code>.</dd>
     *   <dt>triples</dt><dd>An array of {@link jQuery.rdf.triple}s associated with the particular match.</dd>
     * </dl>
     * @returns {jQuery} A jQuery object holding the results of the function for each of the matches on the original {@link jQuery.rdf} object.
     * @example
     * var photos = $('html').rdf()
     *   .where('?photo dc:creator ?creator')
     *   .where('?creator foaf:img ?photo')
     *   .map(function () {
     *     return this.photo.value;
     *   });
     */
    map: function (callback) {
      return $($.map(this.matches, function (match, i) {
        // in the callback, "this" is the bindings, and the arguments are swapped from $.map()
        return callback.call(match.bindings, i, match.bindings, match.triples);
      }));
    },

    /**
     * Returns a {@link jQuery} object that wraps this {@link jQuery.rdf} object.
     * @returns {jQuery}
     */
    jquery: function () {
      return $(this);
    }
  };

  $.rdf.fn.init.prototype = $.rdf.fn;

  $.rdf.gleaners = [];

  /**
   * Dumps the triples passed as the first argument into a format that can be shown to the user or sent to a server.
   * @param {jQuery.rdf.triple[]} triples An array (or {@link jQuery} object) of {@link jQuery.rdf.triple}s.
   * @param {Object} [options] Options that control the format of the dump.
   * @param {String} [options.format='application/json'] The mime type of the format of the dump. The supported formats are:
   * <table>
   *   <tr><th>mime type</th><th>description</th></tr>
   *   <tr>
   *     <td><code>application/json</code></td>
   *     <td>An <a href="http://n2.talis.com/wiki/RDF_JSON_Specification">RDF/JSON</a> object</td>
   *   </tr>
   *   <tr>
   *     <td><code>application/rdf+xml</code></td>
   *     <td>An DOMDocument node holding XML in <a href="http://www.w3.org/TR/rdf-syntax-grammar/">RDF/XML syntax</a></td>
   *   </tr>
   * </table>
   * @param {Object} [options.namespaces={}] A set of namespace bindings used when mapping resource URIs to CURIEs or QNames (particularly in a RDF/XML serialisation).
   * @param {boolean} [options.serialize=false] If true, rather than creating an Object, the function will return a string which is ready to display or send to a server.
   * @returns {Object|String} The alternative representation of the triples.
   */
  $.rdf.dump = function (triples, options) {
    var opts = $.extend({}, $.rdf.dump.defaults, options || {}),
      format = opts.format,
      serialize = opts.serialize,
      dump;
    if (format === 'application/json') {
      dump = createJson(triples, opts);
      return serialize ? $.toJSON(dump) : dump;
    } else if (format === 'application/rdf+xml') {
      dump = createRdfXml(triples, opts);
      if (serialize) {
        if (dump.xml) {
          return dump.xml.replace(/\s+$/,'');
        } else {
          serializer = new XMLSerializer();
          return serializer.serializeToString(dump);
        }
      } else {
        return dump;
      }
    } else {
      throw "Unrecognised dump format: " + format + ". Expected application/json or application/rdf+xml.";
    }
  };

  $.rdf.dump.defaults = {
    format: 'application/json',
    serialize: false,
    namespaces: {}
  }

  /**
   * Gleans RDF triples from the nodes held by the {@link jQuery} object, puts them into a {@link jQuery.rdf.databank} and returns a {@link jQuery.rdf} object that allows you to query and otherwise manipulate them. The mechanism for gleaning RDF triples from the web page depends on the rdfQuery modules that have been included. The core version of rdfQuery doesn't support any gleaners; other versions support a RDFa gleaner, and there are some modules available for common microformats.
   * @methodOf jQuery#
   * @name jQuery#rdf
   * @returns {jQuery.rdf} An empty query over the triples stored within the page.
   * @example $('#content').rdf().databank.dump();
   */
  $.fn.rdf = function () {
    var triples = [];
    if ($(this)[0] && $(this)[0].nodeType === 9) {
      return $(this).children('*').rdf();
    } else if ($(this).length > 0) {
      triples = $(this).map(function (i, elem) {
        return $.map($.rdf.gleaners, function (gleaner) {
          return gleaner.call($(elem));
        });
      });
      return $.rdf({ triples: triples, namespaces: $(this).xmlns() });
    } else {
      return $.rdf();
    }
  };

  $.extend($.expr[':'], {

    about: function (a, i, m) {
      var j = $(a),
        resource = m[3] ? j.safeCurie(m[3]) : null,
        isAbout = false;
      $.each($.rdf.gleaners, function (i, gleaner) {
        isAbout = gleaner.call(j, { about: resource });
        if (isAbout) {
          return null;
        }
      });
      return isAbout;
    },

    type: function (a, i, m) {
      var j = $(a),
        type = m[3] ? j.curie(m[3]) : null,
        isType = false;
      $.each($.rdf.gleaners, function (i, gleaner) {
        if (gleaner.call(j, { type: type })) {
          isType = true;
          return null;
        }
      });
      return isType;
    }

  });


  /**
   * <p>Creates a new jQuery.rdf.databank object. This should be invoked as a method rather than constructed using new; indeed you will not usually want to generate these objects directly, but manipulate them through a {@link jQuery.rdf} object.</p>
   * @class Represents a triplestore, holding a bunch of {@link jQuery.rdf.triple}s.
   * @param {(String|jQuery.rdf.triple)[]} [triples=[]] An array of triples to store in the databank.
   * @param {Object} [options] Initialisation of the databank.
   * @param {Object} [options.namespaces] An object representing a set of namespace bindings used when interpreting the CURIEs in strings representing triples. Rather than passing this in when you construct the {@link jQuery.rdf.databank} instance, you will usually want to use the {@link jQuery.rdf.databank#prefix} method.
   * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the strings representing triples.
   * @returns {jQuery.rdf.databank} The newly-created databank.
   * @see jQuery.rdf
   */
  $.rdf.databank = function (triples, options) {
    return new $.rdf.databank.fn.init(triples, options);
  };

  $.rdf.databank.fn = $.rdf.databank.prototype = {
    init: function (triples, options) {
      var i;
      triples = triples || [];
      options = options || {};
      this.id = databankID();
      if (options.union === undefined) {
        this.queries = {};
        this.tripleStore = {};
        this.objectStore = {};
        this.baseURI = options.base || $.uri.base();
        this.namespaces = $.extend({}, options.namespaces || {});
        for (i = 0; i < triples.length; i += 1) {
          this.add(triples[i]);
        }
      } else {
        this.union = options.union;
      }
      return this;
    },
    
    /**
     * Sets or returns the base URI of the {@link jQuery.rdf.databank}.
     * @param {String|jQuery.uri} [base]
     * @returns A {@link jQuery.uri} if no base URI is specified, otherwise returns this {@link jQuery.rdf.databank} object.
     * @see jQuery.rdf#base
     */
    base: function (base) {
      if (this.union === undefined) {
        if (base === undefined) {
          return this.baseURI;
        } else {
          this.baseURI = base;
          return this;
        }
      } else if (base === undefined) {
        return this.union[0].base();
      } else {
        $.each(this.union, function (i, databank) {
          databank.base(base);
        });
        return this;
      }
    },

    /**
     * Sets or returns a namespace binding on the {@link jQuery.rdf.databank}.
     * @param {String} [prefix]
     * @param {String} [namespace]
     * @returns {Object|jQuery.uri|jQuery.rdf} If no prefix or namespace is specified, returns an object providing all namespace bindings on the {@link jQuery.rdf#databank}. If a prefix is specified without a namespace, returns the {@link jQuery.uri} associated with that prefix. Otherwise returns this {@link jQuery.rdf} object after setting the namespace binding.
     * @see jQuery.rdf#prefix
     */
    prefix: function (prefix, uri) {
      var namespaces = {};
      if (this.union === undefined) {
        if (prefix === undefined) {
          return this.namespaces;
        } else if (uri === undefined) {
          return this.namespaces[prefix];
        } else {
          this.namespaces[prefix] = uri;
          return this;
        }
      } else if (uri === undefined) {
        $.each(this.union, function (i, databank) {
          $.extend(namespaces, databank.prefix());
        });
        if (prefix === undefined) {
          return namespaces;
        } else {
          return namespaces[prefix];
        }
      } else {
        $.each(this.union, function (i, databank) {
          databank.prefix(prefix, uri);
        });
        return this;
      }
    },

    /**
     * Adds a triple to the {@link jQuery.rdf.databank} or another {@link jQuery.rdf.databank} object to create a union.
     * @param {String|jQuery.rdf.triple|jQuery.rdf.databank} triple The triple or {@link jQuery.rdf.databank} object to be added to this one. If the triple is a {@link jQuery.rdf.databank} object, the two databanks are unioned together. If the triple is a string, it's parsed as a {@link jQuery.rdf.triple}.
     * @param {Object} [options]
     * @param {Object} [options.namespaces] An object representing a set of namespace bindings used to interpret CURIEs within the triple. Defaults to the namespace bindings defined on the {@link jQuery.rdf.databank}.
     * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the triple. Defaults to the base URI defined on the {@link jQuery.rdf.databank}.
     * @returns {jQuery.rdf.databank} This {@link jQuery.rdf.databank} object.
     * @see jQuery.rdf#add
     */
    add: function (triple, options) {
      var base = (options && options.base) || this.base(),
        namespaces = $.extend({}, this.prefix(), (options && options.namespaces) || {}),
        databank;
      if (triple === this) {
        return this;
      } else if (triple.tripleStore !== undefined) {
        // merging two databanks
        if (this.union === undefined) {
          databank = $.rdf.databank(undefined, { union: [this, triple] });
          return databank;
        } else {
          this.union.push(triple);
          return this;
        }
      } else {
        if (typeof triple === 'string') {
          triple = $.rdf.triple(triple, { namespaces: namespaces, base: base, source: triple });
        }
        if (this.union === undefined) {
          if (this.tripleStore[triple.subject] === undefined) {
            this.tripleStore[triple.subject] = [];
          }
          if ($.inArray(triple, this.tripleStore[triple.subject]) === -1) {
            this.tripleStore[triple.subject].push(triple);
            if (triple.object.type === 'uri' || triple.object.type === 'bnode') {
              if (this.objectStore[triple.object] === undefined) {
                this.objectStore[triple.object] = [];
              }
              this.objectStore[triple.object].push(triple);
            }
            addToDatabankQueries(this, triple);
          }
        } else {
          $.each(this.union, function (i, databank) {
            databank.add(triple);
          });
        }
        return this;
      }
    },

    /**
     * Removes a triple from the {@link jQuery.rdf.databank}.
     * @param {String|jQuery.rdf.triple} triple The triple to be removed.
     * @param {Object} [options]
     * @param {Object} [options.namespaces] An object representing a set of namespace bindings used to interpret any CURIEs within the triple. Defaults to the namespace bindings defined on the {@link jQuery.rdf.databank}.
     * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the triple. Defaults to the base URI defined on the {@link jQuery.rdf.databank}.
     * @returns {jQuery.rdf.databank} The {@link jQuery.rdf.databank} object itself.
     * @see jQuery.rdf#remove
     */
    remove: function (triple, options) {
      var base = (options && options.base) || this.base(),
        namespaces = $.extend({}, this.prefix(), (options && options.namespaces) || {}),
        striples, otriples,
        databank;
      if (typeof triple === 'string') {
        triple = $.rdf.triple(triple, { namespaces: namespaces, base: base, source: triple });
      }
      striples = this.tripleStore[triple.subject];
      if (striples !== undefined) {
        striples.splice($.inArray(triple, striples), 1);
      }
      if (triple.object.type === 'uri' || triple.object.type === 'bnode') {
        otriples = this.objectStore[triple.object];
        if (otriples !== undefined) {
          otriples.splice($.inArray(triple, otriples), 1);
        }
      }
      removeFromDatabankQueries(this, triple);
      return this;
    },

    /**
     * Creates a new databank containing all the triples in this {@link jQuery.rdf.databank} except those in the {@link jQuery.rdf.databank} passed as the argument.
     * @param {jQuery.rdf.databank} data The other {@link jQuery.rdf.databank}
     * @returns {jQuery.rdf.databank} A new {@link jQuery.rdf.databank} containing the triples in this {@link jQuery.rdf.databank} except for those in the data parameter.
     * @example
     * var old = $('html').rdf().databank;
     * ...some processing occurs...
     * var new = $('html').rdf().databank;
     * var added = new.except(old);
     * var removed = old.except(new);
     */
    except: function (data) {
      var store = data.tripleStore,
        diff = [];
      $.each(this.tripleStore, function (s, ts) {
        var ots = store[s];
        if (ots === undefined) {
          diff = diff.concat(ts);
        } else {
          $.each(ts, function (i, t) {
            if ($.inArray(t, ots) === -1) {
              diff.push(t);
            }
          });
        }
      });
      return $.rdf.databank(diff);
    },

    /**
     * Provides a {@link jQuery} object containing the triples held in this {@link jQuery.rdf.databank}.
     * @returns {jQuery} A {@link jQuery} object containing {@link jQuery.rdf.triple} objects.
     */
    triples: function () {
      var triples = [];
      if (this.union === undefined) {
        $.each(this.tripleStore, function (s, t) {
          triples = triples.concat(t);
        });
      } else {
        $.each(this.union, function (i, databank) {
          triples = triples.concat(databank.triples().get());
        });
        triples = $.unique(triples);
      }
      return $(triples);
    },

    /**
     * Tells you how many triples the databank contains.
     * @returns {Integer} The number of triples in the {@link jQuery.rdf.databank}.
     * @example $('html').rdf().databank.size();
     */
    size: function () {
      return this.triples().length;
    },

    /**
     * Provides <a href="http://n2.talis.com/wiki/Bounded_Descriptions_in_RDF#Simple_Concise_Bounded_Description">simple concise bounded descriptions</a> of the resources that are passed in the argument. This mirrors the <a href="http://www.w3.org/TR/rdf-sparql-query/#describe">DESCRIBE</a> form in SPARQL.
     * @param {(String|jQuery.rdf.resource)[]} resources An array that can contain strings, {@link jQuery.rdf.resource}s or a mixture of the two.
     * @returns {jQuery} A {@link jQuery} object holding the {@link jQuery.rdf.triple}s that describe the listed resources.
     * @see jQuery.rdf#describe
     */
    describe: function (resources) {
      var i, r, t, rhash = {}, triples = [];
      while (resources.length > 0) {
        r = resources.pop();
        if (rhash[r] === undefined) {
          if (r.value === undefined) {
            r = $.rdf.resource(r);
          }
          if (this.tripleStore[r] !== undefined) {
            for (i = 0; i < this.tripleStore[r].length; i += 1) {
              t = this.tripleStore[r][i];
              triples.push(t);
              if (t.object.type === 'bnode') {
                resources.push(t.object);
              }
            }
          }
          if (this.objectStore[r] !== undefined) {
            for (i = 0; i < this.objectStore[r].length; i += 1) {
              t = this.objectStore[r][i];
              triples.push(t);
              if (t.subject.type === 'bnode') {
                resources.push(t.subject);
              }
            }
          }
          rhash[r] = true;
        }
      }
      return $.unique(triples);
    },

    /**
     * Dumps the triples in the databank into a format that can be shown to the user or sent to a server.
     * @param {Object} [options] Options that control the formatting of the triples. See {@link jQuery.rdf.dump} for details.
     * @returns {Object|Node|String}
     * @see jQuery.rdf.dump
     */
    dump: function (options) {
      options = $.extend({ namespaces: this.namespaces, base: this.base }, options || {});
      return $.rdf.dump(this.triples(), options);
    },

    /**
     * Loads some data into the databank.
     * @param {Node|Object} data If the data is a node, it's interpreted to be an <a href="http://www.w3.org/TR/rdf-syntax-grammar/">RDF/XML syntax</a> document and will be parsed as such. Otherwise, it's taken to be a <a href="http://n2.talis.com/wiki/RDF_JSON_Specification">RDF/JSON</a> object. The data cannot be a string; it must be parsed before it is passed to this function.
     * @returns {jQuery.rdf.databank} The {@link jQuery.rdf.databank} itself.
     * @see jQuery.rdf#load
     */
    load: function (data) {
      var i, triples;
      if (data.ownerDocument !== undefined) {
        triples = parseRdfXml(data);
      } else {
        triples = parseJson(data);
      }
      for (i = 0; i < triples.length; i += 1) {
        this.add(triples[i]);
      }
      return this;
    },

    /**
     * Provides a string representation of the databank which simply specifies how many triples it contains.
     * @returns {String}
     */
    toString: function () {
      return '[Databank with ' + this.size() + ' triples]';
    }
  };

  $.rdf.databank.fn.init.prototype = $.rdf.databank.fn;

  /**
   * <p>Creates a new jQuery.rdf.pattern object. This should be invoked as a method rather than constructed using new; indeed you will not usually want to generate these objects directly, since they are automatically created from strings where necessary, such as by {@link jQuery.rdf#where}.</p>
   * @class Represents a pattern that may or may not match a given {@link jQuery.rdf.triple}.
   * @param {String|jQuery.rdf.resource|jQuery.rdf.blank} subject The subject pattern, or a single string that defines the entire pattern. If the subject is specified as a string, it can be a fixed resource (<code>&lt;<var>uri</var>&gt;</code> or <code><var>curie</var></code>), a blank node (<code>_:<var>id</var></code>) or a variable placeholder (<code>?<var>name</var></code>).
   * @param {String|jQuery.rdf.resource} [property] The property pattern. If the property is specified as a string, it can be a fixed resource (<code>&lt;<var>uri</var>&gt;</code> or <code><var>curie</var></code>) or a variable placeholder (<code>?<var>name</var></code>).
   * @param {String|jQuery.rdf.resource|jQuery.rdf.blank|jQuery.rdf.literal} [value] The value pattern. If the property is specified as a string, it can be a fixed resource (<code>&lt;<var>uri</var>&gt;</code> or <code><var>curie</var></code>), a blank node (<code>_:<var>id</var></code>), a literal (<code>"<var>value</var>"</code>) or a variable placeholder (<code>?<var>name</var></code>).
   * @param {Object} [options] Initialisation of the pattern.
   * @param {Object} [options.namespaces] An object representing a set of namespace bindings used when interpreting the CURIEs in the subject, property and object.
   * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the subject, property and object.
   * @param {boolean} [options.optional]
   * @returns {jQuery.rdf.pattern} The newly-created pattern.
   * @throws {String} Errors if any of the strings are not in a recognised format.
   * @example pattern = $.rdf.pattern('?person', $.rdf.type, 'foaf:Person', { namespaces: { foaf: "http://xmlns.com/foaf/0.1/" }});
   * @example 
   * pattern = $.rdf.pattern('?person a foaf:Person', { 
   *   namespaces: { foaf: "http://xmlns.com/foaf/0.1/" }, 
   *   optional: true 
   * });
   * @see jQuery.rdf#where
   * @see jQuery.rdf.resource
   * @see jQuery.rdf.blank
   * @see jQuery.rdf.literal
   */
  $.rdf.pattern = function (subject, property, object, options) {
    var pattern, m, optional;
    // using a two-argument version; first argument is a Turtle statement string
    if (object === undefined) {
      options = property || {};
      m = $.trim(subject).match(tripleRegex);
      if (m.length === 3 || (m.length === 4 && m[3] === '.')) {
        subject = m[0];
        property = m[1];
        object = m[2];
      } else {
        throw "Bad Pattern: Couldn't parse string " + subject;
      }
      optional = (options.optional === undefined) ? $.rdf.pattern.defaults.optional : options.optional;
    }
    if (memPattern[subject] && 
        memPattern[subject][property] && 
        memPattern[subject][property][object] && 
        memPattern[subject][property][object][optional]) {
      return memPattern[subject][property][object][optional];
    }
    pattern = new $.rdf.pattern.fn.init(subject, property, object, options);
    if (memPattern[pattern.subject] &&
        memPattern[pattern.subject][pattern.property] &&
        memPattern[pattern.subject][pattern.property][pattern.object] &&
        memPattern[pattern.subject][pattern.property][pattern.object][pattern.optional]) {
      return memPattern[pattern.subject][pattern.property][pattern.object][pattern.optional];
    } else {
      if (memPattern[pattern.subject] === undefined) {
        memPattern[pattern.subject] = {};
      }
      if (memPattern[pattern.subject][pattern.property] === undefined) {
        memPattern[pattern.subject][pattern.property] = {};
      }
      if (memPattern[pattern.subject][pattern.property][pattern.object] === undefined) {
        memPattern[pattern.subject][pattern.property][pattern.object] = {};
      }
      memPattern[pattern.subject][pattern.property][pattern.object][pattern.optional] = pattern;
      return pattern;
    }
  };

  $.rdf.pattern.fn = $.rdf.pattern.prototype = {
    init: function (s, p, o, options) {
      var opts = $.extend({}, $.rdf.pattern.defaults, options);
      /**
       * The placeholder for the subject of triples matching against this pattern.
       * @type String|jQuery.rdf.resource|jQuery.rdf.blank
       */
      this.subject = s.toString().substring(0, 1) === '?' ? s : subject(s, opts);
      /**
       * The placeholder for the property of triples matching against this pattern.
       * @type String|jQuery.rdf.resource
       */
      this.property = p.toString().substring(0, 1) === '?' ? p : property(p, opts);
      /**
       * The placeholder for the object of triples matching against this pattern.
       * @type String|jQuery.rdf.resource|jQuery.rdf.blank|jQuery.rdf.literal
       */
      this.object = o.toString().substring(0, 1) === '?' ? o : object(o, opts);
      /**
       * Whether the pattern should only optionally match against the triple
       * @type boolean
       */
      this.optional = opts.optional;
      return this;
    },

    /**
     * Creates a new {@link jQuery.rdf.pattern} with any variable placeholders within this one's subject, property or object filled in with values from the bindings passed as the argument.
     * @param {Object} bindings An object holding the variable bindings to be used to replace any placeholders in the pattern. These bindings are of the type held by the {@link jQuery.rdf} object.
     * @returns {jQuery.rdf.pattern} A new {@link jQuery.rdf.pattern} object.
     * @example
     * pattern = $.rdf.pattern('?thing a ?class');
     * // pattern2 matches all triples that indicate the classes of this page. 
     * pattern2 = pattern.fill({ thing: $.rdf.resource('<>') });
     */
    fill: function (bindings) {
      var s = this.subject,
        p = this.property,
        o = this.object;
      if (typeof s === 'string' && bindings[s.substring(1)]) {
        s = bindings[s.substring(1)];
      }
      if (typeof p === 'string' && bindings[p.substring(1)]) {
        p = bindings[p.substring(1)];
      }
      if (typeof o === 'string' && bindings[o.substring(1)]) {
        o = bindings[o.substring(1)];
      }
      return $.rdf.pattern(s, p, o, { optional: this.optional });
    },

    /**
     * Creates a new Object holding variable bindings by matching the passed triple against this pattern.
     * @param {jQuery.rdf.triple} triple A {@link jQuery.rdf.triple} for this pattern to match against.
     * @returns {null|Object} An object containing the bindings of variables (as specified in this pattern) to values (as specified in the triple), or <code>null</code> if the triple doesn't match the pattern.
     * pattern = $.rdf.pattern('?thing a ?class');
     * bindings = pattern.exec($.rdf.triple('<> a foaf:Person', { namespaces: ns }));
     * thing = bindings.thing; // the resource for this page
     * class = bindings.class; // a resource for foaf:Person
     */
    exec: function (triple) {
      var binding = {};
      binding = testResource(triple.subject, this.subject, binding);
      if (binding === null) {
        return null;
      }
      binding = testResource(triple.property, this.property, binding);
      if (binding === null) {
        return null;
      }
      binding = testResource(triple.object, this.object, binding);
      return binding;
    },

    /**
     * Tests whether this pattern has any variable placeholders in it or not.
     * @returns {boolean} True if the pattern doesn't contain any variable placeholders.
     * @example
     * $.rdf.pattern('?thing a ?class').isFixed(); // false
     * $.rdf.pattern('<> a foaf:Person', { namespaces: ns }).isFixed(); // true
     */
    isFixed: function () {
      return typeof this.subject !== 'string' &&
        typeof this.property !== 'string' &&
        typeof this.object !== 'string';
    },

    /**
     * Creates a new triple based on the bindings passed to the pattern, if possible.
     * @param {Object} bindings An object holding the variable bindings to be used to replace any placeholders in the pattern. These bindings are of the type held by the {@link jQuery.rdf} object.
     * @returns {null|jQuery.rdf.triple} A new {@link jQuery.rdf.triple} object, or null if not all the variable placeholders in the pattern are specified in the bindings. The {@link jQuery.rdf.triple#source} of the generated triple is set to the string value of this pattern.
     * @example
     * pattern = $.rdf.pattern('?thing a ?class');
     * // triple is a new triple '<> a foaf:Person'
     * triple = pattern.triple({ 
     *   thing: $.rdf.resource('<>'),
     *   class: $.rdf.resource('foaf:Person', { namespaces: ns }) 
     * });
     */
    triple: function (bindings) {
      var t = this;
      if (!this.isFixed()) {
        t = this.fill(bindings);
      }
      if (t.isFixed()) {
        return $.rdf.triple(t.subject, t.property, t.object, { source: this.toString() });
      } else {
        return null;
      }
    },

    /**
     * Returns a string representation of the pattern by concatenating the subject, property and object.
     * @returns {String}
     */
    toString: function () {
      return this.subject + ' ' + this.property + ' ' + this.object;
    }
  };

  $.rdf.pattern.fn.init.prototype = $.rdf.pattern.fn;

  $.rdf.pattern.defaults = {
    base: $.uri.base(),
    namespaces: {},
    optional: false
  };

  /**
   * <p>Creates a new jQuery.rdf.triple object. This should be invoked as a method rather than constructed using new; indeed you will not usually want to generate these objects directly, since they are automatically created from strings where necessary, such as by {@link jQuery.rdf#add}.</p>
   * @class Represents an RDF triple.
   * @param {String|jQuery.rdf.resource|jQuery.rdf.blank} subject The subject of the triple, or a single string that defines the entire triple. If the subject is specified as a string, it can be a fixed resource (<code>&lt;<var>uri</var>&gt;</code> or <code><var>curie</var></code>) or a blank node (<code>_:<var>id</var></code>).
   * @param {String|jQuery.rdf.resource} [property] The property pattern. If the property is specified as a string, it must be a fixed resource (<code>&lt;<var>uri</var>&gt;</code> or <code><var>curie</var></code>).
   * @param {String|jQuery.rdf.resource|jQuery.rdf.blank|jQuery.rdf.literal} [value] The value pattern. If the property is specified as a string, it can be a fixed resource (<code>&lt;<var>uri</var>&gt;</code> or <code><var>curie</var></code>), a blank node (<code>_:<var>id</var></code>), or a literal (<code>"<var>value</var>"</code>).
   * @param {Object} [options] Initialisation of the triple.
   * @param {Object} [options.namespaces] An object representing a set of namespace bindings used when interpreting the CURIEs in the subject, property and object.
   * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the subject, property and object.
   * @returns {jQuery.rdf.triple} The newly-created triple.
   * @throws {String} Errors if any of the strings are not in a recognised format.
   * @example pattern = $.rdf.triple('<>', $.rdf.type, 'foaf:Person', { namespaces: { foaf: "http://xmlns.com/foaf/0.1/" }});
   * @example 
   * pattern = $.rdf.triple('<> a foaf:Person', { 
   *   namespaces: { foaf: "http://xmlns.com/foaf/0.1/" }
   * });
   * @see jQuery.rdf#add
   * @see jQuery.rdf.resource
   * @see jQuery.rdf.blank
   * @see jQuery.rdf.literal
   */
  $.rdf.triple = function (subject, property, object, options) {
    var triple, graph, m;
    // using a two-argument version; first argument is a Turtle statement string
    if (object === undefined) {
      options = property;
      m = $.trim(subject).match(tripleRegex);
      if (m.length === 3 || (m.length === 4 && m[3] === '.')) {
        subject = m[0];
        property = m[1];
        object = m[2];
      } else {
        throw "Bad Triple: Couldn't parse string " + subject;
      }
    }
    graph = (options && options.graph) || '';
    if (memTriple[graph] &&
        memTriple[graph][subject] &&
        memTriple[graph][subject][property] &&
        memTriple[graph][subject][property][object]) {
      return memTriple[graph][subject][property][object];
    }
    triple = new $.rdf.triple.fn.init(subject, property, object, options);
    graph = triple.graph || '';
    if (memTriple[graph] &&
        memTriple[graph][triple.subject] &&
        memTriple[graph][triple.subject][triple.property] &&
        memTriple[graph][triple.subject][triple.property][triple.object]) {
      return memTriple[graph][triple.subject][triple.property][triple.object];
    } else {
      if (memTriple[graph] === undefined) {
        memTriple[graph] = {};
      }
      if (memTriple[graph][triple.subject] === undefined) {
        memTriple[graph][triple.subject] = {};
      }
      if (memTriple[graph][triple.subject][triple.property] === undefined) {
        memTriple[graph][triple.subject][triple.property] = {};
      }
      memTriple[graph][triple.subject][triple.property][triple.object] = triple;
      return triple;
    }
  };

  $.rdf.triple.fn = $.rdf.triple.prototype = {
    init: function (s, p, o, options) {
      var opts;
      opts = $.extend({}, $.rdf.triple.defaults, options);
      /**
       * The subject of the triple.
       * @type jQuery.rdf.resource|jQuery.rdf.blank
       */
      this.subject = subject(s, opts);
      /**
       * The property of the triple.
       * @type jQuery.rdf.resource
       */
      this.property = property(p, opts);
      /**
       * The object of the triple.
       * @type jQuery.rdf.resource|jQuery.rdf.blank|jQuery.rdf.literal
       */
      this.object = object(o, opts);
      /**
       * (Experimental) The named graph the triple belongs to.
       * @type jQuery.rdf.resource|jQuery.rdf.blank
       */
      this.graph = opts.graph === undefined ? undefined : subject(opts.graph, opts);
      /**
       * The source of the triple, which might be a node within the page (if the RDF is generated from the page) or a string holding the pattern that generated the triple.
       */
      this.source = opts.source;
      return this;
    },

    /**
     * Always returns true for triples.
     * @see jQuery.rdf.pattern#isFixed
     */
    isFixed: function () {
      return true;
    },

    /**
     * Always returns this triple.
     * @see jQuery.rdf.pattern#triple
     */
    triple: function (bindings) {
      return this;
    },

    /**
     * Returns a <a href="http://n2.talis.com/wiki/RDF_JSON_Specification">RDF/JSON</a> representation of this triple.
     * @returns {Object}
     */
    dump: function () {
      var e = {},
        s = this.subject.value.toString(),
        p = this.property.value.toString();
      e[s] = {};
      e[s][p] = this.object.dump();
      return e;
    },

    /**
     * Returns a string representing this triple in Turtle format.
     * @returns {String}
     */
    toString: function () {
      return this.subject + ' ' + this.property + ' ' + this.object + ' .';
    }
  };

  $.rdf.triple.fn.init.prototype = $.rdf.triple.fn;

  $.rdf.triple.defaults = {
    base: $.uri.base(),
    source: [document],
    namespaces: {}
  };

  /**
   * <p>Creates a new jQuery.rdf.resource object. This should be invoked as a method rather than constructed using new; indeed you will not usually want to generate these objects directly, since they are automatically created from strings where necessary, such as by {@link jQuery.rdf#add}.</p>
   * @class Represents an RDF resource.
   * @param {String|jQuery.uri} value The value of the resource. If it's a string it must be in the format <code>&lt;<var>uri</var>&gt;</code> or <code><var>curie</var></code>.
   * @param {Object} [options] Initialisation of the resource.
   * @param {Object} [options.namespaces] An object representing a set of namespace bindings used when interpreting the CURIE specifying the resource.
   * @param {String|jQuery.uri} [options.base] The base URI used to interpret any relative URIs used within the URI specifying the resource.
   * @returns {jQuery.rdf.resource} The newly-created resource.
   * @throws {String} Errors if the string is not in a recognised format.
   * @example thisPage = $.rdf.resource('<>');
   * @example foaf.Person = $.rdf.resource('foaf:Person', { namespaces: ns });
   * @see jQuery.rdf.pattern
   * @see jQuery.rdf.triple
   * @see jQuery.rdf.blank
   * @see jQuery.rdf.literal
   */
  $.rdf.resource = function (value, options) {
    var resource;
    if (memResource[value]) {
      return memResource[value];
    }
    resource = new $.rdf.resource.fn.init(value, options);
    if (memResource[resource]) {
      return memResource[resource];
    } else {
      memResource[resource] = resource;
      return resource;
    }
  };

  $.rdf.resource.fn = $.rdf.resource.prototype = {
    /**
     * Always fixed to 'uri' for resources.
     * @type String
     */
    type: 'uri',
    /**
     * The URI for the resource.
     * @type jQuery.rdf.uri
     */
    value: undefined,

    init: function (value, options) {
      var m, prefix, uri, opts;
      if (typeof value === 'string') {
        m = uriRegex.exec(value);
        opts = $.extend({}, $.rdf.resource.defaults, options);
        if (m !== null) {
          this.value = $.uri.resolve(m[1].replace(/\\>/g, '>'), opts.base);
        } else if (value.substring(0, 1) === ':') {
          uri = opts.namespaces[''];
          if (uri === undefined) {
            throw "Malformed Resource: No namespace binding for default namespace in " + value;
          } else {
            this.value = $.uri.resolve(uri + value.substring(1));
          }
        } else if (value.substring(value.length - 1) === ':') {
          prefix = value.substring(0, value.length - 1);
          uri = opts.namespaces[prefix];
          if (uri === undefined) {
            throw "Malformed Resource: No namespace binding for prefix " + prefix + " in " + value;
          } else {
            this.value = $.uri.resolve(uri);
          }
        } else {
          try {
            this.value = $.curie(value, { namespaces: opts.namespaces });
          } catch (e) {
            throw "Malformed Resource: Bad format for resource " + e;
          }
        }
      } else {
        this.value = value;
      }
      return this;
    }, // end init

    /**
     * Returns a <a href="http://n2.talis.com/wiki/RDF_JSON_Specification">RDF/JSON</a> representation of this triple.
     * @returns {Object}
     */
    dump: function () {
      return {
        type: 'uri',
        value: this.value.toString()
      };
    },

    /**
     * Returns a string representing this resource in Turtle format.
     * @returns {String}
     */
    toString: function () {
      return '<' + this.value + '>';
    }
  };

  $.rdf.resource.fn.init.prototype = $.rdf.resource.fn;

  $.rdf.resource.defaults = {
    base: $.uri.base(),
    namespaces: {}
  };

  /**
   * A {@link jQuery.rdf.resource} for rdf:type
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.type = $.rdf.resource('<' + rdfNs + 'type>');
  /**
   * A {@link jQuery.rdf.resource} for rdfs:label
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.label = $.rdf.resource('<' + rdfsNs + 'label>');
  /**
   * A {@link jQuery.rdf.resource} for rdf:first
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.first = $.rdf.resource('<' + rdfNs + 'first>');
  /**
   * A {@link jQuery.rdf.resource} for rdf:rest
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.rest = $.rdf.resource('<' + rdfNs + 'rest>');
  /**
   * A {@link jQuery.rdf.resource} for rdf:nil
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.nil = $.rdf.resource('<' + rdfNs + 'nil>');
  /**
   * A {@link jQuery.rdf.resource} for rdf:subject
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.subject = $.rdf.resource('<' + rdfNs + 'subject>');
  /**
   * A {@link jQuery.rdf.resource} for rdf:property
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.property = $.rdf.resource('<' + rdfNs + 'property>');
  /**
   * A {@link jQuery.rdf.resource} for rdf:object
   * @constant
   * @type jQuery.rdf.resource
   */
  $.rdf.object = $.rdf.resource('<' + rdfNs + 'object>');

  /**
   * <p>Creates a new jQuery.rdf.blank object. This should be invoked as a method rather than constructed using new; indeed you will not usually want to generate these objects directly, since they are automatically created from strings where necessary, such as by {@link jQuery.rdf#add}.</p>
   * @class Represents an RDF blank node.
   * @param {String} value A representation of the blank node in the format <code>_:<var>id</var></code> or <code>[]</code> (which automatically creates a new blank node with a unique ID).
   * @returns {jQuery.rdf.blank} The newly-created blank node.
   * @throws {String} Errors if the string is not in a recognised format.
   * @example newBlank = $.rdf.blank('[]');
   * @example identifiedBlank = $.rdf.blank('_:fred');
   * @see jQuery.rdf.pattern
   * @see jQuery.rdf.triple
   * @see jQuery.rdf.resource
   * @see jQuery.rdf.literal
   */
  $.rdf.blank = function (value) {
    var blank;
    if (memBlank[value]) {
      return memBlank[value];
    }
    blank = new $.rdf.blank.fn.init(value);
    if (memBlank[blank]) {
      return memBlank[blank];
    } else {
      memBlank[blank] = blank;
      return blank;
    }
  };

  $.rdf.blank.fn = $.rdf.blank.prototype = {
    /**
     * Always fixed to 'bnode' for blank nodes.
     * @type String
     */
    type: 'bnode',
    /**
     * The value of the blank node in the format <code>_:<var>id</var></code>
     * @type String
     */
    value: undefined,
    /**
     * The id of the blank node.
     * @type String
     */
    id: undefined,

    init: function (value) {
      if (value === '[]') {
        this.id = blankNodeID();
        this.value = '_:' + this.id;
      } else if (value.substring(0, 2) === '_:') {
        this.id = value.substring(2);
        this.value = value;
      } else {
        throw "Malformed Blank Node: " + value + " is not a legal format for a blank node";
      }
      return this;
    },

    /**
     * Returns a <a href="http://n2.talis.com/wiki/RDF_JSON_Specification">RDF/JSON</a> representation of this blank node.
     * @returns {Object}
     */
    dump: function () {
      return {
        type: 'bnode',
        value: this.value
      };
    },

    /**
     * Returns the value this blank node.
     * @returns {String}
     */
    toString: function () {
      return this.value;
    }
  };

  $.rdf.blank.fn.init.prototype = $.rdf.blank.fn;

  /**
   * <p>Creates a new jQuery.rdf.literal object. This should be invoked as a method rather than constructed using new; indeed you will not usually want to generate these objects directly, since they are automatically created from strings where necessary, such as by {@link jQuery.rdf#add}.</p>
   * @class Represents an RDF literal.
   * @param {String|boolean|Number} value Either the value of the literal or a string representation of it. If the datatype or lang options are specified, the value is taken as given. Otherwise, if it's a Javascript boolean or numeric value, it is interpreted as a value with a xsd:boolean or xsd:double datatype. In all other cases it's interpreted as a literal as defined in <a href="http://www.w3.org/TeamSubmission/turtle/#literal">Turtle syntax</a>.
   * @param {Object} [options] Initialisation options for the literal.
   * @param {String} [options.datatype] The datatype for the literal. This should be a safe CURIE; in other words, it can be in the format <code><var>uri</var></code> or <code>[<var>curie</var>]</code>. Must not be specified if options.lang is also specified.
   * @param {String} [options.lang] The language for the literal. Must not be specified if options.datatype is also specified.
   * @param {Object} [options.namespaces] An object representing a set of namespace bindings used when interpreting a CURIE in the datatype.
   * @param {String|jQuery.uri} [options.base] The base URI used to interpret a relative URI in the datatype.
   * @returns {jQuery.rdf.literal} The newly-created literal.
   * @throws {String} Errors if the string is not in a recognised format or if both options.datatype and options.lang are specified.
   * @example trueLiteral = $.rdf.literal(true);
   * @example numericLiteral = $.rdf.literal(5);
   * @example dateLiteral = $.rdf.literal('"2009-07-13"^^xsd:date', { namespaces: ns });
   * @see jQuery.rdf.pattern
   * @see jQuery.rdf.triple
   * @see jQuery.rdf.resource
   * @see jQuery.rdf.blank
   */
  $.rdf.literal = function (value, options) {
    var literal;
    if (memLiteral[value]) {
      return memLiteral[value];
    }
    literal = new $.rdf.literal.fn.init(value, options);
    if (memLiteral[literal]) {
      return memLiteral[literal];
    } else {
      memLiteral[literal] = literal;
      return literal;
    }
  };

  $.rdf.literal.fn = $.rdf.literal.prototype = {
    /**
     * Always fixed to 'literal' for literals.
     * @type String
     */
    type: 'literal',
    /**
     * The value of the literal as a string.
     * @type String
     */
    value: undefined,
    /**
     * The language of the literal, if it has one; otherwise undefined.
     * @type String
     */
    lang: undefined,
    /**
     * The datatype of the literal, if it has one; otherwise undefined.
     * @type jQuery.uri
     */
    datatype: undefined,

    init: function (value, options) {
      var
        m, datatype,
        opts = $.extend({}, $.rdf.literal.defaults, options);
      if (opts.lang !== undefined && opts.datatype !== undefined) {
        throw "Malformed Literal: Cannot define both a language and a datatype for a literal (" + value + ")";
      }
      if (opts.datatype !== undefined) {
        datatype = $.safeCurie(opts.datatype, { namespaces: opts.namespaces });
        $.extend(this, $.typedValue(value.toString(), datatype));
      } else if (opts.lang !== undefined) {
        this.value = value.toString();
        this.lang = opts.lang;
      } else if (typeof value === 'boolean') {
        $.extend(this, $.typedValue(value.toString(), xsdNs + 'boolean'));
      } else if (typeof value === 'number') {
        $.extend(this, $.typedValue(value.toString(), xsdNs + 'double'));
      } else if (value === 'true' || value === 'false') {
        $.extend(this, $.typedValue(value, xsdNs + 'boolean'));
      } else if ($.typedValue.valid(value, xsdNs + 'integer')) {
        $.extend(this, $.typedValue(value, xsdNs + 'integer'));
      } else if ($.typedValue.valid(value, xsdNs + 'decimal')) {
        $.extend(this, $.typedValue(value, xsdNs + 'decimal'));
      } else if ($.typedValue.valid(value, xsdNs + 'double') &&
                 !/^\s*([\-\+]?INF|NaN)\s*$/.test(value)) {  // INF, -INF and NaN aren't valid literals in Turtle
        $.extend(this, $.typedValue(value, xsdNs + 'double'));
      } else {
        m = literalRegex.exec(value);
        if (m !== null) {
          this.value = (m[2] || m[4]).replace(/\\"/g, '"');
          if (m[9]) {
            datatype = $.rdf.resource(m[9], opts);
            $.extend(this, $.typedValue(this.value, datatype.value));
          } else if (m[7]) {
            this.lang = m[7];
          }
        } else {
          throw "Malformed Literal: Couldn't recognise the value " + value;
        }
      }
      return this;
    }, // end init

    /**
     * Returns a <a href="http://n2.talis.com/wiki/RDF_JSON_Specification">RDF/JSON</a> representation of this blank node.
     * @returns {Object}
     */
    dump: function () {
      var e = {
        type: 'literal',
        value: this.value.toString()
      };
      if (this.lang !== undefined) {
        e.lang = this.lang;
      } else if (this.datatype !== undefined) {
        e.datatype = this.datatype.toString();
      }
      return e;
    },
    
    /**
     * Returns a string representing this resource in <a href="http://www.w3.org/TeamSubmission/turtle/#literal">Turtle format</a>.
     * @returns {String}
     */
    toString: function () {
      var val = '"' + this.value + '"';
      if (this.lang !== undefined) {
        val += '@' + this.lang;
      } else if (this.datatype !== undefined) {
        val += '^^<' + this.datatype + '>';
      }
      return val;
    }
  };

  $.rdf.literal.fn.init.prototype = $.rdf.literal.fn;

  $.rdf.literal.defaults = {
    base: $.uri.base(),
    namespaces: {},
    datatype: undefined,
    lang: undefined
  };

})(jQuery);
