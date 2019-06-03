package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Event {
  private EventType type;
  private String source;
  private String channel;
  private String dateTime;
  private String transactionId;
  private boolean receiptReceived;
}
