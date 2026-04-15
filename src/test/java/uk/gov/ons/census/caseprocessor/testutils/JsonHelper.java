package uk.gov.ons.census.caseprocessor.testutils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import uk.gov.ons.census.caseprocessor.utils.ObjectMapperFactory;

public class JsonHelper {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public static <T> T convertJsonBytesToObject(byte[] bytes, Class<T> clazz) {
    try {
      return objectMapper.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
