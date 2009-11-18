package de.anomic.search;

public enum ContentDomain {
    
    ALL(-1),
    TEXT(0),
    IMAGE(1),
    AUDIO(2),
    VIDEO(3),
    APP(4);
    
    private int code;
    
    ContentDomain(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return this.code;
    }
    
    public static ContentDomain contentdomParser(final String dom) {
        if (dom.equals("text")) return TEXT;
        else if (dom.equals("image")) return IMAGE;
        else if (dom.equals("audio")) return AUDIO;
        else if (dom.equals("video")) return VIDEO;
        else if (dom.equals("app")) return APP;
        return TEXT;
    }
    
    public String toString() {
        if (this == TEXT) return "text";
        else if (this == IMAGE) return "image";
        else if (this == AUDIO) return "audio";
        else if (this == VIDEO) return "video";
        else if (this == APP) return "app";
        return "text";
    }
}
