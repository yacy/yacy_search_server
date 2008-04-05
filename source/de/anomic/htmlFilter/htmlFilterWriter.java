// htmlFilterOutputStream.java
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// $LastChangedDate: 2006-09-15 17:01:25 +0200 (Fr, 15 Sep 2006) $
// $LastChangedRevision: 2598 $
// $LastChangedBy: theli $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software;the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
 This class implements an output stream. Any data written to that output
 is automatically parsed.
 After finishing with writing, the htmlFilter can be read out.

 */

package de.anomic.htmlFilter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.Properties;

import de.anomic.server.serverCharBuffer;
import de.anomic.yacy.yacyURL;

public final class htmlFilterWriter extends Writer {

    public static final char lb = '<';
    public static final char rb = '>';
    public static final char dash = '-';
    public static final char excl = '!';
    public static final char singlequote = '\'';
    public static final char doublequote = '"';

    private OutputStream outStream;
    private OutputStreamWriter out;
    private serverCharBuffer buffer;
    private String       filterTag;
    private Properties   filterOpts;
    private serverCharBuffer filterCont;
    private htmlFilterScraper scraper;
    private htmlFilterTransformer transformer;
    private boolean inSingleQuote;
    private boolean inDoubleQuote;
    private boolean inComment;
    private boolean inScript;
    private boolean binaryUnsuspect;
    private boolean passbyIfBinarySuspect;

    public htmlFilterWriter(
            OutputStream outStream,
            String outputStreamCharset,
            htmlFilterScraper scraper,
            htmlFilterTransformer transformer,
            boolean passbyIfBinarySuspect
    ) throws UnsupportedEncodingException {
        this.outStream     = outStream;
        this.scraper       = scraper;
        this.transformer   = transformer;
        this.buffer        = new serverCharBuffer(1024);
        this.filterTag     = null;
        this.filterOpts    = null;
        this.filterCont    = null;
        this.inSingleQuote = false;
        this.inDoubleQuote = false;
        this.inComment     = false;
        this.inScript      = false;
        this.binaryUnsuspect = true;
        this.passbyIfBinarySuspect = passbyIfBinarySuspect;
        
        if (this.outStream != null) {
            this.out = new OutputStreamWriter(this.outStream,(outputStreamCharset == null)?"UTF-8":outputStreamCharset);
        }        
    }

    public static char[] genTag0raw(String tagname, boolean opening, char[] tagopts) {
            serverCharBuffer bb = new serverCharBuffer(tagname.length() + tagopts.length + 3);
            bb.append((int)'<');
            if (!opening) {
                bb.append((int)'/');
            }
            bb.append(tagname);
            if (tagopts.length > 0) {
//              if (tagopts[0] == (byte) 32)
                bb.append(tagopts);
//              else bb.append((byte) 32).append(tagopts);
            }
            bb.append((int)'>');
            return bb.getChars();
    }

    public static char[] genTag1raw(String tagname, char[] tagopts, char[] text) {
            serverCharBuffer bb = new serverCharBuffer(2 * tagname.length() + tagopts.length + text.length + 5);
            bb.append((int)'<').append(tagname);
            if (tagopts.length > 0) {
//              if (tagopts[0] == (byte) 32)
                bb.append(tagopts);
//              else bb.append((byte) 32).append(tagopts);
            }
            bb.append((int)'>');
            bb.append(text);
            bb.append((int)'<').append((int)'/').append(tagname).append((int)'>');
            return bb.getChars();
    }

    public static char[] genTag0(String tagname, Properties tagopts, char quotechar) {   
            char[] tagoptsx = (tagopts.size() == 0) ? null : genOpts(tagopts, quotechar);
            serverCharBuffer bb = new serverCharBuffer(tagname.length() + ((tagoptsx == null) ? 0 : (tagoptsx.length + 1)) + tagname.length() + 2);
            bb.append((int)'<').append(tagname);
            if (tagoptsx != null) {
                bb.append(32);
                bb.append(tagoptsx);
            }
            bb.append((int)'>');
            return bb.getChars();          
    }

    public static char[] genTag1(String tagname, Properties tagopts, char[] text, char quotechar) {  
            char[] gt0 = genTag0(tagname, tagopts, quotechar);
            serverCharBuffer cb = new serverCharBuffer(gt0, gt0.length + text.length + tagname.length() + 3);
            cb.append(text).append((int)'<').append((int)'/').append(tagname).append((int)'>');
            return cb.getChars();        
    }

    // a helper method for pretty-printing of properties for html tags
    public static char[] genOpts(Properties prop, char quotechar) {   
            Enumeration<?> e = prop.propertyNames();
            serverCharBuffer bb = new serverCharBuffer(prop.size() * 40);
            String key;
            while (e.hasMoreElements()) {
                key = (String) e.nextElement();
                bb.append(32).append(key).append((int)'=').append((int)quotechar);
                bb.append(prop.getProperty(key));
                bb.append((int)quotechar); 
            }
            if (bb.length() > 0) return bb.getChars(1);
            return bb.getChars(); 
    }

    private char[] filterTag(String tag, boolean opening, char[] content, char quotechar) {
//      System.out.println("FILTER1: filterTag=" + ((filterTag == null) ? "null" : filterTag) + ", tag=" + tag + ", opening=" + ((opening) ? "true" : "false") + ", content=" + new String(content)); // debug
        if (filterTag == null) {
            // we are not collection tag text
            if (tag == null) {
                // and this is not a tag opener/closer
                if (scraper != null) scraper.scrapeText(content, null);
                if (transformer != null) return transformer.transformText(content);
                return content;
            }
            
            // we have a new tag
            if (opening) {
                if ((scraper != null) && (scraper.isTag0(tag))) {
                    // this single tag is collected at once here
                    scraper.scrapeTag0(tag, new serverCharBuffer(content).propParser());
                }
                if ((transformer != null) && (transformer.isTag0(tag))) {
                    // this single tag is collected at once here
                    return transformer.transformTag0(tag, new serverCharBuffer(content).propParser(), quotechar);
                } else if (((scraper != null) && (scraper.isTag1(tag))) ||
                           ((transformer != null) && (transformer.isTag1(tag)))) {
                    // ok, start collecting
                    filterTag = tag;
                    filterOpts = new serverCharBuffer(content).propParser();
                    filterCont = new serverCharBuffer();
                    return new char[0];
                } else {
                     // we ignore that thing and return it again
                     return genTag0raw(tag, true, content);
                }
            }
            
            // we ignore that thing and return it again
            return genTag0raw(tag, false, content);
            
        }
        
        // we are collection tag text for the tag 'filterTag'
        if (tag == null) {
            // go on collecting content
            if (scraper != null) scraper.scrapeText(content, filterTag);
            if (transformer != null) {
                filterCont.append(transformer.transformText(content));
            } else {
                filterCont.append(content);
            }
            return new char[0];
        }
        
        // it's a tag! which one?
        if ((opening) || (!(tag.equals(filterTag)))) {
            // this tag is not our concern. just add it
            filterCont.append(genTag0raw(tag, opening, content));
            return new char[0];
        }
        
        // it's our closing tag! return complete result.
        char[] ret;
        if (scraper != null) scraper.scrapeTag1(filterTag, filterOpts, filterCont.getChars());
        if (transformer != null) {
            ret = transformer.transformTag1(filterTag, filterOpts, filterCont.getChars(), quotechar);
        } else {
            ret = genTag1(filterTag, filterOpts, filterCont.getChars(), quotechar);
        }
        filterTag = null;
        filterOpts = null;
        filterCont = null;
        return ret;
    }

    private char[] filterFinalize(char quotechar) {
        if (filterTag == null) {
            return new char[0];
        }
        
        // it's our closing tag! return complete result.
        char[] ret;
        if (scraper != null) scraper.scrapeTag1(filterTag, filterOpts, filterCont.getChars());
        if (transformer != null) {
            ret = transformer.transformTag1(filterTag, filterOpts, filterCont.getChars(), quotechar);
        } else {
            ret = genTag1(filterTag, filterOpts, filterCont.getChars(), quotechar);
        }
        filterTag = null;
        filterOpts = null;
        filterCont = null;
        return ret;
    }

    private char[] filterSentence(char[] in, char quotechar) {
        if (in.length == 0) return in;
//      System.out.println("FILTER0: " + new String(in)); // debug
        // scan the string and parse structure
        if (in.length > 2 && in[0] == lb) {
            
            // a tag
            String tag;
            int tagend;
            if (in[1] == '/') {
                // a closing tag
                tagend = tagEnd(in, 2);
                tag = new String(in, 2, tagend - 2); 
                char[] text = new char[in.length - tagend - 1];
                System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
                return filterTag(tag, false, text, quotechar);
            }
            
            // an opening tag
            tagend = tagEnd(in, 1);
            tag = new String(in, 1, tagend - 1);    
            char[] text = new char[in.length - tagend - 1];
            System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
            return filterTag(tag, true, text, quotechar);
        }
        
        // a text
        return filterTag(null, true, in, quotechar);
    }

    private static int tagEnd(char[] tag, int start) {
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

    public void write(int c) throws IOException {
//      System.out.println((char) b);
        if ((binaryUnsuspect) && (binaryHint((char)c))) {
            binaryUnsuspect = false;
            if (passbyIfBinarySuspect) finalize();
        }

        if (binaryUnsuspect || !passbyIfBinarySuspect) {
            char[] filtered;
            if (inSingleQuote) {
                buffer.append(c);
                if (c == singlequote) inSingleQuote = false;
                // check error cases
                if ((c == rb) && (buffer.charAt(0) == lb)) {
                    inSingleQuote = false;
                    // the tag ends here. after filtering: pass on
                    filtered = filterSentence(buffer.getChars(), singlequote);
                    if (out != null) { out.write(filtered); }
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else if (inDoubleQuote) {
                buffer.append(c);
                if (c == doublequote) inDoubleQuote = false;
                // check error cases
                if (c == rb && buffer.charAt(0) == lb) {
                    inDoubleQuote = false;
                    // the tag ends here. after filtering: pass on
                    filtered = filterSentence(buffer.getChars(), doublequote);
                    if (out != null) out.write(filtered);
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else if (inComment) {
                buffer.append(c);
                if (c == rb &&
                    buffer.length() > 6 &&
                    buffer.charAt(buffer.length() - 3) == dash) {
                    // comment is at end
                    inComment = false;
                    if (out != null) out.write(buffer.getChars());
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else if (inScript) {
                buffer.append(c);
                int bufferLength = buffer.length();
                if ((c == rb) && (bufferLength > 14) &&
                    (buffer.charAt(bufferLength - 8) == '/') &&
                    (buffer.charAt(bufferLength - 7) == 's') &&
                    (buffer.charAt(bufferLength - 6) == 'c') &&
                    (buffer.charAt(bufferLength - 5) == 'r') &&
                    (buffer.charAt(bufferLength - 4) == 'i') &&
                    (buffer.charAt(bufferLength - 3) == 'p') &&
                    (buffer.charAt(bufferLength - 2) == 't')) {
                    // script is at end
                    inScript = false;
                    if (out != null) out.write(buffer.getChars());
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else {
                if (buffer.length() == 0) {
                    if (c == rb) {
                        // very strange error case; we just let it pass
                        if (out != null) out.write(c);
                    } else {
                        buffer.append(c);
                    }
                } else if (buffer.charAt(0) == lb) {
                    if (c == singlequote) inSingleQuote = true;
                    if (c == doublequote) inDoubleQuote = true;
                    // fill in tag text
                    if ((buffer.length() == 3) && (buffer.charAt(1) == excl) &&
                        (buffer.charAt(2) == dash) && (c == dash)) {
                        // this is the start of a comment
                        inComment = true;
                        buffer.append(c);
                    } else if ((buffer.length() == 6) &&
                               (buffer.charAt(1) == 's') &&
                               (buffer.charAt(2) == 'c') &&
                               (buffer.charAt(3) == 'r') &&
                               (buffer.charAt(4) == 'i') &&
                               (buffer.charAt(5) == 'p') &&
                                             (c  == 't')) {
                        // this is the start of a comment
                        inScript = true;
                        buffer.append(c);
                    } else if (c == rb) {
                        buffer.append(c);
                        // the tag ends here. after filtering: pass on
                        filtered = filterSentence(buffer.getChars(), doublequote);
                        if (out != null) out.write(filtered);
                        // buffer = new serverByteBuffer();
                        buffer.reset();
                    } else if (c == lb) {
                        // this is an error case
                        // we consider that there is one rb missing
                        if (buffer.length() > 0) {
                            filtered = filterSentence(buffer.getChars(), doublequote);
                            if (out != null) out.write(filtered);
                        }
                        // buffer = new serverByteBuffer();
                        buffer.reset();
                        buffer.append(c);
                    } else {
                        buffer.append(c);
                    }
                } else {
                    // fill in plain text
                    if (c == lb) {
                        // the text ends here
                        if (buffer.length() > 0) {
                            filtered = filterSentence(buffer.getChars(), doublequote);
                            if (out != null) out.write(filtered);
                        }
                        // buffer = new serverByteBuffer();
                        buffer.reset();
                        buffer.append(c);
                    } else {
                        // simply append
                        buffer.append(c);
                    }
                }
            }
        } else {
            out.write(c);
        }
    }

    public void write(char b[]) throws IOException {
        write(b, 0, b.length);
    }

    public void write(char b[], int off, int len) throws IOException {
//      System.out.println(new String(b, off, len));
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0) throw new IndexOutOfBoundsException();
        for (int i = off ; i < (len - off) ; i++) this.write(b[i]);
    }

    public void flush() throws IOException {
        // we cannot flush the current string buffer to prevent that
        // the filter process is messed up
        // instead, we simply flush the underlying output stream
        if (out != null) out.flush();
        // if you want to flush all, call close() at end of writing;
    }

    public void finalize() throws IOException {
        // if we are forced to close, we of course flush the buffer first,
        // then close the connection
        close();
    }

    public void close() throws IOException {
        char quotechar = (inSingleQuote) ? singlequote : doublequote;
        if (buffer != null) {
            if (buffer.length() > 0) {
                char[] filtered = filterSentence(buffer.getChars(), quotechar);
                if (out != null) out.write(filtered);
            }
            buffer = null;
        }
        char[] finalized = filterFinalize(quotechar);
        if (out != null) {
            if (finalized != null) out.write(finalized);
            out.flush();
            out.close();
        }
        filterTag = null;
        filterOpts = null;
        filterCont = null;
//      if (scraper != null) {scraper.close(); scraper = null;}
//      if (transformer != null) {transformer.close(); transformer = null;}
    }

    private static boolean binaryHint(char c) {
        // space, punctiation and symbols, letters and digits (ASCII/latin)
        //if (c >= 31 && c < 128) return false;
        if(c >= 31) return false;
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
        return !binaryUnsuspect;
    }

    public static void main(String[] args) {
        // takes one argument: a file name 
        if (args.length != 1) return;
        char[] buffer = new char[512];
        try {
            htmlFilterContentScraper scraper = new htmlFilterContentScraper(new yacyURL("http://localhost:8080", null));
            htmlFilterTransformer transformer = new htmlFilterContentTransformer();            
            // TODO: this does not work at the moment
            System.exit(0);
            Reader is = new FileReader(args[0]);
            FileOutputStream fos = new FileOutputStream(new File(args[0] + ".out"));
            Writer os = new htmlFilterWriter(fos, "UTF-8",scraper, transformer, false);
            int i;
            while ((i = is.read(buffer)) > 0) os.write(buffer, 0, i);
            os.close();
            fos.close();
            is.close();
            scraper.print();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}