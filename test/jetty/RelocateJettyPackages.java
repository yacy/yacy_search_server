/*
 * Build-time bytecode relocator for the Jetty 12 migration.
 *
 * This is deliberately a small build tool, not YaCy runtime code. It keeps
 * Solr's public packages unchanged while moving its private Jetty 10 linkage
 * below net.yacy.solr9.jetty.
 */
package net.yacy.test.jetty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public final class RelocateJettyPackages {

    private static final Relocation[] RELOCATIONS = {
            new Relocation("org/eclipse/jetty", "net/yacy/solr9/jetty",
                    "org.eclipse.jetty", "net.yacy.solr9.jetty")
    };

    private RelocateJettyPackages() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: RelocateJettyPackages INPUT.jar OUTPUT.jar");
        }
        relocate(Path.of(args[0]), Path.of(args[1]));
    }

    private static void relocate(final Path input, final Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        final Remapper remapper = new BridgeRemapper();

        try (JarFile source = new JarFile(input.toFile());
                JarOutputStream target = new JarOutputStream(Files.newOutputStream(output))) {
            final Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || isSignature(entry.getName())) {
                    continue;
                }
                final String outputName = relocateEntryName(entry.getName());
                final JarEntry outputEntry = new JarEntry(outputName);
                outputEntry.setTime(entry.getTime());
                target.putNextEntry(outputEntry);
                try (InputStream stream = source.getInputStream(entry)) {
                    final byte[] content = readAll(stream);
                    if (entry.getName().endsWith(".class")) {
                        target.write(relocateClass(content, remapper));
                    } else if (entry.getName().startsWith("META-INF/services/")) {
                        target.write(relocateText(new String(content, StandardCharsets.UTF_8))
                                .getBytes(StandardCharsets.UTF_8));
                    } else {
                        target.write(content);
                    }
                }
                target.closeEntry();
            }
        }
    }

    private static final class BridgeRemapper extends Remapper {

        private BridgeRemapper() {
            super(Opcodes.ASM9);
        }

        @Override
        public String map(final String internalName) {
            return relocateInternalName(internalName);
        }

        @Override
        public Object mapValue(final Object value) {
            if (value instanceof String) {
                final String text = (String) value;
                return relocateText(text);
            }
            return super.mapValue(value);
        }
    }

    private static final class Relocation {
        private final String sourceInternal;
        private final String targetInternal;
        private final String sourceBinary;
        private final String targetBinary;

        private Relocation(final String sourceInternal, final String targetInternal,
                final String sourceBinary, final String targetBinary) {
            this.sourceInternal = sourceInternal;
            this.targetInternal = targetInternal;
            this.sourceBinary = sourceBinary;
            this.targetBinary = targetBinary;
        }
    }

    private static byte[] relocateClass(final byte[] content, final Remapper remapper) {
        final ClassReader reader = new ClassReader(content);
        final ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, remapper), 0);
        return writer.toByteArray();
    }

    private static boolean isSignature(final String name) {
        final String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
    }

    private static String replacePrefix(final String value, final String source, final String target) {
        if (value.equals(source)) {
            return target;
        }
        if (value.startsWith(source + "/")) {
            return target + value.substring(source.length());
        }
        return value;
    }

    private static String relocateEntryName(final String name) {
        final String internalName = relocateInternalName(name);
        if (internalName.startsWith("META-INF/services/")) {
            return relocateText(internalName);
        }
        return internalName;
    }

    private static String relocateInternalName(final String name) {
        String relocated = name;
        for (final Relocation relocation : RELOCATIONS) {
            relocated = replacePrefix(relocated, relocation.sourceInternal, relocation.targetInternal);
        }
        return relocated;
    }

    private static String relocateText(final String text) {
        String relocated = text;
        for (final Relocation relocation : RELOCATIONS) {
            relocated = relocated.replace(relocation.sourceBinary, relocation.targetBinary)
                    .replace(relocation.sourceInternal, relocation.targetInternal);
        }
        return relocated;
    }

    private static byte[] readAll(final InputStream stream) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        stream.transferTo(output);
        return output.toByteArray();
    }
}
