package uk.gov.ons.census.casesvc.service;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Component
public class SurveyService {
  private final UacService uacService;
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public SurveyService(UacService uacService, CaseService caseService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processMessage(ResponseManagementEvent surveyEvent, OffsetDateTime messageTimestamp) {

    if (!processEvent(surveyEvent, messageTimestamp)) {
      return;
    }

    UacQidLink surveyLaunchedForQid =
        uacService.findByQid(surveyEvent.getPayload().getResponse().getQuestionnaireId());

    Case caze = surveyLaunchedForQid.getCaze();

    if (caze != null && surveyEvent.getEvent().getChannel().equals("RH")) {
      caze.setSurveyLaunched(true);
      caseService.saveCaseAndEmitCaseUpdatedEvent(caze, null);
    }

    eventLogger.logUacQidEvent(
        surveyLaunchedForQid,
        surveyEvent.getEvent().getDateTime(),
        "Survey launched",
        EventType.SURVEY_LAUNCHED,
        surveyEvent.getEvent(),
        surveyEvent.getPayload().getResponse(),
        messageTimestamp);
  }

  private boolean processEvent(
      ResponseManagementEvent surveyEvent, OffsetDateTime messageTimestamp) {
    String logEventDescription;
    EventType logEventType;
    ResponseDTO logEventPayload;
    EventDTO event = surveyEvent.getEvent();

    switch (event.getType()) {
      case SURVEY_LAUNCHED:
        return true;

      case RESPONDENT_AUTHENTICATED:
        logEventDescription = "Respondent authenticated";
        logEventType = EventType.RESPONDENT_AUTHENTICATED;
        logEventPayload = surveyEvent.getPayload().getResponse();
        break;

      default:
        // Should never get here
        throw new RuntimeException(
            String.format("Event Type '%s' is invalid on this topic", event.getType()));
    }

    UacQidLink uacQidLink =
        uacService.findByQid(surveyEvent.getPayload().getResponse().getQuestionnaireId());

    eventLogger.logUacQidEvent(
        uacQidLink,
        surveyEvent.getEvent().getDateTime(),
        logEventDescription,
        logEventType,
        surveyEvent.getEvent(),
        logEventPayload,
        messageTimestamp);

    return false;
  }
}
