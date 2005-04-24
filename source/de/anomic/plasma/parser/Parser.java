/*
 * Check4Update is a stand-alone server application that can be used to 
 * monitor various types of online resources for updates and changes and
 * notifies the user if a modification was detected.
 * 
 * Copyright (C) 2005 Martin Thelian
 * 
 * This program is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 2 of the License, or (at 
 * your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * For more information, please email thelian@users.sourceforge.net
 * 
 */ 

/* =======================================================================
 * Revision Control Information
 * $Source: $
 * $Author: $
 * $Date: $
 * $Revision: $
 * ======================================================================= */

package de.anomic.plasma.parser;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;

import de.anomic.plasma.plasmaParserDocument;

public interface Parser {
    
    public plasmaParserDocument parse(URL location, String mimeType, byte[] source)
    throws ParserException;
    
    public plasmaParserDocument parse(URL location, String mimeType, File sourceFile)
    throws ParserException;
    
    public plasmaParserDocument parse(URL location, String mimeType, InputStream source) 
    throws ParserException;
            
    public HashSet getSupportedMimeTypes();
    
    public void reset();
    
    
}
