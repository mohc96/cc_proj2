package com.cse46.project2part2.webtier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebtierApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebtierApplication.class, args);
	}

}
