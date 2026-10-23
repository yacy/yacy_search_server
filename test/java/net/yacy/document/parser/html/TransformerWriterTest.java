package net.yacy.document.parser.html;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Test;

public class TransformerWriterTest {

    @Test
    public void generatePairedTagPreservesOpeningAttributesAndText() {
        final Properties attributes = new Properties();
        attributes.setProperty("class", "notice");

        final char[] generated = TransformerWriter.generatePairedTag(
                "p", attributes, "status".toCharArray(), '"');

        assertEquals("<p class=\"notice\">status</p>", new String(generated));
    }

    @Test
    public void generatePairedTagWithNoAttributesAndEmptyText() {
        final char[] generated = TransformerWriter.generatePairedTag(
                "section", new Properties(), new char[0], '"');

        assertEquals("<section></section>", new String(generated));
    }
}
