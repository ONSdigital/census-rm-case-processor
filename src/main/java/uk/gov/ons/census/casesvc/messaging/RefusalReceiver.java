package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RefusalProcessor;

@MessageEndpoint
public class RefusalReceiver {
  private final RefusalProcessor refusalProcessor;

  public RefusalReceiver(RefusalProcessor refusalProcessor) {
    this.refusalProcessor = refusalProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "refusalInputChannel")
  public void receiveMessage(ResponseManagementEvent refusalEvent) {
    refusalProcessor.processRefusal(refusalEvent);
  }
}
