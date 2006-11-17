package de.anomic.server.logging;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import de.anomic.plasma.plasmaParser;
import de.anomic.server.logging.logParsers.LogParser;

public class LogalizerHandler extends Handler {

    public static boolean enabled;
    public static boolean debug;
    private String logParserPackage;
    private HashMap parsers;
    
    public LogalizerHandler() {
        super();
        configure();
    }    

    private HashMap loadParsers() {
        HashMap parsers = new HashMap();
        try {
            if (enabled) System.out.println("Searching for additional content parsers in package " + logParserPackage);
            // getting an uri to the parser subpackage
            String packageURI = plasmaParser.class.getResource("/"+logParserPackage.replace('.','/')).toString();
            if (enabled) System.out.println("LogParser directory is " + packageURI);
            
            File parserDir = new File(new URI(packageURI));
            //System.out.println(parserDir.toString());
            String [] parserDirFiles = parserDir.list(parserNameFilter);
            if(parserDirFiles == null && enabled) {
                System.out.println("Can't find any parsers in "+parserDir.getAbsolutePath());
            }
            //System.out.println(parserDirFiles.length);
            for (int i=0; i<parserDirFiles.length; i++) {
                String tmp = parserDirFiles[i].substring(0,parserDirFiles[i].indexOf(".class"));
                Class tempClass = Class.forName(logParserPackage+"."+tmp);
                if (tempClass.isInterface() && enabled) System.out.println(tempClass.getName() + " is an Interface");
                else {
                    Object theParser = tempClass.newInstance();
                    if (theParser instanceof LogParser) {
                        LogParser theLogParser = (LogParser) theParser;
                        //System.out.println(bla.getName() + " is a logParser");
                        parsers.put(theLogParser.getParserType(), theParser);
                        if (enabled) System.out.println("Added " + theLogParser.getClass().getName() + " as " + theLogParser.getParserType() + " Parser.");
                    }
                    else {
                        //System.out.println(bla.getName() + " is not a logParser");
                        if (enabled) System.out.println("Rejected " + tempClass.getName() + ". Class does not implement the logParser-Interface");

                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return parsers;
    }
    
    /**
     * Get any configuration properties set
     */
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String className = getClass().getName();

        if(manager.getProperty(className + ".enabled").equalsIgnoreCase("true")) enabled = true;

        logParserPackage = manager.getProperty(className + ".parserPackage");

        parsers = loadParsers();
    }
    
    public void publish(LogRecord record) {
        if (enabled) {
            LogParser temp = (LogParser) parsers.get(record.getLoggerName());
            if (temp != null) {
                int returnV = temp.parse(record.getLevel().toString(), record.getMessage());
                //if (enabled) System.out.println("Logalizertest: " + returnV + " --- " + record.getLevel() + " --- " + record.getMessage());
                if (enabled) System.out.println("Logalizertest: " + returnV + " --- " + record.getLevel());
            }
        }
    }
    
    public void close() throws SecurityException {
        // TODO Auto-generated method stub

    }

    public void flush() {
        // TODO Auto-generated method stub

    }
    
    private static final FilenameFilter parserNameFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.matches(".*.class");
        }
    };


}
