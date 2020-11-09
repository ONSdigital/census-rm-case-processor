package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.service.*;

@MessageEndpoint
public class AddressReceiver {
  private final InvalidAddressService invalidAddressService;
  private final AddressModificationService addressModificationService;
  private final NewAddressReportedService newAddressReportedService;
  private final AddressTypeChangeService addressTypeChangeService;
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public AddressReceiver(
      InvalidAddressService invalidAddressService,
      AddressModificationService addressModificationService,
      NewAddressReportedService newAddressReportedService,
      AddressTypeChangeService addressTypeChangeService,
      CaseService caseService,
      EventLogger eventLogger) {
    this.addressModificationService = addressModificationService;
    this.newAddressReportedService = newAddressReportedService;
    this.addressTypeChangeService = addressTypeChangeService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.invalidAddressService = invalidAddressService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "addressInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    ResponseManagementEvent responseManagementEvent = message.getPayload();

    EventDTO event = responseManagementEvent.getEvent();

    switch (event.getType()) {
      case ADDRESS_NOT_VALID:
        invalidAddressService.processMessage(responseManagementEvent, messageTimestamp);
        break;

      case ADDRESS_MODIFIED:
        addressModificationService.processMessage(responseManagementEvent, messageTimestamp);
        break;

      case ADDRESS_TYPE_CHANGED:
        addressTypeChangeService.processMessage(responseManagementEvent, messageTimestamp);
        break;

      case NEW_ADDRESS_REPORTED:
        newAddressReported(responseManagementEvent, messageTimestamp);
        break;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format("Event Type '%s' is invalid on this topic", event.getType()));
    }
  }

  private void newAddressReported(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    if (responseManagementEvent.getPayload().getNewAddress().getSourceCaseId() != null) {
      newAddressReportedService.processNewAddressFromSourceId(
          responseManagementEvent,
          messageTimestamp,
          responseManagementEvent.getPayload().getNewAddress().getSourceCaseId());
    } else {
      newAddressReportedService.processNewAddress(responseManagementEvent, messageTimestamp);
    }
  }

  private void logEvent(
      JsonNode eventPayload,
      EventDTO event,
      String eventDescription,
      EventType eventType,
      OffsetDateTime messageTimestamp) {

    Case caze = caseService.getCaseByCaseId(getCaseId(eventPayload));

    eventLogger.logCaseEvent(
        caze,
        event.getDateTime(),
        eventDescription,
        eventType,
        event,
        eventPayload,
        messageTimestamp);
  }

  private UUID getCaseId(JsonNode json) {
    return UUID.fromString(json.at("/collectionCase/id").asText());
  }
}
