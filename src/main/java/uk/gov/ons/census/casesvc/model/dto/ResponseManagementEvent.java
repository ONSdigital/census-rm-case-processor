package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ResponseManagementEvent {

  @JsonProperty("event")
  private EventDTO eventDTO;

  @JsonProperty("payload")
  private PayloadDTO payloadDTO;
}
