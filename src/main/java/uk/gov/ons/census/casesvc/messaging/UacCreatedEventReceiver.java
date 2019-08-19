package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.UacProcessor;

@MessageEndpoint
public class UacCreatedEventReceiver {
  private final UacProcessor uacProcessor;

  public UacCreatedEventReceiver(UacProcessor uacProcessor) {
    this.uacProcessor = uacProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "uacCreatedInputChannel")
  public void receiveMessage(ResponseManagementEvent uacCreatedEvent) {
    uacProcessor.ingestUacCreatedEvent(uacCreatedEvent);
  }
}
