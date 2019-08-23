package uk.gov.ons.census.casesvc.logging;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;

@Component
public class EventLogger {

  private final EventRepository eventRepository;

  public EventLogger(EventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  public void logCaseEvent(
      Case caze,
      OffsetDateTime eventDate,
      String eventDescription,
      EventType eventType,
      EventDTO event,
      String eventPayload) {
    Event loggedEvent = buildEvent(eventDate, eventDescription, eventType, event, eventPayload);
    loggedEvent.setCaze(caze);

    eventRepository.save(loggedEvent);
  }

  public void logUacQidEvent(
      UacQidLink uacQidLink,
      OffsetDateTime eventDate,
      String eventDescription,
      EventType eventType,
      EventDTO event,
      String eventPayload) {
    Event loggedEvent = buildEvent(eventDate, eventDescription, eventType, event, eventPayload);
    loggedEvent.setUacQidLink(uacQidLink);

    eventRepository.save(loggedEvent);
  }

  private Event buildEvent(
      OffsetDateTime eventDate,
      String eventDescription,
      EventType eventType,
      EventDTO event,
      String eventPayload) {
    Event loggedEvent = new Event();

    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(eventDate);
    loggedEvent.setRmEventProcessed(OffsetDateTime.now());
    loggedEvent.setEventDescription(eventDescription);
    loggedEvent.setEventType(eventType);
    loggedEvent.setEventChannel(event.getChannel());
    loggedEvent.setEventSource(event.getSource());
    loggedEvent.setEventTransactionId(event.getTransactionId());
    loggedEvent.setEventPayload(eventPayload);
    return loggedEvent;
  }
}
