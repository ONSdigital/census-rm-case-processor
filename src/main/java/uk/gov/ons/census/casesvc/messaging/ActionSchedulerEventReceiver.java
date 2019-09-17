package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.EventService;

@MessageEndpoint
public class ActionSchedulerEventReceiver {

  private final EventService eventService;

  public ActionSchedulerEventReceiver(EventService eventService) {
    this.eventService = eventService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "actionCaseInputChannel")
  public void receiveMessage(ResponseManagementEvent event) {
    EventTypeDTO eventType = event.getEvent().getType();

    if (eventType == EventTypeDTO.PRINT_CASE_SELECTED) {
      eventService.processPrintCaseSelected(event);
    } else if (eventType == EventTypeDTO.FIELD_CASE_SELECTED) {
      eventService.processFieldCaseSelected(event);
    } else {
      throw new RuntimeException(String.format("Unexpected event type '%s' received", eventType));
    }
  }
}
