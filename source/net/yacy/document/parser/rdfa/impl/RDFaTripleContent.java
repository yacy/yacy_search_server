package net.yacy.document.parser.rdfa.impl;

import net.yacy.document.parser.rdfa.IRDFaTriple;

public class RDFaTripleContent implements IRDFaTriple {

	private final String subjectURI;
	private final String subjectNodeURI;
	private final String propertyURI;
	private final String value;
	private final String dataType;
	private final String language;
	private final String objectNodeURI;
	private final String objectURI;

	public RDFaTripleContent(String subjectURI, String subjectNodeURI,
			String propertyURI, String value, String dataType, String language, String objectNodeURI, String objectURI) {
				this.subjectURI = subjectURI;
				this.subjectNodeURI = subjectNodeURI;
				this.propertyURI = propertyURI;
				this.value = value;
				this.dataType = dataType;
				this.language = language;
				this.objectNodeURI = objectNodeURI;
				this.objectURI = objectURI;
	}

	@Override
    public String getSubjectURI() {
		return subjectURI;
	}

	@Override
    public String getSubjectNodeURI() {
		return subjectNodeURI;
	}

	@Override
    public String getPropertyURI() {
		return propertyURI;
	}

	@Override
    public String getValue() {
		return value;
	}

	@Override
    public String getDataType() {
		return dataType;
	}

	@Override
    public String getLanguage() {
		return language;
	}

	@Override
	public String getObjectURI() {
		return objectURI;
	}

	@Override
	public String getObjectNodeURI() {
		return objectNodeURI;
	}


}
