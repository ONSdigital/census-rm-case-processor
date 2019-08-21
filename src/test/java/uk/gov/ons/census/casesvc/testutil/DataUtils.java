package uk.gov.ons.census.casesvc.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacCreatedDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.type.RefusalType;

public class DataUtils {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  private static final EasyRandom easyRandom;
  private static final ObjectMapper objectMapper;

  static {
    easyRandom = new EasyRandom();
    objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public static Case getRandomCase() {
    // uacQidLinks and Events have to be set to avoid a stack overflow in easy random
    Case randomCase = easyRandom.nextObject(Case.class);
    randomCase.setUacQidLinks(null);
    randomCase.setEvents(null);
    randomCase.setCaseId(TEST_CASE_ID);
    return randomCase;
  }

  public static Case getRandomCaseWithUacQidLinks(int numLinks) {
    Case randomCase = getRandomCase();
    List<UacQidLink> uacQidLinks = new LinkedList<>();

    for (int i = 0; i < numLinks; i++) {
      UacQidLink uacQidLink = easyRandom.nextObject(UacQidLink.class);
      uacQidLink.setCaze(randomCase);
      uacQidLinks.add(uacQidLink);
    }

    randomCase.setUacQidLinks(uacQidLinks);

    return randomCase;
  }

  public static UacQidLink generateRandomUacQidLink(Case linkedCase) {
    UacQidLink uacQidLink = easyRandom.nextObject(UacQidLink.class);
    uacQidLink.setCaze(linkedCase);
    uacQidLink.setEvents(null);
    linkedCase.setUacQidLinks(List.of(uacQidLink));
    return uacQidLink;
  }

  public static ResponseManagementEvent getTestResponseManagementEvent() {
    ResponseManagementEvent managementEvent = easyRandom.nextObject(ResponseManagementEvent.class);
    managementEvent.getEvent().setChannel("EQ");
    managementEvent.getEvent().setSource("RECEIPTING");

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementReceiptEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();

    EventDTO event = managementEvent.getEvent();
    event.setType(EventTypeDTO.RESPONSE_RECEIVED);
    event.setSource("RECEIPT_SERVICE");
    event.setChannel("EQ");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setUac(null);
    payload.setCollectionCase(null);
    payload.setRefusal(null);
    payload.setPrintCaseSelected(null);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementRefusalEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();

    EventDTO event = managementEvent.getEvent();
    event.setType(EventTypeDTO.REFUSAL_RECEIVED);
    event.setSource("CONTACT CENTRE API");
    event.setChannel("CC");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setUac(null);
    payload.setCollectionCase(null);
    payload.setReceipt(null);
    payload.setPrintCaseSelected(null);

    RefusalDTO refusal = payload.getRefusal();
    refusal.setType(RefusalType.HARD_REFUSAL);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementFulfilmentRequestedEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();

    EventDTO event = managementEvent.getEvent();
    event.setType(EventTypeDTO.FULFILMENT_REQUESTED);
    event.setSource("CONTACT CENTRE API");
    event.setChannel("CC");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setUac(null);
    payload.setCollectionCase(null);
    payload.setReceipt(null);
    payload.setPrintCaseSelected(null);
    payload.setRefusal(null);

    FulfilmentRequestDTO fulfilmentRequest = payload.getFulfilmentRequest();
    fulfilmentRequest.setCaseId(null);
    fulfilmentRequest.setFulfilmentCode(null);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementQuestionnaireLinkedEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();

    EventDTO event = managementEvent.getEvent();
    event.setType(EventTypeDTO.QUESTIONNAIRE_LINKED);
    event.setSource("FIELDWORK_GATEWAY");
    event.setChannel("FIELD");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setRefusal(null);
    payload.setCollectionCase(null);
    payload.setReceipt(null);
    payload.setPrintCaseSelected(null);
    payload.setFulfilmentRequest(null);

    return managementEvent;
  }

  public static RefusalDTO convertJsonToRefusalDTO(String json) {
    try {
      return objectMapper.readValue(json, RefusalDTO.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed converting Json To RefusalDTO", e);
    }
  }

  public static FulfilmentRequestDTO convertJsonToFulfilmentRequestDTO(String json) {
    try {
      return objectMapper.readValue(json, FulfilmentRequestDTO.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed converting Json To FulfilmentRequestDTO", e);
    }
  }

  public static ResponseManagementEvent generateUacCreatedEvent(Case linkedCase) {
    UacCreatedDTO uacCreatedPayload = easyRandom.nextObject(UacCreatedDTO.class);
    uacCreatedPayload.setCaseId(linkedCase.getCaseId());
    EventDTO eventDTO = easyRandom.nextObject(EventDTO.class);
    eventDTO.setType(EventTypeDTO.RM_UAC_CREATED);
    PayloadDTO payloadDTO = new PayloadDTO();
    ResponseManagementEvent uacCreatedEvent = new ResponseManagementEvent();
    payloadDTO.setUacQidCreated(uacCreatedPayload);
    uacCreatedEvent.setEvent(eventDTO);
    uacCreatedEvent.setPayload(payloadDTO);
    return uacCreatedEvent;
  }
}
