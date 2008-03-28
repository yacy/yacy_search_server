// advancedURLPattern.java 
// -----------------------
// part of YaCy
// (C) by Alexander Fieger webmaster@lulabad.de
// first published on http://www.lulabad.de
// Ingolstadt, Germany, 2006
// last major change: 12.08.2006
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
package de.anomic.index;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import de.anomic.kelondro.kelondroMSetTools;

public class indexRegexWhiteReferenceBlacklist extends indexAbstractReferenceBlacklist implements indexReferenceBlacklist {

	public indexRegexWhiteReferenceBlacklist(File rootPath) {
        super(rootPath);    
    }
    
    public String getEngineInfo() {
        return "Regex YaCy Whitelist Engine by lulabad";
    }    
    
    public boolean isListed(String blacklistType, String hostlow, String path) {
        if (hostlow == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();

        // getting the proper blacklist
        HashMap blacklistMap = super.getBlacklistMap(blacklistType);
        ArrayList app;
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        
        Iterator it = blacklistMap.entrySet().iterator();
        while( it.hasNext() ) {
        	Map.Entry me = (Map.Entry)it.next();
        	try {
        		Matcher domainregex = Pattern.compile((String)me.getKey()).matcher(hostlow);
            	app = (ArrayList) me.getValue();
                for (int i=0; i<app.size(); i++) {
                	Matcher pathregex = Pattern.compile((String)app.get(i)).matcher(path);
                	if ((domainregex.matches()) && (pathregex.matches())) return false;
                }
        	} catch (PatternSyntaxException e) {}
        }
        return true;
    }
    public void add(String blacklistType, String host, String path) {
        if (host == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();
        
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);

        StringBuffer sb = new StringBuffer(host);
        if(sb.charAt(0) == '*') sb.insert(0,'.');
        
        
        HashMap<String, ArrayList<String>> blacklistMap = super.getBlacklistMap(blacklistType);
        ArrayList<String> hostList = blacklistMap.get(sb.toString().toLowerCase());
        if (hostList == null)
            blacklistMap.put(sb.toString().toLowerCase(), (hostList = new ArrayList<String>()));
        hostList.add(path);
    }
    public void remove(String blacklistType, String host, String path) {
        StringBuffer sb = new StringBuffer(host);
        if(sb.charAt(0) == '*') sb.insert(0,'.');

        HashMap blacklistMap = getBlacklistMap(blacklistType);
        ArrayList hostList = (ArrayList)blacklistMap.get(sb.toString());
        if(hostList != null)
        	hostList.remove(path);
        if (hostList.size() == 0)
            blacklistMap.remove(sb.toString());
    }
    public void loadList(String blacklistType, String filenames, String sep) {
        
        HashMap<String,ArrayList<String>> blacklistMapTmp = new HashMap<String,ArrayList<String>>();
        String[] filenamesarray = filenames.split(",");

        if( filenamesarray.length > 0) {
            for (int i = 0; i < filenamesarray.length; i++) {
            	blacklistMapTmp.putAll(kelondroMSetTools.loadMapMultiValsPerKey(new File(this.blacklistRootPath, filenamesarray[i]).toString(), sep));
            }
        }
        Iterator it = blacklistMapTmp.entrySet().iterator();
        ArrayList app;
        while( it.hasNext() ) {
        	Map.Entry me = (Map.Entry)it.next();
        	app = (ArrayList) me.getValue();
            for (int i=0; i<app.size(); i++) {
            	this.add(blacklistType,(String) me.getKey(),(String) app.get(i));
            }
        }
    }

}
