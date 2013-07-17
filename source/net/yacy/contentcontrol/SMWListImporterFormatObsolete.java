package net.yacy.contentcontrol;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.util.ConcurrentLog;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class SMWListImporterFormatObsolete implements Runnable{

	private final ArrayBlockingQueue<SMWListRow> listEntries;
    private final Reader importFile;
    private final JSONParser parser;

	public SMWListImporterFormatObsolete(final Reader importFile, final int queueSize) {
        this.listEntries = new ArrayBlockingQueue<SMWListRow>(queueSize);
        this.importFile = importFile;
        this.parser = new JSONParser();

	}


	@Override
    public void run() {
		try {
			ConcurrentLog.info("SMWLISTSYNC", "Importer run()");
			Object obj = this.parser.parse(this.importFile);
			
			JSONObject jsonObject = (JSONObject) obj;
			
			JSONArray items = (JSONArray) jsonObject.get("items");
			
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> iterator = items.iterator();
			while (iterator.hasNext()) {
				this.parseItem (iterator.next());
			}

		} catch (final IOException e) {
			ConcurrentLog.logException(e);
		} catch (final ParseException e) {
			ConcurrentLog.logException(e);
		} finally {

			try {
				ConcurrentLog.info("SMWLISTSYNC", "Importer inserted poison pill in queue");
				this.listEntries.put(SMWListRow.POISON);
			} catch (final InterruptedException e) {
				ConcurrentLog.logException(e);
			}
		}
	}

    private void parseItem(JSONObject jsonObject) {
    	
    	try {    	
    		SMWListRow row = new SMWListRow();
    		@SuppressWarnings("unchecked")
			Iterator<String> iterator = jsonObject.keySet().iterator();
    		
    		while (iterator.hasNext()) {
    			String entryKey = iterator.next();
    			
    			Object value = jsonObject.get (entryKey);
    			String valueKey = "";
    			
    			if (value instanceof java.lang.String) {
    				valueKey = value.toString();
    			} else if (value instanceof JSONArray) {
    				valueKey = jsonListAll ((JSONArray) value);
    			}
    			
    			row.add (entryKey, valueKey);
    		}
    		
    		this.listEntries.put(row);
    		
    	} catch (final Exception e) {
    		ConcurrentLog.info("SMWLISTSYNC", "import of entry failed");
    	}
		
	}


	private String jsonListAll(JSONArray value) {
		String res = "";
		
		@SuppressWarnings("unchecked")
		Iterator<Object> iterator = value.listIterator();
		while (iterator.hasNext()) {
			Object val = iterator.next();
			res += val.toString()+",";
		}
		
		if (res.endsWith (",")) {
			res = res.substring (0, res.length()-1);
		}
		
		return res;
	}


	public SMWListRow take() {
        try {
            return this.listEntries.take();
        } catch (final InterruptedException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }
}
