package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.QuestionnaireLinkedProcessor;

@MessageEndpoint
public class QuestionnaireLinkedReceiver {
  private final QuestionnaireLinkedProcessor questionnaireLinkedProcessor;

  public QuestionnaireLinkedReceiver(QuestionnaireLinkedProcessor questionnaireLinkedProcessor) {
    this.questionnaireLinkedProcessor = questionnaireLinkedProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "questionnaireLinkedInputChannel")
  public void receiveMessage(ResponseManagementEvent questionnaireLinkedEvent) {
    questionnaireLinkedProcessor.processQuestionnaireLinked(questionnaireLinkedEvent);
  }
}
