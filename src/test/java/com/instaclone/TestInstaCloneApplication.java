package com.instaclone;

import org.springframework.boot.SpringApplication;

public class TestInstaCloneApplication {

	public static void main(String[] args) {
		SpringApplication.from(InstaCloneApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
