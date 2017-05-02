// IndexImportMediawiki.java
// -------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 04.05.2009 on http://yacy.net
// Frankfurt, Germany
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

import java.io.File;
import java.net.MalformedURLException;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.TransactionManager;
import net.yacy.document.importer.MediawikiImporter;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Import of MediaWiki dump files in the local index.
 */
public class IndexImportMediawiki_p {

	/**
	 * Run conditions :
	 * - no MediaWiki import thread is running : allow to start a new import by filling the "file" parameter
	 * - the MediaWiki import thread is running : returns monitoring information.
	 * @param header servlet request header
	 * @param post request parameters. Supported keys :
	 *            <ul>
	 *            <li>file : a dump URL or file path on this YaCy server local file system</li>
	 *            <li>report : when set, display the currently running thread monitoring info, or the last import report when no one is running.
	 *            Ignored when no import thread is known.</li>
	 *            </ul>
	 * @param env server environment
	 * @return the servlet answer object
	 */
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (MediawikiImporter.job != null && (MediawikiImporter.job.isAlive() || (post != null && post.containsKey("report")))) {
            /* one import is running, or report was explicitly requested : no option to insert anything */
            prop.put("import", 1);
            /* Only refresh automatically when the job is running */
            prop.put("refresh", MediawikiImporter.job.isAlive() ? 1 : 0);
            final String jobErrorMessage = MediawikiImporter.job.status();
            if( jobErrorMessage != null && !jobErrorMessage.isEmpty()) {
            	prop.put("import_status", 1);
            	prop.put("import_status_message", jobErrorMessage);
            }
            prop.put("import_thread", MediawikiImporter.job.isAlive() ? 2 : 0);
            prop.put("import_dump", MediawikiImporter.job.source());
            prop.put("import_count", MediawikiImporter.job.count());
            prop.put("import_speed", MediawikiImporter.job.speed());
            prop.put("import_runningHours", (MediawikiImporter.job.runningTime() / 60) / 60);
            prop.put("import_runningMinutes", (MediawikiImporter.job.runningTime() / 60) % 60);
            prop.put("import_remainingHours", (MediawikiImporter.job.remainingTime() / 60) / 60);
            prop.put("import_remainingMinutes", (MediawikiImporter.job.remainingTime() / 60) % 60);
        } else {
            prop.put("import", 0);
            prop.put("refresh", 0);
           	prop.put("import_prevReport", MediawikiImporter.job != null ? 1 : 0);
            if (post == null) {
                prop.put("import_status", 0);
                
                /* Acquire a transaction token for the next POST form submission */
                final String token = TransactionManager.getTransactionToken(header);
                prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, token);
                prop.put("import_" + TransactionManager.TRANSACTION_TOKEN_PARAM, token);
                
            } else {
                if (post.containsKey("file")) {
                	/* Check the transaction is valid */
                	TransactionManager.checkPostTransaction(header, post);
                	
                    String file = post.get("file");
					MultiProtocolURL sourceURL = null;
					int status = 0;
					String sourceFilePath = "";
					try {
						sourceURL = new MultiProtocolURL(file);
						if(sourceURL.isFile()) {
							final File sourcefile = sourceURL.getFSFile();
							sourceFilePath = sourcefile.getAbsolutePath();
							if (!sourcefile.exists()) {
								status = 2;
							} else if (!sourcefile.canRead()) {
								status = 3;
							} else if (sourcefile.isDirectory()) {
								status = 4;
							}
						}
					} catch (MalformedURLException e) {
						status = 1;
					}
					if (status == 0) {
						MediawikiImporter.job = new MediawikiImporter(sourceURL, sb.surrogatesInPath);
						MediawikiImporter.job.start();
						prop.put("import_dump", MediawikiImporter.job.source());
						prop.put("import_thread", 1);
						prop.put("import", 1);
						prop.put("refresh", 1);
					} else {
						prop.put("import_status", status);
						prop.put("import_status_sourceFile", sourceFilePath);
						
		                /* Acquire a transaction token for the next POST form submission */
		                final String token = TransactionManager.getTransactionToken(header);
		                prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, token);
		                prop.put("import_" + TransactionManager.TRANSACTION_TOKEN_PARAM, token);
					}
                    prop.put("import_count", 0);
                    prop.put("import_speed", 0);
                    prop.put("import_runningHours", 0);
                    prop.put("import_runningMinutes", 0);
                    prop.put("import_remainingHours", 0);
                    prop.put("import_remainingMinutes", 0);
                }
            }
        }
        return prop;
    }
}
