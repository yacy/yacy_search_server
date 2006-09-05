// URL.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 13.07.2006 on http://www.anomic.de
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.net;

// this class exsist to provide a system-wide normal form representation of urls,
// and to prevent that java.net.URL usage causes DNS queries which are used in java.net.

import java.io.File;
import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URL {

    private String protocol, host, userInfo, path, quest, ref;
    private int port;
    
    public URL(String url) throws MalformedURLException {
        if (url == null) throw new MalformedURLException("url string is null");
        parseURLString(url);
    }
    
    public void parseURLString(String url) throws MalformedURLException {
        // identify protocol
        int p = url.indexOf(':');
        if (p < 0) throw new MalformedURLException("protocol is not given in '" + url + "'");
        this.protocol = url.substring(0, p).toLowerCase().trim();

        if (url.substring(p + 1, p + 3).equals("//")) {
            // identify host, userInfo and file for http and ftp protocol
            int q = url.indexOf('/', p + 3);
            int r;
            if (q < 0) {
                if ((r = url.indexOf('@', p + 3)) < 0) {
                    host = url.substring(p + 3);
                    userInfo = null;
                } else {
                    host = url.substring(r + 1);
                    userInfo = url.substring(p + 3, r);
                }
                path = "/";
            } else {
                host = url.substring(p + 3, q);
                if ((r = host.indexOf('@')) < 0) {
                    userInfo = null;
                } else {
                    userInfo = host.substring(0, r);
                    host = host.substring(r + 1);
                }
                path = url.substring(q);
            }
            
            resolveBackpath();
            identPort(url);
            identRef();
            identQuest();
        } else {
            // this is not a http or ftp url
            if (protocol.equals("mailto")) {
                // parse email url
                int q = url.indexOf('@', p + 3);
                if (q < 0) {
                    throw new MalformedURLException("wrong email address: " + url);
                } else {
                    userInfo = url.substring(p + 1, q);
                    host = url.substring(q + 1);
                    path = null;
                    port = -1;
                    quest = null;
                    ref = null;
                }
            } else {
                throw new MalformedURLException("unknown protocol: " + url);
            }
        }
        
    }

    public URL(File file) throws MalformedURLException {
        this("file", "", -1, file.getAbsolutePath());
    }

    public URL(URL baseURL, String relPath) throws MalformedURLException {
        if (baseURL == null) throw new MalformedURLException("base URL is null");
        int p = relPath.indexOf(':');
        String relprotocol = (p < 0) ? null : relPath.substring(0, p).toLowerCase();
        if (relprotocol != null) {
            if ("http.https.ftp.mailto".indexOf(relprotocol) >= 0) {
                parseURLString(relPath);
            } else {
                throw new MalformedURLException("unknown protocol: " + relprotocol);
            }
        } else {
            this.protocol = baseURL.protocol;
            this.host = baseURL.host;
            this.port = baseURL.port;
            this.userInfo = baseURL.userInfo;
            if (relPath.toLowerCase().startsWith("javascript:")) {
                this.path = baseURL.path;
            } else if (relPath.startsWith("/")) {
                this.path = relPath;
            } else if (baseURL.path.endsWith("/")) {
                if (relPath.startsWith("#") || relPath.startsWith("?")) {
                    throw new MalformedURLException("relative path malformed: " + relPath);
                } else {
                    this.path = baseURL.path + relPath;
                }
            } else {
                if (relPath.startsWith("#") || relPath.startsWith("?")) {
                    this.path = baseURL.path + relPath;
                } else {
                    int q = baseURL.path.lastIndexOf('/');
                    if (q < 0) {
                        this.path = relPath;
                    } else {
                        this.path = baseURL.path.substring(0, q + 1) + relPath;
                    }
                }
            }
            this.quest = baseURL.quest;
            this.ref = baseURL.ref;

            resolveBackpath();
            identRef();
            identQuest();
        }
    }
    
    public URL(String protocol, String host, int port, String path) throws MalformedURLException {
        if (protocol == null) throw new MalformedURLException("protocol is null");
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        identRef();
        identQuest();
    }

    private void resolveBackpath() throws MalformedURLException {
        // resolve '..'
        int p;
        while ((p = path.indexOf("/..")) >= 0) {
            String head = path.substring(0, p);
            int q = head.lastIndexOf('/');
            if (q < 0) throw new MalformedURLException("backpath cannot be resolved in path = " + path);
            path = head.substring(0, q) + path.substring(p + 3);
        }
    }
    
    private void identPort(String inputURL) throws MalformedURLException {
        // identify ref in file
        int r = host.indexOf(':');
        if (r < 0) {
            this.port = -1;
        } else {
            try {
                this.port = Integer.parseInt(host.substring(r + 1));
                this.host = host.substring(0, r);
            } catch (NumberFormatException e) {
                throw new MalformedURLException("wrong port in host fragment '" + this.host + "' of input url '" + inputURL + "'");
            }
        }
    }
    
    private void identRef() {
        // identify ref in file
        int r = path.indexOf('#');
        if (r < 0) {
            this.ref = null;
        } else {
            this.ref = path.substring(r + 1);
            this.path = path.substring(0, r);
        }
    }
    
    private void identQuest() {
        // identify quest in file
        int r = path.indexOf('?');
        if (r < 0) {
            this.quest = null;
        } else {
            this.quest = path.substring(r + 1);
            this.path = path.substring(0, r);
        }
    }
    
    public String getFile() {
        return getFile(true);
    }
    
    public String getFile(boolean includeReference) {
        // this is the path plus quest plus ref
        if (quest != null) return ((includeReference) && (ref != null)) ? path + "?" + quest + "#" + ref : path + "?" + quest;
        return ((includeReference) && (ref != null)) ? path + "#" + ref : path;
    }

    public String getPath() {
        return path;
    }

    public String getAuthority() {
        return ((port >= 0) && (host != null)) ? host + ":" + port : ((host != null) ? host : "");
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getRef() {
        return ref;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public String getQuery() {
        return quest;
    }

    public String toNormalform() {
        return toString(false);
    }
    
    public String toString() {
        return toString(true);
    }
    
    public String toString(boolean includeReference) {
        // generates a normal form of the URL
        boolean defaultPort = false;
        if (this.protocol.equals("mailto")) {
            return this.protocol + ":" + this.userInfo + "@" + this.host;
        } else if (this.protocol.equals("http")) {
            if (this.port < 0 || this.port == 80)  { defaultPort = true; }
        } else if (this.protocol.equals("ftp")) {
            if (this.port < 0 || this.port == 21)  { defaultPort = true; }
        } else if (this.protocol.equals("https")) {
            if (this.port < 0 || this.port == 443) { defaultPort = true; }
        }
        String path = this.getFile(includeReference);

        if (path.length() == 0 || path.charAt(0) != '/') { path = "/" + path; }

        Pattern pathPattern = Pattern.compile("(/[^/\\.]+/)[.]{2}(?=/)|/\\.(?=/)|/(?=/)");
        Matcher matcher = pathPattern.matcher(path);
        while (matcher.find()) {
            path = matcher.replaceAll("");
            matcher.reset(path);
        }
        
        if (defaultPort) { return this.protocol + "://" + (this.userInfo!=null?this.userInfo+"@":"") + this.getHost().toLowerCase() + path; }
        return this.protocol + "://" + (this.userInfo!=null?this.userInfo+"@":"")+ this.getHost().toLowerCase() + ((defaultPort) ? "" : (":" + this.port)) + path;
    }
    
    public boolean equals(URL other) {
        return (((this.protocol == other.protocol) || (this.protocol.equals(other.protocol))) &&
                ((this.host     == other.host    ) || (this.host.equals(other.host))) &&
                ((this.userInfo == other.userInfo) || (this.userInfo.equals(other.userInfo))) &&
                ((this.path     == other.path    ) || (this.path.equals(other.path))) &&
                ((this.quest    == other.quest   ) || (this.quest.equals(other.quest))) &&
                ((this.ref      == other.ref     ) || (this.ref.equals(other.ref))) &&
                ((this.port     == other.port    )));
    }
    
    public int hashCode() {
        return this.toString().hashCode();
    }
    
    public int compareTo(Object h) {
        assert (h instanceof URL);
        return this.toString().compareTo(((URL) h).toString());
    }
    
    public static void main(String[] args) {
        String[][] test = new String[][]{
          new String[]{null, "http://www.anomic.de/home/test?x=1#home"},
          new String[]{null, "http://www.anomic.de/home/test?x=1"},
          new String[]{null, "http://www.anomic.de/home/test#home"},
          new String[]{null, "ftp://ftp.anomic.de/home/test#home"},
          new String[]{null, "http://www.anomic.de/home/../abc/"},
          new String[]{null, "mailto:abcdefg@nomailnomail.com"},
          new String[]{"http://www.anomic.de/home", "test"},
          new String[]{"http://www.anomic.de/home", "test/"},
          new String[]{"http://www.anomic.de/home/", "test"},
          new String[]{"http://www.anomic.de/home/", "test/"},
          new String[]{"http://www.anomic.de/home/index.html", "test.htm"},
          new String[]{"http://www.anomic.de/home/index.html", "http://www.yacy.net/test"},
          new String[]{"http://www.anomic.de/home/index.html", "ftp://ftp.yacy.net/test"},
          new String[]{"http://www.anomic.de/home/index.html", "../test"},
          new String[]{"http://www.anomic.de/home/index.html", "mailto:abcdefg@nomailnomail.com"},
          new String[]{null, "news:de.test"},
          new String[]{"http://www.anomic.de/home", "news:de.test"},
          new String[]{"http://www.anomic.de/home", "ftp://ftp.anomic.de/src"},
          new String[]{null, "ftp://ftp.delegate.org/"},
          new String[]{"http://www.anomic.de/home", "ftp://ftp.delegate.org/"}
        };
        String environment, url;
        de.anomic.net.URL aURL = null;
        java.net.URL jURL = null;
        for (int i = 0; i < test.length; i++) {
            environment = test[i][0];
            url = test[i][1];
            if (environment == null) {
                try {aURL = new de.anomic.net.URL(url);} catch (MalformedURLException e) {aURL = null;}
                try {jURL = new java.net.URL(url);} catch (MalformedURLException e) {jURL = null;}
            } else {
                try {aURL = new de.anomic.net.URL(new de.anomic.net.URL(environment), url);} catch (MalformedURLException e) {aURL = null;}
                try {jURL = new java.net.URL(new java.net.URL(environment), url);} catch (MalformedURLException e) {jURL = null;}
            }
            if (((aURL == null) && (jURL != null)) ||
                ((aURL != null) && (jURL == null)) ||
                ((aURL != null) && (jURL != null) && (!(jURL.toString().equals(aURL.toString()))))) {
                System.out.println("Difference for environment=" + environment + ", url=" + url + ":");
                System.out.println((jURL == null) ? "jURL rejected input" : "jURL=" + jURL.toString());
                System.out.println((aURL == null) ? "aURL rejected input" : "aURL=" + aURL.toString());
                System.out.println();
            }
        }
    }
}
