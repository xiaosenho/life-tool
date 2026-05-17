package com.lifetool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LifeToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeToolApplication.class, args);
    }
}
