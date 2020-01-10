package uk.gov.ons.census.casesvc.utility;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.messaging.Message;

public class MsgDateHelper {
  public static OffsetDateTime getMsgTimeStamp(Message<?> msg) {

    if (msg.getHeaders().getTimestamp() == null) {
      throw new RuntimeException("Message Headers missing Timestamp");
    }

    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(msg.getHeaders().getTimestamp()), ZoneId.of("UTC"));
  }
}
