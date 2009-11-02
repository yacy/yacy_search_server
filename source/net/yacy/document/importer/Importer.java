package net.yacy.document.importer;

public interface Importer extends Runnable {

    
    public String source();
    
    public int count();
    
    /**
     * return the number of articles per second
     * @return
     */
    public int speed();
    
    /**
     * return the time this import is already running
     * @return
     */
    public long runningTime();
    
    
    /**
     * return the remaining seconds for the completion of all records in milliseconds
     * @return
     */
    public long remainingTime();

    public String status();
    
    public boolean isAlive();
    
    public void start();
    
    /**
     * the run method from runnable
     */
    public void run();
    
}
