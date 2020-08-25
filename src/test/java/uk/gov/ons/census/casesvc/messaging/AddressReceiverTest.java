package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.service.*;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

@RunWith(MockitoJUnitRunner.class)
public class AddressReceiverTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private InvalidAddressService invalidAddressService;

  @Mock private AddressModificationService addressModificationService;

  @Mock private NewAddressReportedService newAddressReportedService;

  @Mock private AddressTypeChangedService addressTypeChangedService;

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
  public void testAddressTypeChangeEventType() {
    ResponseManagementEvent rme = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(ADDRESS_TYPE_CHANGED);
    rme.setEvent(event);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(rme);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);
    underTest.receiveMessage(message);

    verify(addressTypeChangedService).processMessage(eq(rme), eq(expectedDateTime));
  }

  @Test(expected = RuntimeException.class)
  public void testInvalidAddressEventTypeException() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setType(CASE_CREATED);

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
