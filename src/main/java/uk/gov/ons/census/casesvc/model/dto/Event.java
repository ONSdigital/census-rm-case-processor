package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Event {
  private String type;
  private String channel;
  private String dateTime;
  private String transactionId;
}
