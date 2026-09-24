package com.ilham.personal_finance_api;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

    // Zona waktu bisnis untuk menentukan "hari ini" / "bulan ini", tidak bergantung pada zona server
    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Jakarta}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
