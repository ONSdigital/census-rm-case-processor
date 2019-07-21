package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceiptDTO extends BaseDTO {

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("caseId")
  private String caseId;

  private String questionnaireId;

  private boolean unreceipt;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("dateTime")
  private OffsetDateTime responseDateTime;
}
