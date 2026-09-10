package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class InvalidAddress {
  private UUID caseId;
  private String reason;
  private String notes;
}
