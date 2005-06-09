package de.anomic.server.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public final class ConsoleOutHandler extends StreamHandler{

    public ConsoleOutHandler() {
        setLevel(Level.FINEST);
        setFormatter(new SimpleFormatter());
        setOutputStream(System.out);        
    }
    
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();
    }
    
    public void close() {
        flush();
    }
}
