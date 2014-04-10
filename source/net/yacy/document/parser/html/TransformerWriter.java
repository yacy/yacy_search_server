// htmlFilterOutputStream.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

/*
 This class implements an output stream. Any data written to that output
 is automatically parsed.
 After finishing with writing, the htmlFilter can be read out.

 */

package net.yacy.document.parser.html;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Stack;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.io.CharBuffer;


public final class TransformerWriter extends Writer {

    public static final char lb = '<';
    public static final char rb = '>';
    public static final char dash = '-';
    public static final char excl = '!';
    public static final char singlequote = '\'';
    public static final char doublequote = '"';

    private final OutputStream outStream;
    private OutputStreamWriter out;
    private CharBuffer buffer;
    private Stack<ContentScraper.Tag> tagStack;
    private final Scraper scraper;
    private final Transformer transformer;
    private boolean inSingleQuote;
    private boolean inDoubleQuote;
    private boolean inComment;
    private boolean binaryUnsuspect;
    private final boolean passbyIfBinarySuspect;
    
    public TransformerWriter(
            final OutputStream outStream,
            final Charset charSet,
            final Scraper scraper,
            final Transformer transformer,
            final boolean passbyIfBinarySuspect
    ) {
    	this(outStream, charSet, scraper, transformer, passbyIfBinarySuspect, 64);
    }

    public TransformerWriter(
            final OutputStream outStream,
            final Charset charSet,
            final Scraper scraper,
            final Transformer transformer,
            final boolean passbyIfBinarySuspect,
            final int initialBufferSize
    ) {
        this.outStream     = outStream;
        this.scraper       = scraper;
        this.transformer   = transformer;
        this.buffer        = new CharBuffer(ContentScraper.MAX_DOCSIZE, initialBufferSize);
        this.tagStack      = new Stack<ContentScraper.Tag>();
        this.inSingleQuote = false;
        this.inDoubleQuote = false;
        this.inComment     = false;
        this.binaryUnsuspect = true;
        this.passbyIfBinarySuspect = passbyIfBinarySuspect;

        if (this.outStream != null) {
            this.out = new OutputStreamWriter(this.outStream,(charSet == null)?Charset.defaultCharset():charSet);
        }
    }

    public static char[] genTag0raw(final String tagname, final boolean opening, final char[] tagopts) {
            final CharBuffer bb = new CharBuffer(ContentScraper.MAX_DOCSIZE, tagname.length() + tagopts.length + 3);
            bb.append('<');
            if (!opening) {
                bb.append('/');
            }
            bb.append(tagname);
            if (tagopts.length > 0) {
//              if (tagopts[0] == (byte) 32)
                bb.append(tagopts);
//              else bb.append((byte) 32).append(tagopts);
            }
            bb.append('>');
            final char[] result = bb.getChars();
            bb.close();
            return result;
    }

    public static char[] genTag1raw(final String tagname, final char[] tagopts, final char[] text) {
            final CharBuffer bb = new CharBuffer(ContentScraper.MAX_DOCSIZE, 2 * tagname.length() + tagopts.length + text.length + 5);
            bb.append('<').append(tagname);
            if (tagopts.length > 0) {
//              if (tagopts[0] == (byte) 32)
                bb.append(tagopts);
//              else bb.append((byte) 32).append(tagopts);
            }
            bb.append('>');
            bb.append(text);
            bb.append('<').append('/').append(tagname).append('>');
            final char[] result = bb.getChars();
            bb.close();
            return result;
    }

    public static char[] genTag0(final String tagname, final Properties tagopts, final char quotechar) {
            final char[] tagoptsx = (tagopts.isEmpty()) ? null : genOpts(tagopts, quotechar);
            final CharBuffer bb = new CharBuffer(ContentScraper.MAX_DOCSIZE, tagname.length() + ((tagoptsx == null) ? 0 : (tagoptsx.length + 1)) + tagname.length() + 2);
            bb.append('<').append(tagname);
            if (tagoptsx != null) {
                bb.appendSpace();
                bb.append(tagoptsx);
            }
            bb.append('>');
            final char[] result = bb.getChars();
            bb.close();
            return result;
    }

    public static char[] genTag1(final String tagname, final Properties tagopts, final char[] text, final char quotechar) {
            final char[] gt0 = genTag0(tagname, tagopts, quotechar);
            final CharBuffer cb = new CharBuffer(ContentScraper.MAX_DOCSIZE, gt0, gt0.length + text.length + tagname.length() + 3);
            cb.append(text).append('<').append('/').append(tagname).append('>');
            final char[] result = cb.getChars();
            cb.close();
            return result;
    }

    // a helper method for pretty-printing of properties for html tags
    public static char[] genOpts(final Properties prop, final char quotechar) {
            final Enumeration<?> e = prop.propertyNames();
            final CharBuffer bb = new CharBuffer(ContentScraper.MAX_DOCSIZE, prop.size() * 40);
            String key;
            while (e.hasMoreElements()) {
                key = (String) e.nextElement();
                bb.appendSpace().append(key).append('=').append(quotechar);
                bb.append(prop.getProperty(key));
                bb.append(quotechar);
            }
            final char[] result;
            if (bb.length() > 0)
                result = bb.getChars(1);
            else
                result = bb.getChars();
            bb.close();
            return result;
    }

    /**
     * the token processor distinguishes three different types of input: opening tag, closing tag, text content
     * @param in - the token to be processed
     * @param quotechar
     * @return a processed version of the token
     */
    private char[] tokenProcessor(final char[] in, final char quotechar) {
        if (in.length == 0) return in;
        
        // scan the string and parse structure
        if (in.length <= 2 || in[0] != lb) return filterTag(in); // this is a text

        // this is a tag
        String tag;
        int tagend;
        if (in[1] == '/') {
            // a closing tag
            tagend = tagEnd(in, 2);
            tag = new String(in, 2, tagend - 2).toLowerCase();
            final char[] text = new char[in.length - tagend - 1];
            System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
            return filterTag(text, quotechar, tag, false);
        }

        // an opening tag
        tagend = tagEnd(in, 1);
        tag = new String(in, 1, tagend - 1).toLowerCase();
        final char[] text = new char[in.length - tagend - 1];
        System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
        return filterTag(text, quotechar, tag, true);
    }
    
    // distinguish the following cases:
    // - (1) not collecting data for a tag and getting no tag (not opener and not close)
    // - (2) not collecting data for a tag and getting a tag opener
    // - (3) not collecting data for a tag and getting a tag close
    // - (4) collecting data for a tag and getting no tag (not opener and not close)
    // - (5) collecting data for a tag and getting a new/different tag opener without closing the previous tag
    // - (6) collecting data for a tag and getting a tag close for the wrong tag (a different than the opener)
    // - (7) collecting data for a tag and getting the correct close tag for that collecting tag
    
    /**
     * 
     * @param content
     * @return
     */
    private char[] filterTag(final char[] content) {
        if (this.tagStack.size() == 0) {
            // we are not collection tag text -> case (1) - (3)
            // case (1): this is not a tag opener/closer
            if (this.scraper != null && content.length > 0) this.scraper.scrapeText(content, null);
            if (this.transformer != null) return this.transformer.transformText(content);
            return content;
        }

        // we are collection tag text for the tag 'filterTag' -> case (4) - (7)
        // case (4): getting no tag, go on collecting content
        if (this.scraper != null) {
            this.scraper.scrapeText(content, this.tagStack.lastElement().name);
        }
        if (this.transformer != null) {
            this.tagStack.lastElement().content.append(this.transformer.transformText(content));
        } else {
            this.tagStack.lastElement().content.append(content);
        }
        return new char[0];
    }
            
    private char[] filterTag(final char[] content, final char quotechar, final String tagname, final boolean opening) {
        assert tagname != null;
        
        if (this.tagStack.size() == 0) {
            // we are not collection tag text -> case (1) - (3)

            // we have a new tag
            if (opening) {
                // case (2):
                return filterTagOpening(tagname, content, quotechar);
            }

            // its a close tag where no should be
            // case (3): we ignore that thing and return it again
            return genTag0raw(tagname, false, content);

        }

        // we are collection tag text for the tag 'filterTag' -> case (4) - (7)
        if (tagname.equals("!")) filterTag(content);

        // it's a tag! which one?
        if (opening) {
            // case (5): the opening should not be here. But we keep the order anyway
            this.tagStack.lastElement().content.append(filterTagOpening(tagname, content, quotechar));
            return new char[0];
        }

        if (!tagname.equalsIgnoreCase(this.tagStack.lastElement().name)) {
            // case (6): its a closing tag, but the wrong one. just add it.
            this.tagStack.lastElement().content.append(genTag0raw(tagname, opening, content));
            return new char[0];
        }

        // it's our closing tag! return complete result.
        return filterTagCloseing(quotechar);
    }

    private char[] filterTagOpening(final String tagname, final char[] content, final char quotechar) {
        final CharBuffer charBuffer = new CharBuffer(ContentScraper.MAX_DOCSIZE, content);
        ContentScraper.Tag tag = new ContentScraper.Tag(tagname, charBuffer.propParser());
        charBuffer.close();
        if (this.scraper != null && this.scraper.isTag0(tagname)) {
            // this single tag is collected at once here
            this.scraper.scrapeTag0(tag);
        }
        if (this.transformer != null && this.transformer.isTag0(tagname)) {
            // this single tag is collected at once here
            char[] b = new char[0];
            b = this.transformer.transformTag0(tag, quotechar);
            return b;
        } else if ((this.scraper != null && this.scraper.isTag1(tagname)) ||
                   (this.transformer != null && this.transformer.isTag1(tagname))) {
            // ok, start collecting; we don't push this here to the scraper or transformer; we do that when the tag is closed.
            this.tagStack.push(tag);
            return new char[0];
        } else {
             // we ignore that thing and return it again
             return genTag0raw(tagname, true, content);
        }
    }

    private char[] filterTagCloseing(final char quotechar) {
        char[] ret;
        ContentScraper.Tag tag = this.tagStack.lastElement();
        if (this.scraper != null) this.scraper.scrapeTag1(tag);
        if (this.transformer != null) {
            ret = this.transformer.transformTag1(tag, quotechar);
        } else {
            ret = genTag1(tag.name, tag.opts, tag.content.getChars(), quotechar);
        }
        if ((this.scraper != null && this.scraper.isTag1(tag.name)) ||
            (this.transformer != null && this.transformer.isTag1(tag.name))) {
            // remove the tag from the stack as soon as the tag is processed
            this.tagStack.pop();
            // at this point the characters from the recently processed tag must be attached to the previous tag
            if (this.tagStack.size() > 0) this.tagStack.lastElement().content.append(ret);
        }
        return ret;
    }

    private char[] filterFinalize(final char quotechar) {
        if (this.tagStack.size() == 0) {
            return new char[0];
        }

        // it's our closing tag! return complete result.
        char[] ret;
        if (this.scraper != null) this.scraper.scrapeTag1(this.tagStack.lastElement());
        if (this.transformer != null) {
            ret = this.transformer.transformTag1(this.tagStack.lastElement(), quotechar);
        } else {
            ret = genTag1(this.tagStack.lastElement().name, this.tagStack.lastElement().opts, this.tagStack.lastElement().content.getChars(), quotechar);
        }
        this.tagStack.pop();
        return ret;
    }

    private static int tagEnd(final char[] tag, final int start) {
        char c;
        for (int i = start; i < tag.length; i++) {
            c = tag[i];
            if (c != '!' && c != '-' &&
                (c < '0' || c > '9') &&
                (c < 'a' || c > 'z') &&
                (c < 'A' || c > 'Z')
            ) return i;
        }
        return tag.length - 1;
    }

    /**
     * this is the tokenizer of the parser: it splits the input into pieces which are
     * - quoted text parts
     * - commented text parts
     * - tags (opening and closing)
     * - text content between all these parts
     * The tokens are then parsed with the filterSentence method
     */
    @Override
    public void write(final int c) throws IOException {
        //System.out.println((char) c);
        if ((this.binaryUnsuspect) && (binaryHint((char)c))) {
            this.binaryUnsuspect = false;
            if (this.passbyIfBinarySuspect) close();
        }

        if (this.binaryUnsuspect || !this.passbyIfBinarySuspect) {
            char[] filtered;
            if (this.inSingleQuote) {
                this.buffer.append(c);
                if (c == singlequote) this.inSingleQuote = false;
                // check error cases
                if ((c == rb) && (this.buffer.length() > 0 && this.buffer.charAt(0) == lb)) {
                    this.inSingleQuote = false;
                    // the tag ends here. after filtering: pass on
                    filtered = tokenProcessor(this.buffer.getChars(), singlequote);
                    if (this.out != null) { this.out.write(filtered); }
                    // this.buffer = new serverByteBuffer();
                    this.buffer.reset();
                }
            } else if (this.inDoubleQuote) {
                this.buffer.append(c);
                if (c == doublequote) this.inDoubleQuote = false;
                // check error cases
                if (c == rb && this.buffer.length() > 0 && this.buffer.charAt(0) == lb) {
                    this.inDoubleQuote = false;
                    // the tag ends here. after filtering: pass on
                    filtered = tokenProcessor(this.buffer.getChars(), doublequote);
                    if (this.out != null) this.out.write(filtered);
                    // this.buffer = new serverByteBuffer();
                    this.buffer.reset();
                }
            } else if (this.inComment) {
                this.buffer.append(c);
                if (c == rb &&
                    this.buffer.length() > 6 &&
                    this.buffer.charAt(this.buffer.length() - 3) == dash) {
                    // comment is at end
                    this.inComment = false;
                    final char[] comment = this.buffer.getChars();
                    if (this.scraper != null) this.scraper.scrapeComment(comment);
                    if (this.out != null) this.out.write(comment);
                    // this.buffer = new serverByteBuffer();
                    this.buffer.reset();
                }
            } else {
                if (this.buffer.isEmpty()) {
                    if (c == rb) {
                        // very strange error case; we just let it pass
                        if (this.out != null) this.out.write(c);
                    } else {
                        this.buffer.append(c);
                    }
                } else if (this.buffer.length() > 0 && this.buffer.charAt(0) == lb) {
                    if (c == singlequote) this.inSingleQuote = true;
                    if (c == doublequote) this.inDoubleQuote = true;
                    // fill in tag text
                    if ((this.buffer.length() >= 3) && (this.buffer.charAt(1) == excl) &&
                        (this.buffer.charAt(2) == dash) && (c == dash)) {
                        // this is the start of a comment
                        this.inComment = true;
                        this.buffer.append(c);
                    } else if (c == rb) {
                        this.buffer.append(c);
                        // the tag ends here. after filtering: pass on
                        filtered = tokenProcessor(this.buffer.getChars(), doublequote);
                        if (this.out != null) this.out.write(filtered);
                        // this.buffer = new serverByteBuffer();
                        this.buffer.reset();
                    } else if (c == lb) {
                        // this is an error case
                        // we consider that there is one rb missing
                        if (this.buffer.length() > 0) {
                            filtered = tokenProcessor(this.buffer.getChars(), doublequote);
                            if (this.out != null) this.out.write(filtered);
                        }
                        // this.buffer = new serverByteBuffer();
                        this.buffer.reset();
                        this.buffer.append(c);
                    } else {
                        this.buffer.append(c);
                    }
                } else {
                    // fill in plain text
                    if (c == lb) {
                        // the text ends here
                        if (this.buffer.length() > 0) {
                            filtered = tokenProcessor(this.buffer.getChars(), doublequote);
                            if (this.out != null) this.out.write(filtered);
                        }
                        // this.buffer = new serverByteBuffer();
                        this.buffer.reset();
                        this.buffer.append(c);
                    } else {
                        // simply append
                        this.buffer.append(c);
                    }
                }
            }
        } else {
            this.out.write(c);
        }
    }

    @Override
    public void write(final char b[]) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final char b[], final int off, final int len) throws IOException {
//      System.out.println(UTF8.String(b, off, len));
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0) throw new IndexOutOfBoundsException();
        for (int i = off ; i < (len - off) ; i++) this.write(b[i]);
    }

    @Override
    public void flush() throws IOException {
        // we cannot flush the current string this.buffer to prevent that
        // the filter process is messed up
        // instead, we simply flush the underlying output stream
        if (this.out != null) this.out.flush();
        if (this.scraper != null) this.scraper.finish();
        // if you want to flush all, call close() at end of writing;
    }

    @Override
    public void close() throws IOException {
        flush();
        final char quotechar = (this.inSingleQuote) ? singlequote : doublequote;
        if (this.buffer != null) {
            if (this.buffer.length() > 0) {
                final char[] filtered = tokenProcessor(this.buffer.getChars(), quotechar);
                if (this.out != null) this.out.write(filtered);
            }
            this.buffer.close();
            this.buffer = null;
        }
        final char[] finalized = filterFinalize(quotechar);
        if (this.out != null) {
            if (finalized != null) this.out.write(finalized);
            this.out.flush();
            this.out.close();
        }
        this.tagStack.clear();
        this.tagStack = null;
        if (this.scraper != null) this.scraper.finish();
    }

    private static boolean binaryHint(final char c) {
        // space, punctiation and symbols, letters and digits (ASCII/latin)
        //if (c >= 31 && c < 128) return false;
        if(c > 31) return false;
        //  8 = backspace
        //  9 = horizontal tab
        // 10 = new line (line feed)
        // 11 = vertical tab
        // 12 = new page (form feed)
        // 13 = carriage return
        if (c > 7 && c <= 13) return false;
        //if (Character.isLetterOrDigit(c)) return false;
//      return false;
//      System.err.println("BINARY HINT: " + (int) c);
        return true;
    }

    public boolean binarySuspect() {
        return !this.binaryUnsuspect;
    }

    public static void main(final String[] args) {
        // takes one argument: a file name
        if (args.length != 1) return;
        // TODO: this does not work at the moment
        System.out.println("this does not work at the moment");
        System.exit(0);
        final char[] buffer = new char[512];
        try {
            final ContentScraper scraper = new ContentScraper(new DigestURL("http://localhost:8090"), 1000);
            final Transformer transformer = new ContentTransformer();
            final Reader is = new FileReader(args[0]);
            final FileOutputStream fos = new FileOutputStream(new File(args[0] + ".out"));
            final Writer os = new TransformerWriter(fos, UTF8.charset, scraper, transformer, false);
            int i;
            while ((i = is.read(buffer)) > 0) os.write(buffer, 0, i);
            os.close();
            fos.close();
            is.close();
            scraper.print();
        } catch (final MalformedURLException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }

}