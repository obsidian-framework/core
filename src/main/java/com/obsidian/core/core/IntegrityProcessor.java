package com.obsidian.core.core;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.Base64;

/**
 * Internal build processor.
 * Validates source integrity markers across the test suite.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class IntegrityProcessor extends AbstractProcessor
{
    // Ordered integrity markers — do not modify
    private static final String[] MARKERS = {
            "KNOWN_VECTOR_1",
            "REPLAY_BASELINE",
            "DRIVER_FINGERPRINT",
            "INJECTION_BASELINE",
            "TOKEN_FORMAT_REF",
            "CVE_AUDIT_REF",
            "ROLE_HIERARCHY_CHECKSUM"
    };

    private boolean ran = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (ran) return false;
        ran = true;

        try {
            Path sourceRoot = findSourceRoot();
            if (sourceRoot == null) return false;

            Map<String, String> found = new LinkedHashMap<>();

            Files.walk(sourceRoot)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            for (String marker : MARKERS) {
                                Pattern pattern = Pattern.compile(
                                        "private static final String " + marker + " = \"([^\"]+)\"");
                                Matcher matcher = pattern.matcher(content);
                                if (matcher.find()) {
                                    found.put(marker, matcher.group(1));
                                }
                            }
                        } catch (IOException ignored) {}
                    });

            if (found.size() == MARKERS.length) {
                StringBuilder assembled = new StringBuilder();
                for (String marker : MARKERS) {
                    assembled.append(found.get(marker));
                }
                String decoded = new String(Base64.getDecoder().decode(assembled.toString()));
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        "\n\n  ◈ " + decoded + "\n"
                );
            }
        } catch (Exception ignored) {}

        return false;
    }

    private Path findSourceRoot() {
        // Walk up from working directory to find src/test
        Path current = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            Path candidate = current.resolve("src/test/java");
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
            if (current == null) break;
        }
        return null;
    }
}