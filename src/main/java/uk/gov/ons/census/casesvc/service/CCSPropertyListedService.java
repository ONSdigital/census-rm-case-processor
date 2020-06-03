package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
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
  private final UacQidLinkRepository uacQidLinkRepository;

  public CCSPropertyListedService(
      UacService uacService,
      EventLogger eventLogger,
      CaseService caseService,
      UacQidLinkRepository uacQidLinkRepository) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
    this.uacQidLinkRepository = uacQidLinkRepository;
  }

  public void processCCSPropertyListed(
      ResponseManagementEvent ccsPropertyListedEvent, OffsetDateTime messageTimestamp) {
    CCSPropertyDTO ccsProperty = ccsPropertyListedEvent.getPayload().getCcsProperty();
    boolean isInvalidAddress = ccsProperty.getInvalidAddress() != null;
    boolean hasOneOrMoreQids = ccsProperty.getUac() != null;
    RefusalType refusal = null;

    if (ccsProperty.getRefusal() != null) {
      if (ccsProperty.getRefusal().getType() != RefusalType.EXTRAORDINARY_REFUSAL
          && ccsProperty.getRefusal().getType() != RefusalType.HARD_REFUSAL) {
        throw new RuntimeException("Unexpected refusal type" + ccsProperty.getRefusal().getType());
      }
      refusal = ccsProperty.getRefusal().getType();
    }

    Case caze =
        caseService.createCCSCase(
            ccsProperty.getCollectionCase().getId(),
            ccsProperty.getSampleUnit(),
            refusal,
            isInvalidAddress);

    // always generate a new uac-qid pair even if linking existing pair, this is in case field
    // worker has to visit address again and launch an EQ
    uacService.createUacQidLinkedToCCSCase(caze);
    if (hasOneOrMoreQids) {
      handleUacQidLinksForCase(ccsProperty.getUac(), caze);
    } else {
      sendActiveCCSCaseToField(caze);
    }

    eventLogger.logCaseEvent(
        caze,
        ccsPropertyListedEvent.getEvent().getDateTime(),
        CCS_ADDRESS_LISTED,
        EventType.CCS_ADDRESS_LISTED,
        ccsPropertyListedEvent.getEvent(),
        convertObjectToJson(ccsProperty),
        messageTimestamp);
  }

  private void handleUacQidLinksForCase(List<UacDTO> qids, Case caze) {
    for (UacDTO qid : qids) {
      addUacLinkForQidAndCase(qid.getQuestionnaireId(), caze);
    }
  }

  private void sendActiveCCSCaseToField(Case caze) {
    if (caze.getRefusalReceived() == null && !caze.isAddressInvalid()) {
      caseService.saveCaseAndEmitCaseCreatedEvent(
          caze, buildMetadata(EventTypeDTO.CCS_ADDRESS_LISTED, ActionInstructionType.CREATE));
    }
  }

  private void addUacLinkForQidAndCase(String qid, Case caze) {
    UacQidLink uacQidLink = uacService.findByQid(qid);
    uacQidLink.setCaze(caze);
    uacQidLinkRepository.saveAndFlush(uacQidLink);
  }
}
