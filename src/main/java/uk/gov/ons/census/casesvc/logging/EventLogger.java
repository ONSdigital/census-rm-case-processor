package uk.gov.ons.census.casesvc.logging;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;

@Component
public class EventLogger {

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String EVENT_CHANNEL = "RM";

  private final EventRepository eventRepository;

  public EventLogger(EventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  public void logEvent(
      UacQidLink uacQidLink, String eventDescription, EventType eventType, PayloadDTO payload) {

    // Keep hardcoded for non-receipting calls for now
    EventDTO event = new EventDTO();
    event.setSource(EVENT_SOURCE);
    event.setChannel(EVENT_CHANNEL);

    logEvent(uacQidLink, eventDescription, eventType, convertObjectToJson(payload), event, null);
  }

  public void logReceiptEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      ReceiptDTO payload,
      EventDTO event,
      OffsetDateTime eventMetaDataDateTime) {

    logEvent(
        uacQidLink,
        eventDescription,
        eventType,
        convertObjectToJson(payload),
        event,
        eventMetaDataDateTime);
  }

  public void logRefusalEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      RefusalDTO payload,
      EventDTO event,
      OffsetDateTime eventMetaDataDateTime) {

    logEvent(
        uacQidLink,
        eventDescription,
        eventType,
        convertObjectToJson(payload),
        event,
        eventMetaDataDateTime);
  }

  public void logQuestionnaireLinkedEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      UacDTO payload,
      EventDTO event) {

    logEvent(uacQidLink, eventDescription, eventType, convertObjectToJson(payload), event, null);
  }

  public void logEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      String jsonPayload,
      EventDTO event,
      OffsetDateTime eventMetaDataDateTime) {

    Event loggedEvent = new Event();
    loggedEvent.setId(UUID.randomUUID());

    if (eventMetaDataDateTime != null) {
      loggedEvent.setEventDate(eventMetaDataDateTime);
    }

    loggedEvent.setEventDate(OffsetDateTime.now());
    loggedEvent.setRmEventProcessed(OffsetDateTime.now());
    loggedEvent.setEventDescription(eventDescription);
    loggedEvent.setUacQidLink(uacQidLink);
    loggedEvent.setEventType(eventType);

    // Only set Case Id if Addressed
    if (uacQidLink.getCaze() != null) {
      loggedEvent.setCaseId(uacQidLink.getCaze().getCaseId());
    }

    loggedEvent.setEventChannel(event.getChannel());
    loggedEvent.setEventSource(event.getSource());

    loggedEvent.setEventTransactionId(UUID.randomUUID());
    loggedEvent.setEventPayload(jsonPayload);

    eventRepository.save(loggedEvent);
  }
}
