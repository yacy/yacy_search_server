package net.yacy.data.ymark;

import java.util.Comparator;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.kelondro.blob.Tables;

public class TablesRowComparator implements Comparator<Tables.Row> {
    private String sortname;
    private boolean desc;

    public TablesRowComparator(final String sortname, final String sortorder) {
        setSortName(sortname);
        if(sortorder.equals("desc"))
        	this.desc = true;
    	else
    		this.desc = false;
    }

    public void setSortName(final String sortname) {
        this.sortname = sortname;
    }

    @Override
    public int compare(Tables.Row row0, Tables.Row row1) {
        if(row0 != null && row1 != null) {
            if(row0.containsKey(this.sortname) && row1.containsKey(this.sortname)) {
               String name1 = UTF8.String(row0.get(this.sortname)).toLowerCase();
               String name2 = UTF8.String(row1.get(this.sortname)).toLowerCase();
               return (this.desc) ? name2.compareTo(name1) : name1.compareTo(name2);
            }
        }
        return 0;
    }
}