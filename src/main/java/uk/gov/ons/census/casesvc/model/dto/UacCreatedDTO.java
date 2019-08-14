package uk.gov.ons.census.casesvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class UacCreatedDTO {
  private String uac;
  private String qid;
  private UUID caseId;
}
