package uk.gov.ons.census.casesvc.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.ons.census.casesvc.model.dto.Receipt;

public class JsonHelper {
  private static final ObjectMapper objectMapper;

  static {
    objectMapper = new ObjectMapper();
  }

  public static String convertObjectToJson(Receipt receipt) {
    try {
      return objectMapper.writeValueAsString(receipt);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting Object To Json");
    }
  }
}
