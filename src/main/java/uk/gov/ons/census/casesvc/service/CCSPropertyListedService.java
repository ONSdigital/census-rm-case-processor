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
  private final FieldworkFollowupService fieldworkFollowupService;
  private final UacQidLinkRepository uacQidLinkRepository;

  public CCSPropertyListedService(
      UacService uacService,
      EventLogger eventLogger,
      CaseService caseService,
      FieldworkFollowupService fieldworkFollowupService,
      UacQidLinkRepository uacQidLinkRepository) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
    this.fieldworkFollowupService = fieldworkFollowupService;
    this.uacQidLinkRepository = uacQidLinkRepository;
  }

  public void processCCSPropertyListed(ResponseManagementEvent ccsPropertyListedEvent) {
    CCSPropertyDTO ccsProperty = ccsPropertyListedEvent.getPayload().getCcsProperty();
    boolean isRefused = ccsProperty.getRefusal() != null;
    boolean isInvalidAddress = ccsProperty.getInvalidAddress() != null;
    boolean isQidProvided = ccsProperty.getUac() != null;

    Case caze =
        caseService.createCCSCase(
            ccsProperty.getCollectionCase().getId(),
            ccsProperty.getSampleUnit(),
            isRefused,
            isInvalidAddress);

    // always generate a new uac-qid pair even if linking existing pair, this is in case field
    // worker has to visit address again and launch an EQ
    uacService.createUacQidLinkedToCCSCase(caze);
    if (isQidProvided) {
      addUacLinkForQidAndCaze(ccsProperty.getUac().getQuestionnaireId(), caze);
    } else {
      sendActiveCCSCaseToField(caze);
    }

    eventLogger.logCaseEvent(
        caze,
        ccsPropertyListedEvent.getEvent().getDateTime(),
        CCS_ADDRESS_LISTED,
        EventType.CCS_ADDRESS_LISTED,
        ccsPropertyListedEvent.getEvent(),
        convertObjectToJson(ccsProperty));
  }

  private void sendActiveCCSCaseToField(Case caze) {
    if (!caze.isRefusalReceived() && !caze.isAddressInvalid()) {
      fieldworkFollowupService.buildAndSendFieldWorkFollowUp(caze, "CCS", false);
    }
  }

  private void addUacLinkForQidAndCaze(String qid, Case caze) {
    UacQidLink uacQidLink = uacService.findByQid(qid);
    uacQidLink.setCaze(caze);
    uacQidLinkRepository.saveAndFlush(uacQidLink);
  }
}
