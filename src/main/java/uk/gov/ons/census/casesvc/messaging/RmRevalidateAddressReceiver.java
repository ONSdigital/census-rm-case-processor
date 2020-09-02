package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RmRevalidateAddressService;

@MessageEndpoint
public class RmRevalidateAddressReceiver {

  private final RmRevalidateAddressService rmRevalidateAddressService;

  public RmRevalidateAddressReceiver(RmRevalidateAddressService rmRevalidateAddressService) {
    this.rmRevalidateAddressService = rmRevalidateAddressService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "rmRevalidateAddressInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    rmRevalidateAddressService.processMessage(message.getPayload(), messageTimestamp);
  }
}
