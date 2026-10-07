package com.apnileap.teamc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TeamCApplication {
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.version"));
        SpringApplication.run(TeamCApplication.class, args);
    }
}
