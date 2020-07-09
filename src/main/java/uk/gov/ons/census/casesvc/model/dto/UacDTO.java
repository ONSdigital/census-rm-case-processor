package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.UUID;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class UacDTO {
  private String uacHash;
  private String uac;
  private Boolean active;
  private String questionnaireId;
  private String caseType;
  private String region;
  private UUID caseId;
  private UUID collectionExerciseId;
  private String formType;
  private UUID individualCaseId;
}
