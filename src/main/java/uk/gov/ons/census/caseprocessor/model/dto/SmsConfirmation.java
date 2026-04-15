package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class SmsConfirmation {
  private UUID caseId;
  private String packCode;
  private String uac;
  private String qid;
  private Object uacMetadata;
  private boolean scheduled;
  private Map<String, String> personalisation;
}
