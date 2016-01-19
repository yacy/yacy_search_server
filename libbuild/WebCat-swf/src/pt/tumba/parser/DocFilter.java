package pt.tumba.parser;

/**
 * 
 * Generic interface for all document filters, converting a source format into HTML
 * 
 * @author Bruno Martins
 *
 */

public interface DocFilter {

/**
 * Return the original size of the document, before the filtering process. 
 * 
 * @return The original size of the document, before the filtering process.
 */
 public int originalSize();

}