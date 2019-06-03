package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacProcessor;

@MessageEndpoint
public class UnaddressedReceiver {
  private final UacProcessor uacProcessor;

  public UnaddressedReceiver(UacProcessor uacProcessor) {
    this.uacProcessor = uacProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "unaddressedInputChannel")
  public void receiveMessage(CreateUacQid createUacQid) {
    UacQidLink uacQidLink =
        uacProcessor.saveUacQidLink(
            null, Integer.parseInt(createUacQid.getQuestionnaireType()), createUacQid.getBatchId());
    uacProcessor.emitUacUpdatedEvent(uacQidLink, null);
    uacProcessor.logEvent(uacQidLink, "Unaddressed UAC/QID pair created", EventType.UAC_UPDATED);
  }
}
