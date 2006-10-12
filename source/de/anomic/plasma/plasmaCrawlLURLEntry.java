package de.anomic.plasma;

import java.io.IOException;
import java.util.Date;

import de.anomic.net.URL;
import de.anomic.kelondro.kelondroRow;
import de.anomic.index.indexEntry;

public interface plasmaCrawlLURLEntry {

    public kelondroRow.Entry toRowEntry() throws IOException;

    public String hash();

    public URL url();

    public String descr();

    public Date moddate();

    public Date loaddate();

    public String referrerHash();

    public char doctype();

    public int copyCount();

    public boolean local();

    public int quality();

    public String language();

    public int size();

    public int wordCount();

    public String snippet();

    public indexEntry word();

    public boolean isOlder(plasmaCrawlLURLEntry other);

    public String toString(String snippet);

    public String toString();

    public void print();

}
