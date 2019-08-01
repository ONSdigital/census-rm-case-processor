package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentProcessor;

@MessageEndpoint
public class FulfilmentReceiver {
  private final FulfilmentProcessor fulfilmentProcessor;

  public FulfilmentReceiver(FulfilmentProcessor fulfilmentProcessor) {
    this.fulfilmentProcessor = fulfilmentProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "fulfilmentInputChannel")
  public void receiveMessage(ResponseManagementEvent fulfilmentEvent) {
    fulfilmentProcessor.processFulfilmentRequest(fulfilmentEvent);
  }
}
