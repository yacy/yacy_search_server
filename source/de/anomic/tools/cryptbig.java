// crypt.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 13.05.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.Provider;
import java.security.Security;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.server.serverCodings;

public class cryptbig {

    // --------------------------------------------------------
    // Section: random salt generation
    // --------------------------------------------------------

    private static long saltcounter = 0;
    private static Random saltrandom = new Random(System.currentTimeMillis());

    public static String randomSalt() {
        // generate robust 48-bit random number
        long salt = (saltrandom.nextLong() & 0XffffffffffffL) + (System.currentTimeMillis() & 0XffffffffffffL) + ((1001 * saltcounter) & 0XffffffffffffL);
        saltcounter++;
        // we generate 48-bit salt values, that are represented as 8-character
        // b64-encoded strings
        return kelondroBase64Order.standardCoder.encodeLong(salt & 0XffffffffffffL, 8);
    }

    // --------------------------------------------------------
    // Section: PBE + PublicKey based on passwords encryption
    // --------------------------------------------------------

    public static final String vDATE = "20030925";
    public static final String copyright = "[ 'crypt' v" + vDATE + " by Michael Christen / www.anomic.de ]";
    public static final String magicString = "crypt|anomic.de|0"; // magic identifier inside every '.crypt' - file
    public static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.ENGLISH);

    String cryptMethod; // one of ["TripleDES", "Blowfish", "DESede", "DES"]
    private static final String defaultMethod = "PBEWithMD5AndDES"; //"DES";

    Cipher ecipher;
    Cipher dcipher;
    
    public cryptbig(String pbe) {
	// this is possible, but not recommended
	this(pbe, (pbe + "XXXXXXXX").substring(0, 8));
    }

    public cryptbig(String pbe, String salt) {
	this(pbe, salt, defaultMethod);
    }


    private cryptbig(String pbe, String salt, String method) {
	// a Password-Based Encryption. The SecretKey is created on the fly
	PBEKeySpec keySpec = new PBEKeySpec(pbe.toCharArray());
	try {
	    if (salt.length() > 8) salt = salt.substring(0,8);
	    if (salt.length() < 8) salt = (salt + "XXXXXXXX").substring(0,8);
	    
	    // create the PBE key
	    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(method);
	    SecretKey key = keyFactory.generateSecret(keySpec);

	    // create parameter spec for PBE
	    PBEParameterSpec paramSpec = new PBEParameterSpec(salt.getBytes(), 1000 /*ITERATIONS*/);
        
	    // Create a cipher and initialize it for encrypting end decrypting
	    cryptMethod = method;
	    ecipher = Cipher.getInstance(cryptMethod);
	    dcipher = Cipher.getInstance(cryptMethod);
	    ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec); // paramSpec only for PBE!
	    dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
	} catch (javax.crypto.NoSuchPaddingException e) {
	} catch (java.security.InvalidKeyException e) {
	} catch (java.security.NoSuchAlgorithmException e) {
	} catch (java.security.spec.InvalidKeySpecException e) {
	} catch (java.security.InvalidAlgorithmParameterException e) {
	}
    }
    
    // Encode a string into a new string using utf-8, crypt and b64
    public String encryptString(String str) {
	try {
	    byte[] utf = str.getBytes("UTF8");
	    byte[] enc = encryptArray(utf);
	    if (enc == null) return null;
	    return kelondroBase64Order.standardCoder.encode(enc);
	} catch (UnsupportedEncodingException e) {
	}
	return null;
    }

    // Decode a string into a new string using b64, crypt and utf-8
    public String decryptString(String str) {
	try {
	    byte[] b64dec = kelondroBase64Order.standardCoder.decode(str, "de.anomic.tools.cryptbig.decryptString()");
	    if (b64dec == null) return null; // error in input string (inconsistency)
	    byte[] dec = decryptArray(b64dec);
	    if (dec == null) return null;
	    return new String(dec, "UTF-8");
	} catch (UnsupportedEncodingException e) {
	}
	return null;
    }

    // Encode a byte array into a new byte array
    public byte[] encryptArray(byte[] b) {
	if (b == null) return null;
	try {
	    return ecipher.doFinal(b);
	} catch (javax.crypto.BadPaddingException e) {
	} catch (IllegalBlockSizeException e) {
	}
	return null;
    }
    
    // Decode a string into a new string using b64, crypt and utf-8
    public byte[] decryptArray(byte[] b) {
	if (b == null) return null;
	try {
	    return dcipher.doFinal(b);
	} catch (javax.crypto.BadPaddingException e) {
	} catch (IllegalBlockSizeException e) {
	}
	return null;
    }


    // This method returns the available implementations for a service type
    public static Set<String> listCryptoMethods(String serviceType) {
        Set<String> result = new HashSet<String>();
    
        // All providers
        Provider[] providers = Security.getProviders();
        for (int i = 0; i < providers.length; i++) {
            // Get services provided by each provider
            Set<?> keys = providers[i].keySet();
            for (Iterator<?> it = keys.iterator(); it.hasNext(); ) {
                String key = (String) it.next();
                key = key.split(" ")[0];
                if (key.startsWith(serviceType + ".")) {
                    result.add(key.substring(serviceType.length() + 1));
                } else if (key.startsWith("Alg.Alias." + serviceType + ".")) {
                    // This is an alias
                    result.add(key.substring(serviceType.length() + 11));
                }
            }
        }
        return result;
    }

    public static void testCryptMethods(Set<String> methods) {
	    String method;
	    Iterator<String> i = methods.iterator();
	    while (i.hasNext()) {
		method = (String) i.next();
		System.out.print(method + " : ");
		try {
		    cryptbig crypter = new cryptbig("abrakadabra", method);
		    String encrypted = crypter.encryptString("nicht verraten abc 1234567890");
		    System.out.print(encrypted + "/");
		    String decrypted = crypter.decryptString(encrypted);
		    System.out.println(decrypted);
		} catch (Exception e) {
		    System.out.println("Exception: " + e.getMessage());
		    e.printStackTrace();
		}
	    }
    }

    public void encryptFile(String inFileName, String outFileName, boolean compress) {
	/*
	  File-Format of encrypted file:
	  Filename: b64-of-encryption-of-<encryption-date: YYYYMMddHHmmSSsss> plus extension ".crypt"
	  File Content:
	  <original file name>
	  <original file date>
	  <original file size>
	  <compressed-before-encryption-flag>
	  <compressed-after-encryption-flag>
	  <binary>
	*/
	try {
	    File   inFile           = new File(inFileName);
	    String inFileDate       = dateFormatter.format(new Date(inFile.lastModified())); // 17 byte
	    String encryptionDate   = dateFormatter.format(new Date()); // 17 byte
	    String inFileSize       = kelondroBase64Order.standardCoder.encodeLong(inFile.length(), 11); // 64 / 6 = 11; 11 byte
	    String flag             = "1"; // 1 byte
	    //int    inFileNameLength = inFileName.length(); // 256
	    String X                = inFileDate + encryptionDate + inFileSize + flag + inFileName;

	    System.out.println("TEST: preserving inFileDate    : " + dateFormatter.parse(inFileDate, new ParsePosition(0)));
	    System.out.println("TEST: preserving encryptionDate: " + dateFormatter.parse(encryptionDate, new ParsePosition(0)));
	    System.out.println("TEST: preserving inFileLength  : " + inFile.length());
	    System.out.println("TEST: preserving flag          : " + flag);
	    System.out.println("TEST: preserving inFileName    : " + inFileName);
	    System.out.println("TEST: preserving X-String      : " + X);

	    // start encryption
	    InputStream  fin  = new CipherInputStream(new FileInputStream(inFile), ecipher);
	    OutputStream fout = new FileOutputStream(outFileName);

	    // write magic and properties of original file
	    // - we encrypt the original date, the encryption date, the file size, the flag
	    //   and file name together to the string A and calculate the length AL of that string
	    // - the length of the file name is therefore equal to AL-(17+17+11+1) = AL-46
	    // - AL is then b64-ed and also encrypted which results into string B
	    // - the length of B is BL; BL is then b64-ed to a string C of fixed length 1
	    // - after the magic String we write C, B and A
	    try {
		String A = new String(ecipher.doFinal(X.getBytes("UTF8")));
		String B = new String(ecipher.doFinal(kelondroBase64Order.standardCoder.encodeLong(A.length(), 2).getBytes("UTF8"))); // most probable not longer than 4
		String C = kelondroBase64Order.standardCoder.encodeLong(B.length(), 1); // fixed length 1 (6 bits, that should be enough)
		fout.write(magicString.getBytes()); // the magic string, used to identify a 'crypt'-file
		fout.write(C.getBytes());
		fout.write(B.getBytes());
		fout.write(A.getBytes());

		// write content of file
		copy(fout, fin, 512);
	    }
	    catch (javax.crypto.IllegalBlockSizeException e) {System.err.println("ERROR:" + e.getMessage());}
	    catch (javax.crypto.BadPaddingException e) {System.err.println("ERROR:" + e.getMessage());}
	    // finished files
	    fin.close();
	    fout.close();
	} catch (FileNotFoundException e) {
	    System.err.println("ERROR: file '" + inFileName + "' not found");
	} catch (IOException e) {
	    System.err.println("ERROR: IO trouble");
	}
    }
	
    public void decryptFile(String inFileName, String outFileName) {
	InputStream  fin = null;
	OutputStream fout = null;
	try {
	    // Start opening the files
	    fin  = new BufferedInputStream(new FileInputStream(inFileName), 4096);

	    // read the file properties
	    byte[] thisMagic = new byte[magicString.length()]; fin.read(thisMagic);

	    if (!((new String(thisMagic)).equals(magicString))) {
		// this is not an crypt file, so dont do anything
		fin.close();
		return;
	    }
	    byte[] C = new byte[1]; fin.read(C); // the length of the following String, encoded as b64
	    byte[] B = new byte[(int) kelondroBase64Order.standardCoder.decodeLong(new String(C))]; fin.read(B); // this is again the length of the following string, as encrypted b64-ed integer
	    byte[] A = new byte[(int) kelondroBase64Order.standardCoder.decodeLong(new String(dcipher.doFinal(B), "UTF8"))]; fin.read(A);
	    String X = new String(dcipher.doFinal(A), "UTF8");

	    System.out.println("TEST: detecting X-String      : " + X);

	    // reconstruct the properties
	    Date   inFileDate     = dateFormatter.parse(X.substring(0, 17), new ParsePosition(0));
	    Date   encryptionDate = dateFormatter.parse(X.substring(17, 34),  new ParsePosition(0));
	    long   inFileSize     = kelondroBase64Order.standardCoder.decodeLong(X.substring(34, 45));
	    String flag           = X.substring(45, 46);
	    String origFileName   = X.substring(46);

	    System.out.println("TEST: detecting inFileDate    : " + inFileDate);
	    System.out.println("TEST: detecting encryptionDate: " + encryptionDate);
	    System.out.println("TEST: detecting inFileLength  : " + inFileSize);
	    System.out.println("TEST: detecting flag          : " + flag);
	    System.out.println("TEST: detecting inFileName    : " + origFileName);

	    // open the output file
	    fout = new BufferedOutputStream(new CipherOutputStream(new FileOutputStream(outFileName), dcipher), 4096);

	    // read and decrypt the file
	    copy(fout, fin, 512);
	    
	    // close the files
	    fin.close();
	    fout.close();

	    // do postprocessing
	} catch (BadPaddingException e) {
	    System.err.println("ERROR: decryption of '" + inFileName + "' not possible: " + e.getMessage());
	} catch (IllegalBlockSizeException e) {
	    System.err.println("ERROR: decryption of '" + inFileName + "' not possible: " + e.getMessage());
	} catch (FileNotFoundException e) {
	    System.err.println("ERROR: file '" + inFileName + "' not found");
	} catch (IOException e) {
	    System.err.println("ERROR: IO trouble");
	    try {fin.close(); fout.close();} catch (Exception ee) {}
	}
    }
	
    private static void copy(OutputStream out, InputStream in, int bufferSize) throws IOException {
	InputStream  bIn  = new BufferedInputStream(in, bufferSize);
	OutputStream bOut = new BufferedOutputStream(out, bufferSize);
	byte[] buf = new byte[bufferSize];
	int n;
	while ((n = bIn.read(buf)) > 0) bOut.write(buf, 0, n);
	bIn.close();
	bOut.close();
    }


    public static String scrambleString(String key, String s) {
	// we perform several operations
	// - generate salt
	// - gzip string
	// - crypt string with key and salt
	// - base64-encode result
	// - attach salt and return
	String salt = randomSalt();
	//System.out.println("Salt=" + salt);
	cryptbig c = new cryptbig(key, salt);
	boolean gzFlag = true;
	byte[] gz = gzip.gzipString(s);
	if (gz.length > s.length()) {
	    // revert compression
	    try {
		gz = s.getBytes("UTF8");
		gzFlag = false;
	    } catch (UnsupportedEncodingException e) {
		return null;
	    }
	}
	//System.out.println("GZIP length=" + gz.length);
	if (gz == null) return null;
	byte[] enc = c.encryptArray(gz);
	if (enc == null) return null;
	return salt + ((gzFlag) ? "1" : "0") + kelondroBase64Order.enhancedCoder.encode(enc);
    }

    public static String descrambleString(String key, String s) {
	String salt = s.substring(0, 8);
	boolean gzFlag = (s.charAt(8) == '1');
	s = s.substring(9);
	cryptbig c = new cryptbig(key, salt);
	byte[] b64dec = kelondroBase64Order.enhancedCoder.decode(s, "de.anomic.tools.cryptbig.descrambleString()");
	if (b64dec == null) return null; // error in input string (inconsistency)
	byte[] dec = c.decryptArray(b64dec);
	if (dec == null) return null;
	if (gzFlag)
	    return gzip.gunzipString(dec);
	else
	    try {return new String(dec,"UTF8");} catch (UnsupportedEncodingException e) {return null;}

    }


    // --------------------------------------------------------
    // Section: simple Codings
    // --------------------------------------------------------


    public static String simpleEncode(String content) {
	return simpleEncode(content, null, 'b');
    }

    public static String simpleEncode(String content, String key) {
	return simpleEncode(content, key, 'b');
    }

    public static String simpleEncode(String content, String key, char method) {
	if (key == null) key = "NULL";
	if (method == 'p') return "p|" + content;
	if (method == 'b') return "b|" + kelondroBase64Order.enhancedCoder.encodeString(content);
	if (method == 'z') return "z|" + kelondroBase64Order.enhancedCoder.encode(gzip.gzipString(content));
	if (method == 'c') return "c|" + scrambleString(key, content);
	return null;
    }

    public static String simpleDecode(String encoded, String key) {
	if ((encoded == null) || (encoded.length() < 3)) return null;
	if (encoded.charAt(1) != '|') return encoded; // not encoded
	char method = encoded.charAt(0);
	encoded = encoded.substring(2);
	if (method == 'p') return encoded;
	if (method == 'b') return kelondroBase64Order.enhancedCoder.decodeString(encoded, "de.anomic.tools.cryptbig.simpleDecode()");
	if (method == 'z') return gzip.gunzipString(kelondroBase64Order.enhancedCoder.decode(encoded, "de.anomic.tools.cryptbig.simpleDecode()"));
	if (method == 'c') return descrambleString(key, encoded);
	return null;
    }


    // --------------------------------------------------------
    // Section: one-way encryption
    // --------------------------------------------------------

    public static String oneWayEncryption(String key) {
	cryptbig crypter = new cryptbig(key);
	String e = crypter.encryptString(key);
	if (e.length() == 0) e = "0XXXX";
	if (e.length() % 2 == 1) e += "X";
	while (e.length() < 32) e = e + e;
	char[] r = new char[16];
	for (int i = 0; i < 16; i++) r[i] = e.charAt(2 * i + 1);
	return new String(r);
    }

    // --------------------------------------------------------
    // Section: command interface
    // --------------------------------------------------------

    private static void help() {
	System.out.println("AnomicCrypt (2003) by Michael Christen");
	System.out.println("Password-based encryption using the " + defaultMethod + "-method in standard java");
	System.out.println("usage: crypt -h | -help");
	System.out.println("       crypt -1 <passwd>");
	System.out.println("       crypt -md5 <file>");
	System.out.println("       crypt ( -es64 | -ds64 | -ec64 | -dc64 ) <string>");
	System.out.println("       crypt ( -e | -d ) <key> <string>");
	System.out.println("       crypt -enc <key> <file>  \\");
	System.out.println("                   [-o <target-file> | -preserveFilename]  \\");
	System.out.println("                   [-d <YYYYMMddHHmmSSsss> | -preserveDate] [-noZip]");
	System.out.println("       crypt -dec <key> <file>  \\");
	System.out.println("                   [-o <target-file> | -preserveFilename]  \\");
	System.out.println("                   [-d <YYYYMMddHHmmSSsss> | -preserveDate]");
	System.out.println("       crypt ( -info | -name | -size | -date | -edate )  \\");
	System.out.println("                   <key> <encrypted-file>");
    }

    private static void longhelp() {
	// --line-help--   *---------------------------------------------------------------
	System.out.println("AnomicCrypt (2003) by Michael Christen");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -1 <passwd>");
	System.out.println("");
	System.out.println("   One-way encryption of the given password.");
	System.out.println("   The result is computed by encoding the word with the word as");
	System.out.println("   the password and repeating it until the length is greater");
	System.out.println("   than 32. Then every second character is taken to compose the");
	System.out.println("   result which has always the length of 16 characters.");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -md5 <file>");
	System.out.println("");
	System.out.println("   MD5 digest according to RFC 1321. The resulting bytes are");
	System.out.println("   encoded as two-digit hex and concatenated to a single string.");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -ec64 <cardinal>");
	System.out.println("");
	System.out.println("   Encoding of a cardianal (a positive long integer) with the");
	System.out.println("   built-in non-standard base-64 algorithm.");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -dc64 <string>");
	System.out.println("");
	System.out.println("   Decoding of the given b64-coded string to a cardinal number.");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -es64 <string>");
	System.out.println("");
	System.out.println("   Encoding of a given String to a b64 string.");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -ds64 <string>");
	System.out.println("");
	System.out.println("   Decoding of a given b64-coded string to a normal string.");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -e <key> <string>");
	System.out.println("");
	System.out.println("   Encryption of a given Unicode-String.");
	System.out.println("   The given string is first encoded to an UTF-8 byte stream, then");
	System.out.println("   encoded using a password based encryption and then finaly");
	System.out.println("   encoded to b64 to generate a printable form.");
	System.out.println("   The PBE method is " + defaultMethod + ".");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -d <key> <string>");
	System.out.println("");
	System.out.println("   Decryption of a string.");
	System.out.println("   The string is b64-decoded, " + defaultMethod + "-decrypted, ");
	System.out.println("   and then transformed to an unicode string.");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -enc <key> <file>  \\");
	System.out.println("            [-o <target-file> | -preserveFilename]  \\");
	System.out.println("            [-d <target-date YYYYMMddHHmmSSsss> | -preserveDate] [-noZip]");
	System.out.println("");
	System.out.println("");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt -dec <key> <file>  \\");
	System.out.println("            [-o <target-file> | -preserveFilename]  \\");
	System.out.println("            [-d <target-date YYYYMMddHHmmSSsss> | -preserveDate]");
	System.out.println("");
	System.out.println("");
	System.out.println("crypt ( -info | -name | -size | -date | -edate ) <key> <encrypted-file>");
	System.out.println("");
	System.out.println("");
    }

    public static void main(String[] s) {
	if (s.length == 0) {
	    help();
	    System.exit(0);
	}
	if ((s[0].equals("-h")) || (s[0].equals("-help"))) {
	    longhelp();
	    System.exit(0);
	}
	if (s[0].equals("-tc")) {
	    // list all available crypt mehtods:
	    Set<String> methods = listCryptoMethods("Cipher");
	    System.out.println(methods.size() + " crypt methods:" + methods.toString());
	    testCryptMethods(methods);
	    System.exit(0);
	}
	if (s[0].equals("-random")) {
	    int count = ((s.length == 2) ? (Integer.parseInt(s[1])) : 1);
	    for (int i = 0; i < count; i++) System.out.println(randomSalt());
	    System.exit(0);
	}
	if (s[0].equals("-1")) {
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(oneWayEncryption(s[1]));
	    System.exit(0);
	}
	if (s[0].equals("-ec64")) {
	    // generate a b64 encoding from a given cardinal
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(kelondroBase64Order.standardCoder.encodeLong(Long.parseLong(s[1]), 0));
	    System.exit(0);
	}
	if (s[0].equals("-dc64")) {
	    // generate a b64 decoding from a given cardinal
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(kelondroBase64Order.standardCoder.decodeLong(s[1]));
	    System.exit(0);
	}
	if (s[0].equals("-es64")) {
	    // generate a b64 encoding from a given string
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(kelondroBase64Order.standardCoder.encodeString(s[1]));
	    System.exit(0);
	}
	if (s[0].equals("-ds64")) {
	    // generate a b64 decoding from a given string
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(kelondroBase64Order.standardCoder.decodeString(s[1], ""));
	    System.exit(0);
	}
	if (s[0].equals("-ess")) {
	    // 'scramble' string
	    if (s.length != 3) {help(); System.exit(-1);}
	    long t = System.currentTimeMillis();
	    System.out.println(scrambleString(s[1], s[2]));
	    System.out.println("Calculation time: " + (System.currentTimeMillis() - t) + " milliseconds");
	    System.exit(0);
	}
	if (s[0].equals("-dss")) {
	    // 'descramble' string
	    if (s.length != 3) {help(); System.exit(-1);}
	    long t = System.currentTimeMillis();
	    System.out.println(descrambleString(s[1], s[2]));
	    System.out.println("Calculation time: " + (System.currentTimeMillis() - t) + " milliseconds");
	    System.exit(0);
	}
	if (s[0].equals("-e")) {
	    if (s.length != 3) {help(); System.exit(-1);}
	    System.out.println((new cryptbig(s[1])).encryptString(s[2]));
	    System.exit(0);
	}
	if (s[0].equals("-d")) {
	    if (s.length != 3) {help(); System.exit(-1);}
	    System.out.println((new cryptbig(s[1])).decryptString(s[2]));
	    System.exit(0);
	}
	if (s[0].equals("-md5")) {
	    // generate a public key from a password that can be used for encryption
	    if (s.length != 2) {help(); System.exit(-1);}
	    String md5s = serverCodings.encodeMD5Hex(new File(s[1]));
	    System.out.println(md5s);
	    System.exit(0);
	}
	if (s[0].equals("-enc")) {
	    if ((s.length < 3) || (s.length > 4)) {help(); System.exit(-1);}
	    String target;
	    if (s.length == 3) target = s[2] + ".crypt"; else target = s[3];
	    (new cryptbig(s[1])).encryptFile(s[2], target, true /*compress*/);
	    System.exit(0);
	}
	if (s[0].equals("-dec")) {
	    if ((s.length < 3) || (s.length > 4)) {help(); System.exit(-1);}
	    String target;
	    if (s.length == 3) {
		if (s[2].endsWith(".crypt"))
		    target = s[2].substring(0, s[2].length() - 7);
		else
		    target = s[2] + ".decoded";
	    } else {
		target = s[3];
	    }
	    (new cryptbig(s[1])).decryptFile(s[2], target);
	    System.exit(0);
	}
	help(); System.exit(-1);
    }

}
