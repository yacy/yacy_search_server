package net.yacy.interaction.contentcontrol;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import de.anomic.data.ymark.YMarkEntry;
import de.anomic.data.ymark.YMarkSMWJSONImporter;
import de.anomic.data.ymark.YMarkUtil;

public class ContentControlImportThread {
	
	private Switchboard sb;
	
	private Boolean locked = false;
	
	private String lastsync = "1900-01-01T01:00:00";
	
	private String currenttimestamp = "1900-01-01T01:00:00";
	
	private long offset = 0;
	
	private long limit = 500;
	
	private long currentmax = 0;
	
	private boolean runningjob = false;

	public ContentControlImportThread(final Switchboard sb) {
        final long time = System.currentTimeMillis();

        this.sb = sb;
        
        
		if (this.sb.getConfigBool("contentcontrol.smwimport.purgelistoninit",
				false)) {
			this.sb.tables.clear(this.sb.getConfig(
					"contentcontrol.smwimport.targetlist", "contentcontrol"));

		}
        
    }
	
	private final String wikiurlify (String s) {
		
		String ret = s;
		
		ret = ret.replace("-", "-2D");
		ret = ret.replace("+", "-2B");
		ret = ret.replace(" ", "-20");
		
		ret = ret.replace("[", "-5B");
		ret = ret.replace("]", "-5D");
		
		ret = ret.replace(":", "-3A");
		ret = ret.replace(">", "-3E");
		
		ret = ret.replace("?", "-3F");
		
		
		return ret;
	}
	
	@SuppressWarnings("deprecation")
	public final void run() {

		if (!locked) {

			locked = true;

			if (sb.getConfigBool("contentcontrol.smwimport.enabled", false) == true) {

				if (runningjob) {

					Log.logInfo("CONTENTCONTROL",
							"CONTENTCONTROL importing max. " + limit
									+ " elements at " + offset + " of "
									+ currentmax + ", since "
									+ currenttimestamp);

					URL bmks_json;

					String currenttimestampurl = wikiurlify (currenttimestamp);

					try {

						if (!sb.getConfig("contentcontrol.smwimport.baseurl",
								"").equals("")) {
							
							

							bmks_json = new URL(
									sb.getConfig(
											"contentcontrol.smwimport.baseurl",
											"")
											+ wikiurlify ("/[[Category:Web Page]] [[Modification date::>" +currenttimestamp+ "]]")
													
											+ wikiurlify ("/?Url/?Filter/?Article has average rating/?Category")
											+ "/mainlabel%3D" 
											+ "/offset%3D" + offset
											+ "/limit%3D" + limit
											+ "/format%3Djson");

							offset += limit;

							if (offset > currentmax) {
								runningjob = false;
							}

							InputStreamReader reader = null;
							try {
								reader = new InputStreamReader(
										bmks_json.openStream(), "UTF-8");
							} catch (Exception e) {

								Log.logException(e);
								runningjob = false;
							}

							if (reader != null) {
								YMarkSMWJSONImporter bookmarkImporter = null;
								try {
									bookmarkImporter = new YMarkSMWJSONImporter(
											reader, 200, "");
								} catch (final Exception e) {
									// TODO: display an error message
									Log.logException(e);
									runningjob = false;
								}

								Thread t;
								YMarkEntry bmk;

								t = new Thread(bookmarkImporter,
										"YMarks - Network bookmark importer");
								t.start();

								while ((bmk = bookmarkImporter.take()) != YMarkEntry.POISON) {

									if (bmk == YMarkEntry.EMPTY) {

										runningjob = false;

									} else {

										try {
											sb.tables.bookmarks.addBookmark(
													sb.getConfig("contentcontrol.smwimport.targetlist", "contentcontrol"), bmk,
													true, true);

										} catch (Exception e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								}

							} else {

							}
						}

						else {

						}

					} catch (MalformedURLException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}

				} else {

					try {

						if (!sb.getConfig("contentcontrol.smwimport.baseurl",
								"").equals("")) {

							URL bmks_count;

							bmks_count = new URL(
									sb.getConfig(
											"contentcontrol.smwimport.baseurl",
											"")
											+ wikiurlify ("/[[Category:Web Page]] [[Modification date::>" +lastsync+ "]]")
											
											
											+ wikiurlify ("/?Url/?Filter/?Article has average rating/?Category")
											+ "/mainlabel%3D" 
											+ "/format%3Dsupercount");

							String reply = UTF8.String(new HTTPClient()
									.GETbytes(bmks_count.toString()));

							String overallcount = reply.split(",")[0];

							String lastsyncstring = reply.split(",")[1];

							currentmax = Integer.parseInt(overallcount);

							if (currentmax > 0) {

								Log.logInfo("CONTENTCONTROL",
										"CONTENTCONTROL import job counts "
												+ currentmax
												+ " new elements between "
												+ lastsync + " and "
												+ currenttimestamp);

								currenttimestamp = lastsync;

								runningjob = true;
								lastsync = lastsyncstring;
								offset = 0;
							}
						} else {
							Log.logWarning("CONTENTCONTROL",
									"No SMWimport URL defined");
						}

					} catch (MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

				locked = false;

			}
		}

		return;
	}

}
