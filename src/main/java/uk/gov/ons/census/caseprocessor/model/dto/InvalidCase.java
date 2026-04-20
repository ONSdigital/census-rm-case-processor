package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class InvalidCase {
  private UUID caseId;
  private String reason;
}
