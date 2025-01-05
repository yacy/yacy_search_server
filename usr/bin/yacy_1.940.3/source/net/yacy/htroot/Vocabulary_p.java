/**
 *  Vocabulary_p
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 07.05.2012 at https://yacy.net
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

package net.yacy.htroot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.language.synonyms.SynonymLibrary;
import net.yacy.cora.lod.vocabulary.DCTerms;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.Tagging.SOTuple;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.StreamResponse;
import net.yacy.data.TransactionManager;
import net.yacy.data.WorkTables;
import net.yacy.document.LibraryProvider;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Handle creation and edition of vocabularies through the Vocabulary_p.html page.
 */
public class Vocabulary_p {

	/** Logger */
	private final static ConcurrentLog LOG = new ConcurrentLog(Vocabulary_p.class.getSimpleName());

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        /* Acquire a transaction token for the next POST form submission */
        final String nextToken = TransactionManager.getTransactionToken(header);
        prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, nextToken);
        prop.put("edit_" + TransactionManager.TRANSACTION_TOKEN_PARAM, nextToken);
        prop.put("create_" + TransactionManager.TRANSACTION_TOKEN_PARAM, nextToken);

        final Collection<Tagging> vocs = LibraryProvider.autotagging.getVocabularies();

        String vocabularyName = (post == null) ? null : post.get("vocabulary", null);
        final String discovername = (post == null) ? null : post.get("discovername", null);
        Tagging vocabulary = vocabularyName == null ? null : LibraryProvider.autotagging.getVocabulary(vocabularyName);
        if (vocabulary == null) {
        	vocabularyName = null;
        }
        if (post != null) {
                // create a vocabulary
                if (vocabulary == null && discovername != null && discovername.length() > 0) {
                	/* Check the transaction is valid */
                	TransactionManager.checkPostTransaction(header, post);

                    // get details of creation
                    String discoverobjectspace = post.get("discoverobjectspace", "");
                    MultiProtocolURL discoveruri = null;
                    if (discoverobjectspace.length() > 0) try {discoveruri = new MultiProtocolURL(discoverobjectspace);} catch (final MalformedURLException e) {}
                    if (discoveruri == null) discoverobjectspace = "";
                    final Map<String, Tagging.SOTuple> table = new LinkedHashMap<String, Tagging.SOTuple>();
                    final File propFile = LibraryProvider.autotagging.getVocabularyFile(discovername);
                    final boolean discoverNot = post.get("discovermethod", "").equals("none");
                    final boolean discoverFromPath = post.get("discovermethod", "").equals("path");
                    final boolean discoverFromTitle = post.get("discovermethod", "").equals("title");
                    final boolean discoverFromTitleSplitted = post.get("discovermethod", "").equals("titlesplitted");
                    final boolean discoverFromAuthor = post.get("discovermethod", "").equals("author");
                    final boolean discoverFromCSV = post.get("discovermethod", "").equals("csv");
                    final String discoverFromCSVPath = post.get("discoverpath", "").replaceAll("%20", " ");


                    final Segment segment = sb.index;
                    String t;
                    int csvFileStatus = 0;
                    if (!discoverNot) {
                        if (discoverFromCSV) {
    						if(discoverFromCSVPath.isEmpty()) {
    							csvFileStatus = 1;
    						} else {
    							DigestURL csvUrl = null;
    		                    if(discoverFromCSVPath.contains("://")) {
    		                    	try {
    		                    		csvUrl = new DigestURL(discoverFromCSVPath);
    		                    	} catch(final MalformedURLException e) {
    									csvFileStatus = 5;
    									prop.put("create_csvFileStatus_csvUrl", discoverFromCSVPath);
    		                    	}
    		                    } else {
    		                    	final File discoverFromCSVFile = new File(discoverFromCSVPath);
    		                    	final String csvPath = discoverFromCSVFile.getAbsolutePath();
    		                    	if (!discoverFromCSVFile.exists()) {
    		                    		csvFileStatus = 2;
    		                    		prop.put("create_csvFileStatus_csvPath", csvPath);
    		                    	} else if (!discoverFromCSVFile.canRead()) {
    		                    		csvFileStatus = 3;
    		                    		prop.put("create_csvFileStatus_csvFile", csvPath);
    		                    	} else if (discoverFromCSVFile.isDirectory()) {
    		                    		csvFileStatus = 4;
    		                    		prop.put("create_csvFileStatus_csvPath", csvPath);
    		                    	} else {
        		                    	try {
        		                    		csvUrl = new DigestURL(discoverFromCSVFile);
        		                    	} catch(final MalformedURLException e) {
        									csvFileStatus = 5;
        									prop.put("create_csvFileStatus_csvUrl", "file://" + discoverFromCSVFile.getAbsolutePath());
        		                    	}
    		                    	}
    		                    }

    		                    if(csvUrl != null) {
    		                    	try {
    		                    		handleDiscoverFromCSV(sb, post, table, csvUrl);
    		                    	} catch(final IOException e) {
    		                    		LOG.warn("Could not read CSV file at " + csvUrl, e);
    		                    		csvFileStatus = 3;
    		                    		prop.put("create_csvFileStatus_csvFile", csvUrl.toString());
    		                    	}
    		                    }
    						}
                        } else {
                            final Iterator<DigestURL> ui = segment.urlSelector(discoveruri, Long.MAX_VALUE, 100000);
                            while (ui.hasNext()) {
                                final DigestURL u = ui.next();
                                final String u0 = u.toNormalform(true);
                                t = "";
                                if (discoverFromPath) {
                                    final int exp = u0.lastIndexOf('.');
                                    if (exp < 0) continue;
                                    final int slp = u0.lastIndexOf('/', exp);
                                    if (slp < 0) continue;
                                    t = u0.substring(slp, exp);
                                    int p;
                                    while ((p = t.indexOf(':')) >= 0) t = t.substring(p + 1);
                                    while ((p = t.indexOf('=')) >= 0) t = t.substring(p + 1);
                                }
                                if (discoverFromTitle || discoverFromTitleSplitted) {
                                    final URIMetadataNode m = segment.fulltext().getMetadata(u.hash());
                                    if (m != null) t = m.dc_title();
                                    if (t.endsWith(".jpg") || t.endsWith(".gif")) continue;
                                }
                                if (discoverFromAuthor) {
                                    final URIMetadataNode m = segment.fulltext().getMetadata(u.hash());
                                    if (m != null) t = m.dc_creator();
                                }
                                t = t.replaceAll("_", " ").replaceAll("\"", " ").replaceAll("'", " ").replaceAll(",", " ").replaceAll("  ", " ").trim();
                                if (t.isEmpty()) continue;
                                if (discoverFromTitleSplitted) {
                                    final String[] ts = CommonPattern.SPACES.split(t);
                                    for (final String s: ts) {
                                        if (s.isEmpty()) continue;
                                        if (s.endsWith(".jpg") || s.endsWith(".gif")) continue;
                                        table.put(s, new Tagging.SOTuple(Tagging.normalizeTerm(s), u0));
                                    }
                                } else if (discoverFromAuthor) {
                                    final String[] ts = CommonPattern.SEMICOLON.split(t); // author names are often separated by ';'
                                    for (String s: ts) {
                                        if (s.isEmpty()) continue;
                                        final int p = s.indexOf(','); // check if there is a reversed method to mention the name
                                        if (p >= 0) s = s.substring(p + 1).trim() + " " + s.substring(0, p).trim();
                                        table.put(s, new Tagging.SOTuple(Tagging.normalizeTerm(s), u0));
                                    }
                                } else {
                                    table.put(t, new Tagging.SOTuple(Tagging.normalizeTerm(t), u0));
                                }
                            }
                        }
                    }
					prop.put("create_csvFileStatus", csvFileStatus);
                    if(csvFileStatus == 0) {
                    	try {
                    		final Tagging newvoc = new Tagging(discovername, propFile, discoverobjectspace, table);
                    		prop.put("create_vocabWriteError", false);

                            LibraryProvider.autotagging.addVocabulary(newvoc);
                            vocabularyName = discovername;
                            vocabulary = newvoc;

                            // store this call as api call
                            sb.tables.recordAPICall(post, "Vocabulary_p.html", WorkTables.TABLE_API_TYPE_CRAWLER, "vocabulary creation for " + discovername);
                    	} catch(final IOException e) {
                    		prop.put("create_vocabWriteError", true);
                    		final String vocabPath = propFile.getAbsolutePath();
                    		prop.put("create_vocabWriteError_vocabPath", vocabPath);
                    		LOG.severe("Could not write vocabulary file at " + vocabPath, e);
                    	}
                    }
                } else if (vocabulary != null && post.containsKey("set")) {
                	/* Check the transaction is valid */
                	TransactionManager.checkPostTransaction(header, post);

                	try {
                		// check if objectspace was set
                		vocabulary.setObjectspace(post.get("objectspace", vocabulary.getObjectspace() == null ? "" : vocabulary.getObjectspace()));

                		// check if a term was added
                		if (post.get("add_new", "").equals("checked") && post.get("newterm", "").length() > 0) {
                			String objectlink = post.get("newobjectlink", "");
                			if (objectlink.length() > 0) try {
                				objectlink = new MultiProtocolURL(objectlink).toNormalform(true);
                			} catch (final MalformedURLException e) {}
                			vocabulary.put(post.get("newterm", ""), post.get("newsynonyms", ""), objectlink);
                		}

                		// check if a term was modified
                		for (final Map.Entry<String, String> e : post.entrySet()) {
                			if (e.getKey().startsWith("modify_") && e.getValue().equals("checked")) {
                				final String term = e.getKey().substring(7);
                				final String synonyms = post.get("synonyms_" + term, "");
                				final String objectlink = post.get("objectlink_" + term, "");
                				vocabulary.put(term, synonyms, objectlink);
                			}
                		}

                		// check if the vocabulary shall be cleared
                		if (post.get("clear_table", "").equals("checked") ) {
                			vocabulary.clear();
                		}

                		// check if the vocabulary shall be deleted
                		if (post.get("delete_vocabulary", "").equals("checked") ) {
                			LibraryProvider.autotagging.deleteVocabulary(vocabularyName);
                			vocabulary = null;
                			vocabularyName = null;
                		}

                		// check if a term shall be deleted
                		if (vocabulary != null && vocabulary.size() > 0) for (final Map.Entry<String, String> e : post.entrySet()) {
                			if (e.getKey().startsWith("delete_") && e.getValue().equals("checked")) {
                				final String term = e.getKey().substring(7);
                				vocabulary.delete(term);
                			}
                		}

                		// check the isFacet and isMatchFromLinkedData properties
                		if (vocabulary != null && post.containsKey("set")) {
                			final boolean isFacet = post.getBoolean("isFacet");
                			vocabulary.setFacet(isFacet);
                			final Set<String> omit = env.getConfigSet("search.result.show.vocabulary.omit");
                			if (isFacet) {
                				omit.remove(vocabularyName);
                			} else {
                				omit.add(vocabularyName);
                			}
                			env.setConfig("search.result.show.vocabulary.omit", omit);

                			final boolean isMatchFromLinkedData = post.getBoolean("vocabularies.matchLinkedData");
                			vocabulary.setMatchFromLinkedData(isMatchFromLinkedData);
                			final Set<String> matchLinkedDataVocs = env.getConfigSet(SwitchboardConstants.VOCABULARIES_MATCH_LINKED_DATA_NAMES);
                			if (isMatchFromLinkedData) {
                				matchLinkedDataVocs.add(vocabularyName);
                			} else {
                				matchLinkedDataVocs.remove(vocabularyName);
                			}
                			env.setConfig(SwitchboardConstants.VOCABULARIES_MATCH_LINKED_DATA_NAMES, matchLinkedDataVocs);
                		}
                    } catch (final IOException e) {
                        ConcurrentLog.logException(e);
                    }
                }

        }

        int count = 0;
        for (final Tagging v: vocs) {
            prop.put("vocabularyset_" + count + "_name", v.getName());
            prop.put("vocabularyset_" + count + "_selected", ((vocabularyName != null && vocabularyName.equals(v.getName())) || (discovername != null && discovername.equals(v.getName()))) ? 1 : 0);
            count++;
        }
        prop.put("vocabularyset", count);

        prop.put("create", vocabularyName == null ? 1 : 0);

        if (vocabulary == null) {
            prop.put("edit", 0);
        } else {
            prop.put("edit", 1);
            final boolean editable = vocabulary.getFile() != null && vocabulary.getFile().exists();
            prop.put("edit_editable", editable ? 1 : 0);
            prop.putHTML("edit_editable_file", editable ? vocabulary.getFile().getAbsolutePath() : "");
            prop.putHTML("edit_name", vocabulary.getName());
            prop.putXML("edit_namexml", vocabulary.getName());
            prop.putHTML("edit_namespace", vocabulary.getNamespace());
            prop.put("edit_isFacet", vocabulary.isFacet() ? 1 : 0);
            prop.put("edit_vocabularies.matchLinkedData", vocabulary.isMatchFromLinkedData());
            prop.put("edit_size", vocabulary.size());
            prop.putHTML("edit_predicate", vocabulary.getPredicate());
            prop.putHTML("edit_prefix", Tagging.DEFAULT_PREFIX);
            prop.putHTML("edit_editable_objectspace", vocabulary.getObjectspace() == null ? "" : vocabulary.getObjectspace());
            prop.putHTML("edit_editable_objectspacepredicate", DCTerms.references.getPredicate());
            int c = 0;
            boolean dark = false;
            final int osl = vocabulary.getObjectspace() == null ? 0 : vocabulary.getObjectspace().length();
            final Map<String, SOTuple> list = vocabulary.list();
            prop.put("edit_size", list.size());
            for (final Map.Entry<String, SOTuple> entry: list.entrySet()) {
                prop.put("edit_terms_" + c + "_editable", editable ? 1 : 0);
                prop.put("edit_terms_" + c + "_dark", dark ? 1 : 0); dark = !dark;
                prop.putXML("edit_terms_" + c + "_label", osl > entry.getValue().getObjectlink().length() ? entry.getKey() : entry.getValue().getObjectlink().substring(osl));
                prop.putHTML("edit_terms_" + c + "_term", entry.getKey());
                prop.putXML("edit_terms_" + c + "_termxml", entry.getKey());
                prop.putHTML("edit_terms_" + c + "_editable_term", entry.getKey());
                final String synonymss = entry.getValue().getSynonymsCSV();
                prop.putHTML("edit_terms_" + c + "_editable_synonyms", synonymss);
                if (synonymss.length() > 0) {
                    final String[] synonymsa = entry.getValue().getSynonymsList();
                    for (int i = 0; i < synonymsa.length; i++) {
                        prop.put("edit_terms_" + c + "_synonyms_" + i + "_altLabel", synonymsa[i]);
                    }
                    prop.put("edit_terms_" + c + "_synonyms", synonymsa.length);
                } else {
                    prop.put("edit_terms_" + c + "_synonyms", 0);
                }
                prop.putXML("edit_terms_" + c + "_editable_objectlink", entry.getValue().getObjectlink());
                c++;
                if (c > 3000) break;
            }
            prop.put("edit_terms", c);

        }

        // make charset list for import method selector
        prop.putHTML("create_charset_" + 0 + "_name", "autodetect");
        prop.put("create_charset_" + 0 + "_selected", 1);
        int c = 1;
        for (final String cs: Charset.availableCharsets().keySet()) {
            prop.putHTML("create_charset_" + c + "_name", cs);
            prop.put("create_charset_" + c + "_selected", 0);
            c++;
        }
        prop.put("create_charset", c);

        // return rewrite properties
        return prop;
    }

	/**
	 * Parse a CSV content line and extract field values. When the last field of
	 * this line starts with and unclosed escape character, the current line is
	 * appended to the escapedContent buffer.
	 *
	 * @param line
	 *            a raw line from a CSV document. Must not be null.
	 * @param separatorPattern
	 *            the field separator character compiled as a Pattern instance. Must
	 *            not be null.
	 * @param escape
	 *            escape character
	 * @param multiLineContent
	 *            eventually holds content of previous lines whose last field
	 *            includes an escaped line separator
	 * @return the list of field values extracted from the line, eventually empty.
	 */
    private static List<String> parseCSVLine(final String line, final Pattern separatorPattern, final String escape, final StringBuilder multiLineContent) {
        final List<String> fields = new ArrayList<>();
        String[] tokens = separatorPattern.split(line);
        if (tokens.length == 0) {
        	tokens = new String[]{line};
        }

        /* Handle continuation of multi-lines field content escaped between escape char */
        if(multiLineContent.length() > 0) {
        	int closingEscapeIndex = -1;
        	for(int index = 0; index < tokens.length; index++) {
        		if(tokens[index].endsWith(escape)) {
        			closingEscapeIndex = index;
        			break;
        		}
        	}
        	if(closingEscapeIndex >= 0) {
        		/* End of multi-line escape */
            	multiLineContent.append("\n").append(line);
                tokens = separatorPattern.split(multiLineContent.toString());
        		multiLineContent.setLength(0);
                if (tokens.length == 0) {
                	tokens = new String[]{line};
                }
        	} else {
        		/* Multi-line escape continue */
                multiLineContent.append("\n").append(line);
            	return fields;
        	}
        }

        /* Handle separator char escaped between escape char */
        final StringBuilder escapedSeparatorContent = new StringBuilder();
        for(final String field : tokens) {
        	if(escapedSeparatorContent.length() == 0) {
        		if(field.startsWith(escape) && !field.endsWith(escape)) {
        			/* Beginning of escape */
        			escapedSeparatorContent.append(field).append(separatorPattern.toString());
        			continue;
        		}
        	} else if(field.endsWith(escape)) {
        		/* End of field escape */
        		escapedSeparatorContent.append(field);
        		fields.add(escapedSeparatorContent.toString());
        		escapedSeparatorContent.setLength(0);
    			continue;
        	} else {
        		/* Escape continue */
        		escapedSeparatorContent.append(field).append(separatorPattern.toString());
    			continue;
        	}
       		fields.add(field);
        }

        if(escapedSeparatorContent.length() > 0) {
        	/* Handle beginning of field content with escaped line separator */
        	multiLineContent.setLength(0);
            multiLineContent.append(line);
        }
        return fields;
    }

    /**
     * Fill the vocabulary table from a CSV file.
     * @param sb the main Switchbaord instance. Must not be null.
     * @param post current request parameters. Must not be null.
     * @param table the vocabulary table to fill. Must not be null.
     * @param csvFileUrl the file URL. Must not be null.
     * @throws IOException when a read/write error occurred
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException when the file does not exists or can not be read for some reason.
     */
	protected static void handleDiscoverFromCSV(final Switchboard sb, final serverObjects post, final Map<String, Tagging.SOTuple> table,
			final DigestURL csvFileUrl)
			throws IOException, UnsupportedEncodingException, FileNotFoundException {
		String charsetName = post.get("charset", StandardCharsets.UTF_8.name());
		final String columnSeparator = post.get("columnSeparator", ";");
		final String escapeChar = "\"";
		final int lineStart = post.getInt("discoverLineStart", 0);
		final int discovercolumnliteral = post.getInt("discovercolumnliteral", 0);
		final int discovercolumnsynonyms = post.getInt("discovercolumnsynonyms", -1);
		final int discovercolumnobjectlink = post.getInt("discovercolumnobjectlink", -1);
        final boolean discoverenrichsynonyms = post.get("discoversynonymsmethod", "none").equals("enrichsynonyms");
        final boolean discoverreadcolumn = post.get("discoversynonymsmethod", "none").equals("readcolumn");

	    final Pattern separatorPattern = Pattern.compile(columnSeparator);

		// auto-detect charset, used code from http://jchardet.sourceforge.net/; see also: http://www-archive.mozilla.org/projects/intl/chardet.html
		if (charsetName.equals("autodetect")) {

			try (final StreamResponse streamResponse = sb.loader.openInputStream(
					sb.loader.request(csvFileUrl, true, false), CacheStrategy.IFFRESH, BlacklistType.CRAWLER,
					ClientIdentification.yacyInternetCrawlerAgent, Integer.MAX_VALUE);) {
				if(streamResponse == null || streamResponse.getContentStream() == null) {
					throw new IOException("Could not get CSV content at " + csvFileUrl);
				}

				charsetName = streamResponse.getResponse().getCharacterEncoding();

				if(charsetName == null) {
					/* Charset not provided in response headers : try to detect it from content */
					final List<String> charsets = FileUtils.detectCharset(streamResponse.getContentStream());
					charsetName = charsets.get(0);
					LOG.info("detected charset: " + charsetName + " used to read " + csvFileUrl.toString());
				} else {
					LOG.info("detected charset: " + charsetName + " used to read " + csvFileUrl.toString());
					/* Use now the open stream */
					try (final InputStreamReader reader = new InputStreamReader(streamResponse.getContentStream(), charsetName);
							final BufferedReader bufferedReader = new BufferedReader(reader);) {
						discoverFromCSVReader(table, escapeChar, lineStart, discovercolumnliteral, discovercolumnsynonyms,
								discovercolumnobjectlink, discoverenrichsynonyms, discoverreadcolumn, separatorPattern,
								bufferedReader);
					}
					return;
				}
			}
		}

		// when autodetection of content charset has been selected, a remote resource may opened again, but has some chances to be now in cache
		try(final StreamResponse streamResponse = sb.loader.openInputStream(
				sb.loader.request(csvFileUrl, true, false), CacheStrategy.IFFRESH, BlacklistType.CRAWLER,
				ClientIdentification.yacyInternetCrawlerAgent, Integer.MAX_VALUE);) {
			if(streamResponse == null || streamResponse.getContentStream() == null) {
				throw new IOException("Could not get CSV content at " + csvFileUrl);
			}
			try (final InputStreamReader reader = new InputStreamReader(streamResponse.getContentStream(), charsetName);
					final BufferedReader bufferedReader = new BufferedReader(reader);) {
				discoverFromCSVReader(table, escapeChar, lineStart, discovercolumnliteral, discovercolumnsynonyms,
						discovercolumnobjectlink, discoverenrichsynonyms, discoverreadcolumn, separatorPattern,
						bufferedReader);
			}
		}
	}

	/**
	 * Fill the vocabulary table from reader open on CSV content.
	 * @param table the vocabulary table to fill. Must not be null.
	 * @param escapeChar CSV escape character (standard is double quote). Must not be null.
	 * @param lineStart index (zero based) of the first line to parse. Previous lines are ignored.
	 * @param literalsColumn index (zero based) of the column to read for literals
	 * @param synonymsColumn index (zero based) of the column to read for synonyms. Set to -1 to ignore.
	 * @param discovercolumnobjectlink
	 * @param discoverenrichsynonyms
	 * @param readSynonymFromColumn when true synonym terms are read from the column at synonymsColumn index
	 * @param separatorPattern the field separator character compiled as a Pattern instance. Must not be null.
	 * @param reader an open reader on CSV content. Must not be null.
	 * @throws IOException when an read error occurred
	 */
	protected static void discoverFromCSVReader(final Map<String, Tagging.SOTuple> table, final String escapeChar,
			final int lineStart, final int literalsColumn, final int synonymsColumn,
			final int discovercolumnobjectlink, final boolean discoverenrichsynonyms, final boolean readSynonymFromColumn,
			final Pattern separatorPattern, final BufferedReader reader) throws IOException {
		String line = null;
		final StringBuilder multiLineContent = new StringBuilder();
		final Map<String, String> synonym2literal = new HashMap<>(); // helper map to check if there are double synonyms
		int lineIndex = -1;
		while ((line = reader.readLine()) != null) {
			lineIndex++;
			if(lineIndex < lineStart) {
				continue;
			}
		    if (line.length() == 0) {
		    	continue;
		    }

			final List<String> fields = parseCSVLine(line, separatorPattern, escapeChar, multiLineContent);
			if (multiLineContent.length() > 0) {
				continue;
			}


		    String literal = literalsColumn < 0 || fields.size() <= literalsColumn ? null : fields.get(literalsColumn).trim();
		    if (literal == null) {
		    	continue;
		    }
		    literal = normalizeLiteral(literal, escapeChar);
		    final String objectlink = discovercolumnobjectlink < 0 || fields.size() <= discovercolumnobjectlink ? null : fields.get(discovercolumnobjectlink).trim();
		    if (literal.length() > 0) {
		        String synonyms = "";
		        if (discoverenrichsynonyms) {
		            final Set<String> sy = SynonymLibrary.getSynonyms(literal);
		            if (sy != null) {
		                for (final String s: sy) {
		                	synonyms += "," + s;
		                }
		            }
		        } else if (readSynonymFromColumn) {
		            synonyms = synonymsColumn < 0 || fields.size() <= synonymsColumn ? null : fields.get(synonymsColumn).trim();
		            synonyms = normalizeLiteral(synonyms, escapeChar);
		        } else {
		            synonyms = Tagging.normalizeTerm(literal);
		        }
		        // check double synonyms
		        if (synonyms.length() > 0) {
		            final String oldliteral = synonym2literal.get(synonyms);
		            if (oldliteral != null && !literal.equals(oldliteral)) {
		                // replace old entry with combined new
		                table.remove(oldliteral);
		                final String newliteral = oldliteral + "," + literal;
		                literal = newliteral;
		            }
		            synonym2literal.put(synonyms, literal);
		        }
		        // store term
		        table.put(literal, new Tagging.SOTuple(synonyms, objectlink == null ? "" : objectlink));
		    }
		}
	}

    private static String normalizeLiteral(String literal, final String escapeChar) {
        if (literal == null) {
        	return "";
        }
        if(literal.length() > 1 && literal.startsWith(escapeChar) && literal.endsWith(escapeChar)) {
            literal = literal.replace("\"\"", "\"");
        }
        if (literal.length() > 0 && (literal.charAt(0) == '"' || literal.charAt(0) == '\'')) {
        	literal = literal.substring(1);
        }
        if (literal.length() > 0 && (literal.charAt(literal.length() - 1) == '"' || literal.charAt(literal.length() - 1) == '\'')) {
        	literal = literal.substring(0, literal.length() - 1);
        }
        return literal;
    }
}
