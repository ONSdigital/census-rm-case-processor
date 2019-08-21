package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RefusalService;

@MessageEndpoint
public class RefusalReceiver {
  private final RefusalService refusalService;

  public RefusalReceiver(RefusalService refusalService) {
    this.refusalService = refusalService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "refusalInputChannel")
  public void receiveMessage(ResponseManagementEvent refusalEvent) {
    refusalService.processRefusal(refusalEvent);
  }
}
