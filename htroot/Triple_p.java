
import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;
import java.util.List;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.interaction.TripleStore;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.search.Switchboard;


import de.anomic.http.server.HTTPDemon;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory ;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.sparql.algebra.Algebra ;
import com.hp.hpl.jena.sparql.algebra.Op ;
import com.hp.hpl.jena.sparql.engine.QueryIterator ;
import com.hp.hpl.jena.sparql.engine.binding.Binding ;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;


public class Triple_p {

	public static serverObjects respond(final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		final Switchboard sb = (Switchboard) env;
		final serverObjects prop = new serverObjects();

		boolean hasRights = sb.verifyAuthentication(header);

		prop.put("display", 1); // Fixed to 1
		prop.putHTML("mode_output", "no query performed");
		
		String q = "PREFIX lln: <http://virtual.liebel-lab.org/n#>\n"+
"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
"SELECT ?resource ?pa\n"+
"WHERE {\n"+
	"?resource lln:percentageofarea ?pa .\n"+
"FILTER (xsd:float (?pa) > 3.000)\n"+
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
							DigestURI d = new DigestURI (s);
							
							if (d.getHost().endsWith(".yacy")) {
								newurl = d.getProtocol()+"://"+HTTPDemon.getAlternativeResolver().resolve(d.getHost())+d.getPath();
								System.out.println (newurl);
							}
						} catch (MalformedURLException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						TripleStore.Load (newurl);
					}					

				}
			
				if (post.containsKey("rdffile")) {

		            TripleStore.Add(post.get("rdffile$file"));
				}
				
				if (post.containsKey("query")) {

					// Create a new query
					 String queryString = post.get("query");
					 
					 q = queryString;
					 
					 int count = 0;
					 
					 try {
					 
					com.hp.hpl.jena.query.Query query = QueryFactory.create(queryString);
					 
					// Execute the query and obtain results
					QueryExecution qe = QueryExecutionFactory.create(query, TripleStore.model);
					ResultSet resultSet = qe.execSelect();
					 
					ByteArrayOutputStream sos = new ByteArrayOutputStream();
					
					ResultSetFormatter.outputAsRDF(sos, "", resultSet);

					prop.putHTML("mode_rdfdump", sos.toString());
					

					
					
					for (String s: resultSet.getResultVars()) {
						
						prop.putHTML("mode_output_"+count+"_caption", s);
						count ++;
					}
					 } catch (Exception e) {
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
