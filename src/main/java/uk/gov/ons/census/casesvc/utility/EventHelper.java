package uk.gov.ons.census.casesvc.utility;

import uk.gov.ons.census.casesvc.model.dto.Event;
import uk.gov.ons.census.casesvc.model.dto.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

public class EventHelper {

    private static final String EVENT_SOURCE = "CASE_SERVICE";
    private static final String EVENT_CHANNEL = "RM";

    public static Event createEvent(EventType eventType) {
        Event event = new Event();
        event.setChannel(EVENT_CHANNEL);
        event.setSource(EVENT_SOURCE);
        event.setDateTime(LocalDateTime.now().toString());
        event.setTransactionId(UUID.randomUUID().toString());
        event.setType(eventType);
        return event;
    }
}
