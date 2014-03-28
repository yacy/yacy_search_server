/**
 *  Application
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

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


public class Application extends JFrame implements DocumentListener {

    private static final long serialVersionUID = 1753502658600073141L;

    public Application(String windowName, final Operation operation, List<JMenu> menues, Layout pageprovider) {
        super(windowName);
        
        try {
            getContentPane().setLayout(pageprovider.getPage((JComponent) getContentPane(), this));
            
            this.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    operation.closeAndExit();
                }
            });
            this.addWindowStateListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    operation.closeAndExit();
                }
            });
            
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            
            // make menus
            JMenuBar mainMenu = new JMenuBar();
            setJMenuBar(mainMenu);
            for (JMenu menu: menues) mainMenu.add(menu);
            
            pack();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        
    }
    
    
}