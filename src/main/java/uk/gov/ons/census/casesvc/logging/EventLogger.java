package uk.gov.ons.census.casesvc.logging;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.Receipt;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
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
      UacQidLink uacQidLink, String eventDescription, EventType eventType, PayloadDTO payloadDTO) {

    // Keep hardcoded for non-receipting calls for now
    Map<String, String> headers = new HashMap<>();
    headers.put("source", EVENT_SOURCE);
    headers.put("channel", EVENT_CHANNEL);

    logEvent(uacQidLink, eventDescription, eventType, payloadDTO, headers, null);
  }

  public void logReceiptEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      Receipt payload,
      Map<String, String> headers,
      OffsetDateTime eventMetaDataDateTime) {

    validateHeaders(headers);

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

    loggedEvent.setEventChannel(headers.get("channel"));
    loggedEvent.setEventSource(headers.get("source"));

    loggedEvent.setEventTransactionId(UUID.randomUUID());
    loggedEvent.setEventPayload(convertObjectToJson(payload));

    eventRepository.save(loggedEvent);
  }

  public void logRefusalEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      RefusalDTO refusal,
      Map<String, String> headers,
      OffsetDateTime eventMetaDataDateTime) {

    validateHeaders(headers);

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

    loggedEvent.setEventChannel(headers.get("channel"));
    loggedEvent.setEventSource(headers.get("source"));

    loggedEvent.setEventTransactionId(UUID.randomUUID());
    loggedEvent.setEventPayload(convertObjectToJson(refusal));

    eventRepository.save(loggedEvent);
  }

  public void logEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      PayloadDTO payloadDTO,
      Map<String, String> headers,
      OffsetDateTime eventMetaDataDateTime) {

    validateHeaders(headers);

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

    loggedEvent.setEventChannel(headers.get("channel"));
    loggedEvent.setEventSource(headers.get("source"));

    loggedEvent.setEventTransactionId(UUID.randomUUID());
    loggedEvent.setEventPayload(convertObjectToJson(payloadDTO));

    eventRepository.save(loggedEvent);
  }

  private static void validateHeaders(Map<String, String> headers) {
    if (!headers.containsKey("source")) {
      throw new RuntimeException("Missing 'source' header value");
    }

    if (!headers.containsKey("channel")) {
      throw new RuntimeException("Missing 'channel' header value");
    }
  }
}
