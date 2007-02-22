// wikiParser.java 
// ---------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2007
// Created 22.02.2007
//
// This file is contributed by Franz Brauße
//
// $LastChangedDate: $
// $LastChangedRevision: $
// $LastChangedBy: $
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

package de.anomic.data.wiki;

import java.util.ArrayList;
import java.util.regex.Matcher;

import de.anomic.data.wiki.tokens.DefinitionListToken;
import de.anomic.data.wiki.tokens.LinkToken;
import de.anomic.data.wiki.tokens.ListToken;
import de.anomic.data.wiki.tokens.SimpleToken;
import de.anomic.data.wiki.tokens.TableToken;
import de.anomic.data.wiki.tokens.Token;

public class wikiParser {
	
	public static final Token[] tokens = {
		new SimpleToken('=', '=', new String[][] { null, { "h2" }, { "h3" }, { "h4" } }, true),
		new SimpleToken('\'', '\'', new String[][] { null, { "i" }, { "b" }, null, { "b", "i" } }, false),
		new LinkToken("localhost:8080", "Wiki.html?page="),
		new ListToken('*', "ul"),
		new ListToken('#', "ol"),
		new ListToken(':', "blockquote", null),
		new ListToken(' ', null, "tt", false),
		new DefinitionListToken(),
		new TableToken()
	};
	
	private static final String[] BEs;
	static {
		ArrayList r = new ArrayList();
		for (int i=0, k, j; i<tokens.length; i++)
			if (tokens[i].getBlockElementNames() != null)
				for (j=0; j<tokens[i].getBlockElementNames().length; j++) {
					if (tokens[i].getBlockElementNames()[j] == null) continue;
					if ((k = tokens[i].getBlockElementNames()[j].indexOf(' ')) > 1) {
						r.add(tokens[i].getBlockElementNames()[j].substring(0, k));
					} else {
						r.add(tokens[i].getBlockElementNames()[j]);
					}
				}
		r.add("hr");
		BEs = (String[])r.toArray(new String[r.size()]);
	}
	
	public static void main(String[] args) {
		String text = "===Title===\n" +
				"==blubb[== was ==ein '''shice'''==...och.bla\n" +
				"* ein \n" +
				"*==test==\n" +
				"** doppelt\n" +
				"* ''tess*sst''\n" +
				"*** xyz\n" +
				"=]*** huch\n" +
				"* ehehe***\n" +
				"* blubb\n" +
				"bliblablo\n\n\n" +
				"* blubb\n" +
				"{|border=-1\n" +
				"|-\n" +
				"||bla|| blubb\n" +
				"|-\n" +
				"||align center|och||huch||\n" +
				"|}\n" +
				"\n" +
				"# bla\n" +
				"# blubb\n" +
				"'''''ehehehe''''', ne?!\n" +
				"[http://www/index.html,ne?!] -\n" +
				"[[Image:blubb|BLA]] ---- och\n" +
				" blubb1\n" +
				" blubb2\n" +
				":doppel-blubb[= huch =]\n" +
				";hier:da\n" +
				";dort:und so\n" +
				";;und:doppelt";
		// text = "[=\n=]* bla";
		String t = "[=] ein fucking [= test =]-text[=,ne?!=] joa, [=alles=]wunderbar," +
				"[=denk ich=] mal =]";
		long l = System.currentTimeMillis();
		t = parse((args.length > 0) ? args[0] : text);
        System.out.println("parsing time: " + (System.currentTimeMillis() - l) + " ms");
        System.out.println("--- --- ---");
        System.out.println(t);
	}
	
	// TODO:
	// - preParse:
	//   - <pre>~</pre>
	
	public static String parse(String text) {
        Text[] tt = Text.split2Texts(text, "[=", "=]");
        for (int i=0; i<tt.length; i+=2)
        	tt[i].setText(parseUnescaped(tt[i].getText()));
        return replaceBRs(Text.mergeTexts(tt));
	}
	
	public static String parseUnescaped(String text) {
		Token st;
		Matcher m;
		StringBuffer sb;
		for (int i=0; i<tokens.length; i++) {
			st = tokens[i];
			for (int j=0; j<st.getRegex().length; j++) {
				m = st.getRegex()[j].matcher(text);
				sb = new StringBuffer();
				while (m.find()) {
					//System.out.print("found " + st.getClass().getSimpleName() +  ": " +
					//		m.group().replaceAll("\n", "\\\\n").replaceAll("\t", "    ") + ", ");
					if (st.setText(m.group(), j)) {
					//	System.out.println("usable");
					} else {
					//	System.out.println("not usable");
						continue;
					}
					m.appendReplacement(sb, (st.getMarkup() == null) ? m.group() : st.getMarkup());
				}
				text = new String(m.appendTail(sb));
			}
		}
		return text.replaceAll("----", "<hr />");
	}
	
	private static String replaceBRs(String text) {
		StringBuffer sb = new StringBuffer(text.length());
		String[] tt = text.split("\n");
		boolean replace;
		for (int i=0, j; i<tt.length; i++) {
			replace = true;
			for (j=0; j<BEs.length; j++)
				if (tt[i].endsWith(BEs[j] + ">")) { replace = false; break; }
			sb.append(tt[i]);
			if (replace && i < tt.length - 1) sb.append("<br />");
			if (i < tt.length - 1) sb.append("\n");
		}
		return new String(sb);
	}
	
	private static class Text {
		
		public static final String escapeNewLine = "@";
		
		private String text;
		private final boolean escaped;
		private final boolean nl;
		
		public Text(String text, boolean escaped, boolean newLineBefore) {
			this.text = text;
			this.escaped = escaped;
			this.nl = newLineBefore;
		}
		
		public String setTextPlain(String text) { return this.text = text; }
		public String setText(String text) {
			if (this.nl)
				this.text = text.substring(escapeNewLine.length());
			else
				this.text = text;
			return this.text;
		}
		
		public String getTextPlain() { return this.text; }
		public String getText() {
			if (this.nl)
				return escapeNewLine + this.text;
			else
				return this.text;
		}
		
		public String toString() { return this.text; }
		public boolean isEscaped() { return this.escaped; }
		public boolean isNewLineBefore() { return this.nl; }
		
		private static Text[] split2Texts(String text, String escapeBegin, String escapeEnd) {
			if (text == null) return null;
			if (text.length() < 2) return new Text[] { new Text(text, false, true) };
			
			int startLen = escapeBegin.length();
			ArrayList r = new ArrayList();
			boolean escaped = text.startsWith(escapeBegin);
			if (escaped) r.add(new Text("", false, true));
			int i, j = 0;
			while ((i = text.indexOf((escaped) ? escapeEnd : escapeBegin, j)) > -1) {
				r.add(resolve2Text(text, escaped, (j > 0) ? j + startLen : 0, i, escapeEnd));
				j = i;
				escaped = !escaped;
			}
			r.add(resolve2Text(text, escaped, (escaped) ? j : (j > 0) ? j + startLen : 0, -1, escapeEnd));
			return (Text[])r.toArray(new Text[r.size()]);
		}
		
		private static Text resolve2Text(String text, boolean escaped, int from, int to, String escapeEnd) {
			if (to == -1) to = text.length();
			return new Text(
					text.substring(from, to),
					escaped,
					from < escapeEnd.length() + 2 || (!escaped && text.charAt(from - escapeEnd.length() - 1) == '\n'));
		}
		
		private static String mergeTexts(Text[] texts) {
			StringBuffer sb = new StringBuffer();
			for (int n=0; n < texts.length; n++)
				sb.append(texts[n].getTextPlain());
			return new String(sb);
		}
	}
}
