package net.yacy.http.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ServletResourceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void servesFileMetadataAndRangesWithoutJetty() throws Exception {
        final Path root = this.temporaryFolder.newFolder("root").toPath();
        final Path file = Files.writeString(root.resolve("sample.txt"), "0123456789", StandardCharsets.UTF_8);
        final ServletResource base = ServletResource.from(root.toString());
        final ServletResource resource = base.addPath("/sample.txt");

        assertTrue(resource.exists());
        assertFalse(resource.isDirectory());
        assertEquals(10L, resource.length());
        assertEquals(file.toFile(), resource.getFile());
        assertTrue(resource.lastModified() > 0L);

        final ByteArrayOutputStream range = new ByteArrayOutputStream();
        resource.writeTo(range, 2L, 4L);
        assertEquals("2345", range.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void rejectsPathsOutsideResourceBase() throws Exception {
        final ServletResource base = ServletResource.from(
                this.temporaryFolder.newFolder("root").toPath().toString());
        try {
            base.addPath("../outside.txt");
            fail("Path traversal must be rejected");
        } catch (final IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("escapes its base"));
        }
    }

    @Test
    public void createsEscapedDirectoryListing() throws Exception {
        final Path root = this.temporaryFolder.newFolder("root").toPath();
        Files.writeString(root.resolve("<entry>.txt"), "content", StandardCharsets.UTF_8);
        Files.createDirectory(root.resolve("child"));
        final ServletResource directory = ServletResource.from(root.toUri().toURL());

        final String listing = directory.getListHTML("/files/", true, null);
        assertTrue(listing.contains("href=\"../\""));
        assertTrue(listing.contains("%3Centry%3E.txt"));
        assertTrue(listing.contains("&lt;entry&gt;.txt"));
        assertTrue(listing.contains("href=\"/files/child/\""));
    }
}
