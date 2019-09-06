package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResponseDTO {

  @JsonInclude(Include.NON_NULL)
  private String caseId;

  private String questionnaireId;

  @JsonInclude(Include.NON_NULL)
  private Boolean unreceipt;

  private String agentId;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("dateTime")
  private OffsetDateTime responseDateTime;
}
