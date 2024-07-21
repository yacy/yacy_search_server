/**
 *  YaCyApp
 *  Copyright 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 05.08.2010 at https://yacy.net
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

package net.yacy.gui;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import net.yacy.gui.framework.Application;
import net.yacy.gui.framework.Operation;
import net.yacy.gui.framework.Switchboard;

import org.apache.log4j.Logger;

public class YaCyApp {

    public static Logger log = Logger.getLogger(YaCyApp.class);
    private static JFrame app;
    private static Operation operation;
    private static BufferedImage splashImg = null;
    private static File splashFile = null;
    static {
        try {
            javax.swing.UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
            if (splashFile != null) splashImg = ImageIO.read(splashFile);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public class splashCanvas extends Canvas {
        private static final long serialVersionUID = -8823028472678019008L;
        ImageObserver obs;
        public splashCanvas(ImageObserver obs) { this.obs = obs; }
        @Override
        public void paint(Graphics g) {
            if (splashImg != null) g.drawImage(splashImg, 0, 0, this.obs);
        }
    }

    public static class Op implements Operation {

        JFrame app;
        final String host;
        final int port;

        public Op(JFrame app, String host, int port) {
            this.app = app;
            this.host = host;
            this.port = port;
        }

        @Override
        public void closeAndExit() {
            if (this.app != null) this.app.setVisible(false); // fake closing
            //Browser.openBrowser("http://" + host + ":" + port + "/Steering.html?shutdown=");
            net.yacy.search.Switchboard.getSwitchboard().terminate(10, "shutdown request from gui(1)");
            Switchboard.shutdown();
            //System.exit(0);
        }
    }
    
    public static void start(final String host, final int port) {
        System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name","YaCy Search Engine");
        
        /*
        com.apple.eawt.Application osxapp = com.apple.eawt.Application.getApplication();
        osxapp.setAboutHandler(new com.apple.eawt.AboutHandler() {
            @Override public void handleAbout(com.apple.eawt.AppEvent.AboutEvent evt) {}
        });
        osxapp.setPreferencesHandler(new com.apple.eawt.PreferencesHandler() {
            @Override public void handlePreferences(com.apple.eawt.AppEvent.PreferencesEvent pe) {}
        });
        osxapp.setQuitHandler(new com.apple.eawt.QuitHandler(){
            @Override public void handleQuitRequestWith(com.apple.eawt.AppEvent.QuitEvent evt, com.apple.eawt.QuitResponse resp) {
                net.yacy.search.Switchboard.getSwitchboard().terminate(10, "shutdown request from gui(2)");
                Switchboard.shutdown();
            }
        });
         */
        /*
        com.apple.eawt.QuitHandler quitHandler = new com.apple.eawt.QuitHandler() {
            @Override public void handleQuitRequestWith(com.apple.eawt.AppEvent.QuitEvent evt, com.apple.eawt.QuitResponse resp) {
                net.yacy.search.Switchboard.getSwitchboard().terminate(10, "shutdown request from gui(2)");
                Switchboard.shutdown();
            }
        };
        try {
            Toolkits.applicationGetApplication.invoke(Toolkits.setQuitHandler, quitHandler); //wrong number of arguments
        } catch (IllegalAccessException|IllegalArgumentException|InvocationTargetException e) {
            ConcurrentLog.logException(e);
        }
        */
        Switchboard.startInfoUpdater();
        operation = new Op(app, host, port);

        final List<JMenu> menues = new ArrayList<JMenu>();

        // the file menu
        JMenu FileMenu = new JMenu("File");
        JMenuItem OpenItem = new JMenuItem("Open");
        OpenItem.setEnabled(false);
        JMenuItem QuitItem = new JMenuItem("Quit");
        QuitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                operation.closeAndExit();
            }
        });
        FileMenu.add(OpenItem);
        FileMenu.add(QuitItem);
        menues.add(FileMenu);

        // the edit menu
        JMenu EditMenu = new JMenu("Edit");
        JMenuItem CutItem = new JMenuItem("Cut");
        CutItem.setEnabled(false);
        JMenuItem CopyItem = new JMenuItem("Copy");
        CopyItem.setEnabled(false);
        JMenuItem PasteItem = new JMenuItem("Paste");
        PasteItem.setEnabled(false);
        EditMenu.add(CutItem);
        EditMenu.add(CopyItem);
        EditMenu.add(PasteItem);
        menues.add(EditMenu);

        // registering shutdown hook
        log.info("Registering Shutdown Hook");
        Thread t = new Thread("YaCyApp") {
            @Override
            public void run() {
                app = new Application("YaCy GUI", operation, menues, new InfoPage(host, port));
                app.setLocationRelativeTo(null);
                app.setVisible(true);
            }
        };
        Switchboard.addShutdownHook(t, net.yacy.yacy.shutdownSemaphore);
        SwingUtilities.invokeLater(t);
    }

    public static void main(String[] args) {

        if (args.length > 0) Switchboard.load(new File(args[0]));
        start("localhost", 8090);

    }

}