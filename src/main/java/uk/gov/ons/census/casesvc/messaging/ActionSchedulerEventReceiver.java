package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
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
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    ResponseManagementEvent event = message.getPayload();
    EventTypeDTO eventType = event.getEvent().getType();
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);

    if (eventType == EventTypeDTO.PRINT_CASE_SELECTED) {
      eventService.processPrintCaseSelected(event, messageTimestamp);
    } else if (eventType == EventTypeDTO.FIELD_CASE_SELECTED) {
      eventService.processFieldCaseSelected(event, messageTimestamp);
    } else {
      throw new RuntimeException(String.format("Unexpected event type '%s' received", eventType));
    }
  }
}
