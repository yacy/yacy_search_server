/**
 *  AnimationGIF
 *  Copyright 2010 by Michael Christen
 *  First released 20.11.2010 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.visualization;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/*
 * for a GIF Image Metadata Format Specification, see:
 * http://docs.oracle.com/javase/6/docs/api/javax/imageio/metadata/doc-files/gif_metadata.html
 */
public class AnimationGIF {
    
    private final static String formatName    = "javax_imageio_gif_image_1.0";
    private final static String aesNodeName   = "ApplicationExtensions";
    private final static String aeNodeName    = "ApplicationExtension";
    private final static String gceNodeName   = "GraphicControlExtension";
    private final static String delayNodeName = "delayTime";
    private final static String transparencyFlagNodeName = "transparentColorFlag";
    private final static String transparencyIndexNodeName = "transparentColorIndex";

    private int counter, loops;
    private IIOMetadata iiom;
    private ImageWriter writer;
    private ImageWriteParam iwp;
    private ImageOutputStream ios;
    private ByteArrayOutputStream baos;
    
    /**
     * create a gif animation producer
     * @param loops - number of loops for the animated images. -1 = no loops; 0 = indefinitely loops; else: number of loops
     */
    public AnimationGIF(int loops) {
        this.counter = 0;
        this.loops = loops;
        this.ios = null;
        this.writer = null;

        this.baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writerIterator = ImageIO.getImageWritersByFormatName("GIF");
        this.writer = writerIterator.next(); // com.sun.media.imageioimpl.plugins.gif.GIFImageWriter, com.sun.imageio.plugins.gif.GIFImageWriter
        this.ios = new MemoryCacheImageOutputStream(baos);
        this.writer.setOutput(ios);
        this.iwp = writer.getDefaultWriteParam();
    }
    
    /**
     * add an image to the animation
     * @param image the image
     * @param delayMillis the frame time of the image in milliseconds
     * @param transparencyColorIndex the index of the transparent color, -1 if not used
     * @throws IOException
     */
    public void addImage(RenderedImage image, int delayMillis, int transparencyColorIndex) throws IOException {
        if (this.counter == 0) {
            iiom = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), iwp);
            writer.prepareWriteSequence(writer.getDefaultStreamMetadata(iwp));
        }
        if (this.counter == 0 && loops >= 0) {
            IIOMetadata imageMetadata2 = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), iwp);
            try {
                setMetadata(imageMetadata2, delayMillis, transparencyColorIndex);
                setLoops(imageMetadata2, this.loops);
                writer.writeToSequence(new IIOImage(image, null, imageMetadata2), iwp);
            } catch (final IIOInvalidTreeException e) {
                throw new IOException(e.getMessage());
            }
        } else try {
            setMetadata(iiom, delayMillis, transparencyColorIndex);
            writer.writeToSequence(new IIOImage(image, null, iiom), iwp);
        } catch (final IIOInvalidTreeException e) {
            throw new IOException(e.getMessage());
        }
        this.counter++;
    }
    
    /**
     * produce the gif image as byte array
     * @return the gif image
     */
    public byte[] get() {
        if (ios != null) try {
            ios.close();
            ios = null;
        } catch (final IOException e) {}
        if (writer != null) {
            writer.dispose();
            writer = null;
        }
        return baos.toByteArray();
    }
    
    private static void setMetadata(IIOMetadata metaData, int delayMillis, int transparencyColorIndex) throws IIOInvalidTreeException {
        Node tree = metaData.getAsTree(formatName);
        NodeList nodeList = tree.getChildNodes();
        Node gceNode = null;
        for (int i = 0, len = nodeList.getLength(); i < len; i++) {
            Node curNode = nodeList.item(i);
            if (curNode.getNodeName().equals(gceNodeName)) {gceNode = curNode; break;}
        }
        if (gceNode == null) throw new IIOInvalidTreeException("Invalid image metadata, could not find " + gceNodeName + "node.", null, tree);
        Node delayNode = gceNode.getAttributes().getNamedItem(delayNodeName);
        if (delayNode == null) {
            delayNode = tree.getOwnerDocument().createAttribute(delayNodeName);
            gceNode.appendChild(delayNode);
        }
        delayNode.setNodeValue(Integer.valueOf(delayMillis / 10).toString());
        if (transparencyColorIndex >= 0) {
            Node transparencyFlagNode = gceNode.getAttributes().getNamedItem(transparencyFlagNodeName);
            if (transparencyFlagNode == null) {
                transparencyFlagNode = tree.getOwnerDocument().createAttribute(transparencyFlagNodeName);
                gceNode.appendChild(transparencyFlagNode);
            }
            transparencyFlagNode.setNodeValue("TRUE");
            Node transparencyIndexNode = gceNode.getAttributes().getNamedItem(transparencyIndexNodeName);
            if (transparencyIndexNode == null) {
                transparencyIndexNode = tree.getOwnerDocument().createAttribute(transparencyIndexNodeName);
                gceNode.appendChild(transparencyIndexNode);
            }
            transparencyIndexNode.setNodeValue(Integer.valueOf(transparencyColorIndex).toString());
        }
        metaData.setFromTree(formatName, tree);
    }
    
    /**
     * set number of loops for this animation
     * @param metaData
     * @param loops - 0 = loop continuously; 1-65535 = a specific number of loops
     * @throws IIOInvalidTreeException
     */
    private static void setLoops(IIOMetadata metaData, int loops) throws IIOInvalidTreeException {
        Node tree = metaData.getAsTree(formatName);
        IIOMetadataNode aes = new IIOMetadataNode(aesNodeName);
        IIOMetadataNode ae = new IIOMetadataNode(aeNodeName);
        ae.setAttribute("applicationID", "NETSCAPE");
        ae.setAttribute("authenticationCode", "2.0");
        ae.setUserObject(new byte[]{0x1, (byte) (loops & 0xFF), (byte) ((loops >> 8) & 0xFF)});
        aes.appendChild(ae);
        tree.appendChild(aes);
        metaData.setFromTree(formatName, tree);
    }
    
    /**
     * test image generator
     */
    private static RenderedImage generateTestImage(int width, int height, Random r, double angle) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.getGraphics();
        g.setColor(Color.white); g.fillRect(0, 0, width, height); g.setColor(Color.BLUE);
        int x = width / 2;
        int y = height / 2;
        int radius = Math.min(x, y);
        g.drawLine(x, y, x + (int) (radius * Math.cos(angle)), y + (int) (radius * Math.sin(angle)));
        g.drawString("giftest", r.nextInt(width), r.nextInt(height));
        return img;
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true"); // go into headless awt mode
        Random r = new Random(System.currentTimeMillis());
        int framescount = 100;
        AnimationGIF generator = new AnimationGIF(0);
        try {
            for (int i = 0; i < framescount; i++) {
                generator.addImage(generateTestImage(320, 160, r, i * 2 * Math.PI / framescount), 10, 0);
            }
            FileOutputStream fos = new FileOutputStream(new File("/tmp/giftest.gif"));
            fos.write(generator.get());
            fos.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
