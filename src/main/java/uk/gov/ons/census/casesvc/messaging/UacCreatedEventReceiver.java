package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.UacService;

@MessageEndpoint
public class UacCreatedEventReceiver {
  private final UacService uacService;

  public UacCreatedEventReceiver(UacService uacService) {
    this.uacService = uacService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "uacCreatedInputChannel")
  public void receiveMessage(ResponseManagementEvent uacCreatedEvent) {
    uacService.ingestUacCreatedEvent(uacCreatedEvent);
  }
}
