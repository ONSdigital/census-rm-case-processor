package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
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
  public void receiveMessage(ResponseManagementEvent questionnaireLinkedEvent) {
    questionnaireLinkedService.processQuestionnaireLinked(questionnaireLinkedEvent);
  }
}
