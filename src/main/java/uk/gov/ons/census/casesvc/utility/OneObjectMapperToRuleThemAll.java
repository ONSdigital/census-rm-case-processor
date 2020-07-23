package uk.gov.ons.census.casesvc.utility;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// This class exists because case processor is a shoddy mess - test code is for testing and prod
// code is for prod. NEVER change the prod code for the sake of your tests. This golden rule has
// not been followed, hence gigantic mess.
public class OneObjectMapperToRuleThemAll {
  public static final ObjectMapper objectMapper;

  static {
    objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}
