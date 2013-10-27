package net.yacy.data.ymark;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.Parser.Failure;
import net.yacy.document.SentenceReader;
import net.yacy.document.WordTokenizer;
import net.yacy.kelondro.data.word.Word;
import net.yacy.repository.LoaderDispatcher;

public class YMarkAutoTagger implements Runnable, Thread.UncaughtExceptionHandler {

	private static final String EMPTY_STRING = new String();


	public final static String SPACE = " ";
	public final static String POISON = "";
	public final static HashSet<String> stopwords = new HashSet<String>(Arrays.asList(".", "!", "?", "nbsp", "uuml", "ouml", "auml", "amp", "quot", "laquo", "raquo",
			"and", "with", "the", "gt", "lt"));


	private final ArrayBlockingQueue<String> bmkQueue;
	private final YMarkTables ymarks;
	private final String bmk_user;
	private final LoaderDispatcher loader;

	private final boolean merge;

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

	private static Document loadDocument(final String url, final LoaderDispatcher loader, ClientIdentification.Agent agent) throws IOException {
		DigestURL uri;
		Response response;
		try {
			uri = new DigestURL(url);
		} catch (final MalformedURLException e) {
			ConcurrentLog.warn(YMarkTables.BOOKMARKS_LOG, "loadDocument failed due to malformed url: "+url);
			return null;
		}
		response = loader.load(loader.request(uri, true, false), CacheStrategy.IFEXIST, Integer.MAX_VALUE, null, agent);
		try {
			return Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
		} catch (final Failure e) {
			ConcurrentLog.warn(YMarkTables.BOOKMARKS_LOG, "loadDocument failed due to a parser failure for url: "+url);
			return null;
		}
	}

	public static String autoTag(final Document document, final int max, final TreeMap<String, YMarkTag> tags) {
		final TreeSet<YMarkTag> topwords = new TreeSet<YMarkTag>();
		StringBuilder token;

		if(document == null) {
			return EMPTY_STRING;
		}

		//get words from document
		final Map<String, Word> words = new Condenser(document, true, true, LibraryProvider.dymLib, LibraryProvider.synonyms, false).words();

		// generate potential tags from document title, description and subject
		final int bufferSize = document.dc_title().length() + document.dc_description().length + document.dc_subject(' ').length() + 32;
		final StringBuilder buffer = new StringBuilder(bufferSize);
		final StringBuilder pwords = new StringBuilder(1000);
		buffer.append(document.dc_title().toLowerCase());
		for (String s:document.dc_description()) buffer.append(s.toLowerCase());
		buffer.append(document.dc_subject(' ').toLowerCase());
		int score = 0;

		// get phrases
		final TreeMap<String, YMarkTag> phrases = getPhrases(document, 2);
		phrases.putAll(getPhrases(document, 3));
		final Iterator<String> iter = phrases.keySet().iterator();
		while(iter.hasNext()) {
			score = 10;
			final String phrase = iter.next();
			if(phrases.get(phrase).size() > 3 && phrases.get(phrase).size() < 10) {
				score = phrases.get(phrase).size() * phrase.split(" ").length * 20;
			}
			if(isDigitSpace(phrase)) {
				score = 10;
			}
			if(phrases.get(phrase).size() > 2 && buffer.indexOf(phrase) > 1) {
				score = score * 10;
			}
			if (tags.containsKey(phrase)) {
				score = score * 20;
			}
			topwords.add(new YMarkTag(phrase, score));
			pwords.append(phrase);
			pwords.append(' ');
		}

		// loop through potential tag and rank them
        WordTokenizer tokens = new WordTokenizer(new SentenceReader(buffer.toString()), LibraryProvider.dymLib);
        try {
    		while (tokens.hasMoreElements()) {
    			score = 0;
    			token = tokens.nextElement();
    
    			// check if the token appears in the text
    			if (words.containsKey(token.toString())) {
    				final Word word = words.get(token.toString());
    				// token appears in text and matches an existing bookmark tag
    				if (tags.containsKey(token.toString())) {
    					score = word.occurrences() * tags.get(token.toString()).size() * 200;
    				}
    				// token appears in text and has more than 3 characters
    				else if (token.length()>3) {
    					score = word.occurrences() * 100;
    				}
    				// if token is already part of a phrase, reduce score
    				if(pwords.toString().indexOf(token.toString())>1) {
    					score = score / 3;
    				}
    				topwords.add(new YMarkTag(token.toString(), score));
    			}
    		}
        } finally {
            tokens.close();
            tokens = null;
        }
		score = 0;
		buffer.setLength(0);
		for(final YMarkTag tag : topwords) {
			if(score < max) {
				if(tag.size() > 100) {
					buffer.append(tag.name());
					buffer.append(YMarkUtil.TAGS_SEPARATOR);
					score++;
				}
			} else {
				break;
			}
		}
		final String clean =  YMarkUtil.cleanTagsString(buffer.toString());
		if(clean.equals(YMarkEntry.BOOKMARK.TAGS.deflt())) {
			return MultiProtocolURL.getFileExtension(document.dc_source().getFileName());
		}
		return clean;
	}

	private static TreeMap<String, YMarkTag> getPhrases(final Document document, final int size) {
		final TreeMap<String, YMarkTag> phrases = new TreeMap<String, YMarkTag>();
		final StringBuilder phrase = new StringBuilder(128);
		WordTokenizer tokens = new WordTokenizer(new SentenceReader(document.getTextString()), LibraryProvider.dymLib);
		try {
			StringBuilder token;
			int count = 0;

			// loop through text
			while(tokens.hasMoreElements()) {

				token = tokens.nextElement();
				if(stopwords.contains(token.toString()) || isDigitSpace(token.toString()))
					continue;

				// if we have a full phrase, delete the first token
				count++;
				if(count > size)
					phrase.delete(0, phrase.indexOf(SPACE)+1);

				// append new token
				if(phrase.length() > 1)
					phrase.append(SPACE);
				phrase.append(token);

				if(count >= size) {	// make sure we really have a phrase
					if(phrases.containsKey(phrase.toString())) {
						phrases.get(phrase.toString()).inc();
					} else {
						phrases.put(phrase.toString(), new YMarkTag(phrase.toString()));
					}
				}
			}

			return phrases;
		} finally {
			tokens.close();
			tokens = null;
		}
	}

	public static String autoTag(final String url, final LoaderDispatcher loader, ClientIdentification.Agent agent, final int max, final TreeMap<String, YMarkTag> tags) {
		Document document = null;
		String exception = "/IOExceptions";
		try {
			document = loadDocument(url, loader, agent);
		} catch (final IOException e) {
			exception = e.getMessage();
			int start = exception.indexOf('\'')+9;
			int end = exception.indexOf('\'', start);
			if(start >= 0 && end > 0 && start < exception.length() && end < exception.length())
				exception = "/IOExceptions/" + exception.substring(start, end);
		}
		return (document != null) ? autoTag(document, max, tags) : exception;
	}

	public static boolean isDigitSpace(String str) {
		if (str == null) {
			return false;
	    }
	    int sz = str.length();
	    for (int i = 0; i < sz; i++) {
	    	if ((Character.isDigit(str.charAt(i)) == false) && (str.charAt(i) != ' ')) {
	    		return false;
	    	}
	    }
	    return true;
	}

	@Override
    public void run() {
		Thread.currentThread().setUncaughtExceptionHandler(this);
		String url = null;
		String tagString;
		Iterator<String> tit;
		try {
			final TreeMap<String, YMarkTag> tags = this.ymarks.getTags(this.bmk_user);
			while((url = this.bmkQueue.take()) != POISON) {
				tagString = autoTag(url, this.loader, ClientIdentification.yacyInternetCrawlerAgent, 5, tags);
				if (tagString.startsWith("/IOExceptions")) {
					this.ymarks.addFolder(this.bmk_user, url, tagString);
					tagString = "";
				}
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
		} catch (final InterruptedException e) {
			ConcurrentLog.logException(e);
		} catch (final IOException e) {
			ConcurrentLog.warn(YMarkTables.BOOKMARKS_LOG.toString(), "autoTagger - IOException for URL: "+url);
		} finally {
		}
	}

	@Override
    public void uncaughtException(final Thread t, final Throwable e) {
		ConcurrentLog.warn(YMarkTables.BOOKMARKS_LOG, "I caught an uncaughtException in thread "+t.getName());
		ConcurrentLog.logException(e);
	}
}
