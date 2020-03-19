package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.service.CaseService;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;
import uk.gov.ons.census.casesvc.service.NewAddressReportedService;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@MessageEndpoint
public class AddressReceiver {
  private final InvalidAddressService invalidAddressService;
  private final NewAddressReportedService newAddressReportedService;
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public AddressReceiver(
      InvalidAddressService invalidAddressService,
      NewAddressReportedService newAddressReportedService,
      CaseService caseService,
      EventLogger eventLogger) {
    this.newAddressReportedService = newAddressReportedService;
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
        logEvent(
            responseManagementEvent.getPayload().getAddressModification(),
            responseManagementEvent.getEvent(),
            "Address modified",
            EventType.ADDRESS_MODIFIED,
            messageTimestamp);
        // currently logged and ignored
        break;

      case ADDRESS_TYPE_CHANGED:
        logEvent(
            responseManagementEvent.getPayload().getAddressTypeChange(),
            responseManagementEvent.getEvent(),
            "Address type changed",
            EventType.ADDRESS_TYPE_CHANGED,
            messageTimestamp);
        // currently logged and ignored
        break;

      case NEW_ADDRESS_REPORTED:
        newAddressReportedService.processNewAddress(responseManagementEvent, messageTimestamp);
        break;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format("Event Type '%s' is invalid on this topic", event.getType()));
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
        JsonHelper.convertObjectToJson(eventPayload),
        messageTimestamp);
  }

  private UUID getCaseId(JsonNode json) {
    return UUID.fromString(json.at("/collectionCase/id").asText());
  }
}
