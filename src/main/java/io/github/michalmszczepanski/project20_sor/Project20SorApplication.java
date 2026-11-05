package io.github.michalmszczepanski.project20_sor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Project20SorApplication {

	public static void main(String[] args) {
		SpringApplication.run(Project20SorApplication.class, args);
	}

}
