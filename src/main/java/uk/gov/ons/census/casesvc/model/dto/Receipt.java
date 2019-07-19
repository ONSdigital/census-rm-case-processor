package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Receipt {

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("case_id")
  private String caseId;

  @JsonProperty("questionnaire_id")
  private String questionnaire_Id;

  private boolean unreceipt;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("response_dateTime")
  private OffsetDateTime responseDateTime;
}
