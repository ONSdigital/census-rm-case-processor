package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class ResponseDTO {

  private UUID caseId;

  private String questionnaireId;

  private Boolean unreceipt;

  private String agentId;

  @JsonProperty("dateTime")
  private OffsetDateTime responseDateTime;
}
