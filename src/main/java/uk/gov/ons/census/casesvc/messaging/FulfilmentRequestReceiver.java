package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentRequestProcessor;

@MessageEndpoint
public class FulfilmentRequestReceiver {
  private final FulfilmentRequestProcessor fulfilmentRequestProcessor;

  public FulfilmentRequestReceiver(FulfilmentRequestProcessor fulfilmentRequestProcessor) {
    this.fulfilmentRequestProcessor = fulfilmentRequestProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "fulfilmentInputChannel")
  public void receiveMessage(ResponseManagementEvent fulfilmentEvent) {
    fulfilmentRequestProcessor.processFulfilmentRequest(fulfilmentEvent);
  }
}
