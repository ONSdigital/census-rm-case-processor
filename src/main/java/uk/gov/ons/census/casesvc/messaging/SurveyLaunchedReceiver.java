package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.SurveyService;

import java.time.OffsetDateTime;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

@MessageEndpoint
public class SurveyLaunchedReceiver {

  private final SurveyService surveyService;

  public SurveyLaunchedReceiver(SurveyService surveyService) {
    this.surveyService = surveyService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "surveyLaunchedInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    ResponseManagementEvent surveyEvent = message.getPayload();
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    surveyService.processMessage(surveyEvent, messageTimestamp);
  }
}
