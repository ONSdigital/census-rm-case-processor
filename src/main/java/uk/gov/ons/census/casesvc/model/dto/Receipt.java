package uk.gov.ons.census.casesvc.model.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Receipt {
  @JsonProperty("case_id")
  private String caseId;

  @JsonProperty("tx_id")
  private String txId;

  @JsonProperty("questionnaire_id")
  private String questionnaire_Id;

  @JsonProperty("response_dateTime")
  private LocalDateTime responseDateTime;

  @JsonProperty("inbound_channel")
  private String inboundChannel;
}
