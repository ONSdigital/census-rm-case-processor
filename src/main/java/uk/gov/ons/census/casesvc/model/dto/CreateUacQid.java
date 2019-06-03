package uk.gov.ons.census.casesvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class CreateUacQid {
  private String questionnaireType;
  private UUID batchId;
}
