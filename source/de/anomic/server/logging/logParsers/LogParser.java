package de.anomic.server.logging.logParsers;

/**
 * This is the logParser-Interface which all yacy Logalizer-Parser must
 * implement.
 *
 * @author Matthias Söhnholz
 */
public interface LogParser {
    /** 
     * This is the basic parser-method to parse single loglines. It can
     * request to give the current logLine and a number of additional logLines,
     * defined by the return value, to be passed over to the
     * <tt>advancedParse</tt>-method. The method should return -1 if the given
     * line was not processed.
     *
     * TODO: description of logLevels
     *
     * @param logLevel The LogLevel of the line to analyze.
     * @param logLine  The line to be analyze by the parser.
     * @return number of additional lines to be loaded and passed over to the
     * <tt>advancedParse</tt>-method, or if the line was not processed by the
     * parser "-1".
     */
    public int parse(String logLevel, String logLine);
    /**
     * This method prints the Parser-Results to the standard-output.
     */
    public void printResults();
    /**
     * The return value defines which logLines the parser will handle.
     * @return a String that defines the logLines to analyze. For example
     * <b>PLASMA</b> or <b>YACY</b>
     */
    public String getParserType();
}
