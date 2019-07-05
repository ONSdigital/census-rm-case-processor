package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.EventProcessor;

@MessageEndpoint
public class ActionSchedulerEventReceiver {

  private final EventProcessor eventProcessor;

  public ActionSchedulerEventReceiver(EventProcessor eventProcessor) {
    this.eventProcessor = eventProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "actionCaseInputChannel")
  public void receiveMessage(ResponseManagementEvent event) {
    if (event.getEvent().getType() == EventTypeDTO.PRINT_CASE_SELECTED) {
      eventProcessor.processPrintCaseSelected(event);
    } else {
      throw new RuntimeException(); // Unexpected event type received
    }
  }
}
