package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentConfirmedService;

@MessageEndpoint
public class FulfilmentConfirmedReceiver {
  private final FulfilmentConfirmedService fulfilmentConfirmedService;

  public FulfilmentConfirmedReceiver(FulfilmentConfirmedService fulfilmentConfirmedService) {
    this.fulfilmentConfirmedService = fulfilmentConfirmedService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "fulfilmentConfirmedInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    fulfilmentConfirmedService.processFulfilmentConfirmed(message.getPayload(), messageTimestamp);
  }
}
