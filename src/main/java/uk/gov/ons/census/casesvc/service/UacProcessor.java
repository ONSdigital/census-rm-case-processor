package uk.gov.ons.census.casesvc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;
import uk.gov.ons.census.casesvc.utility.Sha256Helper;

@Component
public class UacProcessor {

  private static final String UAC_UPDATE_ROUTING_KEY = "event.uac.update";

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String EVENT_CHANNEL = "RM";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final EventRepository eventRepository;
  private final RabbitTemplate rabbitTemplate;
  private final UacQidServiceClient uacQidServiceClient;
  private final ObjectMapper objectMapper;

  @Value("${queueconfig.outbound-exchange}")
  private String outboundExchange;

  public UacProcessor(
      UacQidLinkRepository uacQidLinkRepository,
      EventRepository eventRepository,
      RabbitTemplate rabbitTemplate,
      UacQidServiceClient uacQidServiceClient,
      ObjectMapper objectMapper) {
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidServiceClient = uacQidServiceClient;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventRepository = eventRepository;
    this.objectMapper = objectMapper;
  }

  public UacQidLink saveUacQidLink(Case caze, int questionnaireType) {
    return saveUacQidLink(caze, questionnaireType, null);
  }

  public UacQidLink saveUacQidLink(Case caze, int questionnaireType, UUID batchId) {
    UacQidDTO uacQid = uacQidServiceClient.generateUacQid(questionnaireType);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(uacQid.getUac());
    uacQidLink.setCaze(caze);
    uacQidLink.setBatchId(batchId);
    uacQidLink.setActive(true);

    uacQidLink.setQid(uacQid.getQid());
    uacQidLinkRepository.save(uacQidLink);

    return uacQidLink;
  }

  public void logEvent(
      UacQidLink uacQidLink, String eventDescription, EventType eventType, PayloadDTO payloadDTO) {
    logEvent(uacQidLink, eventDescription, eventType, payloadDTO, null);
  }

  public void logEvent(
      UacQidLink uacQidLink,
      String eventDescription,
      EventType eventType,
      PayloadDTO payloadDTO,
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

    loggedEvent.setEventChannel(EVENT_CHANNEL);
    loggedEvent.setEventSource(EVENT_SOURCE);
    loggedEvent.setEventTransactionId(UUID.randomUUID());
    loggedEvent.setEventPayload(convertPayloadDTOToJson(payloadDTO));

    eventRepository.save(loggedEvent);
  }

  public PayloadDTO emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze) {
    return emitUacUpdatedEvent(uacQidLink, caze, true);
  }

  public PayloadDTO emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze, boolean active) {
    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.UAC_UPDATED);

    UacDTO uac = new UacDTO();
    uac.setQuestionnaireId(uacQidLink.getQid());
    uac.setUacHash(Sha256Helper.hash(uacQidLink.getUac()));
    uac.setUac(uacQidLink.getUac());
    uac.setActive(active);

    if (caze != null) {
      uac.setCaseId(caze.getCaseId().toString());
      uac.setCaseType(caze.getAddressType());
      uac.setCollectionExerciseId(caze.getCollectionExerciseId());
      uac.setRegion(caze.getRegion());
    }

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setUac(uac);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEventDTO(eventDTO);
    responseManagementEvent.setPayloadDTO(payloadDTO);

    rabbitTemplate.convertAndSend(
        outboundExchange, UAC_UPDATE_ROUTING_KEY, responseManagementEvent);

    return payloadDTO;
  }

  private String convertPayloadDTOToJson(PayloadDTO payloadDTO) {
    try {
      return objectMapper.writeValueAsString(payloadDTO);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting PayloadDTO To Json");
    }
  }
}
