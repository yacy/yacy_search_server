//PKCS12Tool.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2006
//
//This file ist contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Enumeration;

public class PKCS12Tool {

    private final KeyStore kspkcs12;
    private final String kspkcs12Pass;
    
    public PKCS12Tool(final String pkcs12FileName, final String pkcs12Pwd) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException {
        if (pkcs12FileName == null) throw new NullPointerException();
        this.kspkcs12Pass = pkcs12Pwd;
    
        // creating PKCS12 keystore
        this.kspkcs12 = KeyStore.getInstance("PKCS12");
        
        // load pkcs12 file into keystore object
        final FileInputStream fileIn = new FileInputStream(pkcs12FileName);
        this.kspkcs12.load(fileIn,(pkcs12Pwd!=null)?pkcs12Pwd.toCharArray():null);
        
        // close stream
        fileIn.close();
    }
    
    public Enumeration<String> aliases() throws KeyStoreException {
        return this.kspkcs12.aliases();
    }
    
    public void printAliases() throws KeyStoreException {
        final Enumeration<String> aliases = aliases();
        while (aliases.hasMoreElements()) {
            System.out.println(aliases.nextElement());
        }    
    }
    
    public void importToJKS(final String jksName, final String jksPassword) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
        // creating java keystore
        final KeyStore jks=KeyStore.getInstance("JKS");
        
        // loading keystore from file        
        FileInputStream jksFileIn = null;
        final File jksFile = new File(jksName);
                
        if (jksFile.exists()) {
            System.err.println("Loading java keystore from file '" + jksFile + "'");
            jksFileIn = new FileInputStream(jksFile); 
        } else{
            System.err.println("Creating new java keystore '" + jksFile + "'");
        }
        jks.load(jksFileIn,(jksPassword!=null)?jksPassword.toCharArray():null);
        if (jksFileIn != null) jksFileIn.close();
         
        final Enumeration<String> pkcs12Aliases = aliases();
        while (pkcs12Aliases.hasMoreElements()) {
           final String strAlias = pkcs12Aliases.nextElement();
           System.err.println("Importing Alias '" + strAlias + "'");

           if (this.kspkcs12.isKeyEntry(strAlias)) {
              System.err.println("- Alias has key");
              final Key key = this.kspkcs12.getKey(strAlias, (this.kspkcs12Pass!=null)?this.kspkcs12Pass.toCharArray():null);
              System.err.println("- Alias key imported");

              final Certificate[] chain = this.kspkcs12.getCertificateChain(strAlias);
              System.err.println("- Alias certificate chain size: " + chain.length);

              jks.setKeyEntry(strAlias, key, (jksPassword!=null)?jksPassword.toCharArray():null, chain);
           }
        }        
        
        // storing jdk into file
        System.err.print("Storing java keystore");
        final FileOutputStream jksFileOut = new FileOutputStream(jksName);
        jks.store(jksFileOut,(jksPassword!=null)?jksPassword.toCharArray():null);
        jksFileOut.close();
        System.err.print("Import finished.");
    }
    
    /**
     * @param args
     */
    public static void main(final String[] args) throws Exception {
        final PKCS12Tool pkcs12 = new PKCS12Tool("c:/temp/keystore.pkcs12","test");
        //pkcs12.printAliases();
        pkcs12.importToJKS("c:/temp/jks.ks", "test");
   }

}
