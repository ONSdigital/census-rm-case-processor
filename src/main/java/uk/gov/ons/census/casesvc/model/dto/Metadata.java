package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Metadata {
  private ActionInstructionType fieldDecision;
  private EventTypeDTO causeEventType;
}
