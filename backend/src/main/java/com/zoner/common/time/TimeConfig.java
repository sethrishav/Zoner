package com.zoner.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One injectable Clock, so reminder, recurrence and "now"-dependent logic can be tested
 * with a fixed clock instead of the system time.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
