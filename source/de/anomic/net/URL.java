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
        // identify protocol
        int p = url.indexOf("://");
        if (p < 0) throw new MalformedURLException("protocol is not given in '" + url + "'");
        this.protocol = url.substring(0, p).toLowerCase().trim();

        // identify host, userInfo and file
        int q = url.indexOf('/', p + 3);
        int r;
        if (q < 0) {
            if ((r = url.indexOf('@', p + 3)) < 0) {
                host = url.substring(p + 3);
                userInfo = null;
            } else {
                host = url.substring(p + 3, r);
                userInfo = url.substring(r + 1);
            }
            path = "/";
        } else {
            host = url.substring(p + 3, q);
            if ((r = host.indexOf('@')) < 0) {
                userInfo = null;
            } else {
                userInfo = host.substring(r + 1);
                host = host.substring(0, r);
            }
            path = url.substring(q);
        }
        
        identPort();
        identRef();
        identQuest();
    }
    
    public URL(File file) throws MalformedURLException {
        this("file", null, -1, file.getAbsolutePath());
    }

    public URL(URL baseURL, String relPath) throws MalformedURLException {
        if (baseURL == null) throw new MalformedURLException("base URL is null");
        if (relPath.startsWith("/")) relPath = relPath.substring(1);
        this.protocol = baseURL.protocol;
        this.host = baseURL.host;
        this.port = baseURL.port;
        this.path = (baseURL.path.endsWith("/")) ? (baseURL.path + relPath) : (baseURL.path + "/" + relPath);
        this.quest = baseURL.quest;
        this.ref = baseURL.ref;
        
        identRef();
        identQuest();
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

    private void identPort() throws MalformedURLException {
        // identify ref in file
        int r = host.indexOf(':');
        if (r < 0) {
            this.port = -1;
        } else {
            try {
                this.port = Integer.parseInt(host.substring(r + 1));
                this.host = host.substring(0, r);
            } catch (NumberFormatException e) {
                throw new MalformedURLException("wrong port in " + this.host);
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
        // this is the path plus quest plus ref
        if (quest != null) return path + "?" + quest;
        if (ref   != null) return path + "#" + ref;
        return path;
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

    public String toString() {
        // generates a normal form of the URL
        boolean defaultPort = false;
        if (this.protocol.equals("http")) {
            if (this.port < 0 || this.port == 80)  { defaultPort = true; }
        } else if (this.protocol.equals("ftp")) {
            if (this.port < 0 || this.port == 21)  { defaultPort = true; }
        } else if (this.protocol.equals("https")) {
            if (this.port < 0 || this.port == 443) { defaultPort = true; }
        }
        String path = this.getFile();

        if (path.length() == 0 || path.charAt(0) != '/') { path = "/" + path; }

        Pattern pathPattern = Pattern.compile("(/[^/\\.]+/)[.]{2}(?=/)|/\\.(?=/)|/(?=/)");
        Matcher matcher = pathPattern.matcher(path);
        while (matcher.find()) {
            path = matcher.replaceAll("");
            matcher.reset(path);
        }

        return this.protocol + "://" + this.getHost().toLowerCase() + ((defaultPort) ? "" : (":" + this.port)) + getFile();
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
    
    
    public static void main(String[] args) {
        URL u;
        try {u = new URL("http://www.anomic.de/home/test?x=1#home"); System.out.println(u.toString());} catch (MalformedURLException e) {}
        
    }
}
