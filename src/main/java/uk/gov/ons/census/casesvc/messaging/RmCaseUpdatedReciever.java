package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RmCaseUpdatedService;

@MessageEndpoint
public class RmCaseUpdatedReciever {
  private final RmCaseUpdatedService rmCaseUpdatedService;

  public RmCaseUpdatedReciever(RmCaseUpdatedService rmCaseUpdatedService) {
    this.rmCaseUpdatedService = rmCaseUpdatedService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "rmCaseUpdatedInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    rmCaseUpdatedService.processMessage(message.getPayload(), messageTimestamp);
  }
}
