package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.EventHelper.createEventDTO;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacService;

@MessageEndpoint
public class UnaddressedReceiver {

  private final UacService uacService;
  private final EventLogger eventLogger;

  public UnaddressedReceiver(UacService uacService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "unaddressedInputChannel")
  public void receiveMessage(Message<CreateUacQid> message) {
    CreateUacQid createUacQid = message.getPayload();
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    UacQidLink uacQidLink =
        uacService.buildUacQidLink(
            null, Integer.parseInt(createUacQid.getQuestionnaireType()), createUacQid.getBatchId());
    PayloadDTO uacPayloadDTO = uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
    eventLogger.logUacQidEvent(
        uacQidLink,
        OffsetDateTime.now(),
        "Unaddressed UAC/QID pair created",
        EventType.UAC_UPDATED,
        createEventDTO(EventTypeDTO.UAC_UPDATED),
        convertObjectToJson(uacPayloadDTO),
        messageTimestamp);
  }
}
