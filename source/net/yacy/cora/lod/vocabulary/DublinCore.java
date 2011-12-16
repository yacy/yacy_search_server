package net.yacy.cora.lod.vocabulary;

public enum DublinCore implements Vocabulary {

    Contributor,
    Coverage,
    Creator,
    Date,
    Description,
    Format,
    Identifier,
    Language,
    Publisher,
    Relation,
    Rights,
    Source,
    Subject,
    Title,
    Type;
    
    public final static String IDENTIFIER = "http://dublincore.org/documents/2010/10/11/dces/";
    public final static String PREFIX = "dc";
    
    private final String predicate;
    
    private DublinCore() {
        this.predicate = PREFIX + ":" +  this.name().toLowerCase();
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public String getPredicate() {
        return this.predicate;
    }
}
