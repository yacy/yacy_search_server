package de.anomic.document.parser.html;

import java.util.Properties;

public interface ScraperListener extends java.util.EventListener {
    public void scrapeTag0(String tagname, Properties tagopts);
    public void scrapeTag1(String tagname, Properties tagopts, char[] text);
}
