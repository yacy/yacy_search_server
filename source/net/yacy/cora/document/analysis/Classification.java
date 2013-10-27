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

package net.yacy.cora.document.analysis;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.CommonPattern;

public class Classification {

    private static final Set<String> textExtSet = new HashSet<String>();
    private static final Set<String> mediaExtSet = new HashSet<String>();
    private static final Set<String> imageExtSet = new HashSet<String>();
    private static final Set<String> audioExtSet = new HashSet<String>();
    private static final Set<String> videoExtSet = new HashSet<String>();
    private static final Set<String> appsExtSet = new HashSet<String>();
    private static final Set<String> ctrlExtSet = new HashSet<String>();

    public enum ContentDomain {

        ALL(-1),
        TEXT(0),
        IMAGE(1),
        AUDIO(2),
        VIDEO(3),
        APP(4),
        CTRL(5);

        private final int code;

        ContentDomain(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }

        public static ContentDomain contentdomParser(final String dom) {
            if ("all".equals(dom)) return ALL;
            else if ("text".equals(dom)) return TEXT;
            else if ("image".equals(dom)) return IMAGE;
            else if ("audio".equals(dom)) return AUDIO;
            else if ("video".equals(dom)) return VIDEO;
            else if ("app".equals(dom)) return APP;
            else if ("ctrl".equals(dom)) return CTRL;
            return TEXT;
        }

        @Override
        public String toString() {
            if (this == ALL) return "all";
            else if (this == TEXT) return "text";
            else if (this == IMAGE) return "image";
            else if (this == AUDIO) return "audio";
            else if (this == VIDEO) return "video";
            else if (this == APP) return "app";
            else if (this == CTRL) return "ctrl";
            return "text";
        }
    }

    static {

        final String text = "htm,html,phtml,shtml,xhtml,php,php3,php4,php5,cfm,asp,aspx,tex,txt,jsp,mf,asp,aspx,csv,gpx,vcf,xsl,xml,pdf,doc,docx,xls,xlsx,ppt,pptx";
        final String apps = "7z,ace,arc,arj,apk,asf,asx,bat,bin,bkf,bz2,cab,com,css,dcm,deb,dll,dmg,exe,java,gho,ghs,gz,hqx,img,iso,jar,lha,rar,sh,sit,sitx,tar,tbz,tgz,tib,torrent,vbs,war,zip";
        final String audio = "aac,aif,aiff,flac,m4a,m4p,mid,mp2,mp3,oga,ogg,ram,sid,wav,wma";
        final String video = "3g2,3gp,3gp2,3gpp,3gpp2,3ivx,asf,asx,avi,div,divx,dv,dvx,env,f4v,flv,hdmov,m1v,m4v,m-jpeg,mkv,moov,mov,movie,mp2v,mp4,mpe,mpeg,mpg,mpg4,mv4,ogm,ogv,qt,rm,rv,vid,swf,webm,wmv";
        final String image = "ai,bmp,cdr,cmx,emf,eps,gif,img,jpeg,jpg,mng,pct,pdd,pdn,pict,png,psb,psd,psp,tif,tiff,wmf";
        final String ctrl = "sha1,md5,crc32,sfv";

        addSet(textExtSet, text); // image formats
        addSet(imageExtSet, image); // image formats
        addSet(audioExtSet, audio); // audio formats
        addSet(videoExtSet, video); // video formats
        addSet(appsExtSet, apps);   // application formats
        addSet(ctrlExtSet, ctrl);   // control formats
        addSet(mediaExtSet, apps + "," + audio + "," + video + "," + image); // all media formats
    }

    private static void addSet(Set<String> set, final String extString) {
        if ((extString == null) || (extString.isEmpty())) return;
        for (String s: CommonPattern.COMMA.split(extString, 0)) set.add(s.toLowerCase().trim());
    }

    public static boolean isTextExtension(String textExt) {
        if (textExt == null) return false;
        return textExtSet.contains(textExt.trim().toLowerCase());
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

    public static boolean isControlExtension(final String ctrlExt) {
        if (ctrlExt == null) return false;
        return ctrlExtSet.contains(ctrlExt.trim().toLowerCase());
    }

    public static boolean isAnyKnownExtension(String ext) {
        if (ext == null) return false;
        ext = ext.trim().toLowerCase();
        return textExtSet.contains(ext) || mediaExtSet.contains(ext) || ctrlExtSet.contains(ext);
    }

    /**
     * Get the content domain of a document according to the file extension.
     * This can produce wrong results because the extension is a weak hint for the content domain.
     * If possible, use the mime type, call Classification.getContentDomainFromMime()
     * @return the content domain which classifies the content type
     */
    public static ContentDomain getContentDomainFromExt(final String ext) {
        if (isTextExtension(ext)) return ContentDomain.TEXT;
        if (isImageExtension(ext)) return ContentDomain.IMAGE;
        if (isAudioExtension(ext)) return ContentDomain.AUDIO;
        if (isVideoExtension(ext)) return ContentDomain.VIDEO;
        if (isApplicationExtension(ext)) return ContentDomain.APP;
        if (isControlExtension(ext)) return ContentDomain.CTRL;
        return ContentDomain.ALL;
    }

    /**
     * Get the content domain of a document according to the mime type.
     * @return the content domain which classifies the content type
     */
    public static ContentDomain getContentDomainFromMime(final String mime) {
        if (mime.startsWith("text/")) return ContentDomain.TEXT;
        if (mime.startsWith("image/")) return ContentDomain.IMAGE;
        if (mime.startsWith("audio/")) return ContentDomain.AUDIO;
        if (mime.startsWith("video/")) return ContentDomain.VIDEO;
        if (mime.startsWith("application/")) return ContentDomain.APP;
        return ContentDomain.ALL;
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
            } finally {
                if (mimeTableInputStream != null) try { mimeTableInputStream.close(); } catch (final Exception e1) {}
            }
        }
        for (Entry<Object, Object> entry: mimeTable.entrySet()) {
            String ext = (String) entry.getKey();
            String mime = (String) entry.getValue();
            if (mime.startsWith("text/")) textExtSet.add(ext.toLowerCase());
            if (mime.startsWith("audio/")) audioExtSet.add(ext.toLowerCase());
            if (mime.startsWith("video/")) videoExtSet.add(ext.toLowerCase());
            if (mime.startsWith("application/")) appsExtSet.add(ext.toLowerCase());
        }
    }

    public static int countMimes() {
        return mimeTable.size();
    }

    public static String ext2mime(final String ext) {
        return ext == null ? "application/octet-stream" : mimeTable.getProperty(ext.toLowerCase(), "application/" + (ext == null || ext.length() == 0 ? "octet-stream" : ext));
    }

    public static String ext2mime(final String ext, final String dfltMime) {
        return ext == null ? "application/octet-stream" : mimeTable.getProperty(ext.toLowerCase(), dfltMime);
    }

    public static String url2mime(final MultiProtocolURL url, final String dfltMime) {
        return url == null ? "application/octet-stream" : ext2mime(MultiProtocolURL.getFileExtension(url.getFileName()), dfltMime);
    }

    public static String url2mime(final MultiProtocolURL url) {
        return url == null ? "application/octet-stream" : ext2mime(MultiProtocolURL.getFileExtension(url.getFileName()));
    }
}
