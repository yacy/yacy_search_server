package de.anomic.plasma.crawler;

import java.io.IOException;

import de.anomic.plasma.plasmaCrawlLoaderMessage;
import de.anomic.plasma.plasmaHTCache;


public interface plasmaCrawlWorker {

    public static final String threadBaseName = "CrawlerWorker";

    public void setNameTrailer(String trailer);
    
    public void setStopped(boolean isStopped);
    public void setDestroyed(boolean isDestroyed);
    
    public plasmaCrawlLoaderMessage getMessage();
    
    public void reset();
    public void execute();
    public void execute(plasmaCrawlLoaderMessage theNewMsg);
    public void init();
    
    public void close();
    public plasmaHTCache.Entry load() throws IOException;
}
