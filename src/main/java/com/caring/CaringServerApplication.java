package com.caring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CaringServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CaringServerApplication.class, args);
	}

}
