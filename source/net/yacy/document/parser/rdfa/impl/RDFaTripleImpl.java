package net.yacy.document.parser.rdfa.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.parser.rdfa.IRDFaTriple;
import net.yacy.search.Switchboard;

public class RDFaTripleImpl{

	private static Templates templates = null;
	private String propertyURI = null;
	private String subjectURI = null;
	private String subjectNodeURI = null;
	private String objectURI = null;
	private String objectNodeURI = null;
	private String value = null;
	private String dataType = null;
	private String language = null;
	private final Reader in;
	private final Transformer aTransformer;
	private final ArrayList<IRDFaTriple> allRDFaTriples = new ArrayList<IRDFaTriple>();


	public RDFaTripleImpl(Reader in, String base) throws IOException,
			TransformerException, TransformerConfigurationException {

		BufferedReader bufReader = new BufferedReader(in);
                bufReader.mark(2048); // mark position for following reset
		String readLine = bufReader.readLine();
		if (!readLine.toLowerCase().contains("<!doctype")){
			bufReader.reset();
		}

		if (templates == null) {
                    File f = new File(Switchboard.getSwitchboard().appPath, "defaults" + File.separatorChar + "RDFaParser.xsl");
			try {
				StreamSource aSource = new StreamSource(f);
				TransformerFactory aFactory = TransformerFactory.newInstance();
				templates = aFactory.newTemplates(aSource);
			} catch(Exception e){
				ConcurrentLog.severe("RDFA PARSER", "XSL template could not be loaded from " + f.toString());
			}
		}
		this.aTransformer = templates.newTransformer();
		this.aTransformer.setParameter("parser", this);
		this.aTransformer.setParameter("url", base);

		this.in = bufReader;
	}

	public IRDFaTriple[] parse() {
		try {
			this.aTransformer.transform(new StreamSource(this.in), new StreamResult(System.out));
		} catch (final TransformerException e) {
			ConcurrentLog.warn("RDFA PARSER", "Error while reading RDFa");
//			e.printStackTrace();
		}

		return this.allRDFaTriples .toArray(new IRDFaTriple[]{});

	}

	public static boolean flushDataProperty(Object oparser) {
		RDFaTripleImpl parser = ((RDFaTripleImpl)oparser);

		parser.reportDataProperty(parser.subjectURI, parser.subjectNodeURI, parser.propertyURI,
				parser.value, parser.dataType, parser.language, parser.objectNodeURI, parser.objectURI);
		nullAllValues(parser);
		return true;
	}

	private void reportDataProperty(String subjectURI, String subjectNodeURI,
			String propertyURI, String value, String dataType,
			String language, String objectNodeURI, String objectURI) {
			IRDFaTriple triple = new RDFaTripleContent(subjectURI,subjectNodeURI,propertyURI,value,dataType,language, objectNodeURI,objectURI);
			this.allRDFaTriples.add(triple);
	}

	private static void nullAllValues(RDFaTripleImpl parser) {
		parser.propertyURI = null;
		parser.subjectURI = null;
		parser.subjectNodeURI = null;
		parser.objectURI = null;
		parser.objectNodeURI = null;
		parser.value = null;
		parser.dataType = null;
		parser.language = null;
	}

	public static boolean flushObjectProperty(Object oparser) {
		RDFaTripleImpl parser = ((RDFaTripleImpl)oparser);
//		System.out.println("parser added");
		nullAllValues(parser);
		return true;
	}

	public static boolean setTheDatatype(Object parser, String theDatatype) {
		((RDFaTripleImpl)parser).dataType = theDatatype;
		System.out.println(theDatatype);
		return true;
	}

	public static boolean setTheLanguage(Object parser, String theLanguage) {
		((RDFaTripleImpl)parser).language = theLanguage;
		return true;
	}

	public static boolean setTheObjectNodeID(Object parser, String theObjectNodeID) {
		((RDFaTripleImpl)parser).objectNodeURI = theObjectNodeID;
		return true;
	}

	public static boolean setTheObjectURI(Object parser, String theObjectURI) {
		((RDFaTripleImpl)parser).objectURI = theObjectURI;
		return true;
	}

	public static boolean setThePropertyURI(Object parser, String thePropertyURI) {
		((RDFaTripleImpl)parser).propertyURI = thePropertyURI;
		return true;
	}


	public static boolean setTheSubjectNodeID(Object parser, String theSubjectNodeID) {
		((RDFaTripleImpl)parser).subjectNodeURI = theSubjectNodeID;
		System.out.println(theSubjectNodeID);
		return true;
	}

	public static boolean setTheSubjectURI(Object parser, String theSubjectURI) {
		((RDFaTripleImpl)parser).subjectURI = theSubjectURI;
		return true;
	}

	public static boolean setTheValue(Object parser, String theValue) {
		((RDFaTripleImpl)parser).value = theValue;
		return true;
	}
}
