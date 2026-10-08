package org.openraffle;

import org.openraffle.drive.DriveService;
import org.openraffle.drive.FakeDrive;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Runs the real app with an in-memory Google Drive already connected, for trying the
 * upload flow in a browser without Google credentials:
 * {@code mvn test-compile exec:java -Dexec.mainClass=org.openraffle.DevWithFakeDrive -Dexec.classpathScope=test}
 */
@Import(FakeDrive.class)
public class DevWithFakeDrive {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OpenRaffleApplication.class, DevWithFakeDrive.class)
                .properties("server.port=8081")
                .run(args);
    }

    @Bean
    ApplicationRunner connectFakeDrive(DriveService driveService) {
        return args -> driveService.complete("good-code", "http://localhost:8081/drive/callback");
    }
}
