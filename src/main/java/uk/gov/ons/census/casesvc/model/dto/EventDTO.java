package uk.gov.ons.census.casesvc.model.dto;

import java.time.OffsetDateTime;
import lombok.Data;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Data
public class EventDTO {
  private EventType type;
  private String source;
  private String channel;
  private OffsetDateTime dateTime;
  private String transactionId;
}
