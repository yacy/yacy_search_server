/**
 *  Condenser.java
 *  Copyright 2004 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.01.2004 at https://yacy.net
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

package net.yacy.document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;

import org.apache.solr.common.params.MapSolrParams;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.analysis.EnhancedTextProfileSignature;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.language.synonyms.AutotaggingLibrary;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.Tagging.Metatag;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.language.Identificator;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.SetTools;

public final class Condenser extends Tokenizer {

    private long fuzzy_signature = 0, exact_signature = 0; // signatures for double-check detection
    private String fuzzy_signature_text = null; // signatures for double-check detection

    private final Identificator languageIdentificator;
    public LinkedHashSet<Date> dates_in_content;

    public Condenser(
            final Document document,
            final VocabularyScraper scraper,
            final boolean indexText,
            final boolean indexMedia,
            final WordCache meaningLib,
            final boolean doAutotagging,
            final boolean findDatesInContent,
            final int timezoneOffset
            ) {
        super(document.dc_source(), indexText ? document.getTextString() : "", meaningLib, doAutotagging, scraper);

        final String initialThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName("condenser-" + document.dc_identifier()); // for debugging

        // if addMedia == true, then all the media links are also parsed and added to the words
        // added media words are flagged with the appropriate media flag
        this.dates_in_content = new LinkedHashSet<Date>();

        // construct flag set for document
        ContentDomain contentDomain = document.getContentDomain();
        if (contentDomain == ContentDomain.IMAGE || !document.getImages().isEmpty())     this.RESULT_FLAGS.set(flag_cat_hasimage, true);
        if (contentDomain == ContentDomain.AUDIO || !document.getAudiolinks().isEmpty()) this.RESULT_FLAGS.set(flag_cat_hasaudio, true);
        if (contentDomain == ContentDomain.VIDEO || !document.getVideolinks().isEmpty()) this.RESULT_FLAGS.set(flag_cat_hasvideo, true);
        if (contentDomain == ContentDomain.APP   || !document.getApplinks().isEmpty())   this.RESULT_FLAGS.set(flag_cat_hasapp,   true);
        if (document.lat() != 0.0 && document.lon() != 0.0) this.RESULT_FLAGS.set(flag_cat_haslocation, true);

        this.languageIdentificator = new Identificator();

        // add the URL components to the word list
        insertTextToWords(new SentenceReader(document.dc_source().toTokens()), 0, WordReferenceRow.flag_app_dc_identifier, this.RESULT_FLAGS, false, meaningLib);

        Map.Entry<AnchorURL, String> entry;
        if (indexText) {
            String text = document.getTextString();
            if (findDatesInContent) this.dates_in_content = DateDetection.parse(text, timezoneOffset);
            // the phrase counter:
            // phrase   0 are words taken from the URL
            // phrase   1 is the MainTitle
            // phrase   2 is <not used>
            // phrase   3 is the Document Abstract
            // phrase   4 is the Document Author
            // phrase   5 is the Document Publisher
            // phrase   6 are the tags specified in document
            // phrase  10 and above are the section headlines/titles (88 possible)
            // phrase  98 is taken from the embedded anchor/hyperlinks description (REMOVED!)
            // phrase  99 is taken from the media Link url and anchor description
            // phrase 100 and above are lines from the text
            insertTextToWords(new SentenceReader(document.dc_title()),       1, WordReferenceRow.flag_app_dc_title, this.RESULT_FLAGS, true, meaningLib);
            for (String description: document.dc_description()) {
                insertTextToWords(new SentenceReader(description), 3, WordReferenceRow.flag_app_dc_description, this.RESULT_FLAGS, true, meaningLib);
            }
            insertTextToWords(new SentenceReader(document.dc_creator()),     4, WordReferenceRow.flag_app_dc_creator, this.RESULT_FLAGS, true, meaningLib);
            insertTextToWords(new SentenceReader(document.dc_publisher()),   5, WordReferenceRow.flag_app_dc_creator, this.RESULT_FLAGS, true, meaningLib);
            insertTextToWords(new SentenceReader(document.dc_subject(' ')),  6, WordReferenceRow.flag_app_dc_description, this.RESULT_FLAGS, true, meaningLib);
            // missing: tags!
            final String[] titles = document.getSectionTitles();
            for (int i = 0; i < titles.length; i++) {
                insertTextToWords(new SentenceReader(titles[i]), i + 10, WordReferenceRow.flag_app_emphasized, this.RESULT_FLAGS, true, meaningLib);
            }

            // anchors: for text indexing we add only the anchor description
            // REMOVED! Reason:
            // words from the anchor description should appear as normal text in the output from the parser
            // to flag these words as appearance in dc_description would confuse, since the user expects such word as titles of
            // pages that are shown in the search result. The words from the URLS should also not appear as part of the index, because they
            // are not visible in the text and could be used to crate fake-content
            /*
            final Iterator<Map.Entry<yacyURL, String>> i = document.getAnchors().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                if ((entry == null) || (entry.getKey() == null)) continue;
                insertTextToWords(entry.getValue(), 98, indexRWIEntry.flag_app_dc_description, RESULT_FLAGS, true);
            }
            */
        } else {
            this.RESULT_NUMB_WORDS = 0;
            this.RESULT_NUMB_SENTENCES = 0;
        }

        if (indexMedia) {
            // add anchor descriptions: here, we also add the url components
            // audio
            Iterator<Map.Entry<AnchorURL, String>> i = document.getAudiolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(new SentenceReader(entry.getKey().toNormalform(true)), 99, flag_cat_hasaudio, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(entry.getValue()), 99, flag_cat_hasaudio, this.RESULT_FLAGS, true, meaningLib);
            }

            // video
            i = document.getVideolinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(new SentenceReader(entry.getKey().toNormalform(true)), 99, flag_cat_hasvideo, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(entry.getValue()), 99, flag_cat_hasvideo, this.RESULT_FLAGS, true, meaningLib);
            }

            // applications
            i = document.getApplinks().entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                insertTextToWords(new SentenceReader(entry.getKey().toNormalform(true)), 99, flag_cat_hasapp, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(entry.getValue()), 99, flag_cat_hasapp, this.RESULT_FLAGS, true, meaningLib);
            }

            // images
            final Iterator<ImageEntry> j = document.getImages().values().iterator();
            ImageEntry ientry;
            MultiProtocolURL url;
            while (j.hasNext()) {
                ientry = j.next();
                url = ientry.url();
                if (url == null) continue;
                insertTextToWords(new SentenceReader(url.toNormalform(true)), 99, flag_cat_hasimage, this.RESULT_FLAGS, false, meaningLib);
                insertTextToWords(new SentenceReader(ientry.alt()), 99, flag_cat_hasimage, this.RESULT_FLAGS, true, meaningLib);
            }

            // finally check all words for missing flag entry
            final Iterator<Map.Entry<String, Word>> k = this.words.entrySet().iterator();
            Word wprop;
            Map.Entry<String, Word> we;
            while (k.hasNext()) {
                we = k.next();
                wprop = we.getValue();
                if (wprop.flags == null) {
                    wprop.flags = this.RESULT_FLAGS.clone();
                    this.words.put(we.getKey().toLowerCase(), wprop);
                }
            }
        }

        if(doAutotagging) {
            extractAutoTagsFromLinkedDataTypes(document.getLinkedDataTypes(), LibraryProvider.autotagging);
        }

        // extend the tags in the document object with autotagging tags
        if (!this.tags.isEmpty()) {
            document.addMetatags(this.tags);
        }

        String text = document.getTextString();
        this.languageIdentificator.add(text); // use content text for language detection (before we added already title etc. for best identification content text is valuable)

        // create hashes for duplicate detection
        // check dups with http://localhost:8090/solr/select?q=*:*&start=0&rows=3&fl=sku,fuzzy_signature_text_t,fuzzy_signature_l,fuzzy_signature_unique_b
        EnhancedTextProfileSignature fuzzySignatureFactory = new EnhancedTextProfileSignature();
        Map<String,String> sp = new HashMap<String,String>();
        sp.put("quantRate", Float.toString(Ranking.getQuantRate())); // for minTokenLen = 2 the value should not be below 0.24; for minTokenLen = 3 the value must be not below 0.5!
        sp.put("minTokenLen", Integer.toString(Ranking.getMinTokenLen()));
        fuzzySignatureFactory.init(new MapSolrParams(sp));
        fuzzySignatureFactory.add(text);
        this.fuzzy_signature = EnhancedTextProfileSignature.getSignatureLong(fuzzySignatureFactory);
        this.fuzzy_signature_text = fuzzySignatureFactory.getSignatureText().toString();
        this.exact_signature = EnhancedTextProfileSignature.getSignatureLong(text);

        /* Restore the current thread initial name */
        Thread.currentThread().setName(initialThreadName);
    }

    /**
     * Search for tags matching the given linked data types identifiers (absolute
     * URLs) in the given autotagging library. Then fill this instance "tags" map
     * with the eventually matching tags found.
     * 
     * @param linkedDataTypes
     *            a set of linked data typed items identifiers (absolute URLs) to
     *            search
     * @param tagLibrary
     *            the autotagging library holding vocabularies to search in
     */
    protected void extractAutoTagsFromLinkedDataTypes(final Set<DigestURL> linkedDataTypes,
            final AutotaggingLibrary tagLibrary) {
        if (linkedDataTypes == null || tagLibrary == null) {
            return;
        }
        for (final DigestURL linkedDataType : linkedDataTypes) {
            final Set<Metatag> tags = tagLibrary.getTagsFromTermURL(linkedDataType);
            for (final Metatag tag : tags) {
                final String navigatorName = tag.getVocabularyName();
                Set<Tagging.Metatag> tagset = this.tags.get(navigatorName);
                if (tagset == null) {
                    tagset = new HashSet<Metatag>();
                    this.tags.put(navigatorName, tagset);
                }
                tagset.add(tag);
            }
        }
    }

    private void insertTextToWords(
            final SentenceReader text,
            final int phrase,
            final int flagpos,
            final Bitfield flagstemplate,
            final boolean useForLanguageIdentification,
            final WordCache meaningLib) {
        if (text == null) return;
        String word;
        Word wprop;
        WordTokenizer wordenum = new WordTokenizer(text, meaningLib);
        try {
            int pip = 0;
            while (wordenum.hasMoreElements()) {
                word = wordenum.nextElement().toString();
                if (useForLanguageIdentification) this.languageIdentificator.add(word); // langdetect is case sensitive
                    if (word.length() < 2) continue;
                    word = word.toLowerCase(Locale.ENGLISH);
                wprop = this.words.get(word);
                if (wprop == null) wprop = new Word(0, pip, phrase);
                if (wprop.flags == null) wprop.flags = flagstemplate.clone();
                wprop.flags.set(flagpos, true);
                this.words.put(word, wprop);
                pip++;
                this.RESULT_NUMB_WORDS++;
                //this.RESULT_DIFF_WORDS++;
                }
        } finally {
            wordenum.close();
            wordenum = null;
        }
    }

    public int excludeWords(final SortedSet<String> stopwords) {
        // subtracts the given stopwords from the word list
        // the word list shrinkes. This returns the number of shrinked words
        final int oldsize = this.words.size();
        SetTools.excludeDestructive(this.words, stopwords);
        return oldsize - this.words.size();
    }

    public long fuzzySignature() {
        return this.fuzzy_signature;
    }

    public String fuzzySignatureText() {
        return this.fuzzy_signature_text;
    }

    public long exactSignature() {
        return this.exact_signature;
    }

    public String language() {
        return this.languageIdentificator.getLanguage();
    }

    /**
     * get the probability of the detected language received by {@link #language()}
     * @return 0.0 to 1.0
     */
    public double languageProbability() {
        return this.languageIdentificator.getProbability();
    }

    public static void main(final String[] args) {
        // read a property file and convert them into configuration lines
        FileInputStream inStream = null;
        try {
            final File f = new File(args[0]);
            final Properties p = new Properties();
            inStream = new FileInputStream(f);
            p.load(inStream);
            final StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            for (int i = 0; i <= 15; i++) {
                sb.append('"');
                final String s = p.getProperty("keywords" + i);
                final String[] l = CommonPattern.COMMA.split(s);
                for (final String element : l) {
                    sb.append(ASCII.String(Word.word2hash(element)));
                }
                if (i < 15) sb.append(",\n");
            }
            sb.append("}\n");
            System.out.println(sb.toString());
        } catch (final FileNotFoundException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } finally {
            if(inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }

    }

}
