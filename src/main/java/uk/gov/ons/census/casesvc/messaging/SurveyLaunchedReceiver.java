package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.SurveyService;

@MessageEndpoint
public class SurveyLaunchedReceiver {

  private final SurveyService surveyService;

  public SurveyLaunchedReceiver(SurveyService surveyService) {
    this.surveyService = surveyService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "surveyLaunchedInputChannel")
  public void receiveMessage(ResponseManagementEvent surveyEvent) {
    surveyService.processMessage(surveyEvent);
  }
}
