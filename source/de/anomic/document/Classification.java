// Classification.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.07.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package de.anomic.document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public class Classification {

	public static final HashSet<String> supportedHTMLFileExt = new HashSet<String>();
    public static final HashSet<String> supportedHTMLMimeTypes = new HashSet<String>();
    
    private static final HashSet<String> mediaExtSet = new HashSet<String>();
    private static final HashSet<String> imageExtSet = new HashSet<String>();
    private static final HashSet<String> audioExtSet = new HashSet<String>();
    private static final HashSet<String> videoExtSet = new HashSet<String>();
    private static final HashSet<String> appsExtSet = new HashSet<String>();
    private static final Properties mimeTypeLookupByFileExt = new Properties();
    
    public final static HashSet<String> enabledParserList = new HashSet<String>();
    private final static HashSet<String> supportedFileExt = new HashSet<String>();
    
    static {
    	// load a list of extensions from file
        BufferedInputStream bufferedIn = null;
        try {
            mimeTypeLookupByFileExt.load(bufferedIn = new BufferedInputStream(new FileInputStream(new File("httpd.mime"))));
        } catch (final IOException e) {
            System.err.println("ERROR: httpd.mime not found in settings path");
        } finally {
            if (bufferedIn != null) try {
                bufferedIn.close();
            } catch (final Exception e) {}
        }
        
        final String apps = "sit,hqx,img,dmg,exe,com,bat,sh,vbs,zip,jar";
        final String audio = "mp2,mp3,ogg,aac,aif,aiff,wav";
        final String video = "swf,avi,wmv,rm,mov,mpg,mpeg,ram,m4v";
        final String image = "jpg,jpeg,jpe,gif,png,ico,bmp";
        
        imageExtSet.addAll(extString2extList(image)); // image formats
        audioExtSet.addAll(extString2extList(audio)); // audio formats
        videoExtSet.addAll(extString2extList(video)); // video formats
        appsExtSet.addAll(extString2extList(apps)); // application formats

        initMediaExt(extString2extList(apps + "," + // application container
                "tar,gz,bz2,arj,zip,rar," + // archive formats
                "ps,xls,ppt,asf," + // text formats without support
                audio + "," + // audio formats
                video + "," + // video formats
                image // image formats
        ));
    }
    
    public static List<String> extString2extList(final String extString) {
        final LinkedList<String> extensions = new LinkedList<String>();
        if ((extString == null) || (extString.length() == 0)) {
            return extensions;
        }
        final String[] xs = extString.split(",");
        for (int i = 0; i < xs.length; i++)
            extensions.add(xs[i].toLowerCase().trim());
        return extensions;
    }

    public static void initMediaExt(final List<String> mediaExtList) {
        mediaExtSet.addAll(mediaExtList);
    }
    
    public static boolean mediaExtContains(String mediaExt) {
        if (mediaExt == null) return false;
        mediaExt = mediaExt.trim().toLowerCase();

        if (supportedHTMLFileExt.contains(mediaExt)) return false;

        if (supportedFileExtContains(mediaExt)) return false;

        return mediaExtSet.contains(mediaExt);
    }

    public static boolean imageExtContains(final String imageExt) {
        if (imageExt == null) return false;
        return imageExtSet.contains(imageExt.trim().toLowerCase());
    }

    public static boolean audioExtContains(final String audioExt) {
        if (audioExt == null) return false;
        return audioExtSet.contains(audioExt.trim().toLowerCase());
    }

    public static boolean videoExtContains(final String videoExt) {
        if (videoExt == null) return false;
        return videoExtSet.contains(videoExt.trim().toLowerCase());
    }

    public static boolean appsExtContains(final String appsExt) {
        if (appsExt == null) return false;
        return appsExtSet.contains(appsExt.trim().toLowerCase());
    }
    
    public static void initHTMLParsableMimeTypes(
            final String htmlParsableMimeTypes) {
        final LinkedList<String> mimeTypes = new LinkedList<String>();
        if ((htmlParsableMimeTypes == null) || (htmlParsableMimeTypes.length() == 0)) {
            return;
        }
        final String[] realtimeParsableMimeTypeList = htmlParsableMimeTypes
                .split(",");
        for (int i = 0; i < realtimeParsableMimeTypeList.length; i++) {
            mimeTypes.add(realtimeParsableMimeTypeList[i].toLowerCase().trim());
        }
        supportedHTMLMimeTypes.addAll(mimeTypes);
    }

    public static String normalizeMimeType(String mimeType) {
        // if (mimeType == null) doMimeTypeAnalysis
        if (mimeType == null) mimeType = "application/octet-stream";
        mimeType = mimeType.trim().toLowerCase();

        final int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType : mimeType.substring(0, pos));
    }

    public static String getMimeTypeByFileExt(final String fileExt) {
        return mimeTypeLookupByFileExt.getProperty(fileExt, "application/octet-stream");
    }

    public static void initSupportedHTMLFileExt(final List<String> supportedRealtimeFileExtList) {
        supportedHTMLFileExt.addAll(supportedRealtimeFileExtList);
    }

    static boolean HTMLParsableMimeTypesContains(String mimeType) {
        mimeType = normalizeMimeType(mimeType);
        return supportedHTMLMimeTypes.contains(mimeType);
    }
    
    public static boolean supportedContent(final yacyURL url, String mimeType) {
        mimeType = Classification.normalizeMimeType(mimeType);
        if (
                mimeType.equals("text/html") ||
                mimeType.equals("application/xhtml+xml") ||
                mimeType.equals("text/plain")
            ) {
            return supportedMimeTypesContains(mimeType);
        }
        return supportedMimeTypesContains(mimeType) && supportedFileExt(url);
    }        
    
    public static boolean supportedMimeTypesContains(String mimeType) {
        mimeType = Classification.normalizeMimeType(mimeType);
        
        if (Classification.supportedHTMLMimeTypes.contains(mimeType)) return true;
        return enabledParserList.contains(mimeType);
    }        
    
    private static boolean supportedFileExt(final yacyURL url) {
        if (url == null) throw new NullPointerException();
        
        // getting the file path
        final String name = getFileExt(url);
        return supportedFileExtContains(name);
    }
    
    public static boolean supportedFileExtContains(String fileExt) {
        if (fileExt == null) return false;        
        fileExt = fileExt.trim().toLowerCase();
        if (Classification.supportedHTMLFileExt.contains(fileExt)) return true;

        return supportedFileExt.contains(fileExt);
    }        
    
    public static void addParseableMimeTypes(final String enabledMimeTypes) {
        HashSet<String> mimeTypes = null;
        if ((enabledMimeTypes == null) || (enabledMimeTypes.length() == 0)) {
            mimeTypes = new HashSet<String>();
        } else {            
            final String[] enabledMimeTypeList = enabledMimeTypes.split(",");
            mimeTypes = new HashSet<String>(enabledMimeTypeList.length);
            for (int i = 0; i < enabledMimeTypeList.length; i++) mimeTypes.add(enabledMimeTypeList[i].toLowerCase().trim());
        }
        setEnabledParserList(mimeTypes);
    }
    
    public static void enableAllParsers() {
        final Set<String> availableMimeTypes = Parser.availableParserList.keySet();
        setEnabledParserList(availableMimeTypes);
    }
    
    public static String[] setEnabledParserList(final Set<String> mimeTypeSet) {
        
        final HashSet<String> newEnabledParsers = new HashSet<String>();
        final HashSet<String> newSupportedFileExt = new HashSet<String>();
        
        if (mimeTypeSet != null) {
            final Iterator<String> mimeTypes = mimeTypeSet.iterator();
            while (mimeTypes.hasNext()) {
                final String mimeType = mimeTypes.next();
                Idiom theParser = Parser.availableParserList.get(mimeType);
                if (theParser != null) {
                    try {
                        // getting a list of mimeTypes that the parser supports
                        final Hashtable<String, String> parserSupportsMimeTypes = theParser.getSupportedMimeTypes();
                        if (parserSupportsMimeTypes != null) {
                            final Object supportedExtensions = parserSupportsMimeTypes.get(mimeType);
                            if ((supportedExtensions != null) &&
                                    (supportedExtensions instanceof String) &&
                                    (((String)supportedExtensions).length() > 0)) {
                                final String[] extArray = ((String)supportedExtensions).split(",");
                                newSupportedFileExt.addAll(Arrays.asList(extArray));
                            }
                        }
                        newEnabledParsers.add(mimeType);
                        
                    } catch (final Exception e) {
                        Log.logSevere("PARSER", "error in setEnabledParserList", e);
                    } finally {
                        if (theParser != null)
                            theParser = null; // destroy object
                    }
                }
            }
        }
        
        enabledParserList.addAll(newEnabledParsers);
        supportedFileExt.addAll(newSupportedFileExt);

        return newEnabledParsers.toArray(new String[newEnabledParsers.size()]);
    }
    
    @SuppressWarnings("unchecked")
    public static HashSet<String> getEnabledParserList() {
        return (HashSet<String>) enabledParserList.clone();
    }
    
    public static String getFileExt(final yacyURL url) {
        // getting the file path
        String name = url.getPath();

        // tetermining last position of / in the file path
        int p = name.lastIndexOf('/');
        if (p != -1) {
            name = name.substring(p);
        }

        // termining last position of . in file path
        p = name.lastIndexOf('.');
        if (p < 0)
            return "";
        return name.substring(p + 1);
    }
}
