package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class CaseCreatedEvent {
  private Event event;
  private Payload payload;
}
