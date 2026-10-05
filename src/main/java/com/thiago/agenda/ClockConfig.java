package com.thiago.agenda;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relogio da clinica. A API passa "agora" para as procedures em vez de deixar
 * o banco usar o relogio dele, assim os testes controlam o que e passado.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
