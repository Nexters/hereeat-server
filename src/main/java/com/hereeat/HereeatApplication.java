package com.hereeat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class HereeatApplication {

	static void main(String[] args) {
		SpringApplication.run(HereeatApplication.class, args);
	}

}
