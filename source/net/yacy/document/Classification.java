// Classification.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.07.2009 on http://yacy.net
//
// $LastChangedDate: 2009-03-20 16:44:59 +0100 (Fr, 20 Mrz 2009) $
// $LastChangedRevision: 5736 $
// $LastChangedBy: borg-0300 $
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

package net.yacy.document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import net.yacy.kelondro.logging.Log;

public class Classification {

    private static final HashSet<String> mediaExtSet = new HashSet<String>();
    private static final HashSet<String> imageExtSet = new HashSet<String>();
    private static final HashSet<String> audioExtSet = new HashSet<String>();
    private static final HashSet<String> videoExtSet = new HashSet<String>();
    private static final HashSet<String> appsExtSet = new HashSet<String>();
    
    private static final Properties ext2mime = new Properties();
    
    static {
    	// load a list of extensions from file
        BufferedInputStream bufferedIn = null;
        File mimeFile = new File("defaults/httpd.mime");
        if (!mimeFile.exists()) mimeFile = new File("config/mime.properties");
        try {
            ext2mime.load(bufferedIn = new BufferedInputStream(new FileInputStream(mimeFile)));
        } catch (final IOException e) {
            Log.logSevere("Classification", "httpd.mime not found in " + mimeFile.toString(), e);
        } finally {
            if (bufferedIn != null) try {
                bufferedIn.close();
            } catch (final Exception e) {}
        }
        
        final String apps = "7z,ace,arc,arj,asf,asx,bat,bin,bkf,bz2,cab,com,css,dcm,deb,dll,dmg,exe,gho,ghs,gz,hqx,img,iso,jar,lha,rar,sh,sit,sitx,tar,tbz,tgz,tib,torrent,vbs,war,zip";
        final String audio = "aac,aif,aiff,flac,m4a,m4p,mid,mp2,mp3,oga,ogg,ram,wav,wma";
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

}
