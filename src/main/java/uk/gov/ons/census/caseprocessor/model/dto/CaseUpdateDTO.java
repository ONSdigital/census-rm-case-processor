package uk.gov.ons.census.caseprocessor.model.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class CaseUpdateDTO {
  private UUID caseId;
  private String caseRef;
  private UUID collectionExerciseId;
  private UUID surveyId;
  private boolean invalid;
  private RefusalTypeDTO refusalReceived;
  private Map<String, String> sample;
  private Map<String, String> sampleSensitive;
  private OffsetDateTime createdAt;
  private OffsetDateTime lastUpdatedAt;
}
