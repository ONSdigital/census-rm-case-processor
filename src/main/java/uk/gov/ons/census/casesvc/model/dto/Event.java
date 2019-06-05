package uk.gov.ons.census.casesvc.model.dto;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class Event {
  private EventType type;
  private String source;
  private String channel;
  private OffsetDateTime dateTime;
  private String transactionId;
  private boolean receiptReceived;
}
