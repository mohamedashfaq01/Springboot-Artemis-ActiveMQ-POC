package com.learnwithashfaq.artemis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

/**
 * ArtemisJmsApplication — The entry point of our application.
 *
 * WHAT EACH ANNOTATION DOES:
 *
 * @SpringBootApplication — Combines 3 annotations:
 *   1. @Configuration      → This class can define beans
 *   2. @EnableAutoConfiguration → Spring Boot auto-configures based on dependencies
 *   3. @ComponentScan      → Scans this package and sub-packages for @Component, @Service, etc.
 *
 * @EnableJms — Activates JMS listener processing.
 *   Without this, @JmsListener methods won't be discovered or invoked.
 *   Think of it as "turning on the ears" of your application to listen for messages.
 */
@SpringBootApplication
@EnableJms
public class ArtemisJmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArtemisJmsApplication.class, args);
    }
}
