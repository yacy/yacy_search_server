package net.yacy.contentcontrol;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;

public class SMWListSyncThread {

	private final Switchboard sb;
	private Boolean locked = false;
	private String lastsync = "1900-01-01T01:00:00";
	private String currenttimestamp = "1900-01-01T01:00:00";
	private long offset = 0;
	private final long limit = 500;
	private long currentmax = 0;
	private boolean runningjob = false;
	
	private String targetList;
	private String parameters;
	private String query;
	
	public static Boolean dirty = false;

	public SMWListSyncThread(final Switchboard sb, final String targetList, final String query, final String parameters, final Boolean purgeOnInit) {
        this.sb = sb;
        this.targetList = targetList;
        this.parameters = parameters;
        this.query = query;
		if (purgeOnInit) {
			this.sb.tables.clear(targetList);

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

	public final void run() {

		if (!this.locked) {
			this.locked = true;
			if (this.sb.getConfigBool("contentcontrol.smwimport.enabled", false) == true) {
				
				if (!this.runningjob) {
					
					// we have to count all new elements first
					try {
						if (!this.sb.getConfig("contentcontrol.smwimport.baseurl","").equals("")) {
							URL urlCount;

							urlCount = new URL(
									this.sb.getConfig(
											"contentcontrol.smwimport.baseurl",
											"")
											+ wikiurlify ("/[["+this.query+"]] [[Modification date::>" +this.lastsync+ "]]")
											
											+ wikiurlify (this.parameters)
											
											+ "/mainlabel%3D"
											+ "/offset%3D0"
											+ "/limit%3D200000"
											+ "/format%3Dystat");

							String reply = UTF8.String(new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent).GETbytes(urlCount.toString(), null, null, false));
							String overallcount = reply.split(",")[0];
							String lastsyncstring = reply.split(",")[1];
							this.currentmax = Integer.parseInt(overallcount);

							if (this.currentmax > 0) {
								ConcurrentLog.info("SMWLISTSYNC",
										"import job counts "
												+ this.currentmax
												+ " new elements between "
												+ this.lastsync + " and "
												+ this.currenttimestamp);

								this.currenttimestamp = this.lastsync;

								this.runningjob = true;
								this.lastsync = lastsyncstring;
								this.offset = 0;
							}
						} else {
							ConcurrentLog.warn("SMWLISTSYNC",
									"No SMWimport URL defined");
						}
					} catch (final MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (final IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					
				} else {
					
					// there are new elements to be imported
					ConcurrentLog.info("SMWLISTSYNC",
							"importing max. " + this.limit
									+ " elements at " + this.offset + " of "
									+ this.currentmax + ", since "
									+ this.currenttimestamp);
					URL urlImport;
					try {
						if (!this.sb.getConfig("contentcontrol.smwimport.baseurl","").equals("")) {
							urlImport = new URL(
									this.sb.getConfig(
											"contentcontrol.smwimport.baseurl",
											"")
											+ wikiurlify ("/[["+this.query+"]] [[Modification date::>" +this.currenttimestamp+ "]]")

											+ wikiurlify (this.parameters)
											
											+ "/mainlabel%3D"
											+ "/syntax%3Dobsolete"
											+ "/offset%3D" + this.offset
											+ "/limit%3D" + this.limit
											+ "/format%3Djson");
							
							this.offset += this.limit;
							if (this.offset > this.currentmax) {
								this.runningjob = false;
							}

							InputStreamReader reader = null;
							try {
								reader = new InputStreamReader(
										urlImport.openStream(), "UTF-8");
							} catch (final Exception e) {
								ConcurrentLog.logException(e);
								this.runningjob = false;
							}

							if (reader != null) {
								SMWListImporterFormatObsolete smwListImporter = null;
								try {
									smwListImporter = new SMWListImporterFormatObsolete(
											reader, 200);
								} catch (final Exception e) {
									// TODO: display an error message
									ConcurrentLog.logException(e);
									this.runningjob = false;
								}
								Thread t;
								SMWListRow row;
								t = new Thread(smwListImporter,"SMW List Importer");
								t.start();
								while ((row = smwListImporter.take()) != SMWListRow.POISON) {
									if (row == SMWListRow.EMPTY) {
										this.runningjob = false;
									} else {
										try {
											this.sb.tables.insert(targetList, row.getData());
											
											dirty = true;

										} catch (final Exception e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								}
							} 
						}
						

					} catch (final MalformedURLException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
					
				}
				this.locked = false;
			}
		}
		return;
	}

}
