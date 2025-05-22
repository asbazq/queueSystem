package com.queue.queueSystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class QueueSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(QueueSystemApplication.class, args);
	}

}
