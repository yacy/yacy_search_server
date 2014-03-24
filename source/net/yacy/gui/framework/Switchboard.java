/**
 *  Switchboard
 *  Copyright 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 05.08.2010 at http://yacy.net
 *  
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

package net.yacy.gui.framework;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Semaphore;

import javax.swing.text.JTextComponent;

import net.yacy.cora.util.ConcurrentLog;

/**
 * a static class that holds application-wide parameters
 * very useful because global settings need not be passed
 * to all classes and methods
 * 
 * @author m.christen
 *
 */
public class Switchboard {

    /**
     * the shallrun variable is used
     * by all application parts to see if they must terminate
     */
    private static boolean shallrun = true;
    
    /**
     * a global properties object
     */
    private static Properties properties = new Properties();
    
    public final static ConcurrentLog log = new ConcurrentLog(Switchboard.class.getName());

    
    public static void startInfoUpdater() {
        new InfoUpdater(2000).start();
    }
    
    public static void addShutdownHook(Thread mainThread, Semaphore semaphore) {
        // registering shutdown hook
        final Runtime run = Runtime.getRuntime();
        run.addShutdownHook(new shutdownHookThread(mainThread, semaphore));
    }
    
    public static JTextComponent InfoBox = null;
    private static String InfoBoxMessage = "";
    private static long InfoBoxMessageUntil = 0;
    
    public static void info(String infoString, long infoTime) {
        InfoBoxMessage = infoString;
        InfoBoxMessageUntil = System.currentTimeMillis() + infoTime;
    }
    
    public static class InfoUpdater extends Thread {
        long steptime;
        public InfoUpdater(long steptime) {
            this.steptime = steptime;
        }
        @Override
        public void run() {
            while (shallrun) {
                if (InfoBox != null) {
                    if (System.currentTimeMillis() < InfoBoxMessageUntil) {
                        InfoBox.setText(InfoBoxMessage);
                    }
                }
                try {Thread.sleep(steptime);} catch (final InterruptedException e) {}
            }
        }
    }
    
    /**
    * This class is a helper class whose instance is started, when the java virtual
    * machine shuts down. Signals the Switchboard to shut down.
    */
    public static class shutdownHookThread extends Thread {
        private final Thread mainThread;
        private final Semaphore shutdownSemaphore;

        public shutdownHookThread(final Thread mainThread, Semaphore semaphore) {
            super();
            this.mainThread = mainThread;
            this.shutdownSemaphore = semaphore;
        }

        @Override
        public void run() {
            try {
                if (shallrun()) {
                    log.info("Shutdown via shutdown hook.");

                    // send a shutdown signal to the switchboard
                    log.info("Signaling shutdown to the switchboard.");
                    shutdown();
                    
                    // waiting for the main thread to finish execution
                    log.info("Waiting for GUI thread to finish.");
                    this.mainThread.interrupt();
                    if (this.mainThread != null && this.mainThread.isAlive()) {
                        this.mainThread.join();
                    }
                    // wait until everything is written
                    log.info("Waiting for main thread to finish. shutdownSemaphore.permits = " + shutdownSemaphore.availablePermits());
                    shutdownSemaphore.acquireUninterruptibly();
                    //log.info("Aquired shutdown semaphore. remaining permits = " + shutdownSemaphore.availablePermits());
                    
                    // finished
                    log.info("Shutdown Hook Terminated. Shutdown.");
                }
            } catch (final Exception e) {
                log.info("Unexpected error. " + e.getClass().getName(),e);
            }
        }
    }
    
    /**
     * test if the application shall run
     * @return true if the application shall run
     */
    public static boolean shallrun() {
        return shallrun;
    }
    
    /**
     * set a termination signal.
     * this is not reversible.
     */
    public static void shutdown() {
        shallrun = false;
    }
    
    /**
     * initialize the properties with the content of a file
     * @param propFile
     */
    public static void load(File propFile) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(propFile);
            properties.load(fis);
        } catch (final FileNotFoundException e1) {
            log.info("error: file dispatcher.properties does not exist. Exit");
            System.exit(-1);
        } catch (final IOException e1) {
            log.info("error: file dispatcher.properties cannot be readed. Exit");
            System.exit(-1);
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ex) { }
        }
    }
    
    /**
     * access to the properties object
     * @param key
     * @return the property value or null if the property does not exist
     */
    public static String get(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * access to the properties object
     * @param key
     * @param dflt
     * @return
     */
    public static String get(String key, String dflt) {
        return properties.getProperty(key, dflt);
    }
    
    /**
     * convenience access to integer values in properties
     * @param key
     * @param dflt
     * @return
     */
    public static int getInt(String key, int dflt) {
        if (!properties.containsKey(key)) return dflt;
        return Integer.parseInt(properties.getProperty(key));
    }
    
    /**
     * convenience access to boolean values in properties
     * @param key
     * @param dflt
     * @return
     */
    public static boolean getBool(String key, boolean dflt) {
        if (!properties.containsKey(key)) return dflt;
        String s = properties.getProperty(key);
        return s.equals("true") || s.equals("1");
    }
    
    public static File getFile(String key) {
        String s = properties.getProperty(key);
        if (s == null) return null;
        s.replace("/", File.separator);
        return new File(s);
    }
    
    /**
     * set a property
     * @param key
     * @param value
     */
    public static void set(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * convenience method to set a integer property
     * @param key
     * @param value
     */
    public static void set(String key, int value) {
        properties.setProperty(key, Integer.toString(value));
    }
    
    /**
     * convenience method to set a boolean property
     * @param key
     * @param value
     */
    public static void set(String key, boolean value) {
        properties.setProperty(key, (value) ? "true" : "false");
    }
}