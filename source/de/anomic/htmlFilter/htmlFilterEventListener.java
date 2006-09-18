package de.anomic.htmlFilter;

import java.util.EventListener;
import java.util.Properties;

public interface htmlFilterEventListener extends EventListener {
    public void scrapeTag0(String tagname, Properties tagopts);
    public void scrapeTag1(String tagname, Properties tagopts, char[] text);
}
