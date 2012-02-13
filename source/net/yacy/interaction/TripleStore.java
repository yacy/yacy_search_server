// Author: DL

package net.yacy.interaction;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager;

import java.io.*;

import net.yacy.kelondro.logging.Log;


public class TripleStore {
	
	public static Model model = ModelFactory.createDefaultModel();
	
	public static String file;
	
	
	public static void Load (String filename) {
		
		Model tmp  = ModelFactory.createDefaultModel();
		
		Log.logInfo("TRIPLESTORE", "Loading from " + filename);
		
		try {
            InputStream in = FileManager.get().open(filename);
            
            // read the RDF/XML file
            tmp.read(in, null);
        }
        finally
        {
            	model = model.union(tmp);
        }
		
	}
	
	
	public static void Add (String rdffile) {
		
		Model tmp  = ModelFactory.createDefaultModel();
		
		
		try {
            @SuppressWarnings("deprecation")
			InputStream in = new StringBufferInputStream(rdffile);
            
            // read the RDF/XML file
            tmp.read(in, null);
        }
        finally
        {
            	model = model.union(tmp);
        }
		
	}
	
	public static void Save (String filename) {
		
		
    	FileOutputStream fout;
		try {
			
			
			fout = new FileOutputStream(filename);
			
			model.write(fout);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Log.logWarning("TRIPLESTORE", "Saving to " + filename+" failed");
		}
            
    	
	}

}
