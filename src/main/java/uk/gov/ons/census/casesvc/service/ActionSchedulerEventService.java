package uk.gov.ons.census.casesvc.service;

import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

@Service
public class ActionSchedulerEventService {

  private final EventService eventService;

  public ActionSchedulerEventService(EventService eventService) {
    this.eventService = eventService;
  }

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
