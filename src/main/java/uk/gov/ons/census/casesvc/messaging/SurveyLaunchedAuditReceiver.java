package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
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
public class SurveyLaunchedAuditReceiver {

  private final EventLogger eventLogger;

  private final UacService uacService;

  public SurveyLaunchedAuditReceiver(EventLogger eventLogger, UacService uacService) {
    this.eventLogger = eventLogger;
    this.uacService = uacService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "surveyLaunchedInputChannel")
  public void receiveMessage(ResponseManagementEvent event) {
    if (event.getEvent().getType() == EventTypeDTO.SURVEY_LAUNCHED) {
      UacQidLink surveyLaunchedForQid =
          uacService.findByQid(event.getPayload().getReceipt().getQuestionnaireId());
      eventLogger.logUacQidEvent(
          surveyLaunchedForQid,
          OffsetDateTime.now(),
          "Survey launched",
          EventType.SURVEY_LAUNCHED,
          event.getEvent(),
          convertObjectToJson(event.getPayload()));
    } else {
      throw new RuntimeException(); // Unexpected event type received
    }
  }
}
