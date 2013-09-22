// cryptbig.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;


public class cryptbig {

    // --------------------------------------------------------
    // Section: random salt generation
    // --------------------------------------------------------

    private static long saltcounter = 0;
    private static Random saltrandom = new Random(System.currentTimeMillis());

    public static String randomSalt() {
        // generate robust 48-bit random number
        final long salt = (saltrandom.nextLong() & 0XffffffffffffL) + (System.currentTimeMillis() & 0XffffffffffffL) + ((1001 * saltcounter) & 0XffffffffffffL);
        saltcounter++;
        // we generate 48-bit salt values, that are represented as 8-character
        // b64-encoded strings
        return Base64Order.standardCoder.encodeLongSB(salt & 0XffffffffffffL, 8).toString();
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

    public cryptbig(final String pbe) {
	// this is possible, but not recommended
	this(pbe, (pbe + "XXXXXXXX").substring(0, 8));
    }

    public cryptbig(final String pbe, final String salt) {
	this(pbe, salt, defaultMethod);
    }


    private cryptbig(final String pbe, String salt, final String method) {
	// a Password-Based Encryption. The SecretKey is created on the fly
	final PBEKeySpec keySpec = new PBEKeySpec(pbe.toCharArray());
	try {
	    if (salt.length() > 8) salt = salt.substring(0,8);
	    if (salt.length() < 8) salt = (salt + "XXXXXXXX").substring(0,8);

	    // create the PBE key
	    final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(method);
	    final SecretKey key = keyFactory.generateSecret(keySpec);

	    // create parameter spec for PBE
	    final PBEParameterSpec paramSpec = new PBEParameterSpec(UTF8.getBytes(salt), 1000 /*ITERATIONS*/);

	    // Create a cipher and initialize it for encrypting end decrypting
	    this.cryptMethod = method;
	    this.ecipher = Cipher.getInstance(this.cryptMethod);
	    this.dcipher = Cipher.getInstance(this.cryptMethod);
	    this.ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec); // paramSpec only for PBE!
	    this.dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
	} catch (final javax.crypto.NoSuchPaddingException e) {
	} catch (final java.security.InvalidKeyException e) {
	} catch (final java.security.NoSuchAlgorithmException e) {
	} catch (final java.security.spec.InvalidKeySpecException e) {
	} catch (final java.security.InvalidAlgorithmParameterException e) {
	}
    }

    // Encode a string into a new string using utf-8, crypt and b64
    public String encryptString(final String str) {
	try {
	    final byte[] utf = str.getBytes("UTF8");
	    final byte[] enc = encryptArray(utf);
	    if (enc == null) return null;
	    return Base64Order.standardCoder.encode(enc);
	} catch (final UnsupportedEncodingException e) {
	}
	return null;
    }

    // Decode a string into a new string using b64, crypt and utf-8
    public String decryptString(final String str) {
	final byte[] b64dec = Base64Order.standardCoder.decode(str);
    if (b64dec == null) return null; // error in input string (inconsistency)
    final byte[] dec = decryptArray(b64dec);
    if (dec == null) return null;
    return UTF8.String(dec);
    }

    // Encode a byte array into a new byte array
    public byte[] encryptArray(final byte[] b) {
	if (b == null) return null;
	try {
	    return this.ecipher.doFinal(b);
	} catch (final javax.crypto.BadPaddingException e) {
	} catch (final IllegalBlockSizeException e) {
	}
	return null;
    }

    // Decode a string into a new string using b64, crypt and utf-8
    public byte[] decryptArray(final byte[] b) {
	if (b == null) return null;
	try {
	    return this.dcipher.doFinal(b);
	} catch (final javax.crypto.BadPaddingException e) {
	} catch (final IllegalBlockSizeException e) {
	}
	return null;
    }


    // This method returns the available implementations for a service type
    public static Set<String> listCryptoMethods(final String serviceType) {
        final Set<String> result = new HashSet<String>();

        // All providers
        final Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            // Get services provided by each provider
            final Set<?> keys = provider.keySet();
            for (Object name : keys) {
                String key = (String) name;
                key = CommonPattern.SPACE.split(key)[0];
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

    public static void testCryptMethods(final Set<String> methods) {
	    String method;
	    final Iterator<String> i = methods.iterator();
	    while (i.hasNext()) {
		method = i.next();
		System.out.print(method + " : ");
		try {
		    final cryptbig crypter = new cryptbig("abrakadabra", method);
		    final String encrypted = crypter.encryptString("nicht verraten abc 1234567890");
		    System.out.print(encrypted + "/");
		    final String decrypted = crypter.decryptString(encrypted);
		    System.out.println(decrypted);
		} catch (final Exception e) {
		    System.out.println("Exception: " + e.getMessage());
		    ConcurrentLog.logException(e);
		}
	    }
    }

    public void encryptFile(final String inFileName, final String outFileName) {
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
	    final File   inFile           = new File(inFileName);
	    final String inFileDate       = dateFormatter.format(new Date(inFile.lastModified())); // 17 byte
	    final String encryptionDate   = dateFormatter.format(new Date()); // 17 byte
	    final String inFileSize       = Base64Order.standardCoder.encodeLongSB(inFile.length(), 11).toString(); // 64 / 6 = 11; 11 byte
	    final String flag             = "1"; // 1 byte
	    //int    inFileNameLength = inFileName.length(); // 256
	    final String X                = inFileDate + encryptionDate + inFileSize + flag + inFileName;

	    System.out.println("TEST: preserving inFileDate    : " + dateFormatter.parse(inFileDate, new ParsePosition(0)));
	    System.out.println("TEST: preserving encryptionDate: " + dateFormatter.parse(encryptionDate, new ParsePosition(0)));
	    System.out.println("TEST: preserving inFileLength  : " + inFile.length());
	    System.out.println("TEST: preserving flag          : " + flag);
	    System.out.println("TEST: preserving inFileName    : " + inFileName);
	    System.out.println("TEST: preserving X-String      : " + X);

	    // start encryption
	    final InputStream  fin  = new CipherInputStream(new FileInputStream(inFile), this.ecipher);
	    final OutputStream fout = new FileOutputStream(outFileName);

	    // write magic and properties of original file
	    // - we encrypt the original date, the encryption date, the file size, the flag
	    //   and file name together to the string A and calculate the length AL of that string
	    // - the length of the file name is therefore equal to AL-(17+17+11+1) = AL-46
	    // - AL is then b64-ed and also encrypted which results into string B
	    // - the length of B is BL; BL is then b64-ed to a string C of fixed length 1
	    // - after the magic String we write C, B and A
	    try {
		final String A = UTF8.String(this.ecipher.doFinal(X.getBytes("UTF8")));
		final String B = UTF8.String(this.ecipher.doFinal(Base64Order.standardCoder.encodeLongSB(A.length(), 2).toString().getBytes("UTF8"))); // most probable not longer than 4
		final String C = Base64Order.standardCoder.encodeLongSB(B.length(), 1).toString(); // fixed length 1 (6 bits, that should be enough)
		fout.write(UTF8.getBytes(magicString)); // the magic string, used to identify a 'crypt'-file
		fout.write(UTF8.getBytes(C));
		fout.write(UTF8.getBytes(B));
		fout.write(UTF8.getBytes(A));

		// write content of file
		copy(fout, fin, 512);
	    }
	    catch (final javax.crypto.IllegalBlockSizeException e) {System.err.println("ERROR:" + e.getMessage());}
	    catch (final javax.crypto.BadPaddingException e) {System.err.println("ERROR:" + e.getMessage());}
	    // finished files
	    fin.close();
	    fout.close();
	} catch (final FileNotFoundException e) {
	    System.err.println("ERROR: file '" + inFileName + "' not found");
	} catch (final IOException e) {
	    System.err.println("ERROR: IO trouble");
	}
    }

    public void decryptFile(final String inFileName, final String outFileName) {
	InputStream  fin = null;
	OutputStream fout = null;
	try {
	    // Start opening the files
	    fin  = new BufferedInputStream(new FileInputStream(inFileName), 4096);

	    // read the file properties
	    final byte[] thisMagic = new byte[magicString.length()];
	    fin.read(thisMagic);

	    if (!((UTF8.String(thisMagic)).equals(magicString))) {
		// this is not an crypt file, so dont do anything
		fin.close();
		return;
	    }
	    final byte[] C = new byte[1];
	    fin.read(C); // the length of the following String, encoded as b64
	    final byte[] B = new byte[(int) Base64Order.standardCoder.decodeLong(UTF8.String(C))];
	    fin.read(B); // this is again the length of the following string, as encrypted b64-ed integer
	    final byte[] A = new byte[(int) Base64Order.standardCoder.decodeLong(UTF8.String(this.dcipher.doFinal(B)))];
	    fin.read(A);
	    final String X = UTF8.String(this.dcipher.doFinal(A));

	    System.out.println("TEST: detecting X-String      : " + X);

	    // reconstruct the properties
	    final Date   inFileDate     = dateFormatter.parse(X.substring(0, 17), new ParsePosition(0));
	    final Date   encryptionDate = dateFormatter.parse(X.substring(17, 34),  new ParsePosition(0));
	    final long   inFileSize     = Base64Order.standardCoder.decodeLong(X.substring(34, 45));
	    final String flag           = X.substring(45, 46);
	    final String origFileName   = X.substring(46);

	    System.out.println("TEST: detecting inFileDate    : " + inFileDate);
	    System.out.println("TEST: detecting encryptionDate: " + encryptionDate);
	    System.out.println("TEST: detecting inFileLength  : " + inFileSize);
	    System.out.println("TEST: detecting flag          : " + flag);
	    System.out.println("TEST: detecting inFileName    : " + origFileName);

	    // open the output file
	    fout = new BufferedOutputStream(new CipherOutputStream(new FileOutputStream(outFileName), this.dcipher), 4096);

	    // read and decrypt the file
	    copy(fout, fin, 512);

	    // close the files
	    fin.close();
	    fout.close();

	    // do postprocessing
	} catch (final BadPaddingException e) {
	    System.err.println("ERROR: decryption of '" + inFileName + "' not possible: " + e.getMessage());
	} catch (final IllegalBlockSizeException e) {
	    System.err.println("ERROR: decryption of '" + inFileName + "' not possible: " + e.getMessage());
	} catch (final FileNotFoundException e) {
	    System.err.println("ERROR: file '" + inFileName + "' not found");
	} catch (final IOException e) {
	    System.err.println("ERROR: IO trouble");
	    try { if(fin != null) {
	        fin.close();
	    }} catch (final Exception ee) {}
	    try { if(fout != null) {
	        fout.close();
	    }} catch (final Exception ee) {}
	}
    }

    private static void copy(final OutputStream out, final InputStream in, final int bufferSize) throws IOException {
	final InputStream  bIn  = new BufferedInputStream(in, bufferSize);
	final OutputStream bOut = new BufferedOutputStream(out, bufferSize);
	final byte[] buf = new byte[bufferSize];
	int n;
	while ((n = bIn.read(buf)) > 0) bOut.write(buf, 0, n);
	bIn.close();
	bOut.close();
    }


    public static String scrambleString(final String key, final String s) {
	// we perform several operations
	// - generate salt
	// - gzip string
	// - crypt string with key and salt
	// - base64-encode result
	// - attach salt and return
	final String salt = randomSalt();
	//System.out.println("Salt=" + salt);
	final cryptbig c = new cryptbig(key, salt);
	boolean gzFlag = true;
	byte[] gz = gzip.gzipString(s);
	if (gz.length > s.length()) {
	    // revert compression
	    try {
		gz = s.getBytes("UTF8");
		gzFlag = false;
	    } catch (final UnsupportedEncodingException e) {
		return null;
	    }
	}
	//System.out.println("GZIP length=" + gz.length);
	if (gz == null) return null;
	final byte[] enc = c.encryptArray(gz);
	if (enc == null) return null;
	return salt + ((gzFlag) ? "1" : "0") + Base64Order.enhancedCoder.encode(enc);
    }

    public static String descrambleString(final String key, String s) throws IOException {
	final String salt = s.substring(0, 8);
	final boolean gzFlag = (s.charAt(8) == '1');
	s = s.substring(9);
	final cryptbig c = new cryptbig(key, salt);
	final byte[] b64dec = Base64Order.enhancedCoder.decode(s);
	if (b64dec == null) return null; // error in input string (inconsistency)
	final byte[] dec = c.decryptArray(b64dec);
	if (dec == null) return null;
	if (gzFlag) return gzip.gunzipString(dec);
	return UTF8.String(dec);

    }


    // --------------------------------------------------------
    // Section: simple Codings
    // --------------------------------------------------------


    public static String simpleEncode(final String content) {
	return simpleEncode(content, null, 'b');
    }

    public static String simpleEncode(final String content, final String key) {
	return simpleEncode(content, key, 'b');
    }

    public static String simpleEncode(final String content, String key, final char method) {
	if (key == null) key = "NULL";
	if (method == 'p') return "p|" + content;
	if (method == 'b') return "b|" + Base64Order.enhancedCoder.encodeString(content);
	if (method == 'z') return "z|" + Base64Order.enhancedCoder.encode(gzip.gzipString(content));
	if (method == 'c') return "c|" + scrambleString(key, content);
	return null;
    }

    public static String simpleDecode(String encoded, final String key) throws IOException {
	if ((encoded == null) || (encoded.length() < 3)) return null;
	if (encoded.charAt(1) != '|') return encoded; // not encoded
	final char method = encoded.charAt(0);
	encoded = encoded.substring(2);
	if (method == 'p') return encoded;
	if (method == 'b') return Base64Order.enhancedCoder.decodeString(encoded);
	if (method == 'z') return gzip.gunzipString(Base64Order.enhancedCoder.decode(encoded));
	if (method == 'c') return descrambleString(key, encoded);
	return null;
    }


    // --------------------------------------------------------
    // Section: one-way encryption
    // --------------------------------------------------------

    public static String oneWayEncryption(final String key) {
	final cryptbig crypter = new cryptbig(key);
	String e = crypter.encryptString(key);
	if (e.isEmpty()) e = "0XXXX";
	if (e.length() % 2 != 0) e += "X";
	while (e.length() < 32) e = e + e;
	final char[] r = new char[16];
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

    public static void main(final String[] s) {
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
	    final Set<String> methods = listCryptoMethods("Cipher");
	    System.out.println(methods.size() + " crypt methods:" + methods.toString());
	    testCryptMethods(methods);
	    System.exit(0);
	}
	if (s[0].equals("-random")) {
	    final int count = ((s.length == 2) ? (Integer.parseInt(s[1])) : 1);
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
	    System.out.println(Base64Order.standardCoder.encodeLongSB(Long.parseLong(s[1]), 0).toString());
	    System.exit(0);
	}
	if (s[0].equals("-dc64")) {
	    // generate a b64 decoding from a given cardinal
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(Base64Order.standardCoder.decodeLong(s[1]));
	    System.exit(0);
	}
	if (s[0].equals("-es64")) {
	    // generate a b64 encoding from a given string
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(Base64Order.standardCoder.encodeString(s[1]));
	    System.exit(0);
	}
	if (s[0].equals("-ds64")) {
	    // generate a b64 decoding from a given string
	    if (s.length != 2) {help(); System.exit(-1);}
	    System.out.println(Base64Order.standardCoder.decodeString(s[1]));
	    System.exit(0);
	}
	if (s[0].equals("-ess")) {
	    // 'scramble' string
	    if (s.length != 3) {help(); System.exit(-1);}
	    final long t = System.currentTimeMillis();
	    System.out.println(scrambleString(s[1], s[2]));
	    System.out.println("Calculation time: " + (System.currentTimeMillis() - t) + " milliseconds");
	    System.exit(0);
	}
	if (s[0].equals("-dss")) {
	    // 'descramble' string
	    if (s.length != 3) {help(); System.exit(-1);}
	    final long t = System.currentTimeMillis();
	    try {
            System.out.println(descrambleString(s[1], s[2]));
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            ConcurrentLog.logException(e);
        }
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
	    String md5s = "";
        try {
            md5s = Digest.encodeMD5Hex(new File(s[1]));
        } catch (final IOException e) {
            e.printStackTrace();
        }
	    System.out.println(md5s);
	    System.exit(0);
	}
	if (s[0].equals("-enc")) {
	    if ((s.length < 3) || (s.length > 4)) {help(); System.exit(-1);}
	    String target;
	    if (s.length == 3) target = s[2] + ".crypt"; else target = s[3];
	    (new cryptbig(s[1])).encryptFile(s[2], target);
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
