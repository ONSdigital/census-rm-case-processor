package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.EventService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

public class ActionSchedulerEventReceiverTest {

  @Test
  public void testReceiveMessage() {
    // Given
    EventService eventService = mock(EventService.class);
    ActionSchedulerEventReceiver underTest = new ActionSchedulerEventReceiver(eventService);

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    Message<ResponseManagementEvent> message =
        constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.PRINT_CASE_SELECTED);
    responseManagementEvent.setEvent(event);

    // When
    underTest.receiveMessage(message);

    // Then
    verify(eventService).processPrintCaseSelected(eq(responseManagementEvent), eq(expectedDate));
  }

  @Test(expected = RuntimeException.class)
  public void testReceiveUnexpectedMessage() {
    // Given
    EventService eventService = mock(EventService.class);
    ActionSchedulerEventReceiver underTest = new ActionSchedulerEventReceiver(eventService);

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    Message<ResponseManagementEvent> message =
        constructMessageWithValidTimeStamp(responseManagementEvent);

    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.CASE_CREATED);
    responseManagementEvent.setEvent(event);

    // When
    underTest.receiveMessage(message);

    // Then
    // Unreachable code
  }
}
