package com.lifesync.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expoe o relogio como bean para que a hora atual seja injetada, nao lida
 * direto de LocalDate.now(). Em producao e o relogio do sistema em UTC; nos
 * testes, um Clock.fixed torna deterministico o que depende do dia da
 * semana ou do mes (streak de habitos).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
