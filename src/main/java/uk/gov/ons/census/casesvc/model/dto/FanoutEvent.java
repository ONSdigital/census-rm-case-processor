package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class FanoutEvent {
  private Event event;
  private Payload payload;
}
