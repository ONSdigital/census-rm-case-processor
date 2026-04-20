package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@MessageEndpoint
public class DeactivateUacReceiver {
  private final UacService uacService;
  private final EventLogger eventLogger;

  public DeactivateUacReceiver(UacService uacService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "deactivateUacInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    UacQidLink uacQidLink = uacService.findByQid(event.getPayload().getDeactivateUac().getQid());

    uacQidLink.setActive(false);
    uacQidLink =
        uacService.saveAndEmitUacUpdateEvent(
            uacQidLink,
            event.getHeader().getCorrelationId(),
            event.getHeader().getOriginatingUser());

    eventLogger.logUacQidEvent(
        uacQidLink, "Deactivate UAC", EventType.DEACTIVATE_UAC, event, message);
  }
}
