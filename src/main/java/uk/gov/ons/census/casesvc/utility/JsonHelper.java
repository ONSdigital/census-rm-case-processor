package uk.gov.ons.census.casesvc.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.UUID;

public class JsonHelper {
  private static final ObjectMapper objectMapper;

  static {
    objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public static String convertObjectToJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting Object To Json", e);
    }
  }

  public static UUID getUUIDFromJson(String fieldPath, String json) {
    return UUID.fromString(convertJsonToJsonNode(json).at(fieldPath).asText());
  }

  private static JsonNode convertJsonToJsonNode(String json) {
    try {
      return objectMapper.readValue(json, JsonNode.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed converting json to JsonNode", e);
    }
  }
}
