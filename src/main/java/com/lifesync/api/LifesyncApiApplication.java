package com.lifesync.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada da API. Organizada por feature (user, auth, task,
 * habit, goal, ai), nao por camada - cada pacote e praticamente um
 * bounded context.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LifesyncApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifesyncApiApplication.class, args);
    }

}
