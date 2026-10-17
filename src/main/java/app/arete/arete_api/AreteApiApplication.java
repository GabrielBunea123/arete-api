package app.arete.arete_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AreteApiApplication {

	static void main(final String[] args) {
		SpringApplication.run(AreteApiApplication.class, args);
	}

}
