package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CCSPropertyDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class CCSPropertyListedService {

  private static final String CCS_ADDRESS_LISTED = "CCS Address Listed";
  private static final int CCS_INTERVIEWER_HOUSEHOLD_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES = 71;

  private final UacService uacService;
  private final EventLogger eventLogger;
  private final CaseService caseService;
  private final CcsToFieldService ccsToFieldService;

  @Value("${ccsconfig.action-plan-id}")
  private String actionPlanId;

  @Value("${ccsconfig.collection-exercise-id}")
  private String collectionExerciseId;

  public CCSPropertyListedService(
      UacService uacService,
      EventLogger eventLogger,
      CaseService caseService,
      CcsToFieldService ccsToFieldService) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
    this.ccsToFieldService = ccsToFieldService;
  }

  public void processCCSPropertyListed(ResponseManagementEvent ccsPropertyListedEvent) {
    CCSPropertyDTO ccsProperty = ccsPropertyListedEvent.getPayload().getCcsProperty();
    String caseId = ccsProperty.getCollectionCase().getId();

    Case caze =
        caseService.buildCCSCase(
            caseId, ccsProperty.getSampleUnit(), actionPlanId, collectionExerciseId);

    UacQidLink uacQidLink =
        uacService.buildCCSUacQidLink(
            CCS_INTERVIEWER_HOUSEHOLD_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES);

    caze = caseService.saveCCSCaseWithUacQidLink(caze, uacQidLink);

    eventLogger.logCaseEvent(
        caze,
        OffsetDateTime.now(),
        CCS_ADDRESS_LISTED,
        EventType.CCS_ADDRESS_LISTED,
        ccsPropertyListedEvent.getEvent(),
        convertObjectToJson(ccsProperty));

    ccsToFieldService.convertAndSendCCSToField(caze);
  }
}
