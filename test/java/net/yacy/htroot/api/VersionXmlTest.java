package net.yacy.htroot.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

public class VersionXmlTest {

    @Test
    public void versionXmlBuildHashUsesOwnPlaceholder() throws Exception {
        final String xml = new String(Files.readAllBytes(Paths.get("htroot/api/version.xml")),
                StandardCharsets.UTF_8);
        Assert.assertTrue(xml.contains("<buildHash>#[buildHash]#</buildHash>"));
        Assert.assertFalse(xml.contains("<buildHash>#[buildDateTime]#</buildHash>"));
    }

    @Test
    public void statusBoxListsGitCommit() throws Exception {
        final String html = new String(Files.readAllBytes(Paths.get("htroot/Status_p.inc")),
                StandardCharsets.UTF_8);
        Assert.assertTrue(html.contains("Git commit: #[gitCommit]#"));
    }
}
