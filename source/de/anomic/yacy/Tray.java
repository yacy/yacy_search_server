// yacyTray.java
// (C) 2008 by David Wieditz; d.wieditz@gmx.de
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

package de.anomic.yacy;

import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.yacy.gui.framework.Browser;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.OS;

import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;


public final class Tray {
    protected static Switchboard sb;
	
	private static nativeTrayIcon ti;
	private static boolean isIntegrated; // browser integration
	private static boolean isShown = false;
	final private static boolean deutsch = System.getProperty("user.language","").equals("de");
	final private static boolean french = System.getProperty("user.language","").equals("fr");
	
	public static String trayLabel;
	
	public static boolean lockBrowserPopup = true;
	
	
	public static void init(final Switchboard par_sb) {
		sb = par_sb;
		isIntegrated = sb.getConfigBool(SwitchboardConstants.BROWSERINTEGRATION, false);
		trayLabel = sb.getConfig(SwitchboardConstants.TRAY_LABEL, "YaCy");
		try {
			final boolean trayIcon = sb.getConfigBool(SwitchboardConstants.TRAY_ICON_ENABLED, false);
			if (trayIcon && (OS.isWindows || sb.getConfigBool(SwitchboardConstants.TRAY_ICON_FORCED, false))) {
				System.setProperty("java.awt.headless", "false");

				if(nativeTrayIcon.isSupported()) {
					final String iconpath = sb.getAppPath().toString() + "/addon/YaCy_TrayIcon.png".replace("/", File.separator);
					ActionListener al = new ActionListener() {
						public void actionPerformed(final ActionEvent e) {
							trayClickAction();
						}
					};
					ti = new nativeTrayIcon(iconpath, al, setupPopupMenu());

					ti.addToSystemTray();
					isShown = true;
				} else {
					System.setProperty("java.awt.headless", "true");
				}
			}
		} catch (final Exception e) {
			System.setProperty("java.awt.headless", "true");
		}
	}

	public static PopupMenu setupPopupMenu() {
		String label;
		// this is the popup menu
		PopupMenu menu = new PopupMenu();
		MenuItem menuItem;
		
		if(isIntegrated) return menu;
		
		// YaCy Search
		if (deutsch)
                    label = "YaCy Suche";
                else if (french)
                    label = "YaCy Recherche";
                else
                    label = "YaCy Search";
		menuItem = new MenuItem(label);
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				openBrowser("");
			}
		});
		menu.add(menuItem);
		
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
		
		// Peer Administration
		if (deutsch)
                    label = "Peer Administration";
                else if (french)
                    label = "Peer Administration";
                else
                    label = "Peer Administration";
		menuItem = new MenuItem(label);
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				openBrowser("Status.html");
			}
		});
		menu.add(menuItem);
		
		// Separator
		menu.addSeparator();

		// Quit
		if(deutsch) 
                    label = "YaCy Beenden";
                else if(french)
                    label = "Arrêt YaCy";
                else
                    label = "Shutdown YaCy";
		menuItem = new MenuItem(label);
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				sb.terminate("shutdown from tray");
			}
		});
		menu.add(menuItem);
		return menu;
	}
    
	
	protected static void trayClickAction(){	//doubleclick
		if (lockBrowserPopup) {
			String label;
			if (deutsch)
                            label = "Bitte warten bis YaCy gestartet ist.";
                        else if (french)
                            label = "S'il vous plaît attendre jusqu'à YaCy est démarré.";
                        else
                            label = "Please wait until YaCy is started.";
			ti.displayBalloonMessage("YaCy",label);
		} else {
			openBrowser("");
		}
	}
	
	protected static void openBrowser(final String browserPopUpPage){
		if(isIntegrated) return;
		// no need for https, because we are on localhost
		Browser.openBrowser("http://localhost:" + sb.getConfig("port", "8090") + "/" + browserPopUpPage);
	}
	
	public static void removeTray(){
		if (isShown){
		ti.removeFromSystemTray();
		isShown = false;
		}
	}
	
}

class nativeTrayIcon {
	private Object SystemTray;
	private Object TrayIcon;
	private Class<?> SystemTrayClass;
	private Class<?> TrayIconClass;

	public static boolean isSupported() {
		try {
			Class<?> l_SystemTrayClass = Class.forName("java.awt.SystemTray");
			//Object SystemTray = SystemTrayClass.newInsta
			Method isSupportedMethod = l_SystemTrayClass.getMethod("isSupported", (Class[])null);
			Boolean isSupported = (Boolean)isSupportedMethod.invoke(null, (Object[])null);
			return isSupported;
		} catch (Throwable e) {
			return false;
		}

	}

	public nativeTrayIcon(String IconPath, ActionListener al, PopupMenu menu) {
		if(!isSupported()) return;

		final Image i = Toolkit.getDefaultToolkit().getImage(IconPath);
		
		try {
			this.TrayIconClass = Class.forName("java.awt.TrayIcon");
			this.SystemTrayClass = Class.forName("java.awt.SystemTray");

			// with reflections: this.TrayIcon = new TrayIcon(i, "YaCy");
			Class<?> partypes1[] = new Class[3];
			partypes1[0] = Image.class;
			partypes1[1] = String.class;
			partypes1[2] = PopupMenu.class;
			Constructor<?> TrayIconConstructor = TrayIconClass.getConstructor(partypes1);

			Object arglist1[] = new Object[3];
			arglist1[0] = i;
			arglist1[1] = Tray.trayLabel;
			arglist1[2] = menu;
			this.TrayIcon = TrayIconConstructor.newInstance(arglist1);

			// with reflections: this.TrayIcon.setImageAutoSize(true)
			Class<?> partypes2[] = new Class[1];
			partypes2[0] = Boolean.TYPE;
			Method setImageAutoSizeMethod = TrayIconClass.getMethod("setImageAutoSize", partypes2);

			Object arglist2[] = new Object[1];
			arglist2[0] = Boolean.TRUE;
			setImageAutoSizeMethod.invoke(this.TrayIcon, arglist2);

			// with reflections: this.TrayIcon.addActionListener(al)
			Class<?> partypes3[] = new Class[1];
			partypes3[0] = ActionListener.class;
			Method addActionListenerMethod = TrayIconClass.getMethod("addActionListener", partypes3);

			Object arglist3[] = new Object[1];
			arglist3[0] = al;
			addActionListenerMethod.invoke(this.TrayIcon, arglist3);

			// with reflections: nativSystemTray = SystemTray.getDefaultSystemTray()
			Method getDefaultSystemTrayMethod = SystemTrayClass.getMethod("getSystemTray", (Class[])null);

			this.SystemTray = getDefaultSystemTrayMethod.invoke(null, (Object[])null);

		} catch (Throwable e) {
		    Log.logException(e);
			this.TrayIcon = null;
		}
	}

    public void addToSystemTray() {
		try {
			// with reflections: this.SystemTray.add(this.TrayIcon)
			Class<?> partypes1[] = new Class[1];
			partypes1[0] = TrayIconClass;
			Method addMethod = SystemTrayClass.getMethod("add", partypes1);

			Object arglist1[] = new Object[1];
			arglist1[0] = this.TrayIcon;
			addMethod.invoke(this.SystemTray, arglist1);
		} catch (Throwable e) {
		    Log.logException(e);
		}
	}

    public void removeFromSystemTray() {
		try {
			// with reflections: this.SystemTray.remove(this.TrayIcon)
			Class<?> partypes1[] = new Class[1];
			partypes1[0] = TrayIconClass;
			Method removeMethod = SystemTrayClass.getMethod("remove", partypes1);

			Object arglist1[] = new Object[1];
			arglist1[0] = this.TrayIcon;
			removeMethod.invoke(this.SystemTray, arglist1);
		} catch (Throwable e) {
		    Log.logException(e);
		}
	}

    public void displayBalloonMessage(final String title, final String message) {
		try {
			// with reflections: this.TrayIcon.displayBalloonMessage(title, message, TrayIcon.MessageType.NONE)
			Class<?> partypes1[] = new Class[3];
			partypes1[0] = String.class;
			partypes1[1] = String.class;
			partypes1[2] = Class.forName("java.awt.TrayIcon.MessageType");
			Method displayBalloonMessageMethod = TrayIconClass.getMethod("displayBalloonMessage", partypes1);

			Object arglist1[] = new Object[1];
			arglist1[0] = title;
			arglist1[1] = message;
			arglist1[2] = null;
			displayBalloonMessageMethod.invoke(this.TrayIcon, arglist1);
		} catch (Throwable e) {
		    Log.logException(e);
		}
	}
}
