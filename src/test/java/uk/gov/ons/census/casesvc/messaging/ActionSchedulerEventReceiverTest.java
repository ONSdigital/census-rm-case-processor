package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.EventProcessor;

public class ActionSchedulerEventReceiverTest {

  @Test
  public void testReceiveMessage() {
    // Given
    EventProcessor eventProcessor = mock(EventProcessor.class);
    ActionSchedulerEventReceiver underTest = new ActionSchedulerEventReceiver(eventProcessor);

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.PRINT_CASE_SELECTED);
    responseManagementEvent.setEvent(event);

    // When
    underTest.receiveMessage(responseManagementEvent);

    // Then
    verify(eventProcessor).processPrintCaseSelected(eq(responseManagementEvent));
  }

  @Test(expected = RuntimeException.class)
  public void testReceiveUnexpectedMessage() {
    // Given
    EventProcessor eventProcessor = mock(EventProcessor.class);
    ActionSchedulerEventReceiver underTest = new ActionSchedulerEventReceiver(eventProcessor);

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.CASE_CREATED);
    responseManagementEvent.setEvent(event);

    // When
    underTest.receiveMessage(responseManagementEvent);

    // Then
    // Unreachable code
  }
}
