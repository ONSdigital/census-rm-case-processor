package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CCSPropertyDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class CCSPropertyListedService {

  private static final String CCS_ADDRESS_LISTED = "CCS Address Listed";
  private static final int CCS_INTERVIEWER_HOUSEHOLD_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES = 71;

  private final UacQidLinkRepository uacQidLinkRepository;
  private final CaseRepository caseRepository;
  private final UacService uacService;
  private final EventLogger eventLogger;
  private final CaseService caseService;

  @Value("${ccsconfig.action-plan-id}")
  private String actionPlanId;

  @Value("${ccsconfig.collection-exercise-id}")
  private String collectionExerciseId;

  public CCSPropertyListedService(
      UacQidLinkRepository uacQidLinkRepository,
      CaseRepository caseRepository,
      UacService uacService,
      EventLogger eventLogger,
      CaseService caseService) {
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.caseRepository = caseRepository;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public void processCCSPropertyListed(ResponseManagementEvent ccsPropertyListedEvent) {

    UacQidLink uacQidLink = uacService.buildCCSUacQidLink(CCS_INTERVIEWER_HOUSEHOLD_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    CCSPropertyDTO ccsProperty = ccsPropertyListedEvent.getPayload().getCcsProperty();
    String caseId = ccsProperty.getCollectionCase().getId();
    Case caze =
        caseService.saveCCSCase(
            caseId, ccsProperty.getSampleUnit(), actionPlanId, collectionExerciseId);

    List<UacQidLink> uacQidLinks = new LinkedList<>();
    uacQidLinks.add(uacQidLink);
    caze.setUacQidLinks(uacQidLinks);

    caseRepository.saveAndFlush(caze);

    uacQidLink.setCaze(caze);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    eventLogger.logCaseEvent(
        caze,
        OffsetDateTime.now(),
        CCS_ADDRESS_LISTED,
        EventType.CCS_ADDRESS_LISTED,
        ccsPropertyListedEvent.getEvent(),
        convertObjectToJson(ccsProperty));
  }
}
