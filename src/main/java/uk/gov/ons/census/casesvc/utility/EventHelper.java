package uk.gov.ons.census.casesvc.utility;

import java.time.OffsetDateTime;
import java.util.UUID;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.entity.EventType;

public class EventHelper {

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String EVENT_CHANNEL = "RM";

  public static EventDTO createEventDTO(EventType EventType) {
    EventDTO eventDTO = new EventDTO();

    eventDTO.setChannel(EVENT_CHANNEL);
    eventDTO.setSource(EVENT_SOURCE);
    eventDTO.setDateTime(OffsetDateTime.now());
    eventDTO.setTransactionId(UUID.randomUUID().toString());
    eventDTO.setType(EventType);

    return eventDTO;
  }
}
