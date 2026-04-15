package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class UpdateSampleSensitive {

  private UUID caseId;
  private Map<String, String> sampleSensitive;
}
