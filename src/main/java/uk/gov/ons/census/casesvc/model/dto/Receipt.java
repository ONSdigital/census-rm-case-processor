package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
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
  private OffsetDateTime responseDateTime;

  @JsonProperty("inbound_channel")
  private String inboundChannel;
}
