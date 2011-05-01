package de.anomic.data.ymark;

import java.util.Comparator;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.blob.Tables;

public class TablesRowComparator implements Comparator<Tables.Row> {
    private String sortname;
    
    public TablesRowComparator(final String sortname) {
        setSortName(sortname);
    }
    
    public void setSortName(final String sortname) {
        this.sortname = sortname;
    }

    public int compare(Tables.Row row0, Tables.Row row1) {
        if(row0 != null && row1 != null) {
            if(row0.containsKey(this.sortname) && row1.containsKey(this.sortname)) {
               String name1 = UTF8.String(row0.get(this.sortname)).toLowerCase();
               String name2 = UTF8.String(row1.get(this.sortname)).toLowerCase();
               return name1.compareTo(name2);
            }
        }
        return 0;
    }
}