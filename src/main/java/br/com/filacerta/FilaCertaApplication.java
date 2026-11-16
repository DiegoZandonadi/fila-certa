package br.com.filacerta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FilaCertaApplication {

    public static void main(String[] args) {
        SpringApplication.run(FilaCertaApplication.class, args);
    }
}
