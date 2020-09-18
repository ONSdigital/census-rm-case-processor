package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RmUnInvalidateAddressService;

@MessageEndpoint
public class RmUnInvalidateAddressReceiver {

  private final RmUnInvalidateAddressService rmUnInvalidateAddressService;

  public RmUnInvalidateAddressReceiver(RmUnInvalidateAddressService rmUnInvalidateAddressService) {
    this.rmUnInvalidateAddressService = rmUnInvalidateAddressService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "rmUnInvalidateAddressInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    rmUnInvalidateAddressService.processMessage(message.getPayload(), messageTimestamp);
  }
}
