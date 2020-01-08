package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RefusalService;

import java.time.OffsetDateTime;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

@MessageEndpoint
public class RefusalReceiver {
  private final RefusalService refusalService;

  public RefusalReceiver(RefusalService refusalService) {
    this.refusalService = refusalService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "refusalInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    ResponseManagementEvent refusalEvent = message.getPayload();
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    refusalService.processRefusal(refusalEvent, messageTimestamp);
  }
}
