package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class NewCase {
  private UUID caseId;

  private UUID collectionExerciseId;

  private Map<String, String> sample;

  private Map<String, String> sampleSensitive;
}
