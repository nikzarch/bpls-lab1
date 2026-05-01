package com.example.labpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LabpayApplication {

	public static void main(String[] args) {
		SpringApplication.run(LabpayApplication.class, args);
	}

}
