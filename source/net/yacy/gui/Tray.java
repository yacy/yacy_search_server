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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;

import net.yacy.gui.framework.Browser;
import net.yacy.kelondro.util.OS;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;


public final class Tray {
	private Switchboard sb;

	private TrayIcon ti;
	private String trayLabel;

	final private static boolean deutsch = System.getProperty("user.language","").equals("de");
	final private static boolean french = System.getProperty("user.language","").equals("fr");

	// states
	private boolean isShown = false;
	private boolean appIsReady = false;
	private boolean menuEnabled = true;
    private Image trayIcon = null;
    private BufferedImage[] progressIcons = null;

	public Tray(final Switchboard sb_par) {
		sb = sb_par;
		menuEnabled = sb.getConfigBool(SwitchboardConstants.TRAY_MENU_ENABLED, true);
		trayLabel = sb.getConfig(SwitchboardConstants.TRAY_ICON_LABEL, "YaCy");
		try {
			final boolean trayIconEnabled = sb.getConfigBool(SwitchboardConstants.TRAY_ICON_ENABLED, false);
			final boolean trayIconForced = sb.getConfigBool(SwitchboardConstants.TRAY_ICON_FORCED, false);
			if (trayIconEnabled && (OS.isWindows || OS.isMacArchitecture || trayIconForced)) {
			    System.setProperty("java.awt.headless", "false"); // we have to switch off headless mode, else all will fail
			    if (SystemTray.isSupported()) {
    			    final String iconPath = sb.getAppPath().toString() + "/addon/YaCy_TrayIcon.png".replace("/", File.separator);
    			    final String progressPath = sb.getAppPath().toString() + "/addon/progressbar.png".replace("/", File.separator);
    			    final String progressBootingPath = sb.getAppPath().toString() + "/addon/progress_booting.png".replace("/", File.separator);
                    ActionListener al = new ActionListener() {
    					@Override
                        public void actionPerformed(final ActionEvent e) {
    						doubleClickAction();
    					}
    				};
    				this.trayIcon = ImageIO.read(new File(iconPath)); // 128x128
                    Image progress_raw = ImageIO.read(new File(progressPath)); // 149x56
                    Image progressBooting = ImageIO.read(new File(progressBootingPath)); // 128x28
    				BufferedImage progress = new BufferedImage(280, 56, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D progressg = progress.createGraphics();
                    progressg.drawImage(progress_raw, 0, 0, null);
                    progressg.dispose();
                    this.progressIcons = new BufferedImage[4];
                    for (int i = 0; i < 4; i++) {
    				    this.progressIcons[i] = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D h = this.progressIcons[i].createGraphics();
                        h.setBackground(Color.BLACK);
                        h.clearRect(0, 0, 128, 128);
                        h.drawImage(this.trayIcon, 0, 0, 128, 128 - progress.getHeight(), null);
                        h.drawImage(progress.getSubimage(i * 7, 0, 128, progress.getHeight() / 2), 0, 128 - progress.getHeight() / 2, null);
                        h.drawImage(progressBooting, 0, 128 - progress.getHeight(), null);
                        h.dispose();
    				}
                    final PopupMenu menu = (menuEnabled) ? getPopupMenu() : null;
    				ti = new TrayIcon(this.trayIcon, trayLabel, menu);
                    if (OS.isMacArchitecture) setDockIcon(trayIcon);
    				ti.setImageAutoSize(true);
    				ti.addActionListener(al);
    				SystemTray.getSystemTray().add(ti);
    				isShown = true;
    				ti.setToolTip(startupMessage());
    				new TrayAnimation().start();
			    } else {
			        System.setProperty("java.awt.headless", "true");			        
			    }
			}
		} catch (final Exception e) {
			System.setProperty("java.awt.headless", "true");
		}
	}
	
	private class TrayAnimation extends Thread {
	    int ic = 0;
	    @Override
        public void run() {
	        while (!Tray.this.appIsReady) {
	            Tray.this.ti.setImage(Tray.this.progressIcons[ic]);
                if (OS.isMacArchitecture) setDockIcon(Tray.this.progressIcons[ic]);
	            ic++; if (ic >= 4) ic = 0;
	            try {Thread.sleep(80);} catch (InterruptedException e) {break;}
	        }
	        ti.setImage(Tray.this.trayIcon);
	        ti.setToolTip(readyMessage());
            if (OS.isMacArchitecture) setDockIcon(trayIcon);
	    }
	}
	
	private static void setDockIcon(Image icon) {
	    try {
	        Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
	        Method applicationGetApplication = applicationClass.getMethod("getApplication");
	        Object applicationInstance = applicationGetApplication.invoke(null);
	        Method setDockIconImage = applicationClass.getMethod("setDockIconImage", Class.forName("java.awt.Image"));
	        setDockIconImage.invoke(applicationInstance, icon);
        } catch (Throwable e) {}
        // same as: Application.getApplication().setDockIconImage(i);
	}

    private MenuItem menuItemHeadline;
    private MenuItem menuItemSearch;
    private MenuItem menuItemAdministration;
    private MenuItem menuItemTerminate;
	/**
	 * set all functions available
	 */
	public void setReady() {
		appIsReady = true;
		ti.setImage(this.trayIcon);
        if (OS.isMacArchitecture) setDockIcon(trayIcon);
        ti.setToolTip(readyMessage());
        this.menuItemHeadline.setLabel(readyMessage());
        this.menuItemSearch.setEnabled(true);
        this.menuItemAdministration.setEnabled(true);
        this.menuItemTerminate.setEnabled(true);
	}

	public void remove() {
		if (isShown){
			SystemTray.getSystemTray().remove(ti);
			ti = null;
			isShown = false;
		}
	}

    private String startupMessage() {
        if (deutsch)
            return "YaCy startet, bitte warten...";
        else if (french)
            return "S'il vous pla��t attendre jusqu'�� YaCy est d��marr��.";
        else
            return "YaCy is starting, please wait...";
    }
    
    private String readyMessage() {
        if (deutsch) return "YaCy laeuft unter http://localhost:" + sb.getConfig("port", "8090");
        return "YaCy is running at http://localhost:" + sb.getConfig("port", "8090");
    }
    
    private void doubleClickAction() {
        if (!appIsReady) {
            String label = startupMessage();
            ti.displayMessage("YaCy", label, TrayIcon.MessageType.INFO);
        } else {
            openBrowserPage("");
        }
    }

	/**
	 * 
	 * @param browserPopUpPage relative path to the webserver root
	 */
	private void openBrowserPage(final String browserPopUpPage) {
		if(!menuEnabled) return;
		// no need for https, because we are on localhost
		Browser.openBrowser("http://localhost:" + sb.getConfig("port", "8090") + "/" + browserPopUpPage);
	}

	private PopupMenu getPopupMenu() {
		String label;

		PopupMenu menu = new PopupMenu();
		
        // Headline
        this.menuItemHeadline = new MenuItem(startupMessage());
        this.menuItemHeadline.setEnabled(false);
        //this.menuItemHeadline.setFont(this.menuItemHeadline.getFont().deriveFont(Font.BOLD)); // does not work because getFont() returns null;
        menu.add(this.menuItemHeadline);
        
        // Separator
        menu.addSeparator();
        
		// YaCy Search
		if (deutsch)
			label = "YaCy Suche";
		else if (french)
			label = "YaCy Recherche";
		else
			label = "YaCy Search";
		this.menuItemSearch = new MenuItem(label);
		this.menuItemSearch.setEnabled(false);
		this.menuItemSearch.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(final ActionEvent e) {
				openBrowserPage("index.html");
			}
		});
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
		this.menuItemAdministration.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(final ActionEvent e) {
				openBrowserPage("Status.html");
			}
		});
		menu.add(this.menuItemAdministration);
		

		// Separator
		menu.addSeparator();

		// Quit
		if(deutsch) 
			label = "YaCy Beenden";
		else if(french)
			label = "Arr��t YaCy";
		else
			label = "Shutdown YaCy";
		this.menuItemTerminate = new MenuItem(label);
        this.menuItemTerminate.setEnabled(false);
		this.menuItemTerminate.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(final ActionEvent e) {
				sb.terminate("shutdown from tray");
			}
		});
		menu.add(this.menuItemTerminate);
		return menu;
	}

}
