package dev.vestige.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Enforces the module structure from docs/ARCHITECTURE.md §4 by scanning source imports.
 *
 * <p>When a new module is added, declare it in {@link #ALLOWED_DEPENDENCIES} with the modules it
 * may use. Undeclared modules fail the build, so every dependency is a deliberate decision.
 */
class ArchitectureRulesTest {
    private static final String BASE = "dev.vestige";

    /** module -> modules it may import (besides itself, root classes like VestigeIds, and core). */
    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            // Root bootstrap (Vestige.java): wires config and the module list only.
            "", Set.of("config"),
            "core", Set.of(),
            "config", Set.of(),
            "pale", Set.of("config"),
            "datagen", Set.of("config"),
            "dev", Set.of(),
            // Test and client layers sit on top of everything common.
            "gametest", Set.of("*"),
            "client", Set.of("*"));

    /** Common code must never touch these (it would crash dedicated servers). */
    private static final List<String> CLIENT_ONLY_PREFIXES = List.of(
            "dev.vestige.client.", "net.minecraft.client.", "com.mojang.blaze3d.", "net.neoforged.neoforge.client.");

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    private static final Pattern MODULE_ID = Pattern.compile("implements\\s+VestigeModule[\\s\\S]*?String\\s+id\\(\\)\\s*\\{\\s*return\\s+\"([a-z_]+)\"");

    private static Path root;
    private static List<SourceFile> files;

    record SourceFile(Path path, String module, String text, List<String> imports) {}

    @BeforeAll
    static void scan() throws IOException {
        root = Path.of(System.getProperty("vestige.sourceRoot", "src/main/java")).resolve("dev/vestige");
        assertTrue(Files.isDirectory(root), "source root not found: " + root);
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).map(ArchitectureRulesTest::read).toList();
        }
        assertFalse(files.isEmpty(), "no sources scanned");
    }

    private static SourceFile read(Path p) {
        try {
            String text = Files.readString(p);
            Path rel = root.relativize(p);
            String module = rel.getNameCount() > 1 ? rel.getName(0).toString() : "";
            List<String> imports = new ArrayList<>();
            Matcher m = IMPORT.matcher(text);
            while (m.find()) {
                imports.add(m.group(1));
            }
            return new SourceFile(p, module, text, imports);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String moduleOf(String importName) {
        if (!importName.startsWith(BASE + ".")) {
            return null;
        }
        String rest = importName.substring(BASE.length() + 1);
        int dot = rest.indexOf('.');
        // Root-level classes (dev.vestige.VestigeIds) are shared by everyone.
        if (dot < 0 || Character.isUpperCase(rest.charAt(0))) {
            return null;
        }
        return rest.substring(0, dot);
    }

    @Test
    void everyModulePackageIsDeclared() {
        List<String> undeclared = files.stream().map(SourceFile::module).filter(m -> !m.isEmpty())
                .distinct().filter(m -> !ALLOWED_DEPENDENCIES.containsKey(m)).toList();
        assertTrue(undeclared.isEmpty(), "Declare these modules in ALLOWED_DEPENDENCIES: " + undeclared);
    }

    @Test
    void modulesOnlyUseDeclaredDependencies() {
        List<String> violations = new ArrayList<>();
        for (SourceFile f : files) {
            Set<String> allowed = ALLOWED_DEPENDENCIES.getOrDefault(f.module(), Set.of());
            if (allowed.contains("*")) {
                continue;
            }
            for (String imp : f.imports()) {
                String target = moduleOf(imp);
                if (target == null || target.equals(f.module()) || target.equals("core") || allowed.contains(target)) {
                    continue;
                }
                // The module list is the single place allowed to know every module.
                if (f.path().endsWith(Path.of("core", "VestigeModules.java"))) {
                    continue;
                }
                violations.add(root.relativize(f.path()) + " -> " + imp);
            }
        }
        assertTrue(violations.isEmpty(), "Undeclared module dependencies:\n" + String.join("\n", violations));
    }

    @Test
    void commonCodeDoesNotReferenceClientClasses() {
        List<String> violations = new ArrayList<>();
        for (SourceFile f : files) {
            if (f.module().equals("client")) {
                continue;
            }
            for (String imp : f.imports()) {
                if (CLIENT_ONLY_PREFIXES.stream().anyMatch(imp::startsWith)) {
                    violations.add(root.relativize(f.path()) + " -> " + imp);
                }
            }
        }
        assertTrue(violations.isEmpty(), "Client classes referenced from common code:\n" + String.join("\n", violations));
    }

    @Test
    void moduleIdsMatchPackageNames() {
        List<String> violations = new ArrayList<>();
        for (SourceFile f : files) {
            Matcher m = MODULE_ID.matcher(f.text());
            if (m.find() && !m.group(1).equals(f.module())) {
                violations.add(root.relativize(f.path()) + " declares id '" + m.group(1) + "' in package '" + f.module() + "'");
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
