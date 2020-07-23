package uk.gov.ons.census.casesvc.utility;

import com.fasterxml.jackson.core.JsonProcessingException;

public class JsonHelper {
  public static String convertObjectToJson(Object obj) {
    try {
      return OneObjectMapperToRuleThemAll.objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting Object To Json", e);
    }
  }
}
