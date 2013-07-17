// CryptoLib.java
// (C) 2009 by Florian Richter; f1ori@users.berlios.de
// first published 2.3.2009 on http://yacy.net
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
//
package net.yacy.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import net.yacy.cora.order.Base64Order;
import net.yacy.kelondro.io.CharBuffer;


/**
 * Tool functions to sign and verify files and generate keys
 *
 * Start with "java -cp classes de.anomic.tools.CryptoLib --help"
 * from main folder
 *
 * @author flori
 *
 */
public class CryptoLib {

    private static final String HELP =
	"Tool to sign files and verify the signature.\n" +
	"Usage:\n" +
	" --help  Print this help\n" +
	" --sign privatekey file\n" +
	"         Sign file with key and save signature as file.sig\n" +
	" --verify publickey file file.sig\n" +
	"         Verify signatur\n" +
	" --gen-key privatekey publickey\n";

    public static final String algorithm = "DSA";
    public static final int bitkey       = 1024;
    public static final String signAlgorithm = "SHA1with"+algorithm;

    private final KeyFactory keyFact;
    private final Signature sign;

    public CryptoLib() throws NoSuchAlgorithmException {
	this.keyFact = KeyFactory.getInstance(algorithm);
	this.sign = Signature.getInstance(signAlgorithm);
    }

    public PrivateKey getPrivateKeyFromBytes(byte[] keyBuffer) throws InvalidKeySpecException {
	return this.keyFact.generatePrivate(new PKCS8EncodedKeySpec(keyBuffer));
    }

    public PublicKey getPublicKeyFromBytes(byte[] keyBuffer) throws InvalidKeySpecException {
	return this.keyFact.generatePublic(new X509EncodedKeySpec(keyBuffer));
    }

    public byte[] getBytesOfPrivateKey(PrivateKey privKey) throws InvalidKeySpecException {
	EncodedKeySpec keySpec =
	    this.keyFact.getKeySpec(privKey, PKCS8EncodedKeySpec.class);
	return keySpec.getEncoded();
    }

    public byte[] getBytesOfPublicKey(PublicKey pubKey) throws InvalidKeySpecException {
	EncodedKeySpec keySpec =
	    this.keyFact.getKeySpec(pubKey, X509EncodedKeySpec.class);
	return keySpec.getEncoded();
    }

    public byte[] getSignature(PrivateKey privKey, InputStream dataStream) throws InvalidKeyException, SignatureException, IOException {
	this.sign.initSign(privKey);
	byte[] buffer = new byte[1024];
	int count = 0;
	while((count = dataStream.read(buffer)) != -1) {
	    this.sign.update(buffer, 0, count);
	}
	dataStream.close();
	return this.sign.sign();
    }

    public boolean verifySignature(PublicKey pubKey, InputStream dataStream, byte[] signBuffer) throws InvalidKeyException, SignatureException, IOException {
	this.sign.initVerify(pubKey);

	byte[] buffer = new byte[1024];
	int count = 0;
	while((count = dataStream.read(buffer)) != -1) {
	    this.sign.update(buffer, 0, count);
	}
	dataStream.close();

	return this.sign.verify(signBuffer);
    }

    public KeyPair genKeyPair() throws NoSuchAlgorithmException {
	KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
	kpg.initialize(bitkey);
	return kpg.generateKeyPair();
    }


    public static void main(String[] args) {
	try {
	    if(args.length < 1 || args[0].contains("help")) {
		System.out.println(HELP);

	    } else if(args[0].equals("--sign") && args.length==3) {
		CryptoLib cl = new CryptoLib();
		CharBuffer privKeyBuffer = new CharBuffer(new File(args[1]));
		byte[] privKeyByteBuffer = Base64Order.standardCoder.decode(privKeyBuffer.toString());
        privKeyBuffer.close();
		PrivateKey privKey = cl.getPrivateKeyFromBytes(privKeyByteBuffer);

		FileInputStream dataStream = new FileInputStream(args[2]);

		byte[] signBuffer = cl.getSignature(privKey, dataStream);
		FileWriter signFile = new FileWriter(args[2] + ".sig");
		signFile.write(Base64Order.standardCoder.encode(signBuffer));
		signFile.close();
	    } else if(args[0].equals("--verify") && args.length==3) {
		CryptoLib cl = new CryptoLib();
		CharBuffer pubKeyBuffer = new CharBuffer(new File(args[1]));
		byte[] pubKeyByteBuffer = Base64Order.standardCoder.decode(pubKeyBuffer.toString().trim());
		pubKeyBuffer.close();
		PublicKey pubKey = cl.getPublicKeyFromBytes(pubKeyByteBuffer);

		FileInputStream dataStream = new FileInputStream(args[2]);

		CharBuffer signBuffer = new CharBuffer(new File(args[2] + ".sig"));
		byte[] signByteBuffer = Base64Order.standardCoder.decode(signBuffer.toString().trim());
		signBuffer.close();
		if(cl.verifySignature(pubKey, dataStream, signByteBuffer)) {
		    System.out.println("Signature OK!");
		} else {
		    System.out.println("Signature FALSE!!!!!!!!!!!");
		    System.exit(1);
		}

	    } else if(args[0].equals("--gen-key") && args.length==3) {
		CryptoLib cl = new CryptoLib();

		KeyPair kp = cl.genKeyPair();

		FileWriter privFile = new FileWriter(args[1]);
		privFile.write(Base64Order.standardCoder.encode(
			cl.getBytesOfPrivateKey(kp.getPrivate())));
		privFile.close();

		FileWriter pubFile = new FileWriter(args[2]);
		pubFile.write(Base64Order.standardCoder.encode(
			cl.getBytesOfPublicKey(kp.getPublic())));
		pubFile.close();
	    }

	} catch (final FileNotFoundException e) {
	    System.out.println("File not found: " + e.getMessage());
	} catch (final IOException e) {
	    System.out.println("IO-Error: " + e.getMessage());
	} catch (final NoSuchAlgorithmException e) {
	    System.out.println("No such Algorithm: " + e.getMessage());
	} catch (final InvalidKeySpecException e) {
	    System.out.println("Key has invalid format: " + e.getMessage());
	} catch (final InvalidKeyException e) {
	    System.out.println("Invalid Key: " + e.getMessage());
	} catch (final SignatureException e) {
	    System.out.println("Error while signing: " + e.getMessage());
	}
    }
}
