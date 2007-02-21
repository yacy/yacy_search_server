
package de.anomic.data.wiki.tokens;

public class DefinitionListToken extends ListToken {
	
	private static final String[] blockElements = { "dl", "dt", "dd" };
	
	public DefinitionListToken() {
		super(';', null, null);
	}
	
	@Override
	protected StringBuffer parse(String[] t, int depth, StringBuffer sb) {
		sb.append("<dl>\n");
		while (super.aktline < t.length && getGrade(t[super.aktline]) >= depth) {
			for (int j=0; j<depth + 1; j++) sb.append("\t");
			sb.append("<dt>");
			
			if (getGrade(t[super.aktline]) > depth) {
				parse(t, depth + 1, sb);
			} else {
				sb.append(t[super.aktline].substring(depth + 1).replaceFirst(":", "</dt><dd>"));
			}
			
			sb.append("</");
			if (t[super.aktline].indexOf(':') == -1 || getGrade(t[super.aktline]) > depth)
				sb.append("dt");
			else
				sb.append("dd");
			sb.append(">\n");
			super.aktline++;
		}
		for (int j=0; j<depth; j++) sb.append("\t");
		sb.append("</dl>");
		super.aktline--;
		return sb;
	}
	
	public String[] getBlockElementNames() {
		return blockElements;
	}
}
