/**
 *  InfoPage
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

package net.yacy.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import net.yacy.gui.framework.Browser;
import net.yacy.gui.framework.Layout;
import net.yacy.gui.framework.Switchboard;
import net.yacy.kelondro.util.OS;

public class InfoPage implements Layout {

    private static final String COMMIT_ACTION = "commit";
    
    private static final int width = 500;
    private static final int height = 600;
    private static final int textHeight = 18;
    
    final String host;
    final int port;
    JTextComponent SearchBox;
    
    public InfoPage(String host, int port) {
        this.host = host; this.port = port;
    }
    

    private class CommitAction extends AbstractAction {
        private static final long serialVersionUID = 3630229455629476865L;
        @Override
        public void actionPerformed(ActionEvent ev) {
            //int pos = SearchBox.getSelectionEnd();
            Browser.openBrowser("http://" + host + ":" + port + "/yacysearch.html?display=0&verify=true&contentdom=text&nav=all&maximumRecords=10&startRecord=0&resource=global&urlmaskfilter=.*&prefermaskfilter=&indexof=off&meanCount=5&query=" + SearchBox.getText().replace(' ', '+'));
            SearchBox.setText("");
            //SearchBox..insert(" ", pos);
            //SearchBox.setCaretPosition(pos + 1);
        }
    }
    
    @Override
    public LayoutManager getPage(JComponent context, DocumentListener listener) {
        GroupLayout page = new GroupLayout(context);
        
        //String[] fnames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        //for (String fname: fnames) System.out.println("font: " + fname);
        
        Font font = Font.decode("SansSerif");
        if (font != null) font = font.deriveFont((float) 14.0); 
        //if (font != null) font = font.deriveFont(Font.BOLD);

        SearchBox = new JTextField();
        SearchBox.setText("search...");
        SearchBox.setCaretPosition(0);
        SearchBox.moveCaretPosition(9);
        SearchBox.setFont(font.deriveFont((float) 14.0).deriveFont(Font.BOLD));
        SearchBox.setSize(width + 4, textHeight);
        SearchBox.setBorder(BorderFactory.createEmptyBorder());
        SearchBox.setBackground(Color.decode("#EEEEDD"));
        SearchBox.getDocument().addDocumentListener(listener);
        InputMap im = SearchBox.getInputMap();
        ActionMap am = SearchBox.getActionMap();
        im.put(KeyStroke.getKeyStroke("ENTER"), COMMIT_ACTION);
        am.put(COMMIT_ACTION, new CommitAction());
        
        Switchboard.InfoBox = new JTextField();
        Switchboard.InfoBox.setBorder(BorderFactory.createTitledBorder(""));
        Switchboard.InfoBox.setSize(width, textHeight);
        Switchboard.InfoBox.setBorder(BorderFactory.createEmptyBorder());
        Switchboard.InfoBox.setBackground(Color.decode("#EEEEDD"));
        Switchboard.InfoBox.setText("search window initialized");
        Switchboard.InfoBox.setFont(font.deriveFont((float) 11.0));
        
        // make the scroll pane that contains the search result
        JComponent mainText = new JEditorPane();
        mainText.setPreferredSize(new java.awt.Dimension(480, 590));
        String infotext =
            "This is a YaCy GUI wrappper.\n\n" +
            "The YaCy administration interface is in your browser\n" +
            "just open http://localhost:8090\n\n" +
            "You may also enter a search term and press enter,\n" +
            "then the query will be opened in your browser\n";
        if (OS.isMacArchitecture) infotext += "\nThe application data on Mac is stored at ~Library/YaCy/\n";
        ((JEditorPane) mainText).setText(infotext);
        //page.add(new splashCanvas());

        //SplashScreen splash = SplashScreen.getSplashScreen(); 
        //Graphics2D g2 = splash.createGraphics();
        //splash.update();
        
        JScrollPane pane = new JScrollPane();
        pane.setViewportView(mainText);
        
        // combine search box and scroll pane
        page.setVerticalGroup(page.createSequentialGroup()
            .addComponent(SearchBox, GroupLayout.PREFERRED_SIZE, textHeight + 4, GroupLayout.PREFERRED_SIZE)
            .addComponent(pane, 0, height, Short.MAX_VALUE) // height
            .addComponent(Switchboard.InfoBox, GroupLayout.PREFERRED_SIZE, textHeight, GroupLayout.PREFERRED_SIZE)
            ); 
        page.setHorizontalGroup(page.createSequentialGroup()
            .addGroup(page.createParallelGroup()
                .addComponent(SearchBox, GroupLayout.Alignment.LEADING, 0, width, Short.MAX_VALUE) // width
                .addComponent(pane, GroupLayout.Alignment.LEADING, 0, width, Short.MAX_VALUE)
                .addComponent(Switchboard.InfoBox, GroupLayout.Alignment.TRAILING, 0, width, Short.MAX_VALUE)));
        return page;
    }
}
