package com.yogieat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ServerApplication {

	static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

}
