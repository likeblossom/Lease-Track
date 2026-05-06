package com.leasetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeaseTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaseTrackApplication.class, args);
    }
}
