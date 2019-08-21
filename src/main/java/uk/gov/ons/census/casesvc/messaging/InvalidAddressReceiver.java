package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;

@MessageEndpoint
public class InvalidAddressReceiver {
  private final InvalidAddressService invalidAddressService;

  public InvalidAddressReceiver(InvalidAddressService invalidAddressService) {
    this.invalidAddressService = invalidAddressService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "refusalInputChannel")
  public void receiveMessage(ResponseManagementEvent invalidAddressEvent) {
    invalidAddressService.processMessage(invalidAddressEvent);
  }
}
