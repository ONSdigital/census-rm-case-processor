package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
  private String caseId;
  private String collectionExerciseId;
  private Boolean unreceipted;
}
