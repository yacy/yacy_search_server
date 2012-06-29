/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.yacy.utils;

import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.yacy.gui.framework.Browser;

/**
 * Allow running the aplication yacy from java, useful from running from IDE etc
 *
 * @author marek
 */
public class StartFromJava {

    private String cmdStart = "./startYACY.sh";
    private String cmdStop = "./stopYACY.sh";

    public StartFromJava() {
        //FIXME: rewrite browser to general use utility UtilExecuteFile
        if(Browser.systemOS != Browser.systemUnix) {
            throw new UnsupportedOperationException("RUN for other os than Linux not done yet.");
        }
    }

    public void start() throws Exception {
        Browser.openBrowser(cmdStart);
    }

    public void stop() throws Exception {
        Browser.openBrowser(cmdStop);
    }

    public static void main(String[] args) {
        try {
            StartFromJava run = new StartFromJava();
            run.start();
            System.out.println("run ./stopYACY.sh to stop it or type STOP here");
            Scanner sc = new Scanner(System.in);
            String s = "aaa";
            do {
                System.out.println("type STOP to stop YACY");
                s = sc.nextLine();
            } while(!"STOP".equals(s));

            run.stop();

        } catch(Exception ex) {
            Logger.getLogger(StartFromJava.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
