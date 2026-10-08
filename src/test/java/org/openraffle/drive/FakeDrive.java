package org.openraffle.drive;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Stands in for Google Drive in Spring tests. */
@TestConfiguration
public class FakeDrive {

    @Bean
    @Primary
    public FakeDriveClient fakeDriveClient() {
        return new FakeDriveClient();
    }
}
