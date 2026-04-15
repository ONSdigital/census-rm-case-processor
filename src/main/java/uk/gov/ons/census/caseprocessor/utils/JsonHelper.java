package uk.gov.ons.census.caseprocessor.utils;

import static uk.gov.ons.census.caseprocessor.utils.Constants.ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;

public class JsonHelper {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public static String convertObjectToJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting Object To Json", e);
    }
  }

  public static EventDTO convertJsonBytesToEvent(byte[] bytes) {
    EventDTO event;
    try {
      event = objectMapper.readValue(bytes, EventDTO.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (!ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS.contains((event.getHeader().getVersion()))) {
      throw new RuntimeException(
          String.format(
              "Unsupported message version. Got %s but RM only supports %s",
              event.getHeader().getVersion(),
              String.join(", ", ALLOWED_INBOUND_EVENT_SCHEMA_VERSIONS)));
    }

    return event;
  }
}
