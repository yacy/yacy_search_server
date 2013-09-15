/**
 *  Anchor
 *  Copyright 2013 by Michael Peter Christen
 *  first published 15.09.2013 on http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package net.yacy.cora.document.id;

import java.net.MalformedURLException;
import java.util.Properties;

public class AnchorURL extends DigestURL {

    private static final long serialVersionUID = 1586579902179962086L;

    private String nameProperty, textProperty, relProperty, hrefProperty; // may contain additional url properties, such as given in html a href-links

    public AnchorURL(final String url) throws MalformedURLException {
        super(url);
        this.nameProperty = "";
        this.textProperty = "";
        this.relProperty = "";
        this.hrefProperty = "";
    }
    
    public AnchorURL(final DigestURL url) {
        super(url, url.hash());
        this.nameProperty = "";
        this.textProperty = "";
        this.relProperty = "";
        this.hrefProperty = "";
    }

    public AnchorURL(final MultiProtocolURL baseURL, final String relPath) throws MalformedURLException {
        super(baseURL, relPath);
        this.nameProperty = "";
        this.textProperty = "";
        this.relProperty = "";
        this.hrefProperty = "";
    }

    public AnchorURL(final String protocol, final String host, final int port, final String path) throws MalformedURLException {
        super(protocol, host, port, path);
        this.nameProperty = "";
        this.textProperty = "";
        this.relProperty = "";
        this.hrefProperty = "";
    }

    public static AnchorURL newAnchor(final DigestURL baseURL, String relPath) throws MalformedURLException {
        if (relPath.startsWith("//")) {
            // patch for urls starting with "//" which can be found in the wild
            relPath = (baseURL == null) ? "http:" + relPath : baseURL.getProtocol() + ":" + relPath;
        }
        if ((baseURL == null) ||
            isHTTP(relPath) ||
            isHTTPS(relPath) ||
            isFTP(relPath) ||
            isFile(relPath) ||
            isSMB(relPath)/*||
            relPath.contains(":") && patternMail.matcher(relPath.toLowerCase()).find()*/) {
            return new AnchorURL(relPath);
        }
        return new AnchorURL(baseURL, relPath);
    }

    public String getNameProperty() {
        return nameProperty;
    }

    public void setNameProperty(String name) {
        this.nameProperty = name;
    }

    public String getTextProperty() {
        return textProperty;
    }

    public void setTextProperty(String text) {
        this.textProperty = text;
    }

    public String getRelProperty() {
        return relProperty;
    }

    public void setRelProperty(String rel) {
        this.relProperty = rel;
    }

    public String getHrefProperty() {
        return hrefProperty;
    }

    public void setHrefProperty(String href) {
        this.hrefProperty = href;
    }

    public void setAll(final Properties tagopts) {
        this.nameProperty = tagopts.getProperty("name", "");
        this.textProperty = tagopts.getProperty("text", "");
        this.relProperty = tagopts.getProperty("rel", "");
        this.hrefProperty = tagopts.getProperty("href", "");
    }

    public Properties getAll() {
        final Properties tagopts = new Properties();
        tagopts.setProperty("name", this.nameProperty);
        tagopts.setProperty("text", this.textProperty);
        tagopts.setProperty("rel", this.relProperty);
        tagopts.setProperty("href", this.hrefProperty);
        return tagopts;
    }
    
}
