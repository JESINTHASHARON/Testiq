package com.api.test.api_verifier.service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public class PrecheckInstaller {

    private static final String JAR_PREFIX = "BOOT-INF/classes/data/prechecks/";
    private static final String IDE_PATH = "src/main/resources/data/prechecks/";
    private static final String USER_PATH = System.getProperty("user.home") + "/testiq/config/prechecks.json";

    public static void installIfNeeded() {
        File targetFile = new File(USER_PATH);
        File targetDir = targetFile.getParentFile();

        if (targetFile.exists()) {
            System.out.println("Prechecks file already exists.");
            return;
        }
        targetDir.mkdirs();

        try {
            String rawPath = PrecheckInstaller.class.getProtectionDomain().getCodeSource().getLocation().getPath();

            if (isJarRun(rawPath)) {
                extractFromJar(extractRealJarPath(rawPath), targetFile);
            } else {
                extractFromIDE(new File(IDE_PATH + "prechecks.json"), targetFile);
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
        if (cleaned.contains("!"))
            cleaned = cleaned.substring(0, cleaned.indexOf("!"));
        return cleaned;
    }

    private static void extractFromIDE(File src, File dest) throws Exception {
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("IDE COPY -> " + dest.getAbsolutePath());
    }

    private static void extractFromJar(String jarPath, File targetFile) throws Exception {
        try (JarFile jar = new JarFile(jarPath)) {
            jar.stream().forEach(entry -> {
                try {
                    if (entry.getName().equals(JAR_PREFIX + "prechecks.json")) {
                        File out = targetFile;
                        out.getParentFile().mkdirs();
                        try (InputStream input = jar.getInputStream(entry)) {
                            Files.copy(input, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("JAR COPY -> " + out.getAbsolutePath());
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
    }
}
