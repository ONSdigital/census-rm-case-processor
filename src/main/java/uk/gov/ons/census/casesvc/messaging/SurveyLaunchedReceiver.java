package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacService;

@MessageEndpoint
public class SurveyLaunchedReceiver {

  public static final String SURVEY_LAUNCHED = "Survey launched";

  private final EventLogger eventLogger;

  private final UacService uacService;

  public SurveyLaunchedReceiver(EventLogger eventLogger, UacService uacService) {
    this.eventLogger = eventLogger;
    this.uacService = uacService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "surveyLaunchedInputChannel")
  public void receiveMessage(ResponseManagementEvent event) {
    if (event.getEvent().getType() != EventTypeDTO.SURVEY_LAUNCHED) throw new RuntimeException();

    UacQidLink surveyLaunchedForQid =
        uacService.findByQid(event.getPayload().getResponse().getQuestionnaireId());

    eventLogger.logUacQidEvent(
        surveyLaunchedForQid,
        event.getEvent().getDateTime(),
        SURVEY_LAUNCHED,
        EventType.SURVEY_LAUNCHED,
        event.getEvent(),
        convertObjectToJson(event.getPayload()));
  }
}
