package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;

@Data
@NoArgsConstructor
public class Refusal {

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("case_id")
  private String caseId;

  private RefusalType type;

  private String report;

  private String agentId;

  private CollectionCase collectionCase;

  @JsonProperty("questionnaire_id")
  private String questionnaire_Id;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("response_dateTime")
  private OffsetDateTime responseDateTime;
}
