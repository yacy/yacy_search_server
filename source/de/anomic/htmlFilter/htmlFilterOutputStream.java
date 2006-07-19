// htmlFilterOutputStream.java
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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

import de.anomic.net.URL;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.Properties;
import de.anomic.server.serverByteBuffer;

public final class htmlFilterOutputStream extends OutputStream {

    public static final byte lb = (byte) '<';
    public static final byte rb = (byte) '>';
    public static final byte dash = (byte) '-';
    public static final byte excl = (byte) '!';
    public static final byte singlequote = (byte) 39;
    public static final byte doublequote = (byte) 34;

    private OutputStream out;
    private serverByteBuffer buffer;
    private String       filterTag;
    private Properties   filterOpts;
    private serverByteBuffer filterCont;
    private htmlFilterScraper scraper;
    private htmlFilterTransformer transformer;
    private boolean inSingleQuote;
    private boolean inDoubleQuote;
    private boolean inComment;
    private boolean inScript;
    private boolean binaryUnsuspect;
    private boolean passbyIfBinarySuspect;

    public htmlFilterOutputStream(OutputStream out, htmlFilterScraper scraper,
                                  htmlFilterTransformer transformer,
                                  boolean passbyIfBinarySuspect) {
        this.out           = out;
        this.scraper       = scraper;
        this.transformer   = transformer;
        this.buffer        = new serverByteBuffer(1024);
        this.filterTag     = null;
        this.filterOpts    = null;
        this.filterCont    = null;
        this.inSingleQuote = false;
        this.inDoubleQuote = false;
        this.inComment     = false;
        this.inScript      = false;
        this.binaryUnsuspect = true;
        this.passbyIfBinarySuspect = passbyIfBinarySuspect;
    }

    public static byte[] genTag0raw(String tagname, boolean opening, byte[] tagopts) {
        serverByteBuffer bb = new serverByteBuffer(tagname.length() + tagopts.length + 3);
        bb.append((byte) '<');
        if (!opening) {
            bb.append((byte) '/');
        }
        bb.append(tagname.getBytes());
        if (tagopts.length > 0) {
//          if (tagopts[0] == (byte) 32)
            bb.append(tagopts);
//          else bb.append((byte) 32).append(tagopts);
        }
        bb.append((byte) '>');
        return bb.getBytes();
    }

    public static byte[] genTag1raw(String tagname, byte[] tagopts, byte[] text) {
        serverByteBuffer bb = new serverByteBuffer(2 * tagname.length() + tagopts.length + text.length + 5);
        bb.append((byte) '<').append(tagname.getBytes());
        if (tagopts.length > 0) {
//          if (tagopts[0] == (byte) 32)
            bb.append(tagopts);
//          else bb.append((byte) 32).append(tagopts);
        }
        bb.append((byte) '>');
        bb.append(text);
        bb.append((byte) '<').append((byte) '/').append(tagname.getBytes()).append((byte) '>');
        return bb.getBytes();
    }

    public static byte[] genTag0(String tagname, Properties tagopts, byte quotechar) {
        byte[] tagoptsx = (tagopts.size() == 0) ? null : genOpts(tagopts, quotechar);
        serverByteBuffer bb = new serverByteBuffer(tagname.length() + ((tagoptsx == null) ? 0 : (tagoptsx.length + 1)) + tagname.length() + 2).append((byte) '<').append(tagname.getBytes());
        if (tagoptsx != null) {
            bb = bb.append((byte) 32).append(tagoptsx);
        }
        bb = bb.append((byte) '>');
        return bb.getBytes();
    }

    public static byte[] genTag1(String tagname, Properties tagopts, byte[] text, byte quotechar) {
        byte[] gt0 = genTag0(tagname, tagopts, quotechar);
        return new serverByteBuffer(gt0, gt0.length + text.length + tagname.length() + 3).append(text).append(("</" + tagname + ">").getBytes()).getBytes();
    }

    // a helper method for pretty-printing of properties for html tags
    public static byte[] genOpts(Properties prop, byte quotechar) {
        Enumeration e = prop.propertyNames();
        serverByteBuffer bb = new serverByteBuffer(prop.size() * 40);
        String key;
        while (e.hasMoreElements()) {
            key = (String) e.nextElement();
            bb = bb.append((byte) 32).append(key.getBytes()).append((byte) '=');
            bb = bb.append(quotechar).append(prop.getProperty(key).getBytes()).append(quotechar);
        }
        if (bb.length() > 0) return bb.getBytes(1);
        return bb.getBytes();
    }

    private byte[] filterTag(String tag, boolean opening, byte[] content, byte quotechar) {
//      System.out.println("FILTER1: filterTag=" + ((filterTag == null) ? "null" : filterTag) + ", tag=" + tag + ", opening=" + ((opening) ? "true" : "false") + ", content=" + new String(content)); // debug
        if (filterTag == null) {
            // we are not collection tag text
            if (tag == null) {
                // and this is not a tag opener/closer
                if (scraper != null) scraper.scrapeText(content);
                if (transformer != null) return transformer.transformText(content);
                return content;
            }
            
            // we have a new tag
            if (opening) {
                if ((scraper != null) && (scraper.isTag0(tag))) {
                    // this single tag is collected at once here
                    scraper.scrapeTag0(tag, new serverByteBuffer(content).propParser());
                }
                if ((transformer != null) && (transformer.isTag0(tag))) {
                    // this single tag is collected at once here
                    return transformer.transformTag0(tag, new serverByteBuffer(content).propParser(), quotechar);
                } else if (((scraper != null) && (scraper.isTag1(tag))) ||
                           ((transformer != null) && (transformer.isTag1(tag)))) {
                    // ok, start collecting
                    filterTag = tag;
                    filterOpts = new serverByteBuffer(content).propParser();
                    filterCont = new serverByteBuffer();
                    return new byte[0];
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
            if (scraper != null) scraper.scrapeText(content);
            if (transformer != null) {
                filterCont.append(transformer.transformText(content));
            } else {
                filterCont.append(content);
            }
            return new byte[0];
        }
        
        // it's a tag! which one?
        if ((opening) || (!(tag.equals(filterTag)))) {
            // this tag is not our concern. just add it
            filterCont.append(genTag0raw(tag, opening, content));
            return new byte[0];
        }
        
        // it's our closing tag! return complete result.
        byte[] ret;
        if (scraper != null) scraper.scrapeTag1(filterTag, filterOpts, filterCont.getBytes());
        if (transformer != null) {
            ret = transformer.transformTag1(filterTag, filterOpts, filterCont.getBytes(), quotechar);
        } else {
            ret = genTag1(filterTag, filterOpts, filterCont.getBytes(), quotechar);
        }
        filterTag = null;
        filterOpts = null;
        filterCont = null;
        return ret;
    }

    private byte[] filterFinalize(byte quotechar) {
        if (filterTag == null) {
            return new byte[0];
        }
        
        // it's our closing tag! return complete result.
        byte[] ret;
        if (scraper != null) scraper.scrapeTag1(filterTag, filterOpts, filterCont.getBytes());
        if (transformer != null) {
            ret = transformer.transformTag1(filterTag, filterOpts, filterCont.getBytes(), quotechar);
        } else {
            ret = genTag1(filterTag, filterOpts, filterCont.getBytes(), quotechar);
        }
        filterTag = null;
        filterOpts = null;
        filterCont = null;
        return ret;
    }

    private byte[] filterSentence(byte[] in, byte quotechar) {
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
                byte[] text = new byte[in.length - tagend - 1];
                System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
                return filterTag(tag, false, text, quotechar);
            }
            
            // an opening tag
            tagend = tagEnd(in, 1);
            tag = new String(in, 1, tagend - 1);
            byte[] text = new byte[in.length - tagend - 1];
            System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
            return filterTag(tag, true, text, quotechar);
        }
        
        // a text
        return filterTag(null, true, in, quotechar);
    }

    private static int tagEnd(byte[] tag, int start) {
        char c;
        for (int i = start; i < tag.length; i++) {
            c = (char) tag[i];
            if (c != '!' && c != '-' &&
                (c < '0' || c > '9') &&
                (c < 'a' || c > 'z') &&
                (c < 'A' || c > 'Z')
            ) return i;
        }
        return tag.length - 1;
    }

    public void write(int b) throws IOException {
        write((byte) (b & 0xff));
    }

    private void write(byte b) throws IOException {
//      System.out.println((char) b);
        if ((binaryUnsuspect) && (binaryHint(b))) {
            binaryUnsuspect = false;
            if (passbyIfBinarySuspect) finalize();
        }

        if (binaryUnsuspect || !passbyIfBinarySuspect) {
            byte[] filtered;
            if (inSingleQuote) {
                buffer.append(b);
                if (b == singlequote) inSingleQuote = false;
                // check error cases
                if ((b == rb) && (buffer.byteAt(0) == lb)) {
                    inSingleQuote = false;
                    // the tag ends here. after filtering: pass on
                    filtered = filterSentence(buffer.getBytes(), singlequote);
                    if (out != null) { out.write(filtered); }
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else if (inDoubleQuote) {
                buffer.append(b);
                if (b == doublequote) inDoubleQuote = false;
                // check error cases
                if (b == rb && buffer.byteAt(0) == lb) {
                    inDoubleQuote = false;
                    // the tag ends here. after filtering: pass on
                    filtered = filterSentence(buffer.getBytes(), doublequote);
                    if (out != null) out.write(filtered);
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else if (inComment) {
                buffer.append(b);
                if (b == rb &&
                    buffer.length() > 6 &&
                    buffer.byteAt(buffer.length() - 3) == dash) {
                    // comment is at end
                    inComment = false;
                    if (out != null) out.write(buffer.getBytes());
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else if (inScript) {
                buffer.append(b);
                int bufferLength = buffer.length();
                if ((b == rb) && (bufferLength > 14) &&
                    (buffer.byteAt(bufferLength - 8) == (byte) '/') &&
                    (buffer.byteAt(bufferLength - 7) == (byte) 's') &&
                    (buffer.byteAt(bufferLength - 6) == (byte) 'c') &&
                    (buffer.byteAt(bufferLength - 5) == (byte) 'r') &&
                    (buffer.byteAt(bufferLength - 4) == (byte) 'i') &&
                    (buffer.byteAt(bufferLength - 3) == (byte) 'p') &&
                    (buffer.byteAt(bufferLength - 2) == (byte) 't')) {
                    // script is at end
                    inScript = false;
                    if (out != null) out.write(buffer.getBytes());
                    // buffer = new serverByteBuffer();
                    buffer.reset();
                }
            } else {
                if (buffer.length() == 0) {
                    if (b == rb) {
                        // very strange error case; we just let it pass
                        if (out != null) out.write(b);
                    } else {
                        buffer.append(b);
                    }
                } else if (buffer.byteAt(0) == lb) {
                    if (b == singlequote) inSingleQuote = true;
                    if (b == doublequote) inDoubleQuote = true;
                    // fill in tag text
                    if ((buffer.length() == 3) && (buffer.byteAt(1) == excl) &&
                        (buffer.byteAt(2) == dash) && (b == dash)) {
                        // this is the start of a comment
                        inComment = true;
                        buffer.append(b);
                    } else if ((buffer.length() == 6) &&
                               (buffer.byteAt(1) == (byte) 's') &&
                               (buffer.byteAt(2) == (byte) 'c') &&
                               (buffer.byteAt(3) == (byte) 'r') &&
                               (buffer.byteAt(4) == (byte) 'i') &&
                               (buffer.byteAt(5) == (byte) 'p') &&
                                             (b  == (byte) 't')) {
                        // this is the start of a comment
                        inScript = true;
                        buffer.append(b);
                    } else if (b == rb) {
                        buffer.append(b);
                        // the tag ends here. after filtering: pass on
                        filtered = filterSentence(buffer.getBytes(), doublequote);
                        if (out != null) out.write(filtered);
                        // buffer = new serverByteBuffer();
                        buffer.reset();
                    } else if (b == lb) {
                        // this is an error case
                        // we consider that there is one rb missing
                        if (buffer.length() > 0) {
                            filtered = filterSentence(buffer.getBytes(), doublequote);
                            if (out != null) out.write(filtered);
                        }
                        // buffer = new serverByteBuffer();
                        buffer.reset();
                        buffer.append(b);
                    } else {
                        buffer.append(b);
                    }
                } else {
                    // fill in plain text
                    if (b == lb) {
                        // the text ends here
                        if (buffer.length() > 0) {
                            filtered = filterSentence(buffer.getBytes(), doublequote);
                            if (out != null) out.write(filtered);
                        }
                        // buffer = new serverByteBuffer();
                        buffer.reset();
                        buffer.append(b);
                    } else {
                        // simply append
                        buffer.append(b);
                    }
                }
            }
        } else {
            out.write(b);
        }
    }

    public void write(byte b[]) throws IOException {
        this.write(b, 0, b.length);
    }

    public void write(byte b[], int off, int len) throws IOException {
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
        byte quotechar = (inSingleQuote) ? singlequote : doublequote;
        if (buffer != null) {
            if (buffer.length() > 0) {
                byte[] filtered = filterSentence(buffer.getBytes(), quotechar);
                if (out != null) out.write(filtered);
            }
            buffer = null;
        }
        byte[] finalized = filterFinalize(quotechar);
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

    private static boolean binaryHint(byte b) {
        if (b < 0) return false;
        if (b > 31) return false;
        if ((b == 8) || (b == 9) || (b == 10) || (b == 13)) return false;
//      return false;
//      System.out.println("BINARY HINT: " + (int) b);
        return true;
    }

    public boolean binarySuspect() {
        return !binaryUnsuspect;
    }

    public static void main(String[] args) {
        // takes one argument: a file name 
        if (args.length != 1) return;
        byte[] buffer = new byte[512];
        try {
            htmlFilterContentScraper scraper = new htmlFilterContentScraper(new URL("http://localhost:8080"));
            htmlFilterTransformer transformer = new htmlFilterContentTransformer();
            transformer.init("gettext");
            InputStream is = new FileInputStream(args[0]);
            FileOutputStream fos = new FileOutputStream(new File(args[0] + ".out"));
            OutputStream os = new htmlFilterOutputStream(fos, scraper, transformer, false);
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