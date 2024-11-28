// Tray.java
// (C) 2008-2012 by David Wieditz; d.wieditz@gmx.de
// (C) 2008 by Florian Richter; Florian_Richter@gmx.de
// first published 13.07.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.gui.framework.Browser;
import net.yacy.kelondro.util.OS;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;


public final class Tray {
    private final Switchboard sb;

    private TrayIcon ti = null;
    private final String trayLabel;

    final private static boolean deutsch = System.getProperty("user.language","").equals("de");
    final private static boolean french = System.getProperty("user.language","").equals("fr");

    // states
    private boolean isShown = false;
    private boolean appIsReady = false;
    private boolean menuEnabled = true;
    private BufferedImage[] progressIcons = null;
    private final String iconPath;

    private MenuItem menuItemHeadline = null;
    private MenuItem menuItemSearch = null;
    private MenuItem menuItemAdministration = null;
    private MenuItem menuItemTerminate = null;

    public Tray(final Switchboard sb_par) {
        this.sb = sb_par;
        this.menuEnabled = this.sb.getConfigBool(SwitchboardConstants.TRAY_MENU_ENABLED, true);
        this.trayLabel = this.sb.getConfig(SwitchboardConstants.TRAY_ICON_LABEL, "YaCy");
        this.iconPath = this.sb.getAppPath().toString() + "/addon/YaCy_TrayIcon.png".replace("/", File.separator);
        if (this.useTray()) {
            try {
                System.setProperty("java.awt.headless", "false"); // we have to switch off headless mode, else all will fail
                if (SystemTray.isSupported()) {
                    final ActionListener al = e -> Tray.this.doubleClickAction();
                    ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
                    final Image trayIcon = ImageIO.read(new File(this.iconPath)); // 128x128
                    final Image progressBooting = ImageIO.read(new File(this.sb.getAppPath().toString() + "/addon/progress_booting.png".replace("/", File.separator))); // 128x28
                    final BufferedImage progress = this.getProgressImage();
                    this.progressIcons = new BufferedImage[4];
                    for (int i = 0; i < 4; i++) {
                        this.progressIcons[i] = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                        final Graphics2D h = this.progressIcons[i].createGraphics();
                        h.setBackground(Color.BLACK);
                        h.clearRect(0, 0, 128, 128);
                        h.drawImage(trayIcon, 0, 0, 128, 128 - progress.getHeight(), null);
                        h.drawImage(progress.getSubimage(i * 7, 0, 128, progress.getHeight() / 2), 0, 128 - progress.getHeight() / 2, null);
                        h.drawImage(progressBooting, 0, 128 - progress.getHeight(), null);
                        h.dispose();
                    }
                    final PopupMenu menu = (this.menuEnabled) ? this.getPopupMenu() : null;
                    this.ti = new TrayIcon(trayIcon, this.trayLabel, menu);
                    if (OS.isMacArchitecture) setDockIcon(trayIcon);
                    this.ti.setImageAutoSize(true);
                    this.ti.addActionListener(al);
                    SystemTray.getSystemTray().add(this.ti);
                    this.isShown = true;
                    this.ti.setToolTip(this.startupMessage());
                    new TrayAnimation().start();
                } else {
                    System.setProperty("java.awt.headless", "true");
                }
            } catch (final Throwable e) {
                System.setProperty("java.awt.headless", "true");
            }
        }
    }

    private boolean useTray() {
        final boolean trayIconEnabled = this.sb.getConfigBool(SwitchboardConstants.TRAY_ICON_ENABLED, false);
        final boolean trayIconForced = this.sb.getConfigBool(SwitchboardConstants.TRAY_ICON_FORCED, false);
        return trayIconEnabled && (OS.isWindows || OS.isMacArchitecture || trayIconForced);
    }

    private class TrayAnimation extends Thread {

        public TrayAnimation() {
            super(TrayAnimation.class.getSimpleName());
        }

        int ic = 0;
        @Override
        public void run() {
            while (!Tray.this.appIsReady) {
                Tray.this.ti.setImage(Tray.this.progressIcons[this.ic]);
                if (OS.isMacArchitecture) setDockIcon(Tray.this.progressIcons[this.ic]);
                this.ic++; if (this.ic >= 4) this.ic = 0;
                try {Thread.sleep(80);} catch (final InterruptedException e) {break;}
            }
            try {
                ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
                final Image trayIcon = ImageIO.read(new File(Tray.this.iconPath));
                Tray.this.ti.setImage(trayIcon);
                Tray.this.ti.setToolTip(Tray.this.readyMessage());
                setDockIcon(trayIcon);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
            Tray.this.progressIcons = null;
        }
    }

    private static Object applicationInstance;
    private static Method setDockIconImage;

    private static void setDockIcon(final Image icon) {
        if (!OS.isMacArchitecture || setDockIconImage == null || applicationInstance == null) return;
        try {
            setDockIconImage.invoke(applicationInstance, icon);
        } catch (final Throwable e) {}
        // same as: Application.getApplication().setDockIconImage(i);
    }

    /**
     * set all functions available
     */
    public void setReady() {
        this.appIsReady = true;
        if (this.useTray()) {
            try {
                ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
                final Image trayIcon = ImageIO.read(new File(this.iconPath));
                if (this.ti != null) {
                    this.ti.setImage(trayIcon);
                    this.ti.setToolTip(this.readyMessage());
                }
                setDockIcon(trayIcon);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
            if (this.menuItemHeadline != null) this.menuItemHeadline.setLabel(this.readyMessage());
            if (this.menuItemSearch != null) this.menuItemSearch.setEnabled(true);
            if (this.menuItemAdministration != null) this.menuItemAdministration.setEnabled(true);
            if (this.menuItemTerminate != null) this.menuItemTerminate.setEnabled(true);
        }
    }

    public void setShutdown() {
        if (this.useTray()) {
            try {
                if (this.ti != null) {
                    ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
                    final Image trayIcon = ImageIO.read(new File(this.iconPath)); // 128x128
                    final Image progressShutdown = ImageIO.read(new File(this.sb.getAppPath().toString() + "/addon/progress_shutdown.png".replace("/", File.separator))); // 128x28
                    final BufferedImage progress = this.getProgressImage();
                    BufferedImage shutdownIcon;
                    shutdownIcon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                    final Graphics2D h = shutdownIcon.createGraphics();
                    h.setBackground(Color.BLACK);
                    h.clearRect(0, 0, 128, 128);
                    h.drawImage(trayIcon, 0, 0, 128, 128 - progress.getHeight(), null);
                    h.drawImage(progress.getSubimage(0, 0, 128, progress.getHeight() / 2), 0, 128 - progress.getHeight() / 2, null);
                    h.drawImage(progressShutdown, 0, 128 - progress.getHeight(), null);
                    h.dispose();
                    this.ti.setImage(shutdownIcon);
                    setDockIcon(shutdownIcon);
                    this.ti.setToolTip(this.shutdownMessage());
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
            if (this.menuItemHeadline != null) this.menuItemHeadline.setLabel(this.shutdownMessage());
            if (this.menuItemSearch != null) this.menuItemSearch.setEnabled(false);
            if (this.menuItemAdministration != null) this.menuItemAdministration.setEnabled(false);
            if (this.menuItemTerminate != null) this.menuItemTerminate.setEnabled(false);
        }
    }

    private BufferedImage getProgressImage() throws IOException {
        final String progressPath = this.sb.getAppPath().toString() + "/addon/progressbar.png".replace("/", File.separator);
        ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
        final Image progress_raw = ImageIO.read(new File(progressPath)); // 149x56
        final BufferedImage progress = new BufferedImage(149, 56, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D progressg = progress.createGraphics();
        progressg.drawImage(progress_raw, 0, 0, null);
        progressg.dispose();
        return progress;
    }

    public void remove() {
        if (this.isShown){
            SystemTray.getSystemTray().remove(this.ti);
            this.ti = null;
            this.isShown = false;
        }
    }

    private String startupMessage() {
        if (deutsch)
            return "YaCy startet, bitte warten...";
        else if (french)
            return "YaCy est en cours de démarrage, veuillez patienter...";
        else
            return "YaCy is starting, please wait...";
    }

    private String readyMessage() {
        if (deutsch) return "YaCy laeuft unter http://localhost:" + this.sb.getLocalPort();
        else if(french)
            return "YaCy est en cours d'exécution à l'adresse http://localhost:" + this.sb.getLocalPort();
        return "YaCy is running at http://localhost:" + this.sb.getLocalPort();
    }

    private String shutdownMessage() {
        if (deutsch) return "YaCy wird beendet, bitte warten...";
        else if(french)
            return "YaCy est en cours d'arrêt, veuillez patienter...";
        return "YaCy will shut down, please wait...";
    }

    private void doubleClickAction() {
        if (!this.appIsReady) {
            final String label = this.startupMessage();
            this.ti.displayMessage("YaCy", label, TrayIcon.MessageType.INFO);
        } else {
            this.openBrowserPage("");
        }
    }

    /**
     *
     * @param browserPopUpPage relative path to the webserver root
     */
    private void openBrowserPage(final String browserPopUpPage) {
        if(!this.menuEnabled) return;
        // no need for https, because we are on localhost
        Browser.openBrowser("http://localhost:" + this.sb.getConfig(SwitchboardConstants.SERVER_PORT, "8090") + "/" + browserPopUpPage);
    }

    private PopupMenu getPopupMenu() {
        String label;

        final PopupMenu menu = new PopupMenu();

        // Headline
        this.menuItemHeadline = new MenuItem(this.startupMessage());
        this.menuItemHeadline.setEnabled(false);
        //this.menuItemHeadline.setFont(this.menuItemHeadline.getFont().deriveFont(Font.BOLD)); // does not work because getFont() returns null;
        menu.add(this.menuItemHeadline);

        // Separator
        menu.addSeparator();

        // YaCy Search
        if (deutsch)
            label = "YaCy Suche";
        else if (french)
            label = "Recherche YaCy";
        else
            label = "YaCy Search";
        this.menuItemSearch = new MenuItem(label);
        this.menuItemSearch.setEnabled(false);
        this.menuItemSearch.addActionListener(e -> Tray.this.openBrowserPage("index.html"));
        menu.add(this.menuItemSearch);

        // Peer Administration
        if (deutsch)
            label = "Administration";
        else if (french)
            label = "Administration";
        else
            label = "Administration";
        this.menuItemAdministration = new MenuItem(label);
        this.menuItemAdministration.setEnabled(false);
        this.menuItemAdministration.addActionListener(e -> Tray.this.openBrowserPage("Status.html"));
        menu.add(this.menuItemAdministration);


        // Separator
        menu.addSeparator();

        // Quit
        if(deutsch)
            label = "YaCy Beenden";
        else if(french)
            label = "Arrêter YaCy";
        else
            label = "Shutdown YaCy";
        this.menuItemTerminate = new MenuItem(label);
        this.menuItemTerminate.setEnabled(false);
        this.menuItemTerminate.addActionListener(e -> Tray.this.sb.terminate("shutdown from tray"));
        menu.add(this.menuItemTerminate);
        return menu;
    }

}
