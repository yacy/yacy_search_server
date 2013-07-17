// SignatureOutputStream.java 
// ----------------
// (C) 2009 by Florian Richter <mail@f1ori.de>
// first published 16.04.2009 on http://yacy.net
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

package net.yacy.utils;

import java.io.IOException;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

/**
 * A SignatureOuputStream is composed of a Signature and a OutputStream so that
 * write()-methods first update the signature and then pass the data to the
 * underlying OutputStream.
 * 
 * @author flori
 *
 */
public class SignatureOutputStream extends FilterOutputStream {
    
    private Signature signature;
   
    /**
     * create new SignatureOutputStream and setup the Signature
     * @param stream OutputStream to pass data on
     * @param algorithm Algorithm to use for Signature
     * @param publicKey Public key to verify Signature against
     * @throws NoSuchAlgorithmException
     */
    public SignatureOutputStream(OutputStream stream, String algorithm, PublicKey publicKey) throws NoSuchAlgorithmException {
	super(stream);
        try {
    	    signature = Signature.getInstance(algorithm);
            signature.initVerify(publicKey);
        } catch (final InvalidKeyException e) {
            System.out.println("Internal Error at signature:" + e.getMessage());
	}
    }
    
    /**
     * write byte
     * @see FilterOutputStream.write(int b)
     */
    @Override
    public void write(int b) throws IOException {
	try {
	    signature.update((byte)b);
	} catch (final SignatureException e) {
	    throw new IOException("Signature update failed: "+ e.getMessage());
	}
	out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
	try {
	    signature.update(b, off, len);
	} catch (final SignatureException e) {
	    throw new IOException("Signature update failed: "+ e.getMessage());
	}
	out.write(b, off, len);
    }
    
    /**
     * verify signature, don't use this stream for another signature afterwards
     * @param sign signature as bytes
     * @return true, when signature was right
     * @throws SignatureException
     */
    public boolean verify(byte[] sign) throws SignatureException {
	return signature.verify(sign);
    }

}
