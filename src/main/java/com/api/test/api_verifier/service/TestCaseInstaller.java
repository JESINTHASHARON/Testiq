package com.api.test.api_verifier.service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public class TestCaseInstaller {

    private static final String JAR_PREFIX = "BOOT-INF/classes/data/tests/";
    private static final String IDE_PATH = "src/main/resources/data/tests/";

    public static void installIfNeeded() {

        String userHome = System.getProperty("user.home");
        File targetDir = new File(userHome + "/testiq/testcases");

        if (targetDir.exists() && targetDir.list().length > 0) {
            System.out.println("Already extracted.");
            return;
        }

        targetDir.mkdirs();

        try {
            String rawPath = TestCaseInstaller.class.getProtectionDomain().getCodeSource().getLocation().getPath();

            if (isJarRun(rawPath)) {
                String jarPath = extractRealJarPath(rawPath);
                extractFromJar(jarPath, targetDir);
            } else {
                System.out.println("Detected IDE Run");
                extractFromIDE(new File(IDE_PATH), targetDir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isJarRun(String rawPath) {
        return rawPath.contains(".jar");
    }

    private static String extractRealJarPath(String rawPath) {
        String cleaned = rawPath.replace("nested:", "").replace("file:", "");

        if (cleaned.contains("!")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("!"));
        }
        return cleaned;
    }

    private static void extractFromIDE(File src, File dest) throws Exception {
        if (!src.exists()) {
            System.out.println("IDE path does not exist: " + src.getAbsolutePath());
            return;
        }

        if (src.isDirectory()) {
            dest.mkdirs();
            for (File f : src.listFiles()) {
                extractFromIDE(f, new File(dest, f.getName()));
            }
        } else {
            dest.getParentFile().mkdirs();
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void extractFromJar(String jarPath, File targetDir) throws Exception {

        try (JarFile jar = new JarFile(jarPath)) {

            jar.stream().forEach(entry -> {
                try {
                    if (entry.getName().startsWith(JAR_PREFIX) && !entry.isDirectory()) {

                        String relative = entry.getName().substring(JAR_PREFIX.length());
                        File out = new File(targetDir, relative);

                        out.getParentFile().mkdirs();

                        try (InputStream input = jar.getInputStream(entry)) {
                            Files.copy(input, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
    }
}
