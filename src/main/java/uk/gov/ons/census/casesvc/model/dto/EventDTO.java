package uk.gov.ons.census.casesvc.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class EventDTO {
  private EventTypeDTO type;
  private String source;
  private String channel;
  private OffsetDateTime dateTime;
  private UUID transactionId;
}
