/**
 *  Triple_p
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.09.2011 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.lod.JenaTripleStore;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.http.HTTPDemon;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;


public class Triple_p {

	public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header,
			final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
		final serverObjects prop = new serverObjects();

		prop.put("display", 1); // Fixed to 1
		prop.putHTML("mode_output", "no query performed");

		String q = "PREFIX lln: <http://virtual.x/>\n"+
"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
"SELECT ?resource ?pa\n"+
"WHERE {\n"+
	"?resource lln:hasvalue ?pa .\n"+
"FILTER (xsd:float (?pa) > 21.000)\n"+
"}";

		if (post != null) {

				if (post.containsKey("submit")) {
					//
					System.out.println (post.get("submit"));
				}

				if (post.containsKey("rdffileslist")) {

					String list = post.get("rdffileslist");

					for (String s: list.split("\n")) {
						String newurl = s;
						try {
							DigestURL d = new DigestURL (s);

							if (d.getHost().endsWith(".yacy")) {
								newurl = d.getProtocol()+"://"+HTTPDemon.getAlternativeResolver().resolve(d.getHost())+d.getPath();
								System.out.println (newurl);
							}
							JenaTripleStore.load(newurl);
						} catch (final MalformedURLException e) {
							ConcurrentLog.logException(e);
						} catch (final IOException e) {
							ConcurrentLog.logException(e);
						}
					}

				}

				if (post.containsKey("rdffile")) {

		            JenaTripleStore.addFile(post.get("rdffile$file"));
				}

				if (post.containsKey("query")) {

					// Create a new query
					 String queryString = post.get("query");

					 q = queryString;

					 int count = 0;

					 try {

					com.hp.hpl.jena.query.Query query = QueryFactory.create(queryString);

					// Execute the query and obtain results
					QueryExecution qe = QueryExecutionFactory.create(query, JenaTripleStore.model);
					ResultSet resultSet = qe.execSelect();

					ByteArrayOutputStream sos = new ByteArrayOutputStream();

					ResultSetFormatter.outputAsRDF(sos, "", resultSet);

					prop.putHTML("mode_rdfdump", sos.toString());

					int scount = 0;
					while (resultSet.hasNext()) {
						QuerySolution s = resultSet.next();
						prop.put("entries_"+scount+"_s", s.getResource(null).getURI());
						prop.put("entries_"+scount+"_p", s.getResource(null).getURI());
						prop.put("entries_"+scount+"_o", s.getResource(null).getURI());
						scount ++;
					}

					prop.putHTML("entries", ""+scount);

					for (String s: resultSet.getResultVars()) {

						prop.putHTML("mode_output_"+count+"_caption", s);
						count ++;
					}


					 } catch (final Exception e) {
						 prop.putHTML("mode_rdfdump", "error");
					 }



					prop.putHTML("mode_output", ""+count);

				}


		}

		prop.putHTML("mode_query", q);


		// return rewrite properties
		return prop;
	}

}
