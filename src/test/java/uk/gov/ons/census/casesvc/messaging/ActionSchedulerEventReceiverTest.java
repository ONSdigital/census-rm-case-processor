package uk.gov.ons.census.casesvc.messaging;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.service.EventProcessor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ActionSchedulerEventReceiverTest {

  @Test
  public void testReceiveMessage() {
    // Given
    EventProcessor eventProcessor = mock(EventProcessor.class);
    ActionSchedulerEventReceiver underTest = new ActionSchedulerEventReceiver(eventProcessor);

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventType.PRINT_CASE_SELECTED);
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
    event.setType(EventType.CASE_CREATED);
    responseManagementEvent.setEvent(event);

    // When
    underTest.receiveMessage(responseManagementEvent);

    // Then
    // Unreachable code
  }
}
