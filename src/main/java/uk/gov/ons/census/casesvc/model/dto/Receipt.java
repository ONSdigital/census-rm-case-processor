package uk.gov.ons.census.casesvc.model.dto;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Receipt {
  private String case_id;
  private String tx_id;
  private String questionnaire_id;
  private LocalDateTime response_dateTime;
  private String inbound_channel;
}
