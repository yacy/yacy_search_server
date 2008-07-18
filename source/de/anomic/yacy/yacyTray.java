// yacyTray.java
// (C) 2008 by David Wieditz; d.wieditz@gmx.de
// first published 13.07.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate:  $
// $LastChangedRevision:  $
// $LastChangedBy:  $
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

import org.jdesktop.jdic.tray.SystemTray;
import org.jdesktop.jdic.tray.TrayIcon;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverSystem;


public class yacyTray implements ActionListener, ItemListener {
	private boolean testing = false;
	
	plasmaSwitchboard sb;
	
	public static boolean isShown = false;
	public static boolean lockBrowserPopup = true;
	
	private long t1;

    final private static SystemTray tray = SystemTray.getDefaultSystemTray();
    private static TrayIcon ti;
    
	public yacyTray(plasmaSwitchboard sb, boolean showmenu) {
		this.sb = sb;
		
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if( Integer.parseInt(System.getProperty("java.version").substring(2,3)) >=5 )
            System.setProperty("javax.swing.adjustPopupLocationToFit", "false");
        
        JPopupMenu menu;
		JMenuItem menuItem;
		
		final String iconpath = sb.getRootPath().toString() + "/addon/YaCy_TrayIcon.gif".replace("/", File.separator);
		final ImageIcon i = new ImageIcon(iconpath);
		
		// the menu is disabled because of visibility conflicts on windows with jre6
		// anyway the code might be a template for future use
        if (showmenu) {
			// this is the popup menu
			menu = new JPopupMenu("YaCy");
			
			// YaCy Search
			menuItem = new JMenuItem("YaCy Search");
			menuItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					openBrowser("");
				}
			});
			menu.add(menuItem);
			
			// Quit
			if (testing) {
				menu.addSeparator();
				menuItem = new JMenuItem("Quit");
				menuItem.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						System.exit(0);
					}
				});
				menu.add(menuItem);
			}
			
			// Tray Icon
			ti = new TrayIcon(i, "YaCy", menu);
		} else {
			ti = new TrayIcon(i, "YaCy");
		}
       
        ti.setIconAutoSize(true);
        ti.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	trayClickAction();
            }
        });        
        tray.addTrayIcon(ti);
        isShown = true;
    }
	
	private void trayClickAction(){	//detect doubleclick
		if(System.currentTimeMillis() - t1 < 500){
			if (lockBrowserPopup) {
				displayBalloonMessage("YaCy","Please wait until YaCy is started.");
			} else {
				openBrowser("");
			}
			t1 = 0; //protecting against tripleclick
		} else { t1 = System.currentTimeMillis(); }
	}
	
	private void openBrowser(String browserPopUpPage){
		// no need for https, because we are on localhost
		serverSystem.openBrowser("http://localhost:" + sb.getConfig("port", "8080") + "/" + browserPopUpPage);
	}
	
	public void removeTray(){
		tray.removeTrayIcon(ti);
		isShown = false;
	}
	
	public void displayBalloonMessage(String title, String message){
		ti.displayMessage(title, message, 0);
	}

    public void actionPerformed(ActionEvent e) { }

    public void itemStateChanged(ItemEvent e) {	}
    
}
