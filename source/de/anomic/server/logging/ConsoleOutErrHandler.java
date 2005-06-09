package de.anomic.server.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class ConsoleOutErrHandler extends Handler{

    private Level splitLevel = Level.WARNING;
    private final Handler stdOutHandler = new ConsoleOutHandler();
    private final Handler stdErrHandler = new ConsoleHandler();    
    
    public ConsoleOutErrHandler() {
        this.stdOutHandler.setLevel(Level.FINEST);
        this.stdErrHandler.setLevel(Level.WARNING);
        configure();
    }
    
    /**
     * Get any configuration properties set
     */
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String className = getClass().getName();
        
        String level = manager.getProperty(className + ".level");
        setLevel((level == null) ? Level.INFO : Level.parse(level));
        
        Level levelStdOut = parseLevel(manager.getProperty(className + ".levelStdOut"));
        Level levelSplit = parseLevel(manager.getProperty(className + ".levelSplit"));
        Level levelStdErr = parseLevel(manager.getProperty(className + ".levelStdErr"));
        setLevels(levelStdOut,levelSplit,levelStdErr);
        
        String filter = manager.getProperty(className + ".filter");
        setFilter(makeFilter(filter));
        
        String formatter = manager.getProperty(className + ".formatter");
        setFormatter(makeFormatter(formatter));
    }    
    
    private Level parseLevel(String levelName) {
        try {
            return (levelName == null) ? Level.INFO : Level.parse(levelName);
        } catch (Exception e) {
            return Level.ALL;
        }
    }
    
    private Filter makeFilter(String name) {
        if (name == null) return null;
        
        Filter f = null;
        try {
            Class c = Class.forName(name);
            f = (Filter)c.newInstance();
        } catch (Exception e) {
            if (name != null) {
                System.err.println("Unable to load filter: " + name);
            }
        }
        return f;
    }    
    
    private Formatter makeFormatter(String name) {
        if (name == null) return null;
        
        Formatter f = null;
        try {
            Class c = Class.forName(name);
            f = (Formatter)c.newInstance();
        } catch (Exception e) {
            f = new SimpleFormatter();
        }
        return f;
    }    
    
    
    public void publish(LogRecord record) {
        if (!isLoggable(record)) return;
        
        if (record.getLevel().intValue() >= splitLevel.intValue()) {
            this.stdErrHandler.publish(record);
        } else {
            this.stdOutHandler.publish(record);
        }
    }

    public void flush() {
        this.stdOutHandler.flush();
        this.stdErrHandler.flush();
    }

    public void close() throws SecurityException {
        this.stdOutHandler.close();  
        this.stdErrHandler.close();
    }
    
    public synchronized void setLevel(Level newLevel) throws SecurityException {
        super.setLevel(newLevel);
    }
    
    public void setLevels(Level stdOutLevel, Level splitLevel, Level stdErrLevel) throws SecurityException {
        this.stdOutHandler.setLevel(stdOutLevel);
        this.splitLevel = splitLevel;
        this.stdErrHandler.setLevel(stdErrLevel);
    }
    
    public void setFormatter(Formatter newFormatter) throws SecurityException {
        super.setFormatter(newFormatter);
        if (newFormatter == null) return;
        try {
            this.stdOutHandler.setFormatter((Formatter)newFormatter.getClass().newInstance());
            this.stdErrHandler.setFormatter((Formatter)newFormatter.getClass().newInstance());
        } catch (Exception e) {
            throw new SecurityException(e.getMessage());
        }
    }

    public void setFilter(Filter newFilter) throws SecurityException {
        super.setFilter(newFilter);
        if (newFilter == null) return;
        try {
            this.stdOutHandler.setFilter((Filter)newFilter.getClass().newInstance());
            this.stdErrHandler.setFilter((Filter)newFilter.getClass().newInstance());
        } catch (Exception e) {
            throw new SecurityException(e.getMessage());
        }
    }
}
