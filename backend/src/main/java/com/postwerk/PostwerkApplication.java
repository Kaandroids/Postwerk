package com.postwerk;

import com.postwerk.config.GdprProperties;
import com.postwerk.config.JwtProperties;
import com.postwerk.config.MailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, GdprProperties.class, MailProperties.class})
@EnableAsync
@EnableScheduling
public class PostwerkApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostwerkApplication.class, args);
    }
}
