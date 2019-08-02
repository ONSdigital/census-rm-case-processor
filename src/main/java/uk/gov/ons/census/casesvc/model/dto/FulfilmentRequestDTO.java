package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FulfilmentRequestDTO {

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("caseId")
  private String caseId;

  private String fulfilmentCode;

  private String individualCaseId;
}
