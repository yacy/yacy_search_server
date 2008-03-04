
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;


public class screenshot {
    /*
    try {
        Robot robot = new Robot();
    
        Rectangle area = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage bufferedImage = robot.createScreenCapture(area);
    
    } catch (AWTException e) {
    }
    */
    public static void main(String[] args) throws Exception {
        
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        Rectangle screenRect = new Rectangle(screenSize);
        // create screen shot
        Robot robot = new Robot();
        BufferedImage image = robot.createScreenCapture(screenRect);
        String outFileName = "test.png";
        // save captured image to PNG file
        ImageIO.write(image, "png", new File(outFileName));
        // give feedback
        System.out.println("Saved screen shot (" + image.getWidth() + " x " + image.getHeight() + " pixels) to file \"" + outFileName + "\".");

    }
}
