package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class ResponseManagementEvent {

  private EventDTO event;

  private PayloadDTO payload;
}
