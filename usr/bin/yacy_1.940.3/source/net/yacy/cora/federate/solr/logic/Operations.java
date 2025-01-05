package net.yacy.cora.federate.solr.logic;

import java.util.List;

/**
 * The Operations class describes a set of operands which form a term using the same operation.
 */
public interface Operations extends Term {

    /**
     * As a Operations object is a collection of Terms, we must be able to show them
     * @return the list of terms
     */
    public List<Term> getOperands();
    
    /**
     * add another operand to the operations term
     * @param operand
     */
    public void addOperand(Term operand);
    
    /**
     * the operation is binary, if it contains two operands
     * @return if this is a binary operation
     */
    public boolean isBinary();
    
    /**
     * a binary operation * on a set S is called associative if it satisfies the associative law:
     * (x * y) * z = x * (y * z)  for all x,y,z in S. 
     * @return true if this is associative
     */
    public boolean isAssociative(); 
    
    /**
     * In standard truth-functional propositional logic, commutativity refer to two valid rules of replacement.
     * The rules allow one to transpose propositional variables within logical expressions in logical proofs. The rules are:
     * (P OR Q) <=> (Q OR P) 
     * (P AND Q) <=> (Q AND P) 
     * @return true if this is distributive
     */
    public boolean isCommutative();
    
    /**
     * In propositional logic, distribution refers to two valid rules of replacement.
     * The rules allow one to reformulate conjunctions and disjunctions within logical proofs.
     * Given a set S and two binary operators * and + on S, we say that the operation *
     *   is left-distributive over + if, given any elements x, y, and z of S,
     *     x * (y + z) = (x * y) + (x * z)
     *   is right-distributive over + if, given any elements x, y, and z of S:
     *     (y + z) * x = (y * x) + (z * x)
     *   is distributive over + if it is left- and right-distributive.
     * @return true if this is distributive;
     */
    public boolean isDistributive();

    
}
