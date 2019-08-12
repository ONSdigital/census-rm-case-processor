package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacProcessor;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

@MessageEndpoint
public class UnaddressedReceiver {
  private final UacProcessor uacProcessor;
  private final EventLogger eventLogger;

  public UnaddressedReceiver(UacProcessor uacProcessor, EventLogger eventLogger) {
    this.uacProcessor = uacProcessor;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "unaddressedInputChannel")
  public void receiveMessage(CreateUacQid createUacQid) {
    UacQidLink uacQidLink =
        uacProcessor.saveUacQidLink(
            null, Integer.parseInt(createUacQid.getQuestionnaireType()), createUacQid.getBatchId());
    PayloadDTO uacPayloadDTO = uacProcessor.emitUacUpdatedEvent(uacQidLink, null);
    eventLogger.logEvent(
        uacQidLink, "Unaddressed UAC/QID pair created", EventType.UAC_UPDATED, convertObjectToJson(uacPayloadDTO));
  }
}
