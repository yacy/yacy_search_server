package net.yacy.peers.graphics;

public class BannerData {

    private final int width;
    private final int height;
    private final long bgcolor;
    private final long textcolor;
    private final long bordercolor;
    private final String name;
    private final long links;
    private final long words;
    private final String type;
    private final int ppm;
    private final String network;
    private final int peers;
    private final long nlinks;
    private final long nwords;
    private final double nqph;
    private final long nppm;

    public BannerData(
             final int width, 
             final int height, 
             final String bgcolor, 
             final String textcolor, 
             final String bordercolor, 
             final String name, 
             final long links, 
             final long words, 
             final String type, 
             final int ppm, 
             final String network, 
             final int peers, 
             final long nlinks, 
             final long nwords, 
             final double nqph, 
             final long nppm
            ) {
        this.width = width;
        this.height = height;
        try {
        this.bgcolor = Long.parseLong(bgcolor, 16);
        this.textcolor = Long.parseLong(textcolor, 16);
        this.bordercolor = Long.parseLong(bordercolor, 16);
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid color definition.", ex);
        }
        this.name = name;
        this.links = links;
        this.words = words;
        this.type = type;
        this.ppm = ppm;
        this.network = network;
        this.peers = peers;
        this.nlinks = nlinks;
        this.nwords = nwords;
        this.nqph = nqph;
        this.nppm = nppm;
    }

    public final int getWidth() {
        return width;
    }

    public final int getHeight() {
        return height;
    }

    public final long getBgcolor() {
        return bgcolor;
    }

    public final long getTextcolor() {
        return textcolor;
    }

    public final long getBordercolor() {
        return bordercolor;
    }

    public final String getName() {
        return name;
    }

    public final long getLinks() {
        return links;
    }

    public final long getWords() {
        return words;
    }

    public final String getType() {
        return type;
    }

    public final int getPpm() {
        return ppm;
    }

    public final String getNetwork() {
        return network;
    }

    public final int getPeers() {
        return peers;
    }

    public final long getNlinks() {
        return nlinks;
    }

    public final long getNwords() {
        return nwords;
    }

    public final double getNqph() {
        return nqph;
    }

    public final long getNppm() {
        return nppm;
    }

}
