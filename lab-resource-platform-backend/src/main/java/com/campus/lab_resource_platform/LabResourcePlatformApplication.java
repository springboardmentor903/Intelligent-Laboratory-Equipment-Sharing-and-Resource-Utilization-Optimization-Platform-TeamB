package com.campus.lab_resource_platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class LabResourcePlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(LabResourcePlatformApplication.class, args);
		System.out.println("Hello World!");
	}
}