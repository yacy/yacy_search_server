package de.anomic.data.ymark;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.document.UTF8;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.WordTokenizer;
import net.yacy.document.Parser.Failure;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.repository.LoaderDispatcher;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;

public class YMarkAutoTagger implements Runnable, Thread.UncaughtExceptionHandler {

	public final static String SPACE = " ";
	public final static String POISON = "";
	
	private final ArrayBlockingQueue<String> bmkQueue;
	private final YMarkTables ymarks;
	private final String bmk_user;
	private final LoaderDispatcher loader;
	
	private boolean merge;
	
	public YMarkAutoTagger(final ArrayBlockingQueue<String> bmkQueue, final LoaderDispatcher loader, final YMarkTables ymarks, final String bmk_user, final boolean merge) {
		this.bmkQueue = bmkQueue;
		this.ymarks = ymarks;
		this.bmk_user = bmk_user;
		this.loader = loader;
		this.merge = merge;
	}
	
	public YMarkAutoTagger(final LoaderDispatcher loader, final YMarkTables ymarks, final String bmk_user) {
		this.bmkQueue = new ArrayBlockingQueue<String>(1);
		this.ymarks = ymarks;
		this.bmk_user = bmk_user;
		this.loader = loader;
		this.merge = true;
	}
	
	private Document loadDocument(final String url) {
		DigestURI uri;
		Response response;
		try {
			uri = new DigestURI(url);
		} catch (MalformedURLException e) {
			Log.logWarning(YMarkTables.BOOKMARKS_LOG, "loadDocument failed due to malformed url: "+url);
			return null;
		}
		try {
			response = loader.load(loader.request(uri, true, false), CrawlProfile.CacheStrategy.IFEXIST, Long.MAX_VALUE, true);
		} catch (IOException e) {
			Log.logWarning(YMarkTables.BOOKMARKS_LOG, "loadDocument failed due to IOException for url: "+url);
			try {
				this.ymarks.addFolder(this.bmk_user, url, "/IOExceptions");
			} catch (IOException e1) {
				Log.logException(e1);
			} catch (RowSpaceExceededException e1) {
				Log.logException(e1);
			}
			return null;
		}		
		try {
			return Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
		} catch (Failure e) {
			Log.logWarning(YMarkTables.BOOKMARKS_LOG, "loadDocument failed due to a parser failure for url: "+url);
			return null;
		}
	}
	
	public String autoTag(final String url, final int max, final TreeMap<String, YMarkTag> tags) {
		final Document document = loadDocument(url);	         
		final TreeSet<YMarkTag> topwords = new TreeSet<YMarkTag>();
		// final TreeMap<String, YMarkTag> pairs = new TreeMap<String, YMarkTag>();
		
		String token;
		// StringBuilder pair = new StringBuilder(64);
					
		if(document != null) {
			//get words from document
			final Map<String, Word> words = new Condenser(document, true, true, LibraryProvider.dymLib).words();
			
			// generate potential tags from document title, description and subject
			final int bufferSize = document.dc_title().length() + document.dc_description().length() + document.dc_subject(' ').length() + 32;
			final StringBuilder buffer = new StringBuilder(bufferSize);
			buffer.append(document.dc_title());
			buffer.append(document.dc_description());
			buffer.append(document.dc_subject(' '));
			final Enumeration<String> tokens = new WordTokenizer(new ByteArrayInputStream(UTF8.getBytes(buffer.toString())), LibraryProvider.dymLib);
			
			int count = 0;
			
			// loop through potential tag and rank them
			while(tokens.hasMoreElements()) {
				count = 0;
				token = tokens.nextElement();
				
				/*
				pair.delete(0, pair.indexOf(SPACE)+1);
				if(pair.length() > 1)
					pair.append(SPACE);										
				pair.append(token);

				if(pair.indexOf(SPACE) > 1 && pairs.containsKey(pair.toString())) {
					pairs.get(pair.toString()).inc();
				} else {
					pairs.put(pair.toString(), new YMarkTag(pair.toString()));
				}
				*/
									
				// check if the token appears in the text
				if (words.containsKey(token)) {
					Word word = words.get(token);
					// token appears in text and matches an existing bookmark tag
					if (tags.containsKey(token)) {
						count = word.occurrences() * tags.get(token).size() * 100;
					}					
					// token appears in text and has more than 3 characters
					if (token.length()>3) {
						count = word.occurrences() * 100;
					}
					topwords.add(new YMarkTag(token, count));
				}
			}				
			count = 0;
			buffer.setLength(0);				
			for(YMarkTag tag : topwords) {
				if(count < max) {
					if(tag.size() > 100) {
						buffer.append(tag.name());
						buffer.append(YMarkUtil.TAGS_SEPARATOR);
						count++;
					}
				} else {
					break;
				}
			}
			String clean =  YMarkUtil.cleanTagsString(buffer.toString());
			return clean;
		}
		return new String();
	}
	
	public void run() {
		Log.logInfo(YMarkTables.BOOKMARKS_LOG, "autoTagger run()");		
		Thread.currentThread().setUncaughtExceptionHandler(this);
		String url = null;
		String tagString;
		Iterator<String> tit;
		try {
			final TreeMap<String, YMarkTag> tags = this.ymarks.getTags(bmk_user);
			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "autoTagger queue size: "+bmkQueue.size());
			while((url = bmkQueue.take()) != POISON) {
				tagString = this.autoTag(url, 5, tags);
								
				// update tags
				this.ymarks.addTags(this.bmk_user, url, tagString, this.merge);
								
				// update tags
				tit = YMarkUtil.keysStringToSet(tagString).iterator();
				while(tit.hasNext()) {
    				final String tag = tit.next();
					if(tags.containsKey(tag)) {
    					tags.get(tag).inc();
    				} else {
    					tags.put(tag, new YMarkTag(tag));
    				}
				}				
			}	
			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "autoTagger has been poisoned");
		} catch (InterruptedException e) {
			Log.logException(e);
		} catch (IOException e) {
			Log.logWarning(YMarkTables.BOOKMARKS_LOG.toString(), "autoTagger - IOException for URL: "+url);
		} catch (RowSpaceExceededException e) {
			Log.logException(e);
		} finally {
		}
	}

	public void uncaughtException(Thread t, Throwable e) {
		Log.logWarning(YMarkTables.BOOKMARKS_LOG, "I caught an uncaughtException in thread "+t.getName());
		Log.logException(e);
	}
}
