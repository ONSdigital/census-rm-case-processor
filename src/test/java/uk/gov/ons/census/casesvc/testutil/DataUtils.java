package uk.gov.ons.census.casesvc.testutil;

import java.util.UUID;
import org.jeasy.random.EasyRandom;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;

public class DataUtils {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  private static EasyRandom easyRandom;

  static {
    easyRandom = new EasyRandom();
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
    event.setType(EventTypeDTO.UAC_UPDATED);
    event.setSource("RECEIPTING");
    event.setChannel("EQ");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setUac(null);
    payload.setCollectionCase(null);
    payload.setRefusal(null);
    payload.setPrintCaseSelected(null);

    return managementEvent;
  }
}
