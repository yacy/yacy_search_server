package net.yacy.contentcontrol;

import net.yacy.kelondro.blob.Tables;

public class SMWListRow {
	
	private Tables.Data data;
	
	public static final SMWListRow POISON = new SMWListRow();
	public static final SMWListRow EMPTY = new SMWListRow();
	
	public SMWListRow() {
		this.data = new Tables.Data();
	}
	
	public void add (String key, String value) {
		this.data.put(key, value);
	}
	
	public Tables.Data getData() {
		return this.data;
	}

}
