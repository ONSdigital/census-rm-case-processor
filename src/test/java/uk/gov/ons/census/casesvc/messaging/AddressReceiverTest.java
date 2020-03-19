package uk.gov.ons.census.casesvc.messaging;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCaseCaseId;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.service.CaseService;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;
import uk.gov.ons.census.casesvc.service.NewAddressReportedService;
import uk.gov.ons.census.casesvc.utility.JsonHelper;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

@RunWith(MockitoJUnitRunner.class)
public class AddressReceiverTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private InvalidAddressService invalidAddressService;

  @Mock private NewAddressReportedService newAddressReportedService;

  @InjectMocks AddressReceiver underTest;

  @Test
  public void testInvalidAddress() {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(ADDRESS_NOT_VALID);
    responseManagementEvent.setEvent(event);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    underTest.receiveMessage(message);

    verify(invalidAddressService).processMessage(eq(responseManagementEvent), eq(expectedDateTime));
  }

  @Test
  public void testNewAddressReportedServiceCalled() {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(NEW_ADDRESS_REPORTED);
    responseManagementEvent.setEvent(event);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    underTest.receiveMessage(message);

    verify(newAddressReportedService).processNewAddress(eq(responseManagementEvent), eq(expectedDateTime));
  }

  @Test
  public void testAddressModifiedEventTypeLoggedOnly() throws JSONException, IOException {
    PayloadDTO payload = new PayloadDTO();
    payload.setAddressModification(createTestAddressModifiedJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getAddressModification()),
        ADDRESS_MODIFIED,
        EventType.ADDRESS_MODIFIED,
        "Address modified");
  }

  @Test
  public void testAddressTypeChangeEventTypeLoggedOnly() throws JSONException, IOException {
    PayloadDTO payload = new PayloadDTO();
    payload.setAddressTypeChange(createTestAddressTypeChangeJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getAddressTypeChange()),
        ADDRESS_TYPE_CHANGED,
        EventType.ADDRESS_TYPE_CHANGED,
        "Address type changed");
  }

  private void testEventTypeLoggedOnly(
      PayloadDTO payload,
      String expectedEventPayloadJson,
      EventTypeDTO eventTypeDTO,
      EventType eventType,
      String eventDescription)
      throws IOException {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(eventTypeDTO);
    managementEvent.setPayload(payload);

    Case expectedCase = new Case();
    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(expectedCase);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    // when
    underTest.receiveMessage(message);

    verify(caseService).getCaseByCaseId(TEST_CASE_ID);
    verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq(eventDescription),
            eq(eventType),
            eq(managementEvent.getEvent()),
            eq(expectedEventPayloadJson),
            eq(expectedDateTime));
  }

  @Test(expected = RuntimeException.class)
  public void testInvalidAddressEventTypeException() throws IOException {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setType(CASE_CREATED);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);

    String expectedErrorMessage =
        String.format("Event Type '%s' is invalid on this topic", CASE_CREATED);

    try {
      // WHEN
      underTest.receiveMessage(message);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
