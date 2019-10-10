package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CCSPropertyDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class CCSPropertyListedService {
  private static final String CCS_ADDRESS_LISTED = "CCS Address Listed";

  private final UacService uacService;
  private final EventLogger eventLogger;
  private final CaseService caseService;
  private final CcsToFieldService ccsToFieldService;
  private final UacQidLinkRepository uacQidLinkRepository;

  public CCSPropertyListedService(
      UacService uacService,
      EventLogger eventLogger,
      CaseService caseService,
      CcsToFieldService ccsToFieldService,
      UacQidLinkRepository uacQidLinkRepository) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
    this.ccsToFieldService = ccsToFieldService;
    this.uacQidLinkRepository = uacQidLinkRepository;
  }

  public void processCCSPropertyListed(ResponseManagementEvent ccsPropertyListedEvent) {
    CCSPropertyDTO ccsProperty = ccsPropertyListedEvent.getPayload().getCcsProperty();
    boolean isRefused = ccsProperty.getRefusal() != null;

    Case caze =
        caseService.createCCSCase(
            ccsProperty.getCollectionCase().getId(), ccsProperty.getSampleUnit(), isRefused);

    if (ccsProperty.getUac() == null) {
      uacService.createUacQidLinkedToCCSCase(caze);

      if (!caze.isRefusalReceived()) {
        ccsToFieldService.convertAndSendCCSToField(caze);
      }
    } else {
      UacQidLink uacQidLink = uacService.findByQid(ccsProperty.getUac().getQuestionnaireId());
      uacQidLink.setCaze(caze);
      uacQidLinkRepository.saveAndFlush(uacQidLink);
    }

    eventLogger.logCaseEvent(
        caze,
        ccsPropertyListedEvent.getEvent().getDateTime(),
        CCS_ADDRESS_LISTED,
        EventType.CCS_ADDRESS_LISTED,
        ccsPropertyListedEvent.getEvent(),
        convertObjectToJson(ccsProperty));
  }
}
