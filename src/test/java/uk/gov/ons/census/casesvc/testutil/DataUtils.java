package uk.gov.ons.census.casesvc.testutil;

import static org.jeasy.random.FieldPredicates.inClass;
import static org.jeasy.random.FieldPredicates.named;
import static org.jeasy.random.FieldPredicates.ofType;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.CCS_ADDRESS_LISTED;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

public class DataUtils {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  private static final EasyRandom easyRandom;
  private static final ObjectMapper objectMapper;

  static {
    EasyRandomParameters parameters =
        new EasyRandomParameters()
            .excludeField(
                named("addressModification")
                    .and(ofType(JsonNode.class))
                    .and(inClass(PayloadDTO.class)))
            .excludeField(
                named("addressTypeChange")
                    .and(ofType(JsonNode.class))
                    .and(inClass(PayloadDTO.class)))
            .excludeField(
                named("newAddressReported")
                    .and(ofType(JsonNode.class))
                    .and(inClass(PayloadDTO.class)));
    easyRandom = new EasyRandom(parameters);
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

  public static UacQidLink generateRandomUacQidLink() {
    return easyRandom.nextObject(UacQidLink.class);
  }

  public static UacQidLink generateRandomUacQidLinkedToCase(Case linkedCase) {
    UacQidLink uacQidLink = generateRandomUacQidLink();
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

    payload.getResponse().setUnreceipt(false);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementFieldUpdatedEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();

    EventDTO event = managementEvent.getEvent();
    event.setType(EventTypeDTO.FIELD_CASE_UPDATED);
    event.setSource("FIELDWORK_GATEWAY");
    event.setChannel("FIELD");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setUac(null);
    payload.setRefusal(null);
    payload.setPrintCaseSelected(null);
    payload.getCollectionCase().setCeExpectedCapacity(5);

    payload.getResponse().setUnreceipt(false);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementReceiptEventUnreceipt() {
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

    payload.getResponse().setUnreceipt(true);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementSurveyLaunchedEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();

    EventDTO event = managementEvent.getEvent();
    event.setType(EventTypeDTO.SURVEY_LAUNCHED);
    event.setSource("CONTACT_CENTRE_API");
    event.setChannel("CC");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setUac(null);
    payload.setCollectionCase(null);
    payload.setRefusal(null);
    payload.setPrintCaseSelected(null);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementRespondentAuthenticatedEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementSurveyLaunchedEvent();

    managementEvent.getEvent().setChannel("Test channel");
    managementEvent.getEvent().setSource("Test source");

    managementEvent.getEvent().setType(EventTypeDTO.RESPONDENT_AUTHENTICATED);
    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

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
    payload.setResponse(null);
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
    payload.setResponse(null);
    payload.setPrintCaseSelected(null);
    payload.setRefusal(null);
    payload.setCcsProperty(null);

    FulfilmentRequestDTO fulfilmentRequest = payload.getFulfilmentRequest();
    fulfilmentRequest.setCaseId(null);
    fulfilmentRequest.setFulfilmentCode(null);
    fulfilmentRequest.setUacQidCreated(null);

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
    payload.setResponse(null);
    payload.setPrintCaseSelected(null);
    payload.setFulfilmentRequest(null);
    payload.setUacQidCreated(null);
    payload.setInvalidAddress(null);

    return managementEvent;
  }

  public static ResponseManagementEvent getTestResponseManagementCCSAddressListedEvent() {
    ResponseManagementEvent managementEvent = easyRandom.nextObject(ResponseManagementEvent.class);
    managementEvent.getEvent().setType(CCS_ADDRESS_LISTED);
    CCSPropertyDTO ccsPropertyDTO = managementEvent.getPayload().getCcsProperty();
    ccsPropertyDTO.getCollectionCase().setId(TEST_CASE_ID.toString());
    ccsPropertyDTO.setRefusal(null);
    ccsPropertyDTO.setInvalidAddress(null);
    ccsPropertyDTO.setUac(null);
    managementEvent.getPayload().setCcsProperty(ccsPropertyDTO);

    return managementEvent;
  }

  public static <T> T convertJsonToObject(String json, Class<T> clazz) {
    try {
      return objectMapper.readValue(json, clazz);
    } catch (IOException e) {
      throw new RuntimeException("Failed converting Json To FulfilmentRequestDTO", e);
    }
  }

  public static ResponseManagementEvent generateUacCreatedEvent(Case linkedCase) {
    UacCreatedDTO uacCreatedPayload = easyRandom.nextObject(UacCreatedDTO.class);
    uacCreatedPayload.setCaseId(linkedCase.getCaseId());
    uacCreatedPayload.setQid("01234567890");
    EventDTO eventDTO = easyRandom.nextObject(EventDTO.class);
    eventDTO.setType(EventTypeDTO.RM_UAC_CREATED);
    PayloadDTO payloadDTO = new PayloadDTO();
    ResponseManagementEvent uacCreatedEvent = new ResponseManagementEvent();
    payloadDTO.setUacQidCreated(uacCreatedPayload);
    uacCreatedEvent.setEvent(eventDTO);
    uacCreatedEvent.setPayload(payloadDTO);
    return uacCreatedEvent;
  }

  public static JsonNode createTestAddressModifiedJson(UUID caseId) {
    ObjectNode collectionCaseNode =
        objectMapper.createObjectNode().put("id", caseId.toString()).put("ceExpectedCapacity", 20);

    ObjectNode addressNode =
        objectMapper
            .createObjectNode()
            .put("organisationName", "XXXXXXXXXXXXX")
            .put("addressLine1", "1a main street")
            .put("addressLine2", "upper upperingham")
            .put("addressLine3", "")
            .put("townName", "upton")
            .put("postcode", "UP103UP")
            .put("region", "E")
            .put("uprn", "XXXXXXXXXXXXX")
            .put("estabUprn", "XXXXX");

    ObjectNode parentNode = objectMapper.createObjectNode();
    parentNode.set("collectionCase", collectionCaseNode);
    parentNode.set("address", addressNode);

    return parentNode;
  }

  public static JsonNode createTestAddressTypeChangeJson(UUID caseId) {
    ObjectNode collectionCaseNode =
        objectMapper.createObjectNode().put("id", caseId.toString()).put("ceExpectedCapacity", 20);

    ObjectNode addressNode =
        objectMapper
            .createObjectNode()
            .put("organisationName", "XXXXXXXXXXXXX")
            .put("uprn", "XXXXXXXXXXXXX")
            .put("addressType", "CE")
            .put("estabType", "XXX");

    collectionCaseNode.set("address", addressNode);

    ObjectNode parentNode = objectMapper.createObjectNode();
    parentNode.set("collectionCase", collectionCaseNode);

    return parentNode;
  }
}
