package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class EmailRequest {
  private UUID caseId;

  private String email;

  private String packCode;

  private Object uacMetadata;

  private boolean scheduled;
}
