//LogalizerHandler.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Matthias Soehnholz
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.server.logging;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.plasma.plasmaParser;
import de.anomic.server.logging.logParsers.LogParser;
import de.anomic.tools.ListDirs;

public class LogalizerHandler extends Handler {

    public static boolean enabled=false;
    public static boolean debug=false;
    private String logParserPackage;
    private HashMap<String, Object> parsers;
    
    public LogalizerHandler() {
        super();
        configure();
    }    

    private HashMap<String, Object> loadParsers() {
        final HashMap<String, Object> logParsers = new HashMap<String, Object>();
        try {
            if (debug) System.out.println("Searching for additional content parsers in package " + logParserPackage);
            // getting an uri to the parser subpackage
            final String packageURI = plasmaParser.class.getResource("/"+logParserPackage.replace('.','/')).toString();
            if (debug) System.out.println("LogParser directory is " + packageURI);
            
            final ListDirs parserDir = new ListDirs(packageURI);
            final ArrayList<String> parserDirFiles = parserDir.listFiles(".*\\.class");
            if(parserDirFiles.size() == 0 && debug) {
                System.out.println("Can't find any parsers in "+parserDir.toString());
            }
	    for(final String filename: parserDirFiles) {
		final Pattern patternGetClassName = Pattern.compile(".*/([^/]+)\\.class");
		final Matcher matcherClassName = patternGetClassName.matcher(filename);
		matcherClassName.find();
                final String className = matcherClassName.group(1);
                final Class<?> tempClass = Class.forName(logParserPackage+"."+className);
                if (tempClass.isInterface()) {
                    if (debug) System.out.println(tempClass.getName() + " is an Interface");
                } else {
                    final Object theParser = tempClass.newInstance();
                    if (theParser instanceof LogParser) {
                        final LogParser theLogParser = (LogParser) theParser;
                        //System.out.println(bla.getName() + " is a logParser");
                        logParsers.put(theLogParser.getParserType(), theParser);
                        
                        if (debug) System.out.println("Added " + theLogParser.getClass().getName() + " as " + theLogParser.getParserType() + " Parser.");
                    }
                    else {
                        //System.out.println(bla.getName() + " is not a logParser");
                        if (debug) System.out.println("Rejected " + tempClass.getName() + ". Class does not implement the logParser-Interface");

                    }
                }
            }
        } catch (final ClassNotFoundException e) {
            e.printStackTrace();
        } catch (final InstantiationException e) {
            e.printStackTrace();
        } catch (final IllegalAccessException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        } catch (final URISyntaxException e) {
            e.printStackTrace();
        }
        return logParsers;
    }
    
    /**
     * Get any configuration properties set
     */
    private void configure() {
        final LogManager manager = LogManager.getLogManager();
        final String className = getClass().getName();

        if(manager.getProperty(className + ".enabled").equalsIgnoreCase("true")) enabled = true;
        if(manager.getProperty(className + ".debug").equalsIgnoreCase("true")) debug = true;

        logParserPackage = manager.getProperty(className + ".parserPackage");

        parsers = loadParsers();
    }
    
    public void publish(final LogRecord record) {
        if (enabled) {
            final LogParser temp = (LogParser) parsers.get(record.getLoggerName());
            if (temp != null) {
                final int returnV = temp.parse(record.getLevel().toString(), record.getMessage());
                //if (debug) System.out.println("Logalizertest: " + returnV + " --- " + record.getLevel() + " --- " + record.getMessage());
                if (debug) System.out.println("Logalizertest: " + returnV + " --- " + record.getLevel());
            }
        }
    }
    
    public Set<String> getParserNames() {
        return parsers.keySet();
    }
    
    public LogParser getParser(final int number) {
        String o;
        final Iterator<String> it = parsers.keySet().iterator();
        int i = 0;
        while (it.hasNext()) {
            o = it.next();
            if (i++ == number)
                return (LogParser) parsers.get(o);
        }
        return null;
    }
    
    public Hashtable<String, Object> getParserResults(final LogParser parsername) {
        return parsername.getResults();
    }
    
    public void close() throws SecurityException {
        // TODO Auto-generated method stub

    }

    public void flush() {
        // TODO Auto-generated method stub

    }
    /*  
    private static final FilenameFilter parserNameFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.matches(".*.class");
        }
    };
    */
}
