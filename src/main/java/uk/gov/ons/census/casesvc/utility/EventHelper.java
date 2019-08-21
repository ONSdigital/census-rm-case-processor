package uk.gov.ons.census.casesvc.utility;

import java.time.OffsetDateTime;
import java.util.UUID;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;

public class EventHelper {

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String EVENT_CHANNEL = "RM";

  public static EventDTO createEventDTO(EventTypeDTO eventType) {
    EventDTO eventDTO = new EventDTO();

    eventDTO.setChannel(EVENT_CHANNEL);
    eventDTO.setSource(EVENT_SOURCE);
    eventDTO.setDateTime(OffsetDateTime.now());
    eventDTO.setTransactionId(UUID.randomUUID());
    eventDTO.setType(eventType);

    return eventDTO;
  }
}
