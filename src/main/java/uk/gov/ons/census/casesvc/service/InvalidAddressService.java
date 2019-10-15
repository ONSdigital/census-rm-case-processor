package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@Component
public class InvalidAddressService {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public InvalidAddressService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processMessage(ResponseManagementEvent invalidAddressEvent) {
    if (!processEvent(invalidAddressEvent)) {
      return;
    }

    InvalidAddress invalidAddress = invalidAddressEvent.getPayload().getInvalidAddress();

    Case caze =
        caseService.getCaseByCaseId(UUID.fromString(invalidAddress.getCollectionCase().getId()));

    caze.setAddressInvalid(true);

    if (caze.isCcsCase()) {
      caseService.saveCase(caze);
    } else {
      caseService.saveAndEmitCaseUpdatedEvent(caze);
    }

    eventLogger.logCaseEvent(
        caze,
        invalidAddressEvent.getEvent().getDateTime(),
        "Invalid address",
        EventType.ADDRESS_NOT_VALID,
        invalidAddressEvent.getEvent(),
        convertObjectToJson(invalidAddress));
  }

  private boolean processEvent(ResponseManagementEvent addressEvent) {
    String logEventDescription;
    EventType logEventType;
    JsonNode logEventPayload;
    EventDTO event = addressEvent.getEvent();

    switch (event.getType()) {
      case ADDRESS_NOT_VALID:
        return true;

      case ADDRESS_MODIFIED:
        logEventDescription = "Address modified";
        logEventType = EventType.ADDRESS_MODIFIED;
        logEventPayload = addressEvent.getPayload().getAddressModification();
        break;

      case ADDRESS_TYPE_CHANGED:
        logEventDescription = "Address type changed";
        logEventType = EventType.ADDRESS_TYPE_CHANGED;
        logEventPayload = addressEvent.getPayload().getAddressTypeChange();
        break;

      case NEW_ADDRESS_REPORTED:
        logEventDescription = "New Address reported";
        logEventType = EventType.NEW_ADDRESS_REPORTED;
        logEventPayload = addressEvent.getPayload().getNewAddressReported();
        break;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format("Event Type '%s' is invalid on this topic", event.getType()));
    }

    Case caze = caseService.getCaseByCaseId(getCaseId(logEventPayload));

    eventLogger.logCaseEvent(
        caze,
        addressEvent.getEvent().getDateTime(),
        logEventDescription,
        logEventType,
        event,
        JsonHelper.convertObjectToJson(logEventPayload));

    return false;
  }

  private UUID getCaseId(JsonNode json) {
    return UUID.fromString(json.at("/collectionCase/id").asText());
  }
}
