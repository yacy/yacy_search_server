/**
 *  mp3Parser
 *  Copyright 2012 by Stefan Foerster, Norderstedt, Germany
 *  First released 01.10.2012 at https://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.SupportedFileFormat;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagTextField;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.kelondro.util.FileUtils;

/**
 * this parser can parse id3 tags of mp3 audio files
 */
public class audioTagParser extends AbstractParser implements Parser {
	
	/**
	 * Enumeration of audio formats supported by the {@link audioTagParser}.
	 */
	public enum SupportedAudioFormat {

		AIF("audio/aiff", new String[] { "audio/x-aiff" }, new String[] { SupportedFileFormat.AIF.getFilesuffix(),
				SupportedFileFormat.AIFC.getFilesuffix(), SupportedFileFormat.AIFF.getFilesuffix() }),

		/** @see <a href="https://www.iana.org/assignments/media-types/audio/mpeg">mpeg assignment at IANA</a> */
		MPEG("audio/mpeg", new String[] {"audio/MPA"}, new String[] {SupportedFileFormat.MP3.getFilesuffix()}),

		/** @see <a href="https://www.iana.org/assignments/media-types/audio/MPA">MPA assignment at IANA</a> */
		MPA("audio/MPA", new String[] {}),

		/** @see <a href="https://www.iana.org/assignments/media-types/audio/mpa-robust">mpa-robust assignment at IANA</a> */
		MPA_ROBUST("audio/mpa-robust", new String[] {}),

		/** @see <a href="https://www.iana.org/assignments/media-types/audio/mp4">mp4 assignment at IANA</a> */
		MP4("audio/mp4",
				new String[] { SupportedFileFormat.M4A.getFilesuffix() /* Audio-only MPEG-4 */,
						SupportedFileFormat.M4B.getFilesuffix()/* Audio book (Apple) */,
						SupportedFileFormat.M4P.getFilesuffix()/* Apple iTunes */,
						SupportedFileFormat.MP4.getFilesuffix() /* Standard extension */ }), 
		
		/** @see <a href="https://xiph.org/flac/index.html*>FLAC home page</a> */
		FLAC("audio/flac", new String[] { "audio/x-flac" }, new String[] { SupportedFileFormat.FLAC.getFilesuffix() }),

		/** @see <a href="https://www.iana.org/assignments/media-types/audio/ogg">ogg assignment at IANA</a> */
		OGG("audio/ogg", new String[] {SupportedFileFormat.OGG.getFilesuffix()}), 
		
		WMA("audio/x-ms-wma", new String[] { "audio/x-ms-asf" },
				new String[] { SupportedFileFormat.WMA.getFilesuffix() }), 
		
		REAL_AUDIO("audio/vnd.rn-realaudio", new String[] { "audio/x-pn-realaudio" },
				new String[] { SupportedFileFormat.RA.getFilesuffix(), SupportedFileFormat.RM.getFilesuffix() }),

		/** @see <a href="https://tools.ietf.org/html/rfc2361">RFC 2361 memo (not a standard)</a> */
		WAV("audio/vnd.wave", new String[] { "audio/wav", "audio/wave", "audio/x-wav" },
				new String[] { SupportedFileFormat.WAV.getFilesuffix() });

		/** 
		 * Lower case media type.
		 * When possible the subtype not starting with a "x-" prefix is preferred.
		 * @see <a href="https://tools.ietf.org/html/rfc6648">RFC 6648 about Deprecating the "X-" Prefix</a>*/
		private final String mediaType;

		/** Lower case alternate flavors ot the media type */
		private final Set<String> alternateMediaTypes;
		
		/** Lower case file extensions */
		private final Set<String> fileExtensions;
		
		/**
		 * @param mediaType the media type, formatted as "type/subtype"
		 * @param fileExtensions a set of file extensions matching the given media type
		 */
		private SupportedAudioFormat(final String mediaType, final String[] fileExtensions) {
			this(mediaType, new String[] {}, fileExtensions);
		}

		/**
		 * @param mediaType the main media type, formatted as "type/subtype"
		 * @param alternateMediaTypes alternate flavors the the main media type, all formatted as "type/subtype"
		 * @param fileExtensions a set of file extensions matching the given media type
		 */
		private SupportedAudioFormat(final String mediaType, final String[] alternateMediaTypes, final String[] fileExtensions) {
			this.mediaType = mediaType.toLowerCase(Locale.ROOT);
			Set<String> alternates = new HashSet<>();
			for (final String alternateMediaType : alternateMediaTypes) {
				alternates.add(alternateMediaType.toLowerCase(Locale.ROOT));
			}
			if (alternates.isEmpty()) {
				this.alternateMediaTypes = Collections.emptySet();
			} else {
				this.alternateMediaTypes = Collections.unmodifiableSet(alternates);
			}
			
			Set<String> extensions = new HashSet<>();
			for (final String fileExtension : fileExtensions) {
				extensions.add(fileExtension.toLowerCase(Locale.ROOT));
			}
			if (extensions.isEmpty()) {
				this.fileExtensions = Collections.emptySet();
			} else {
				this.fileExtensions = Collections.unmodifiableSet(extensions);
			}

		}

		/**
		 * @return the lower cased standard or preferred media type in the form
		 *         "type/subtype"
		 */
		public String getMediaType() {
			return this.mediaType;
		}

		/**
		 * @return a set of alternate media types in the form "type/subtype", equivalent
		 *         to the main media type. May be empty.
		 */
		public Set<String> getAlternateMediaTypes() {
			return this.alternateMediaTypes;
		}

		/**
		 * @return the set of file extensions related to this media type
		 */
		public Set<String> getFileExtensions() {
			return this.fileExtensions;
		}
		
		/**
		 * @return all the supported media types as strings
		 */
		public static Set<String> getAllMediaTypes() {
			final Set<String> mediaTypes = new HashSet<>();
			for(final SupportedAudioFormat mediaType : SupportedAudioFormat.values()) {
				mediaTypes.add(mediaType.getMediaType());
				for(final String mediaTypeString : mediaType.getAlternateMediaTypes()) {
					mediaTypes.add(mediaTypeString);	
				}
			}
			return mediaTypes;
		}
		
		/**
		 * @return all the supported file extensions
		 */
		public static Set<String> getAllFileExtensions() {
			final Set<String> extensions = new HashSet<>();
			for(final SupportedAudioFormat mediaType : SupportedAudioFormat.values()) {
				extensions.addAll(mediaType.getFileExtensions());
			}
			return extensions;
		}
	}
	
	/** Map from each supported audio file extensions to audio format */
	private final Map<String, SupportedAudioFormat> ext2Format;
	
	/** Map from each supported audio media type to audio format */
	private final Map<String, SupportedAudioFormat> mediaType2Format;
	
	/** Space character */
	private static final char SPACE_CHAR = ' ';
	
	/** Field keys set of description text tags common to various tagging software and audio file formats */
	private static final Set<FieldKey> COMMON_DESCRIPTION_FIELDS = Collections.unmodifiableSet(EnumSet.of(
			FieldKey.ARTIST, FieldKey.ALBUM_ARTIST, FieldKey.ORIGINAL_ARTIST, FieldKey.COMPOSER, FieldKey.CONDUCTOR,
			FieldKey.LYRICIST, FieldKey.ORIGINAL_LYRICIST, FieldKey.ENGINEER, FieldKey.PRODUCER, FieldKey.MIXER,
			FieldKey.DJMIXER, FieldKey.ARRANGER, FieldKey.ARTISTS, FieldKey.GROUPING, FieldKey.RECORD_LABEL,
			FieldKey.REMIXER, FieldKey.ALBUM, FieldKey.ORIGINAL_ALBUM, FieldKey.TITLE, FieldKey.SUBTITLE,
			FieldKey.COMMENT, FieldKey.DISC_SUBTITLE, FieldKey.LYRICS, FieldKey.TAGS, FieldKey.GENRE, FieldKey.MOOD));
	
    public audioTagParser() {
        super("Audio File Meta-Tag Parser");
        
        final Map<String, SupportedAudioFormat> ext2Formats = new HashMap<>();
        
        final Map<String, SupportedAudioFormat> mediaType2Formats = new HashMap<>();
        
        for(final SupportedAudioFormat mediaType : SupportedAudioFormat.values()) {
        	this.SUPPORTED_MIME_TYPES.add(mediaType.getMediaType());
        	this.SUPPORTED_MIME_TYPES.addAll(mediaType.getAlternateMediaTypes());
        	this.SUPPORTED_EXTENSIONS.addAll(mediaType.getFileExtensions());
        	for(final String fileExtension : mediaType.getFileExtensions()) {
        		ext2Formats.put(fileExtension, mediaType);
        	}
        	mediaType2Formats.put(mediaType.getMediaType(), mediaType);
        	for(final String mediaTypeStr : mediaType.getAlternateMediaTypes()) {
        		mediaType2Formats.put(mediaTypeStr, mediaType);
        	}
        }
        
        this.ext2Format = Collections.unmodifiableMap(ext2Formats);
        this.mediaType2Format = Collections.unmodifiableMap(mediaType2Formats);
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Parser.Failure, InterruptedException {
    	return parseWithLimits(location, mimeType, charset, scraper, timezoneOffset, source, Integer.MAX_VALUE, Long.MAX_VALUE);
    }
    
    @Override
    public Document[] parseWithLimits(final DigestURL location, final String mimeType, final String charset, final VocabularyScraper scraper,
    		final int timezoneOffset, final InputStream source, final int maxLinks, final long maxBytes)
    		throws Failure, InterruptedException {
        String filename = location.getFileName();
        String fileExt = MultiProtocolURL.getFileExtension(filename);
        

        SupportedAudioFormat audioFormat = null;
        if(fileExt != null) {
        	audioFormat = this.ext2Format.get(fileExt);
        }
        if(audioFormat == null) {
        	audioFormat =  this.mediaType2Format.get(mimeType);
        }
        
        String normalizedMediaType = mimeType;
    	if(audioFormat != null) {
        	/* normalize to a single Media Type. Advantages : 
        	 * - index document with the right media type when HTTP response header "Content-Type" is missing or has a wrong value
        	 * - for easier search by CollectionSchema.content_type in the index
             */
    		normalizedMediaType = audioFormat.getMediaType();
    		
            if(fileExt.isEmpty() || !ext2Format.containsKey(fileExt)) {
            	/* Normalize extension to a one known by jaudiotagger */
            	fileExt = audioFormat.getFileExtensions().iterator().next();
            }
    	}
        
        
        filename = filename.isEmpty() ? location.toTokens() : MultiProtocolURL.unescape(filename);
   	    
    	File tempFile = null;
        AudioFile f;
        
        try {
        	boolean partiallyParsed = false;
        	if (location.isFile()) {
        		f = AudioFileIO.read(location.getFSFile());
        	} else {
            	// create a temporary file, as jaudiotagger requires a file rather than an input stream 
        		tempFile = File.createTempFile(filename, "." + fileExt);
        		long bytesCopied = FileUtils.copy(source, tempFile, maxBytes);
        		partiallyParsed = bytesCopied == maxBytes && source.read() != -1;
                f = AudioFileIO.read(tempFile);
        	}
            
            final Tag tag = f.getTag();
       
            final Set<String> lang = new HashSet<String>();
            if(tag != null) {
            	lang.add(tag.getFirst(FieldKey.LANGUAGE));
            }
           	
            // title
            final List<String> titles = new ArrayList<String>();
            if(tag != null) {
            	titles.add(tag.getFirst(FieldKey.TITLE));
            	titles.add(tag.getFirst(FieldKey.SUBTITLE));
            	titles.add(tag.getFirst(FieldKey.ALBUM));
            }
            titles.add(filename);
             
            // text
            final List<String> descriptions = new ArrayList<String>(7);
            final StringBuilder text = new StringBuilder(500);
            if(tag != null) {
				for (final FieldKey field : COMMON_DESCRIPTION_FIELDS) {
					processDescriptionTagField(field, tag, descriptions, text);
				}
            }
            text.append(location.toTokens());
            
            // dc:subject
            final String[] subject;
            if(tag != null) {
            	subject = new String[] {tag.getFirst(FieldKey.GENRE)};
            } else {
            	subject = new String[0];
            }
            
			/*
			 * Some URLs may be found in free text tags such as comments or in dedicated
			 * ones such as 'W' prefixed ID3 tags
			 */
			Set<AnchorURL> detectedUrls;
			if (tag != null) {
				detectedUrls = new HashSet<>();
				partiallyParsed = partiallyParsed || extractUrlsFromTags(maxLinks, tag, detectedUrls);
				if (detectedUrls.isEmpty()) {
					/* Set is empty : reuse the empty set constant object */
					detectedUrls = Collections.emptySet();
				}
			} else {
				detectedUrls = Collections.emptySet();
			}
            
            final Document doc = new Document(
                    location,
                    normalizedMediaType,
                    charset,
                    this,
                    lang, // languages
                    subject, // keywords, dc:subject
                    titles, // title
                    tag != null ? tag.getFirst(FieldKey.ARTIST) : null, // author
                    location.getHost(), // publisher
                    null, // sections
                    descriptions, // abstrct
                    0.0d, 0.0d, // lon, lat
                    text.toString(), // text
                    detectedUrls,
                    null,
                    null,
                    false,
                    new Date());
            doc.setPartiallyParsed(partiallyParsed);
            return new Document[]{doc};
        } catch (final Exception e) {
        	throw new Parser.Failure("Unexpected error while parsing audio file. " + e.getMessage(), location);
		} finally {
            if (tempFile != null) {
            	tempFile.delete();
				/*
				 * If temp file deletion failed it should not be an issue as the operation is
				 * delayed to JVM exit (see YaCy custome temp directory deletion in yacy class)
				 */
            }
		}
    }

	/**
	 * Process text tags to detect eventual URLs and fill the urls set.
	 * 
	 * @param maxLinks
	 *            the maximum links to process and to add to the anchors set
	 * @param tag
	 *            parsed audio tags. Must not be null.
	 * @param urls
	 *            the URLs set to fill. Must not be null.
	 * @return true when the tags contain more URLs than maxLinks limit.
	 */
	private boolean extractUrlsFromTags(final int maxLinks, final Tag tag, final Set<AnchorURL> urls) {
		long detectedUrls = 0;
		final Set<AnchorURL> additionalUrls = new HashSet<>();
		try {
			/* Try to iterate over all tag fields */
			final Iterator<TagField> it = tag.getFields();
			while (it.hasNext() && (detectedUrls < maxLinks || additionalUrls.isEmpty())) {
				final TagField field = it.next();
				if (field != null && !field.isEmpty() && !field.isBinary() && field instanceof TagTextField) {
					final String value = ((TagTextField) field).getContent();
					if (detectedUrls < maxLinks) {
						detectedUrls += ContentScraper.findAbsoluteURLs(value, urls, null, maxLinks - detectedUrls);
					} else {
						/* MaxLinks limit reached : check now if at least one more URL is available */
						ContentScraper.findAbsoluteURLs(value, additionalUrls, null, 1);
					}
				}
			}
		} catch (final UnsupportedOperationException ignored) {
			/*
			 * The getFields() function is not supported in the ID3v1Tag class : let's
			 * iterate over common tag fields only
			 */
			final FieldKey[] commonKeys = FieldKey.values();
			for (int keyIndex = 0; keyIndex < commonKeys.length
					&& (detectedUrls < maxLinks || additionalUrls.isEmpty()); keyIndex++) {
				final FieldKey key = commonKeys[keyIndex];
				final List<String> values = tag.getAll(key);
				if (values != null) {
					for (int valIndex = 0; valIndex < values.size()
							&& (detectedUrls < maxLinks || additionalUrls.isEmpty()); valIndex++) {
						final String value = values.get(valIndex);
						if (StringUtils.isNotBlank(value)) {
							if (detectedUrls < maxLinks) {
								detectedUrls += ContentScraper.findAbsoluteURLs(value, urls, null,
										maxLinks - detectedUrls);
							} else {
								/* MaxLinks limit reached : check now if at least one more URL is available */
								ContentScraper.findAbsoluteURLs(value, additionalUrls, null, 1);
							}
						}
					}
				}
			}
		}
		return !additionalUrls.isEmpty();
	}
    
    @Override
    public boolean isParseWithLimitsSupported() {
    	return true;
    }

	/**
	 * Process a description tag field : add its value to the descriptions and to text. All
	 * parameters must not be null.
	 * 
	 * @param fieldKey
	 *            a field key of a text field
	 * @param tag
	 *            a parsed audio tag
	 * @param descriptions
	 *            the document descriptions to fill
	 * @param text
	 *            the document text to fill
	 */
	private void processDescriptionTagField(final FieldKey fieldKey, final Tag tag, final List<String> descriptions,
			final StringBuilder text) {
		final List<String> fieldValues = tag.getAll(fieldKey);
		if (fieldValues != null) {
			for (final String fieldValue : fieldValues) {
				if (StringUtils.isNotBlank(fieldValue)) {
					descriptions.add(fieldKey.name() + ": " + fieldValue);
					text.append(fieldValue);
					text.append(SPACE_CHAR);
				}
			}
		}
	}
}
