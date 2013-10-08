package net.yacy.http;


import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author bubu
 */
public class Jetty9_Implementation_Test {

    public Jetty9_Implementation_Test() {
    }

     /**
     * Test of main method, of class yacy.
     */
    @Test
    public void testYaCyMain() {
        System.out.println("* * * * * * * * * * * * * * * * * * * * *");
        System.err.println("* This is a Implementation test         *");
        System.out.println("* for Jetty 9 (not a normal test case)  *");
        System.out.println("* Shutdown YaCy to continue             *");
        System.out.println("* * * * * * * * * * * * * * * * * * * * *");
        
        String[] args = new String[]{};
        YacyMain.main(args);

        assertTrue(true);

    }

}