package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacService;

@MessageEndpoint
public class DeactivateUacReceiver {

  private static final String DEACTIVATE_UAC = "Deactivated UAC";
  private final UacService uacService;
  private final EventLogger eventLogger;

  public DeactivateUacReceiver(UacService uacService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "deactivateUacInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);

    UacDTO uacDTO = message.getPayload().getPayload().getUac();

    UacQidLink uacQidLink = uacService.findByQid(uacDTO.getQuestionnaireId());
    uacQidLink.setActive(false);
    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

    eventLogger.logUacQidEvent(
        uacQidLink,
        message.getPayload().getEvent().getDateTime(),
        DEACTIVATE_UAC,
        EventType.DEACTIVATE_UAC,
        message.getPayload().getEvent(),
        convertObjectToJson(message.getPayload()),
        messageTimestamp);
  }
}
