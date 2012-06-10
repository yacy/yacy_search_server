package net.yacy.document.parser.rdfa;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.Parser.Failure;
import net.yacy.document.parser.rdfa.impl.RDFaParser;

public class main {
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		URL aURL = null;
		if (args.length < 1) {
			System.out
					.println("Usage: one and only one argument giving a file path or a URL.");
		} else {
			File aFile = new File(args[0]);
			Reader aReader = null;
			if (aFile.exists()) {
				try {
					aReader = new FileReader(aFile);
				} catch (FileNotFoundException e) {
					aReader = null;
				}
			} else {
				try {
					aURL = new URL(args[0]);
					aReader = new InputStreamReader(aURL.openStream());
				} catch (MalformedURLException e) {
				} catch (IOException e) {
					e.printStackTrace();
					aReader = null;
				}

			}

			if (aReader != null) {
				RDFaParser aParser = new RDFaParser("html");
				try {
					aParser.parse(new MultiProtocolURI(args[0]),"","",aURL.openStream());
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Failure e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else
				System.out.println("File or URL not recognized.");

		}

	}
}
