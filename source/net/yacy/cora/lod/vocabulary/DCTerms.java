package net.yacy.cora.lod.vocabulary;

import java.util.Set;

import net.yacy.cora.lod.Literal;
import net.yacy.cora.lod.Vocabulary;

public enum DCTerms implements Vocabulary {

    references;

    public final static String NAMESPACE = "http://purl.org/dc/terms/";
    public final static String PREFIX = "dcterms";

    private final String predicate;

    private DCTerms() {
        this.predicate = NAMESPACE + this.name().toLowerCase();
    }

    private DCTerms(String name) {
        this.predicate = NAMESPACE + name;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getNamespacePrefix() {
        return PREFIX;
    }

    @Override
    public Set<Literal> getLiterals() {
        return null;
    }

    @Override
    public String getPredicate() {
        return this.predicate;
    }

    @Override
    public String getURIref() {
        return PREFIX + ':' + this.name();
    }
}
