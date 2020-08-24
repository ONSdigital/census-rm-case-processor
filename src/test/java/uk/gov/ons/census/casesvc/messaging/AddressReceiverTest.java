package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.NewAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.service.AddressModificationService;
import uk.gov.ons.census.casesvc.service.CaseService;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;
import uk.gov.ons.census.casesvc.service.NewAddressReportedService;
import uk.gov.ons.census.casesvc.utility.JsonHelper;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

@RunWith(MockitoJUnitRunner.class)
public class AddressReceiverTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private InvalidAddressService invalidAddressService;

  @Mock private AddressModificationService addressModificationService;

  @Mock private NewAddressReportedService newAddressReportedService;

  @InjectMocks AddressReceiver underTest;

  @Test
  public void testInvalidAddress() {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(ADDRESS_NOT_VALID);
    responseManagementEvent.setEvent(event);
    Message<ResponseManagementEvent> message =
        constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    underTest.receiveMessage(message);

    verify(invalidAddressService).processMessage(eq(responseManagementEvent), eq(expectedDateTime));
  }

  @Test
  public void testNewAddressReportedServiceCalled() {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(NEW_ADDRESS_REPORTED);
    NewAddress newAddress = new NewAddress();
    newAddress.setSourceCaseId(null);
    PayloadDTO payload = new PayloadDTO();
    payload.setNewAddress(newAddress);
    responseManagementEvent.setEvent(event);
    responseManagementEvent.setPayload(payload);
    Message<ResponseManagementEvent> message =
        constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    underTest.receiveMessage(message);

    verify(newAddressReportedService)
        .processNewAddress(eq(responseManagementEvent), eq(expectedDateTime));
  }

  @Test
  public void testNewAddressReportedServiceCalledWithSourceCaseId() {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(NEW_ADDRESS_REPORTED);
    NewAddress newAddress = new NewAddress();
    newAddress.setSourceCaseId(UUID.randomUUID());
    PayloadDTO payload = new PayloadDTO();
    payload.setNewAddress(newAddress);
    responseManagementEvent.setEvent(event);
    responseManagementEvent.setPayload(payload);
    Message<ResponseManagementEvent> message =
        constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    underTest.receiveMessage(message);

    verify(newAddressReportedService)
        .processNewAddressFromSourceId(
            eq(responseManagementEvent), eq(expectedDateTime), eq(newAddress.getSourceCaseId()));
  }

  @Test
  public void testAddressModifiedEventType() {
    // Given
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(ADDRESS_MODIFIED);
    responseManagementEvent.setEvent(event);
    Message<ResponseManagementEvent> message =
        constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    // When
    underTest.receiveMessage(message);

    // Then
    verify(addressModificationService)
        .processMessage(eq(responseManagementEvent), eq(expectedDateTime));
  }

  @Test
  public void testAddressTypeChangeEventTypeLoggedOnly() throws JSONException, IOException {
    PayloadDTO payload = new PayloadDTO();
    //    payload.setAddressTypeChanged(createTestAddressTypeChangedJson(TEST_CASE_ID));
    //    TODO - Fix test

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getAddressTypeChanged()),
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
