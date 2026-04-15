package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class SmsRequest {
  private UUID caseId;

  private String phoneNumber;

  private String packCode;

  private Object uacMetadata;

  private boolean scheduled;
}
