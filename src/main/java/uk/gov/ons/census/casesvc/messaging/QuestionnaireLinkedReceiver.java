package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.QuestionnaireLinkedService;

@MessageEndpoint
public class QuestionnaireLinkedReceiver {
  private final QuestionnaireLinkedService questionnaireLinkedService;

  public QuestionnaireLinkedReceiver(QuestionnaireLinkedService questionnaireLinkedService) {
    this.questionnaireLinkedService = questionnaireLinkedService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "questionnaireLinkedInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    questionnaireLinkedService.processQuestionnaireLinked(message.getPayload(), messageTimestamp);
  }
}
