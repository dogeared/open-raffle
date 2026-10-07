package org.openraffle.bgg;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Stands in for the real BoardGameGeek client in Spring tests. */
@TestConfiguration
public class FakeBgg {

    @Bean
    @Primary
    public FakeBggClient fakeBggClient() {
        return new FakeBggClient();
    }
}
