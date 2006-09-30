package de.anomic.plasma.crawler;

import java.io.IOException;

public class plasmaCrawlerException extends IOException {

    private static final long serialVersionUID = 1L;

    public plasmaCrawlerException(String errorMsg) {
        super(errorMsg);
    }
}
