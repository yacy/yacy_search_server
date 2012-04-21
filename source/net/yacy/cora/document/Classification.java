/**
 *  Classification.java
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.07.2009 at http://yacy.net
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

package net.yacy.cora.document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import net.yacy.kelondro.logging.Log;

public class Classification {

    private static final Set<String> mediaExtSet = new HashSet<String>();
    private static final Set<String> imageExtSet = new HashSet<String>();
    private static final Set<String> audioExtSet = new HashSet<String>();
    private static final Set<String> videoExtSet = new HashSet<String>();
    private static final Set<String> appsExtSet = new HashSet<String>();
    
    
    static {
        
        final String apps = "7z,ace,arc,arj,apk,asf,asx,bat,bin,bkf,bz2,cab,com,css,dcm,deb,dll,dmg,exe,gho,ghs,gz,hqx,img,iso,jar,lha,rar,sh,sit,sitx,tar,tbz,tgz,tib,torrent,vbs,war,zip";
        final String audio = "aac,aif,aiff,flac,m4a,m4p,mid,mp2,mp3,oga,ogg,ram,sid,wav,wma";
        final String video = "3g2,3gp,3gp2,3gpp,3gpp2,3ivx,asf,asx,avi,div,divx,dv,dvx,env,f4v,flv,hdmov,m1v,m4v,m-jpeg,moov,mov,movie,mp2v,mp4,mpe,mpeg,mpg,mpg4,mv4,ogm,ogv,qt,rm,rv,vid,swf,wmv";
        final String image = "ai,bmp,cdr,cmx,emf,eps,gif,img,jpeg,jpg,mng,pct,pdd,pdn,pict,png,psb,psd,psp,tif,tiff,wmf";
        
        addSet(imageExtSet, image); // image formats
        addSet(audioExtSet, audio); // audio formats
        addSet(videoExtSet, video); // video formats
        addSet(appsExtSet, apps);   // application formats
        addSet(mediaExtSet, apps + "," + audio + "," + video + "," + image); // all media formats
    }
    
    private static void addSet(Set<String> set, final String extString) {
        if ((extString == null) || (extString.length() == 0)) return;
        for (String s: extString.split(",")) set.add(s.toLowerCase().trim());
    }

    public static boolean isMediaExtension(String mediaExt) {
        if (mediaExt == null) return false;
        return mediaExtSet.contains(mediaExt.trim().toLowerCase());
    }

    public static boolean isImageExtension(final String imageExt) {
        if (imageExt == null) return false;
        return imageExtSet.contains(imageExt.trim().toLowerCase());
    }

    public static boolean isAudioExtension(final String audioExt) {
        if (audioExt == null) return false;
        return audioExtSet.contains(audioExt.trim().toLowerCase());
    }

    public static boolean isVideoExtension(final String videoExt) {
        if (videoExt == null) return false;
        return videoExtSet.contains(videoExt.trim().toLowerCase());
    }

    public static boolean isApplicationExtension(final String appsExt) {
        if (appsExt == null) return false;
        return appsExtSet.contains(appsExt.trim().toLowerCase());
    }
    
    public static boolean isPictureMime(final String mimeType) {
        if (mimeType == null) return false;
        return mimeType.toUpperCase().startsWith("IMAGE");
    }


    private static final Properties mimeTable = new Properties();
    
    public static void init(final File mimeFile) {
        if (mimeTable.isEmpty()) {
            // load the mime table
            BufferedInputStream mimeTableInputStream = null;
            try {
                mimeTableInputStream = new BufferedInputStream(new FileInputStream(mimeFile));
                mimeTable.load(mimeTableInputStream);
            } catch (final Exception e) {                
                Log.logException(e);
            } finally {
                if (mimeTableInputStream != null) try { mimeTableInputStream.close(); } catch (final Exception e1) {}                
            }
        }
    }
    
    public static int countMimes() {
        return mimeTable.size();
    }
    
    public static String ext2mime(final String ext) {
        return mimeTable.getProperty(ext, "application/" + ext);
    }
    
    public static String ext2mime(final String ext, final String dfltMime) {
        return mimeTable.getProperty(ext, dfltMime);
    }
    
    public static String url2mime(final MultiProtocolURI url, final String dfltMime) {
        return ext2mime(url.getFileExtension(), dfltMime);
    }
    
    public static String url2mime(final MultiProtocolURI url) {
        return ext2mime(url.getFileExtension());
    }
}
