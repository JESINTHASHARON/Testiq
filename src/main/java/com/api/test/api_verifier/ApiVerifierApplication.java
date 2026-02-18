package com.api.test.api_verifier;

import java.awt.Desktop;
import java.net.URI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.api.test.api_verifier.service.PrecheckInstaller;
import com.api.test.api_verifier.service.TestCaseInstaller;

@SpringBootApplication
public class ApiVerifierApplication {
    public static void main(String[] args) {
        TestCaseInstaller.installIfNeeded();
        SpringApplication.run(ApiVerifierApplication.class, args);
        PrecheckInstaller.installIfNeeded();
        openBrowser("http://localhost:8080");
    }

    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
                return;
            }

            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
                return;
            }

            if (os.contains("linux")) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                return;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }

        } catch (Exception e) {
            System.out.println("Could not open browser: " + e.getMessage());
        }
    }
}