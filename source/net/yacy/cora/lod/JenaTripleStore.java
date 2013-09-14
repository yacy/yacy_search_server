// Author: DL

package net.yacy.cora.lod;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.lod.vocabulary.DCTerms;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.lod.vocabulary.HttpHeader;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.FileManager;


public class JenaTripleStore {

    private final static ConcurrentLog log = new ConcurrentLog(JenaTripleStore.class.getName());
    
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

	public static long size() {
		return model.size();
	}

	public static ConcurrentHashMap<String, Model> privatestorage = null;

	public static void load(String filename) throws IOException {
		if (filename.endsWith(".nt")) LoadNTriples(filename);
		else loadRDF(filename);
	}

	private static void loadRDF(String fileNameOrUri) throws IOException {
		Model tmp  = ModelFactory.createDefaultModel();
		log.info("Loading from " + fileNameOrUri);
        InputStream is = FileManager.get().open(fileNameOrUri);
	    if (is != null) {
	    	// read the RDF/XML file
	    	tmp.read(is, null);
			log.info("loaded " + tmp.size() + " triples from " + fileNameOrUri);
	    	model = model.union(tmp);
	    } else {
	        throw new IOException("cannot read " + fileNameOrUri);
	    }
	}

	private static void LoadNTriples(String fileNameOrUri) throws IOException {
	    log.info("Loading N-Triples from " + fileNameOrUri);
	    InputStream is = FileManager.get().open(fileNameOrUri);
	    LoadNTriples(is);
	}

	public static void LoadNTriples(InputStream is) throws IOException {
	    Model tmp = ModelFactory.createDefaultModel();
	    if (is != null) {
	    	tmp.read(is, null, "N-TRIPLE");
			log.info("loaded " + tmp.size() + " triples");
        	model = model.union(tmp);
	        //model.write(System.out, "TURTLE");
	    } else {
	        throw new IOException("cannot read input stream");
	    }
	}

	public static void addFile(String rdffile) {
		Model tmp  = ModelFactory.createDefaultModel();
		try {
			InputStream in = new ByteArrayInputStream(UTF8.getBytes(rdffile));

            // read the RDF/XML file
            tmp.read(in, null);
        } finally {
            model = model.union(tmp);
        }
	}

	private static void saveFile(String filename) {
		saveFile(filename, model);
	}

	private static void saveFile(String filename, Model model) {
        File f = new File(filename);
        File ftmp = new File(filename + "." + System.currentTimeMillis());
	    if (model.isEmpty() && !f.exists()) {
	        // we don't store zero-size models if they did not exist before
	        log.info("NOT saving triplestore with " + model.size() + " triples to " + filename);
	        return;
	    }
		log.info("Saving triplestore with " + model.size() + " triples to " + filename);
    	OutputStream fout;
		try {
			fout = new BufferedOutputStream(new FileOutputStream(ftmp));
			model.write(fout);
			fout.close();
			// if something went wrong until here, the original file is not overwritten
			// since we are happy here, we can remove the old file and replace it with the new one
			f.delete();
			if (!f.exists()) {
			    ftmp.renameTo(f);
			}
			log.info("Saved triplestore with " + model.size() + " triples to " + filename);
		} catch (final Exception e) {
			log.warn("Saving to " + filename+" failed");
		}
	}

	/**
	 * clear the triplestore
	 */
	public static void clear() {
	    model = ModelFactory.createDefaultModel();
	    init(model);
	}

	/**
	 * Return a Resource instance with the given URI in this model.
	 * @param uri
	 * @return
	 */
	private static Resource getResource(String uri) {
	    return model.getResource(uri);
	}

    public static void deleteObjects(String subject, String predicate) {
        Resource r = subject == null ? null : getResource(subject);
        Property pr = model.getProperty(predicate);
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

    private static void addTriple(String subject, String predicate, String object, Model model) {
        Resource r = model.getResource(subject);
        Property pr = model.getProperty(predicate);
        r.addProperty(pr, object);
        log.info("ADD " + subject + " - " + predicate + " - " + object);
    }

    public static String getObject(final String subject, final String predicate) {
    	Iterator<RDFNode> ni = JenaTripleStore.getObjects(subject, predicate);
    	String object = "";
        if (ni.hasNext()) object = ni.next().toString();
        log.info("GET " + subject + " - " + predicate + " - " + object);
        return object;
    }

    public static Iterator<RDFNode> getObjects(final String subject, final String predicate) {
        final Resource r = subject == null ? null : JenaTripleStore.getResource(subject);
        return getObjects(r, predicate);
    }

    public static String getPrivateObject(final String subject, final String predicate, final String username) {
    	Iterator<RDFNode> ni = JenaTripleStore.getPrivateObjects(subject, predicate, username);
        String object = "";
        if (ni.hasNext()) object = ni.next().toString();
        log.info("GET (" + username + ") " + subject + " - " + predicate + " - " + object);
        return object;
    }

    private static Iterator<RDFNode> getPrivateObjects(final String subject, final String predicate, final String username) {
        if (privatestorage != null && privatestorage.containsKey(username)) {
			return getObjects(privatestorage.get(username).getResource(subject), predicate, privatestorage.get(username));
		}
	    return null;
    }

    private static Iterator<RDFNode> getObjects(final Resource r, final String predicate) {
    	return getObjects(r, predicate, model);
    }

    private static Iterator<RDFNode> getObjects(final Resource r, final String predicate, final Model model) {
        final Property pr = model.getProperty(predicate);
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

    public static Iterator<Resource> getSubjects(final String predicate) {
    	return getSubjects(predicate, model);
    }

    private static Iterator<Resource> getSubjects(final String predicate, final Model model) {
        final Property pr = model.getProperty(predicate);
        final ResIterator iter = model.listSubjectsWithProperty(pr);

        return new Iterator<Resource>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }
            @Override
            public Resource next() {
                return iter.nextResource();
            }
            @Override
            public void remove() {
                iter.remove();
            }
        };
    }

    public static Model getSubmodelBySubject(String subject) {
    	Selector q = new SimpleSelector(model.getResource(subject), (Property) null, (RDFNode) null);
        final Model m = model.query(q);
        m.setNsPrefix(Tagging.DEFAULT_PREFIX, Tagging.DEFAULT_NAMESPACE);
        m.setNsPrefix(DCTerms.PREFIX, DCTerms.NAMESPACE);
        return m;
    }

    public static String getRDFByModel(Model model) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        model.write(baos, "RDF/XML-ABBREV");
        return UTF8.String(baos.toByteArray());
    }

	public static void initPrivateStores() {
		Switchboard switchboard = Switchboard.getSwitchboard();
		log.info("Init private stores");
		if (privatestorage == null) privatestorage = new ConcurrentHashMap<String, Model>();
		if (privatestorage != null) privatestorage.clear();

		try {
			Iterator<net.yacy.data.UserDB.Entry> it = switchboard.userDB.iterator(true);
			while (it.hasNext()) {
				net.yacy.data.UserDB.Entry e = it.next();
				String username = e.getUserName();
				File triplestore = new File(switchboard.getConfig("triplestore", new File(switchboard.getDataPath(), "DATA/TRIPLESTORE").getAbsolutePath()));
                File currentuserfile = new File(triplestore, "private_store_"+username+".rdf");
                log.info("Init " + username + " from "+currentuserfile.getAbsolutePath());
                Model tmp  = ModelFactory.createDefaultModel();
                init (tmp);

                if (currentuserfile.exists()) {
            		log.info("Loading from " + currentuserfile.getAbsolutePath());
                    InputStream is = FileManager.get().open(currentuserfile.getAbsolutePath());
            	    if (is != null) {
            	    	// read the RDF/XML file
            	    	tmp.read(is, null);
            			log.info("loaded " + tmp.size() + " triples from " + currentuserfile.getAbsolutePath());
            	    } else {
            	        throw new IOException("cannot read " + currentuserfile.getAbsolutePath());
            	    }
                }

                if (tmp != null) {
                	privatestorage.put(username, tmp);
                }
			}
		} catch (final Exception anyex) {
			log.warn(anyex);
		}
	}

	private static void savePrivateStores() {
        Switchboard switchboard = Switchboard.getSwitchboard();
		log.info("Saving user triplestores");
		if (privatestorage == null) return;
		for (Entry<String, Model> s : privatestorage.entrySet()) {
			File triplestore = new File(switchboard.getConfig("triplestore", new File(switchboard.getDataPath(), "DATA/TRIPLESTORE").getAbsolutePath()));
            File currentuserfile = new File(triplestore, "private_store_"+s.getKey()+".rdf");
            saveFile (currentuserfile.getAbsolutePath(), s.getValue());
		}
	}

	private static long lastModelSizeStored = -1;

	public static void saveAll() {
        Switchboard sb = Switchboard.getSwitchboard();
        File triplestore = new File(sb.getConfig("triplestore", new File(sb.dataPath, "DATA/TRIPLESTORE").getAbsolutePath()));
        if (model.size() != lastModelSizeStored){
            JenaTripleStore.saveFile(new File(triplestore, "local.rdf").getAbsolutePath());
            lastModelSizeStored = model.size();
        }
        JenaTripleStore.savePrivateStores();
	}

}
