package pt.tumba.parser.swf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *  A Shape Symbol
 *
 *@author     unknown
 *@created    15 de Setembro de 2002
 */
public class Shape extends Symbol {
    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public abstract static class Element {
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public abstract static class Style extends Shape.Element {
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public abstract static class FillStyle extends Shape.Style {
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class ColorFill extends Shape.FillStyle {
        /**
         *  Description of the Field
         */
        protected Color color;


        /**
         *@return    may be Color or AlphaColor
         */
        public Color getColor() {
            return color;
        }


        /**
         *  Sets the color attribute of the ColorFill object
         *
         *@param  color  The new color value
         */
        public void setColor(Color color) {
            this.color = color;
        }


        /**
         *  Constructor for the ColorFill object
         *
         *@param  color  Description of the Parameter
         */
        public ColorFill(Color color) {
            this.color = color;
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class ImageFill extends Shape.FillStyle {
        /**
         *  Description of the Field
         */
        protected Symbol image;
        /**
         *  Description of the Field
         */
        protected Transform matrix;
        /**
         *  Description of the Field
         */
        protected boolean clipped;


        /**
         *  Gets the image attribute of the ImageFill object
         *
         *@return    The image value
         */
        public Symbol getImage() {
            return image;
        }


        /**
         *  Gets the transform attribute of the ImageFill object
         *
         *@return    The transform value
         */
        public Transform getTransform() {
            return matrix;
        }


        /**
         *  Gets the clipped attribute of the ImageFill object
         *
         *@return    The clipped value
         */
        public boolean isClipped() {
            return clipped;
        }


        /**
         *  Sets the image attribute of the ImageFill object
         *
         *@param  image  The new image value
         */
        public void setImage(Symbol image) {
            this.image = image;
        }


        /**
         *  Sets the transform attribute of the ImageFill object
         *
         *@param  matrix  The new transform value
         */
        public void setTransform(Transform matrix) {
            this.matrix = matrix;
        }


        /**
         *  Sets the clipped attribute of the ImageFill object
         *
         *@param  isClipped  The new clipped value
         */
        public void setClipped(boolean isClipped) {
            clipped = isClipped;
        }


        /**
         *  Constructor for the ImageFill object
         *
         *@param  image      Description of the Parameter
         *@param  matrix     Description of the Parameter
         *@param  isClipped  Description of the Parameter
         */
        public ImageFill(Symbol image, Transform matrix, boolean isClipped) {
            this.image = image;
            this.matrix = matrix;
            this.clipped = isClipped;
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class GradientFill extends Shape.FillStyle {
        /**
         *  Description of the Field
         */
        protected Color[] colors;
        /**
         *  Description of the Field
         */
        protected int[] ratios;
        /**
         *  Description of the Field
         */
        protected Transform matrix;
        /**
         *  Description of the Field
         */
        protected boolean radial;


        /**
         *  Gets the colors attribute of the GradientFill object
         *
         *@return    The colors value
         */
        public Color[] getColors() {
            return colors;
        }


        /**
         *  Gets the transform attribute of the GradientFill object
         *
         *@return    The transform value
         */
        public Transform getTransform() {
            return matrix;
        }


        /**
         *  Gets the ratios attribute of the GradientFill object
         *
         *@return    The ratios value
         */
        public int[] getRatios() {
            return ratios;
        }


        /**
         *  Gets the radial attribute of the GradientFill object
         *
         *@return    The radial value
         */
        public boolean isRadial() {
            return radial;
        }


        /**
         *  Sets the colors attribute of the GradientFill object
         *
         *@param  colors  The new colors value
         */
        public void setColors(Color[] colors) {
            this.colors = colors;
        }


        /**
         *  Sets the ratios attribute of the GradientFill object
         *
         *@param  ratios  The new ratios value
         */
        public void setRatios(int[] ratios) {
            this.ratios = ratios;
        }


        /**
         *  Sets the transform attribute of the GradientFill object
         *
         *@param  matrix  The new transform value
         */
        public void setTransform(Transform matrix) {
            this.matrix = matrix;
        }


        /**
         *  Sets the radial attribute of the GradientFill object
         *
         *@param  isRadial  The new radial value
         */
        public void setRadial(boolean isRadial) {
            this.radial = isRadial;
        }


        /**
         *  Constructor for the GradientFill object
         *
         *@param  colors    Description of the Parameter
         *@param  ratios    Description of the Parameter
         *@param  matrix    Description of the Parameter
         *@param  isRadial  Description of the Parameter
         */
        public GradientFill(Color[] colors, int[] ratios,
                Transform matrix, boolean isRadial) {
            this.colors = colors;
            this.matrix = matrix;
            this.radial = isRadial;
            this.ratios = ratios;
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class LineStyle extends Shape.Style {
        /**
         *  Description of the Field
         */
        protected double width;
        /**
         *  Description of the Field
         */
        protected Color color;


        /**
         *  Gets the width attribute of the LineStyle object
         *
         *@return    The width value
         */
        public double getWidth() {
            return width;
        }


        /**
         *  Gets the color attribute of the LineStyle object
         *
         *@return    The color value
         */
        public Color getColor() {
            return color;
        }


        /**
         *  Sets the width attribute of the LineStyle object
         *
         *@param  width  The new width value
         */
        public void setWidth(double width) {
            this.width = width;
        }


        /**
         *  Sets the color attribute of the LineStyle object
         *
         *@param  color  The new color value
         */
        public void setColor(Color color) {
            this.color = color;
        }


        /**
         *  Constructor for the LineStyle object
         *
         *@param  width  Description of the Parameter
         *@param  color  Description of the Parameter
         */
        public LineStyle(double width, Color color) {
            this.width = width;
            this.color = color;
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public abstract static class SetStyle extends Shape.Element {
        /**
         *  Description of the Field
         */
        protected int index;


        /**
         *  Gets the styleIndex attribute of the SetStyle object
         *
         *@return    The styleIndex value
         */
        public int getStyleIndex() {
            return index;
        }


        /**
         *  Sets the styleIndex attribute of the SetStyle object
         *
         *@param  index  The new styleIndex value
         */
        public void setStyleIndex(int index) {
            this.index = index;
        }


        /**
         *  Constructor for the SetStyle object
         *
         *@param  index  Description of the Parameter
         */
        protected SetStyle(int index) {
            this.index = index;
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public abstract static class SetFillStyle extends Shape.SetStyle {
        /**
         *  Constructor for the SetFillStyle object
         *
         *@param  index  Description of the Parameter
         */
        protected SetFillStyle(int index) {
            super(index);
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class SetLeftFillStyle extends Shape.SetFillStyle {
        /**
         *  Constructor for the SetLeftFillStyle object
         *
         *@param  index  Description of the Parameter
         */
        public SetLeftFillStyle(int index) {
            super(index);
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class SetRightFillStyle extends Shape.SetFillStyle {
        /**
         *  Constructor for the SetRightFillStyle object
         *
         *@param  index  Description of the Parameter
         */
        public SetRightFillStyle(int index) {
            super(index);
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class SetLineStyle extends Shape.SetStyle {
        /**
         *  Constructor for the SetLineStyle object
         *
         *@param  index  Description of the Parameter
         */
        public SetLineStyle(int index) {
            super(index);
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public abstract static class Vector extends Shape.Element {
        /**
         *  Description of the Field
         */
        protected double x, y;


        /**
         *  Gets the x attribute of the Vector object
         *
         *@return    The x value
         */
        public double getX() {
            return x;
        }


        /**
         *  Gets the y attribute of the Vector object
         *
         *@return    The y value
         */
        public double getY() {
            return y;
        }


        /**
         *  Sets the x attribute of the Vector object
         *
         *@param  x  The new x value
         */
        public void setX(double x) {
            this.x = x;
        }


        /**
         *  Sets the y attribute of the Vector object
         *
         *@param  y  The new y value
         */
        public void setY(double y) {
            this.y = y;
        }


        /**
         *  Constructor for the Vector object
         *
         *@param  x  Description of the Parameter
         *@param  y  Description of the Parameter
         */
        protected Vector(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class Move extends Shape.Vector {
        /**
         *  Constructor for the Move object
         *
         *@param  x  Description of the Parameter
         *@param  y  Description of the Parameter
         */
        public Move(double x, double y) {
            super(x, y);
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class Line extends Shape.Vector {
        /**
         *  Constructor for the Line object
         *
         *@param  x  Description of the Parameter
         *@param  y  Description of the Parameter
         */
        public Line(double x, double y) {
            super(x, y);
        }
    }


    /**
     *  Description of the Class
     *
     *@author     unknown
     *@created    15 de Setembro de 2002
     */
    public static class Curve extends Shape.Vector {
        /**
         *  Description of the Field
         */
        protected double cx, cy;


        /**
         *  Gets the controlX attribute of the Curve object
         *
         *@return    The controlX value
         */
        public double getControlX() {
            return cx;
        }


        /**
         *  Gets the controlY attribute of the Curve object
         *
         *@return    The controlY value
         */
        public double getControlY() {
            return cy;
        }


        /**
         *  Sets the controlX attribute of the Curve object
         *
         *@param  cx  The new controlX value
         */
        public void setControlX(double cx) {
            this.cx = cx;
        }


        /**
         *  Sets the controlY attribute of the Curve object
         *
         *@param  cy  The new controlY value
         */
        public void setControlY(double cy) {
            this.cy = cy;
        }


        /**
         *  Constructor for the Curve object
         *
         *@param  x         Description of the Parameter
         *@param  y         Description of the Parameter
         *@param  controlX  Description of the Parameter
         *@param  controlY  Description of the Parameter
         */
        public Curve(double x, double y, double controlX, double controlY) {
            super(x, y);
            this.cx = controlX;
            this.cy = controlY;
        }
    }


    /**
     *  Description of the Field
     */
    protected List elements = new ArrayList();
    /**
     *  Description of the Field
     */
    protected double minX, maxX, minY, maxY;
    //bounding rectangle
    /**
     *  Description of the Field
     */
    protected boolean hasAlpha = false;
    /**
     *  Description of the Field
     */
    protected double maxLineWidth;
    /**
     *  Description of the Field
     */
    protected double currx, curry;


    /**
     *  Constructor for the Shape object
     */
    public Shape() { }


    /**
     *  Get the bounding rectangle as a double[4] - (min-X,min-Y,max-X,max-Y)
     *
     *@return    The boundingRectangle value
     */
    public double[] getBoundingRectangle() {
        return new double[]{minX, minY, maxX, maxY};
    }


    /**
     *  Set the bounding rectangle. This will be automatically calculated as the
     *  geometry vectors are defined and this rectangle will be enlarged if it
     *  does not contain all the vectors.
     *
     *@param  minx  The new boundingRectangle value
     *@param  minY  The new boundingRectangle value
     *@param  maxX  The new boundingRectangle value
     *@param  maxY  The new boundingRectangle value
     */
    public void setBoundingRectangle(double minX, double minY,
            double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }


    /**
     *  Access the list of shape elements Each object is a subclass of
     *  Shape.Element
     *
     *@return    The shapeElements value
     */
    public List getShapeElements() {
        return elements;
    }


    /**
     *  Define a line style
     *
     *@param  color  if null then black is assumed
     *@param  width  Description of the Parameter
     */
    public void defineLineStyle(double width, Color color2) {
        Color color = color2;
        if (color == null) {
            color = new Color(0, 0, 0);
        }

        LineStyle style = new LineStyle(width, color);

        if (maxLineWidth < width) {
            maxLineWidth = width;
        }

        if (color instanceof AlphaColor) {
            hasAlpha = true;
        }

        elements.add(style);
    }


    /**
     *  Define a color fill
     *
     *@param  color  if null then white is assumed
     */
    public void defineFillStyle(Color color2) {
        Color color = color2;
        if (color == null) {
            color = new Color(255, 255, 255);
        }
        ColorFill fill = new ColorFill(color);

        if (color instanceof AlphaColor) {
            hasAlpha = true;
        }

        elements.add(fill);
    }


    /**
     *  Define an image fill
     *
     *@param  image    Description of the Parameter
     *@param  matrix   Description of the Parameter
     *@param  clipped  Description of the Parameter
     */
    public void defineFillStyle(Symbol image, Transform matrix, boolean clipped) {
        ImageFill fill = new ImageFill(image, matrix, clipped);

        elements.add(fill);
    }


    /**
     *  Define a gradient fill
     *
     *@param  colors  Description of the Parameter
     *@param  ratios  Description of the Parameter
     *@param  matrix  Description of the Parameter
     *@param  radial  Description of the Parameter
     */
    public void defineFillStyle(Color[] colors, int[] ratios,
            Transform matrix, boolean radial) {
        GradientFill fill = new GradientFill(colors, ratios, matrix, radial);

        elements.add(fill);

        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == null) {
                continue;
            }
            if (colors[i] instanceof AlphaColor) {
                hasAlpha = true;
            }
        }
    }


    /**
     *  Set the left fill style
     *
     *@param  index  The new leftFillStyle value
     */
    public void setLeftFillStyle(int index) {
        SetLeftFillStyle fill = new SetLeftFillStyle(index);

        elements.add(fill);
    }


    /**
     *  Set the right fill style
     *
     *@param  index  The new rightFillStyle value
     */
    public void setRightFillStyle(int index) {
        SetRightFillStyle fill = new SetRightFillStyle(index);

        elements.add(fill);
    }


    /**
     *  Set the line style
     *
     *@param  index  The new lineStyle value
     */
    public void setLineStyle(int index) {
        SetLineStyle style = new SetLineStyle(index);

        elements.add(style);
    }


    /**
     *  Move the pen without drawing any line
     *
     *@param  x  Description of the Parameter
     *@param  y  Description of the Parameter
     */
    public void move(double x, double y) {
        Move move = new Move(x, y);

        if (x < minX) {
            minX = x;
        }
        if (y < minY) {
            minY = y;
        }
        if (x > maxX) {
            maxX = x;
        }
        if (y > maxY) {
            maxY = y;
        }

        elements.add(move);
    }


    /**
     *  Draw a line in the current line style (if any)
     *
     *@param  x  Description of the Parameter
     *@param  y  Description of the Parameter
     */
    public void line(double x, double y) {
        Line line = new Line(x, y);

        if (x < minX) {
            minX = x;
        }
        if (y < minY) {
            minY = y;
        }
        if (x > maxX) {
            maxX = x;
        }
        if (y > maxY) {
            maxY = y;
        }

        elements.add(line);
    }


    /**
     *  Draw a curve in the current line style (if any)
     *
     *@param  x         Description of the Parameter
     *@param  y         Description of the Parameter
     *@param  controlX  Description of the Parameter
     *@param  controlY  Description of the Parameter
     */
    public void curve(double x, double y, double controlX, double controlY) {
        Curve curve = new Curve(x, y, controlX, controlY);

        if (x < minX) {
            minX = x;
        }
        if (y < minY) {
            minY = y;
        }
        if (x > maxX) {
            maxX = x;
        }
        if (y > maxY) {
            maxY = y;
        }

        if (controlX < minX) {
            minX = controlX;
        }
        if (controlY < minY) {
            minY = controlY;
        }
        if (controlX > maxX) {
            maxX = controlX;
        }
        if (controlY > maxY) {
            maxY = controlY;
        }

        elements.add(curve);
    }


    /**
     *  Description of the Method
     *
     *@param  movie             Description of the Parameter
     *@param  timelineWriter    Description of the Parameter
     *@param  definitionWriter  Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  IOException   Description of the Exception
     */
    protected int defineSymbol(Movie movie,
            SWFTagTypes timelineWriter,
            SWFTagTypes definitionWriter)
             throws IOException {
        currx = 0.0;
        curry = 0.0;

        predefineImageFills(movie, timelineWriter, definitionWriter);

        int id = getNextId(movie);

        Rect outline = getRect();

        SWFShape shape = hasAlpha ?
                definitionWriter.tagDefineShape3(id, outline) :
                definitionWriter.tagDefineShape2(id, outline);

        writeShape(shape);

        return id;
    }


    /**
     *  Gets the rect attribute of the Shape object
     *
     *@return    The rect value
     */
    protected Rect getRect() {
        double adjust = maxLineWidth / 2.0;

        Rect outline = new Rect((int) (minX * SWFConstants.TWIPS - adjust * SWFConstants.TWIPS),
                (int) (minY * SWFConstants.TWIPS - adjust * SWFConstants.TWIPS),
                (int) (maxX * SWFConstants.TWIPS + adjust * SWFConstants.TWIPS),
                (int) (maxY * SWFConstants.TWIPS + adjust * SWFConstants.TWIPS));

        return outline;
    }


    /**
     *  Description of the Method
     *
     *@param  movie             Description of the Parameter
     *@param  timelineWriter    Description of the Parameter
     *@param  definitionWriter  Description of the Parameter
     *@exception  IOException   Description of the Exception
     */
    protected void predefineImageFills(Movie movie,
            SWFTagTypes timelineWriter,
            SWFTagTypes definitionWriter)
             throws IOException {
        //--Make sure any image fills are defined prior to the shape
        for (Iterator it = elements.iterator(); it.hasNext(); ) {
            Object el = it.next();

            if (el instanceof Shape.ImageFill) {
                Symbol image = ((Shape.ImageFill) el).getImage();

                if (image != null) {
                    image.define(movie,
                            timelineWriter,
                            definitionWriter);
                }
            }
        }
    }


    /**
     *  Description of the Method
     *
     *@param  shape            Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeShape(SWFShape shape) throws IOException {
        for (Iterator it = elements.iterator(); it.hasNext(); ) {
            Object el = it.next();

            if (el instanceof Shape.ColorFill) {
                Shape.ColorFill fill = (Shape.ColorFill) el;
                shape.defineFillStyle(fill.getColor());
            } else if (el instanceof Shape.ImageFill) {
                Shape.ImageFill fill = (Shape.ImageFill) el;

                Symbol image = fill.getImage();
                int imgId = (image != null) ? image.getId() : 65535;

                shape.defineFillStyle(imgId, fill.getTransform(), fill.isClipped());
            } else if (el instanceof Shape.GradientFill) {
                Shape.GradientFill fill = (Shape.GradientFill) el;

                shape.defineFillStyle(fill.getTransform(),
                        fill.getRatios(),
                        fill.getColors(),
                        fill.isRadial());
            } else if (el instanceof Shape.LineStyle) {
                Shape.LineStyle style = (Shape.LineStyle) el;

                shape.defineLineStyle((int) (style.getWidth() * SWFConstants.TWIPS),
                        style.getColor());
            } else if (el instanceof Shape.SetLeftFillStyle) {
                Shape.SetLeftFillStyle style = (Shape.SetLeftFillStyle) el;
                shape.setFillStyle0(style.getStyleIndex());
            } else if (el instanceof Shape.SetRightFillStyle) {
                Shape.SetRightFillStyle style = (Shape.SetRightFillStyle) el;
                shape.setFillStyle1(style.getStyleIndex());
            } else if (el instanceof Shape.SetLineStyle) {
                Shape.SetLineStyle style = (Shape.SetLineStyle) el;
                shape.setLineStyle(style.getStyleIndex());
            } else {
                writeVector(shape, el);
            }
        }

        shape.done();
    }


    /**
     *  Description of the Method
     *
     *@param  vecs             Description of the Parameter
     *@param  el               Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeVector(SWFVectors vecs, Object el) throws IOException {
        if (el instanceof Shape.Move) {
            Shape.Move move = (Shape.Move) el;

            currx = move.getX() * SWFConstants.TWIPS;
            curry = move.getY() * SWFConstants.TWIPS;

            int x = (int) currx;
            int y = (int) curry;

            vecs.move(x, y);

            //System.out.println( "M: " + x + " " + y );
        } else if (el instanceof Shape.Line) {
            Shape.Line line = (Shape.Line) el;

            double xx = line.getX() * SWFConstants.TWIPS;
            double yy = line.getY() * SWFConstants.TWIPS;

            int dx = (int) (xx - currx);
            int dy = (int) (yy - curry);

            vecs.line(dx, dy);

            //System.out.println( "currx=" + currx + " curry=" + curry + " xx=" + xx + " yy=" + yy + " (xx - currx)=" + (xx - currx) + "  (yy - curry)=" + (yy - curry) );
            //System.out.println( "L: " + dx + " " + dy );

            currx = xx;
            curry = yy;
        } else if (el instanceof Shape.Curve) {
            Shape.Curve curve = (Shape.Curve) el;

            double xx = curve.getX() * SWFConstants.TWIPS;
            double yy = curve.getY() * SWFConstants.TWIPS;
            double cxx = curve.getControlX() * SWFConstants.TWIPS;
            double cyy = curve.getControlY() * SWFConstants.TWIPS;

            int dx = (int) (xx - cxx);
            int dy = (int) (yy - cyy);
            int cx = (int) (cxx - currx);
            int cy = (int) (cyy - curry);

            vecs.curve(cx, cy, dx, dy);

            currx = xx;
            curry = yy;

            //System.out.println( "C: " + cx + " " + cy + " " + dx + " " + dy );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  vecs             Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    protected void writeGlyph(SWFVectors vecs) throws IOException {
        currx = 0.0;
        curry = 0.0;

        for (Iterator it = elements.iterator(); it.hasNext(); ) {
            writeVector(vecs, it.next());
        }

        vecs.done();
    }
}
