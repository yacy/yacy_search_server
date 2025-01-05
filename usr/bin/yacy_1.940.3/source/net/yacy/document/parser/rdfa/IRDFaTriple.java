package net.yacy.document.parser.rdfa;

public interface IRDFaTriple{

	String getPropertyURI();
	String getSubjectURI();
	String getSubjectNodeURI();
	String getObjectURI();
	String getObjectNodeURI();
	String getValue();
	String getDataType();
	String getLanguage();
	
}
