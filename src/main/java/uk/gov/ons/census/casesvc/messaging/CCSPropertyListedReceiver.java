package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.CCSPropertyListedService;

@MessageEndpoint
public class CCSPropertyListedReceiver {
  private final CCSPropertyListedService ccsPropertyListedService;

  public CCSPropertyListedReceiver(CCSPropertyListedService ccsPropertyListedService) {
    this.ccsPropertyListedService = ccsPropertyListedService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "ccsPropertyListedInputChannel")
  public void receiveMessage(ResponseManagementEvent ccsPropertyListedEvent) {
    ccsPropertyListedService.processCCSPropertyListed(ccsPropertyListedEvent);
  }
}
