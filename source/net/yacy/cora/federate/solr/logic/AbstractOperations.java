package net.yacy.cora.federate.solr.logic;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractOperations extends AbstractTerm implements Operations {

    protected final String operandName;
    protected final List<Term> terms;
    
    public AbstractOperations(final String operandName) {
        this.operandName = operandName;
        this.terms = new ArrayList<>();
    }
    
    @Override
    public int hashCode() {
        int h = operandName.hashCode();
        for (Term t: this.terms) h += t.hashCode();
        return h;
    }

    /**
     * Add the term with the defined logical operator.
     * If length of term is empty nothing is changed
     * @param term
     */
    @Override
    public void addOperand(Term term) {
        if (!term.toString().isEmpty()) {
            this.terms.add(term);
        }
    }
    
    /**
     * As a Operations object is a collection of Terms, we must be able to show them
     * @return the list of terms
     */
    @Override
    public List<Term> getOperands() {
        return this.terms;
    }

    /**
     * the weight attribute of a term shows if rewritten terms
     * (using rules of replacement as allowed for propositional logic)
     * are shorter and therefore more efficient.
     * @return the number of operators plus the number of operands plus one
     */
    @Override
    public int weight() {
        return terms.size() * 2;
    }

    @Override
    public boolean isBinary() {
        return this.terms.size() == 2;
    }

    /**
     * can we set brackets anywhere (means: can we change calculation order)?
     */
    @Override
    public boolean isAssociative() {
        return true;
    }

    /**
     * can we switch operands (must be binary)
     */
    @Override
    public boolean isCommutative() {
        return isBinary();
    }

    /**
     * can we 'multiply inside' (must be binary)
     */
    @Override
    public boolean isDistributive() {
        return isBinary();
    }

    @Override
    public Term lightestRewrite() {
        return this;
    }

    /**
     * create a Solr query string from this conjunction
     * @return a string which is a Solr query string
     */
    @Override
    public String toString() {
        if (this.terms.size() == 0) return "";
        StringBuilder sb = new StringBuilder();
        if (this.terms.size() == 1) {
            sb.append(terms.iterator().next().toString());
        } else {
            sb.append('(');
            for (Term term: this.terms) {
                if (sb.length() > 1) sb.append(' ').append(this.operandName).append(' ');
                sb.append(term.toString());
            }
            sb.append(')');
        }
        return sb.toString();
    }
}
