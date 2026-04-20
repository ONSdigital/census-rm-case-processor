package uk.gov.ons.census.caseprocessor.model.dto;

import lombok.Data;

@Data
public class EventDTO {
  private EventHeaderDTO header;
  private PayloadDTO payload;
}
