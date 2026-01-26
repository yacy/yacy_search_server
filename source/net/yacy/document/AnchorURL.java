package net.yacy.document;

public class AnchorURL {
    private String url;
    private String nameProperty;

    public AnchorURL(String url) {
        this.url = url;
    }

    public String getNameProperty() {
        return nameProperty;
    }

    public void setNameProperty(String nameProperty) {
        this.nameProperty = nameProperty;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

