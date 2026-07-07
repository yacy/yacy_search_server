package net.yacy.utils.translation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GenerateSourceMasterXliffTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testMainExtractsSourceVisibleTextAndVerifiesOutput() throws Exception {
        final File sourceRoot = this.temporaryFolder.newFolder("htroot");
        final File sourceFile = new File(sourceRoot, "Network.html");
        Files.write(sourceFile.toPath(), (
                "<html><body>\n"
                + "<a href=\"Network.html\" title=\"Network link\">Network</a>\n"
                + "<input type=\"submit\" value=\"Save\">\n"
                + "<input type=\"hidden\" value=\"Internal secret\">\n"
                + "<script>Ignored script text</script>\n"
                + "</body></html>\n").getBytes(StandardCharsets.UTF_8));

        final File outputFile = this.temporaryFolder.newFile("source-master.lng.xlf");
        GenerateSourceMasterXliff.main(new String[] { sourceRoot.getAbsolutePath(), outputFile.getAbsolutePath() });

        final Map<String, Map<String, String>> master = new TranslatorXliff().loadTranslationsListsFromXliff(outputFile);
        assertTrue(master.containsKey("Network.html"));

        final Map<String, String> entries = master.get("Network.html");
        assertTrue(entries.containsKey("Network"));
        assertTrue(entries.containsKey("\"Network link\""));
        assertTrue(entries.containsKey("\"Save\""));
        assertFalse(entries.containsKey("\"Internal secret\""));
        assertFalse(entries.containsKey("Ignored script text"));
    }
}
