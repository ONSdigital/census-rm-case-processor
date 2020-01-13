package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
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
  @ServiceActivator(inputChannel = "invalidAddressInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    invalidAddressService.processMessage(message.getPayload(), messageTimestamp);
  }
}
