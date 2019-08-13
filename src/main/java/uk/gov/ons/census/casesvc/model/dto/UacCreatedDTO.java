package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UacCreatedDTO {
  private String uac;
  private String qid;
  private UUID caseId;
}
