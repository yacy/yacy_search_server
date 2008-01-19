package de.anomic.plasma.parser;

import java.util.Hashtable;

public class ParserInfo {
    // general parser info
    public Class<?> parserClass;
    public String parserClassName;
    
    public String parserName;
    public String parserVersionNr;
    
    // parser properties
    public String[] libxDependencies;
    public Hashtable<String, String> supportedMimeTypes;
    
    // usage statistic
    public int usageCount = 0;
    
    public String toString() {
        StringBuffer toStr = new StringBuffer();
        
        toStr.append(this.parserName).append(" V")
             .append((this.parserVersionNr==null)?"0.0":this.parserVersionNr).append(" | ")
             .append(this.parserClassName).append(" | ")
             .append(this.supportedMimeTypes);
        
        return toStr.toString();
    }
    
    public synchronized void incUsageCounter() {
        this.usageCount++;
    }
}
