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
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

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

	public Tray(final Switchboard sb_par) {
		sb = sb_par;
		menuEnabled = sb.getConfigBool(SwitchboardConstants.TRAY_MENU_ENABLED, true);
		trayLabel = sb.getConfig(SwitchboardConstants.TRAY_ICON_LABEL, "YaCy");
		try {
			final boolean trayIconEnabled = sb.getConfigBool(SwitchboardConstants.TRAY_ICON_ENABLED, false);
			final boolean trayIconForced = sb.getConfigBool(SwitchboardConstants.TRAY_ICON_FORCED, false);
			if (trayIconEnabled && (OS.isWindows || trayIconForced)) {
				System.setProperty("java.awt.headless", "false"); // we have to switch off headless mode, else all will fail

				if(SystemTray.isSupported()) {
					final String iconPath = sb.getAppPath().toString() + "/addon/YaCy_TrayIcon.png".replace("/", File.separator);
					ActionListener al = new ActionListener() {
						@Override
                        public void actionPerformed(final ActionEvent e) {
							doubleClickAction();
						}
					};
					final Image i = Toolkit.getDefaultToolkit().getImage(iconPath);
					final PopupMenu menu = (menuEnabled) ? getPopupMenu() : null;
					ti = new TrayIcon(i, trayLabel, menu);
					ti.setImageAutoSize(true);
					ti.addActionListener(al);
					SystemTray.getSystemTray().add(ti);
					isShown = true;
				} else {
					System.setProperty("java.awt.headless", "true");
				}
			}
		} catch (final Exception e) {
			System.setProperty("java.awt.headless", "true");
		}
	}

	/**
	 * set all functions available
	 */
	public void setReady() {
		appIsReady = true;
	}

	public void remove() {
		if (isShown){
			SystemTray.getSystemTray().remove(ti);
			ti = null;
			isShown = false;
		}
	}

	private void doubleClickAction() {
		if (!appIsReady) {
			String label;
			if (deutsch)
				label = "Bitte warten bis YaCy gestartet ist.";
			else if (french)
				label = "S'il vous pla��t attendre jusqu'�� YaCy est d��marr��.";
			else
				label = "Please wait until YaCy is started.";
			//ti.displayMessage("YaCy",label);
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
		MenuItem menuItem;

		// YaCy Search
		if (deutsch)
			label = "YaCy Suche";
		else if (french)
			label = "YaCy Recherche";
		else
			label = "YaCy Search";
		menuItem = new MenuItem(label);
		menuItem.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(final ActionEvent e) {
				openBrowserPage("");
			}
		});
		menu.add(menuItem);


		/*  no prominent compare since google can not be displayed in a frame anymore
		// Compare YaCy
		if (deutsch)
			label = "Vergleichs-Suche";
		else if (french)
			label = "Comparer YaCy";
		else
			label = "Compare YaCy";
		menuItem = new MenuItem(label);
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				openBrowser("compare_yacy.html");
			}
		});
		menu.add(menuItem);
		 */

		// Peer Administration
		if (deutsch)
			label = "Peer Administration";
		else if (french)
			label = "Peer Administration";
		else
			label = "Peer Administration";
		menuItem = new MenuItem(label);
		menuItem.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(final ActionEvent e) {
				openBrowserPage("Status.html");
			}
		});
		menu.add(menuItem);

		// Separator
		menu.addSeparator();

		// Quit
		if(deutsch) 
			label = "YaCy Beenden";
		else if(french)
			label = "Arr��t YaCy";
		else
			label = "Shutdown YaCy";
		menuItem = new MenuItem(label);
		menuItem.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(final ActionEvent e) {
				sb.terminate("shutdown from tray");
			}
		});
		menu.add(menuItem);
		return menu;
	}

}
