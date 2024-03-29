package uk.co.sainsburys.breakingchangedetect;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@OpenAPIDefinition
@EntityScan("uk.co.sainsburys.breakingchangedetect.entity")
@EnableJpaRepositories("uk.co.sainsburys.breakingchangedetect.repository")
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public OpenAPIV3Parser openAPIV3Parser() {
		return new OpenAPIV3Parser();
	}

}
