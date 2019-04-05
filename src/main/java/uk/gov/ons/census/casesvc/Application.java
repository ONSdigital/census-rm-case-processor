package uk.gov.ons.census.casesvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;

@Configuration
@SpringBootApplication
@IntegrationComponentScan
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class);
  }
}
