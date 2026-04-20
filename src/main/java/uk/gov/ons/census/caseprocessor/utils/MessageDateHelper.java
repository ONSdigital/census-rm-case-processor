package uk.gov.ons.census.caseprocessor.utils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.messaging.Message;

public class MessageDateHelper {
  public static OffsetDateTime getMessageTimeStamp(Message<?> message) {

    if (message.getHeaders().getTimestamp() == null) {
      throw new RuntimeException("Message Headers missing Timestamp");
    }

    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(message.getHeaders().getTimestamp()), ZoneId.of("UTC"));
  }
}
