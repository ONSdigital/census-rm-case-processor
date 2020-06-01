package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FieldCaseUpdatedService;

@MessageEndpoint
public class FieldCaseUpdatedReceiver {

  private final FieldCaseUpdatedService fieldCaseUpdatedService;

  public FieldCaseUpdatedReceiver(FieldCaseUpdatedService fieldCaseUpdatedService) {
    this.fieldCaseUpdatedService = fieldCaseUpdatedService;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @ServiceActivator(inputChannel = "fieldCaseUpdatedInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    fieldCaseUpdatedService.processFieldCaseUpdatedEvent(message.getPayload(), messageTimestamp);
  }

}