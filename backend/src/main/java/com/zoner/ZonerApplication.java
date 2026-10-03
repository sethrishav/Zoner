package com.zoner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZonerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZonerApplication.class, args);
    }
}

