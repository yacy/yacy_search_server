// Author: DL

package net.yacy.cora.lod;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.lod.vocabulary.DCTerms;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.lod.vocabulary.HttpHeader;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.FileManager;


public class JenaTripleStore {

	public static Model model = ModelFactory.createDefaultModel();
	static {
	    init(model);
	    
	}
	private final static void init(Model model) {
        model.setNsPrefix(YaCyMetadata.PREFIX, YaCyMetadata.NAMESPACE);
        model.setNsPrefix(Tagging.DEFAULT_PREFIX, Tagging.DEFAULT_NAMESPACE);
        model.setNsPrefix(HttpHeader.PREFIX, HttpHeader.NAMESPACE);
        model.setNsPrefix(Geo.PREFIX, Geo.NAMESPACE);
        model.setNsPrefix("pnd", "http://dbpedia.org/ontology/individualisedPnd");
        model.setNsPrefix(DCTerms.PREFIX, DCTerms.NAMESPACE);
	}

	public static ConcurrentHashMap<String, Model> privatestorage = null;

	public static String file;


	public static void load(String filename) throws IOException {
		if (filename.endsWith(".nt")) LoadNTriples(filename);
		else loadRDF(filename);
	}

	public static void loadRDF(String fileNameOrUri) throws IOException {
		Model tmp  = ModelFactory.createDefaultModel();
		Log.logInfo("TRIPLESTORE", "Loading from " + fileNameOrUri);
        InputStream is = FileManager.get().open(fileNameOrUri);
	    if (is != null) {
	    	// read the RDF/XML file
	    	tmp.read(is, null);
			Log.logInfo("TRIPLESTORE", "loaded " + tmp.size() + " triples from " + fileNameOrUri);
	    	model = model.union(tmp);
	    } else {
	        throw new IOException("cannot read " + fileNameOrUri);
	    }
	}

	public static void LoadNTriples(String fileNameOrUri) throws IOException {
	    Model tmp = ModelFactory.createDefaultModel();
		Log.logInfo("TRIPLESTORE", "Loading N-Triples from " + fileNameOrUri);
	    InputStream is = FileManager.get().open(fileNameOrUri);
	    if (is != null) {
	    	tmp.read(is, null, "N-TRIPLE");
			Log.logInfo("TRIPLESTORE", "loaded " + tmp.size() + " triples from " + fileNameOrUri);
        	model = model.union(tmp);
	        //model.write(System.out, "TURTLE");
	    } else {
	        throw new IOException("cannot read " + fileNameOrUri);
	    }
	}

	public static void addFile(String rdffile) {

		Model tmp  = ModelFactory.createDefaultModel();


		try {
			InputStream in = new ByteArrayInputStream(UTF8.getBytes(rdffile));

            // read the RDF/XML file
            tmp.read(in, null);
        }
        finally
        {
            	model = model.union(tmp);
        }

	}
	
	public static void saveFile(String filename) {		
		saveFile(filename, model);
	}

	public static void saveFile(String filename, Model model) {
		Log.logInfo("TRIPLESTORE", "Saving triplestore with " + model.size() + " triples to " + filename);
    	FileOutputStream fout;
		try {
			fout = new FileOutputStream(filename);
			model.write(fout);
			Log.logInfo("TRIPLESTORE", "Saved triplestore with " + model.size() + " triples to " + filename);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.logWarning("TRIPLESTORE", "Saving to " + filename+" failed");
		}
	}

	/**
	 * clear the triplestore
	 */
	public static void clear() {
	    model = ModelFactory.createDefaultModel();
	    init(model);
	}

	
	public static Resource getResource(String uri, String username) {		
		
		if (privatestorage != null && privatestorage.containsKey(username)) {
			
			return getResource (uri, privatestorage.get(username));
		}
		
	    return null;
	}
	
	/**
	 * Return a Resource instance with the given URI in this model.
	 * @param uri
	 * @return
	 */
	public static Resource getResource(String uri) {
	    return getResource(uri, model);
	}
	
	public static Resource getResource(String uri, Model model) {
	    return model.getResource(uri);
	}

	public static Resource getProperty(String uri, String username) {		
		
		if (privatestorage != null && privatestorage.containsKey(username)) {
			
			return getProperty (uri, privatestorage.get(username));
		}
		
	    return null;
	}
	
	/**
	 * Return a Property instance in this model.
	 * @param uri
	 * @return
	 */
    public static Property getProperty(String uri) {
        return getProperty(uri, model);
    }
    
    public static Property getProperty(String uri, Model model) {
        return model.getProperty(uri);
    }

    public static void deleteObjects(String subject, String predicate) {
        Resource r = getResource(subject);
        Property pr = getProperty(predicate);
        JenaTripleStore.model.removeAll(r, pr, (Resource) null);
    }
    
    public static void addTriple(String subject, String predicate, String object, String username) {
    	if (privatestorage != null && privatestorage.containsKey(username)) {
			
    		addTriple (subject, predicate, object, privatestorage.get(username));
		}
		
    }
    
    public static void addTriple(String subject, String predicate, String object) {
    	addTriple (subject, predicate, object, model);
    }

    public static void addTriple(String subject, String predicate, String object, Model model) {
        Resource r = getResource(subject, model);
        Property pr = getProperty(predicate, model);
        r.addProperty(pr, object);
        Log.logInfo("TRIPLESTORE", "ADD " + subject + " - " + predicate + " - " + object);
    }
    
    public static String getObject (final String subject, final String predicate) { 
    		
    	Log.logInfo("TRIPLESTORE", "GET " + subject + " - " + predicate + " ... ");
    	
    	Iterator<RDFNode> ni = JenaTripleStore.getObjects (subject, predicate);
        if (!ni.hasNext()) return "";
        return ni.next().toString();
    
    }
    
    public static String getObject (final String subject, final String predicate, final String username) { 
  
    	Log.logInfo("TRIPLESTORE", "GET " + subject + " - " + predicate + " ... ("+username+")");
    	
    	Iterator<RDFNode> ni = JenaTripleStore.getObjects (subject, predicate, username);
        if (!ni.hasNext()) return "";
        return ni.next().toString();
    
    }
    
    public static Iterator<RDFNode> getObjects(final String subject, final String predicate, final String username) {

        final Resource r = JenaTripleStore.getResource(subject, username);
        
        if (privatestorage != null && privatestorage.containsKey(username)) {
			
			return getObjects(r, predicate, privatestorage.get(username));
		}
		
	    return null;
    }

    public static Iterator<RDFNode> getObjects(final String subject, final String predicate) {        
        final Resource r = JenaTripleStore.getResource(subject);
        return getObjects(r, predicate);
    }
        
    public static Iterator<RDFNode> getObjects(final Resource r, final String predicate) {
    	return getObjects (r, predicate, model);
    }

    public static Iterator<RDFNode> getObjects(final Resource r, final String predicate, final Model model) {
        final Property pr = JenaTripleStore.getProperty(predicate, model);
        final StmtIterator iter = model.listStatements(r, pr, (Resource) null);                
                
        return new Iterator<RDFNode>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }
            @Override
            public RDFNode next() {
                return iter.nextStatement().getObject();
            }
            @Override
            public void remove() {
                iter.remove();
            }
        };
    }

	public static void initPrivateStores() {
		
		Switchboard switchboard = Switchboard.getSwitchboard();

		Log.logInfo("TRIPLESTORE", "Init private stores");
		
		if (privatestorage == null) privatestorage = new ConcurrentHashMap<String, Model>();

		if (privatestorage != null) privatestorage.clear();

		try {

			Iterator<de.anomic.data.UserDB.Entry> it = switchboard.userDB.iterator(true);

			while (it.hasNext()) {
				de.anomic.data.UserDB.Entry e = it.next();
				String username = e.getUserName();

				File triplestore = new File(switchboard.getConfig("triplestore", new File(switchboard.getDataPath(), "DATA/TRIPLESTORE").getAbsolutePath()));
                
                File currentuserfile = new File(triplestore, "private_store_"+username+".rdf");
                
                Log.logInfo("TRIPLESTORE", "Init " + username + " from "+currentuserfile.getAbsolutePath());
                
                Model tmp  = ModelFactory.createDefaultModel();
                
                init (tmp);                      	
                
                if (currentuserfile.exists()) {
                	
                	
            		Log.logInfo("TRIPLESTORE", "Loading from " + currentuserfile.getAbsolutePath());
                    InputStream is = FileManager.get().open(currentuserfile.getAbsolutePath());
            	    if (is != null) {
            	    	// read the RDF/XML file
            	    	tmp.read(is, null);
            			Log.logInfo("TRIPLESTORE", "loaded " + tmp.size() + " triples from " + currentuserfile.getAbsolutePath());
            			
            			
            	    } else {
            	        throw new IOException("cannot read " + currentuserfile.getAbsolutePath());
            	    }
                }
                
                if (tmp != null) {
                
                	privatestorage.put(username, tmp);
                
                }

			}

			}
		catch (Exception anyex) {
			
			Log.logException(anyex);

		}

	}

	public static void savePrivateStores(Switchboard switchboard) {
		
		Log.logInfo("TRIPLESTORE", "Saving user triplestores");

		if (privatestorage == null) return;

		for (Entry<String, Model> s : privatestorage.entrySet()) {
			
			File triplestore = new File(switchboard.getConfig("triplestore", new File(switchboard.getDataPath(), "DATA/TRIPLESTORE").getAbsolutePath()));
          
            File currentuserfile = new File(triplestore, "private_store_"+s.getKey()+".rdf");

            saveFile (currentuserfile.getAbsolutePath(), s.getValue());
				
		}
	}

}
