package net.yacy.data.ymark;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;

import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class YMarkSMWJSONImporter implements Runnable, ContentHandler{

	// Importer Variables
	private final ArrayBlockingQueue<YMarkEntry> bookmarks;
    private final Reader bmk_file;
    private final String RootFolder;
    private final StringBuilder folderstring;
    private YMarkEntry bmk;
    private final JSONParser parser;

    //private boolean empty = true;
    //private int count = 0;

    // Parser Variables
	private final StringBuilder value;
	private final StringBuilder key;
	//private final StringBuilder date;
	private final HashMap<String,String> obj;

	private Boolean isBookmark;

	public YMarkSMWJSONImporter(final Reader bmk_file, final int queueSize, final String root) {
        this.bookmarks = new ArrayBlockingQueue<YMarkEntry>(queueSize);
        this.bmk_file = bmk_file;
        this.RootFolder = root;
        this.folderstring = new StringBuilder(YMarkTables.BUFFER_LENGTH);
        this.folderstring.append(this.RootFolder);
        this.bmk = new YMarkEntry();

        this.parser = new JSONParser();

	    this.value = new StringBuilder(128);
	    this.key = new StringBuilder(16);
		//this.date = new StringBuilder(32);
	    this.obj = new HashMap<String,String>();

		this.isBookmark = false;
		//this.empty = true;
		//this.count = 0;
	}

	@Override
    public void startJSON() throws ParseException, IOException {
	}

	@Override
    public void endJSON() throws ParseException, IOException {
	}

	@Override
    public boolean startArray() throws ParseException, IOException {
		final String key = this.key.toString();

		if(key.equals("items") ) {

			this.isBookmark = true;
			//this.count = 0;

		}
		return true;
	}

	@Override
    public boolean endArray() throws ParseException, IOException {

		return true;
	}

	@Override
    public boolean startObject() throws ParseException, IOException {

		return true;
	}

	@Override
    public boolean endObject() throws ParseException, IOException {

		if(this.isBookmark) {

			if(this.obj.containsKey("category")) {
				String catstr = this.obj.get("category");

				HashSet<String> tags = YMarkUtil.keysStringToSet (catstr);

				HashSet<String> categories = YMarkUtil.keysStringToSet("");

				for (String c: tags) {

					c = c.split(":")[1];

					c = c.replace("/",	"_");
					c = c.replace(" ",	"_");

					if (!c.equals("") && (!c.equals(" "))) {
						categories.add ("sc:"+c);
					}

				}

				if (!Switchboard.getSwitchboard().getConfig("contentcontrol.smwimport.defaultcategory", "").equals("")) {
					categories.add ("sc:"+Switchboard.getSwitchboard().getConfig("contentcontrol.smwimport.defaultcategory", ""));
				}

				catstr = YMarkUtil.keySetToString(categories);

				this.bmk.put(YMarkEntry.BOOKMARK.TAGS.key(), catstr);
			}

			if(this.obj.containsKey("article_has_average_rating")) {
				this.bmk.put(YMarkEntry.BOOKMARK.STARRATING.key(),this.obj.get("article_has_average_rating"));
			}

			this.bmk.put(YMarkEntry.BOOKMARK.TITLE.key(),this.obj.get("label"));
			this.bmk.put(YMarkEntry.BOOKMARK.URL.key(),this.obj.get("url"));
			if(this.obj.containsKey("filter")) {
				this.bmk.put(YMarkEntry.BOOKMARK.FILTER.key(),this.obj.get("filter"));
			} else {
				this.bmk.put(YMarkEntry.BOOKMARK.FILTER.key(),"");
			}
			try {
				this.bookmarks.put(this.bmk);
				//this.count++;
			} catch (InterruptedException e) {
				Log.logException(e);
			}
			this.obj.clear();
			this.bmk = new YMarkEntry();
		}

		return true;
	}

	@Override
    public boolean startObjectEntry(String key) throws ParseException, IOException {
			this.key.setLength(0);
			this.key.append(key);

		return true;
	}

	@Override
    public boolean primitive(Object value) throws ParseException, IOException {

			this.value.setLength(0);
			if(value instanceof java.lang.String) {
				this.value.append((String)value);
			} else if(value instanceof java.lang.Boolean) {
				this.value.append(value);
			} else if(value instanceof java.lang.Number) {
				this.value.append(value);
			}

		return true;
	}

	@Override
    public boolean endObjectEntry() throws ParseException, IOException {

			final String key = this.key.toString();
			final String value = this.value.toString();

			this.obj.put(key, value);

		return true;
	}

	@Override
    public void run() {
		try {
			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "SMWJSON Importer run()");
			//this.empty = true;
			this.parser.parse(this.bmk_file, this, true);

		} catch (IOException e) {
			Log.logException(e);
		} catch (ParseException e) {
			Log.logException(e);
		} finally {

			try {
				Log.logInfo(YMarkTables.BOOKMARKS_LOG, "SMWJSON Importer inserted poison pill in queue");
				this.bookmarks.put(YMarkEntry.POISON);
			} catch (InterruptedException e) {
				Log.logException(e);
			}
		}
	}

    public YMarkEntry take() {
        try {
            return this.bookmarks.take();
        } catch (InterruptedException e) {
            Log.logException(e);
            return null;
        }
    }
}
