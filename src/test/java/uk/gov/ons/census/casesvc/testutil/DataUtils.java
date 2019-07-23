package uk.gov.ons.census.casesvc.testutil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;

public class DataUtils {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  private static EasyRandom easyRandom;
  private static ObjectMapper objectMapper;

  static {
    easyRandom = new EasyRandom();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  public static Case getRandomCase() {
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);

    return caze;
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

  public static ReceiptDTO convertJsonToReceiptDTO(String json) {
    try {
      return objectMapper.readValue(json, ReceiptDTO.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed converting Json To ReceiptDTO", e);
    }
  }

  public static RefusalDTO convertJsonToRefusalDTO(String json) {
    try {
      return objectMapper.readValue(json, RefusalDTO.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed converting Json To ReceiptDTO", e);
    }
  }
}
