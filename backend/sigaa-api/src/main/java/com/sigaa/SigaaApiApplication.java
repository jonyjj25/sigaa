package com.sigaa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SigaaApiApplication {

	public static void main(String[] args) {
		// También cubre los módulos heredados que aún llaman LocalDateTime.now().
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(
			java.time.ZoneId.of(System.getenv().getOrDefault("APP_TIME_ZONE", "America/Panama"))));
		SpringApplication.run(SigaaApiApplication.class, args);
	}

}
