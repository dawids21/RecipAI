package xyz.stasiak.recipai;

import org.springframework.boot.SpringApplication;

public class TestRecipAiApplication {

	public static void main(String[] args) {
		SpringApplication.from(RecipAiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
