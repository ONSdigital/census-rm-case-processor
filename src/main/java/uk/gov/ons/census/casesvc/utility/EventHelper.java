package uk.gov.ons.census.casesvc.utility;

import java.time.OffsetDateTime;
import java.util.UUID;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

public class EventHelper {

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String EVENT_CHANNEL = "RM";
  private static final String FIELD_CHANNEL = "FIELD";

  public static EventDTO createEventDTO(
      EventTypeDTO eventType, String event_channel, String event_source) {
    EventDTO eventDTO = new EventDTO();

    eventDTO.setChannel(event_channel);
    eventDTO.setSource(event_source);
    eventDTO.setDateTime(OffsetDateTime.now());
    eventDTO.setTransactionId(UUID.randomUUID());
    eventDTO.setType(eventType);

    return eventDTO;
  }

  public static EventDTO createEventDTO(EventTypeDTO eventType) {
    return createEventDTO(eventType, EVENT_CHANNEL, EVENT_SOURCE);
  }

  public static boolean isEventChannelField(ResponseManagementEvent event) {
    return event.getEvent().getChannel().equalsIgnoreCase(FIELD_CHANNEL);
  }
}
