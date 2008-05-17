/**
 * jQuery.query - Query String Modification and Creation for jQuery
 * Written in 2007 by Blair Mitchelmore (blair DOT mitchelmore AT gmail DOT com)
 * Licensed under the WTFPL (http://sam.zoy.org/wtfpl/).
 * Date: 2008/02/08
 *
 * @author Blair Mitchelmore
 * @version 1.2.3
 *
 **/
new function(settings) { 
  // Various Settings
  var $separator = settings.separator || '&';
  var $spaces = settings.spaces === false ? false : true;
  var $suffix = settings.suffix === false ? '' : '[]';
  var $hash = settings.hash === true ? true : false;
  var $prefix = settings.prefix === false ? false : true;
  
  jQuery.query = new function() {
    var queryObject = function(a) {
      var self = this;
      self.keys = {};
      
      if (a.queryObject) {
        jQuery.each(a.keys, function(key, val) {
          self.destructiveSet(key, val);
        });
      } else {
        jQuery.each(arguments, function() {
          var q = "" + this;
          q = q.replace(/^[?#]/,''); // remove any leading ? || #
          q = q.replace(/[;&]$/,''); // remove any trailing & || ;
          if ($spaces) q = q.replace(/[+]/g,' '); // replace +'s with spaces

          jQuery.each(q.split(/[&;]/), function(){
            var key = this.split('=')[0];
            var val = this.split('=')[1];
            var temp, hashKey = null, type = null; 
  
            if (!key) return;
          
            if (/^-?[0-9]+\.[0-9]+$/.test(val)) // simple float regex
              val = parseFloat(val);
            else if (/^-?[0-9]+$/.test(val)) // simple int regex
              val = parseInt(val, 10);
              
            if (/\[([^\] ]+)\]$/.test(key)) // hash syntax
              type = Object, hashkey = key.replace(/^.+\[([^\] ]+)\]$/,"$1"), key = key.replace(/\[([^\] ]+)\]$/,"");
            else if (/\[\]$/.test(key)) // array syntax
              type = Array, key = key.replace(/\[\]$/,"");
          
            val = (!val && val !== 0) ? true : val;
          
            if (!type && self.has(key)) 
              type = Array, self.destructiveSet(key, self.has(key, Array) ? self.keys[key] : [self.keys[key]]);
            
            if (val !== false && val !== true)
              val = decodeURIComponent(val);
  	  
            if (!type)
              self.destructiveSet(key, val);
            else
              if (type == Object) 
                temp = self.keys[key] || {}, temp[hashkey] = val, self.destructiveSet(key, temp);
              else if (type == Array)
                temp = self.keys[key] || [], temp.push(val), self.destructiveSet(key, temp);
          });
        });
      }
      return self;
    };
    
    queryObject.prototype = {
      queryObject: true,
      has: function(key, type) {
        var keys = this.keys;
        return !!type ? keys[key] != undefined && keys[key] !== null && keys[key].constructor == type : keys[key] != undefined && keys[key] !== null;
      },
      get: function(key) {
        var value = (key == undefined) ? this.keys : this.keys[key];
        if (!value) 
          return '';
        else if (value.constructor == Array)
          return value.slice(0);
        else if (value.constructor == Object)
          return jQuery.extend({}, value);
        else
          return value;
      },
      destructiveSet: function(key, val) {
        if (val == undefined || val === null)
          this.destructiveRemove(key);
        else
          this.keys[key] = val;
        return this;
      },
      set: function(key, val) {
        return this.copy().destructiveSet(key, val);
      },
      destructiveRemove: function(key) {
        if (typeof this.keys[key] != 'undefined') 
          delete this.keys[key];
        return this;
      },
      remove: function(key) {
        return this.copy().destructiveRemove(key);
      },
      destructiveEmpty: function() {
        var self = this;
        jQuery.each(self.keys, function(key, value) {
          delete self.keys[key];
        });
        return self;
      },
      empty: function(destructive) {
        return this.copy().destructiveEmpty();
      },
      copy: function() {
        return new queryObject(this);
      },
      toString: function() {
        var i = 0, queryString = [], self = this, addFields = function(o, key, value) {
          o.push(key);
          if (value !== true) {
            o.push("=");
            o.push(encodeURIComponent(value));
          }
        };
        jQuery.each(this.keys, function(key, value) {
          var o = [];
          if (value !== false) {
            if (i++ == 0)
              o.push($prefix ? $hash ? "#" : "?" : "");
            if (self.has(key, Object)) {
              var _o = []
              jQuery.each(value, function(_key, _value) {
                var __o = [];
                addFields(__o, key + "[" + _key + "]", _value);
                _o.push(__o.join(""));
              });
              o.push(_o.join($separator));
            } else if (self.has(key, Array)) {
              var _o = []
              jQuery.each(value, function(_key, _value) {
                var __o = [];
                addFields(__o, key + $suffix, _value);
                _o.push(__o.join(""));
              });
              o.push(_o.join($separator));
            } else {
              addFields(o,key,value);
            }
          }
          queryString.push(o.join(""));
        });
        return queryString.join($separator);
      }
    };
    
    return new queryObject(location.search, location.hash);
  };
}(jQuery.query || {}); // Pass in jQuery.query as settings object
