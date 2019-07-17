package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.Refusal;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class RefusalProcessor {
  private static final Logger log = LoggerFactory.getLogger(RefusalProcessor.class);
  public static final String REFUSAL_RECEIVED = "REFUSAL_RECEIVED";
  private static final String CASE_NOT_FOUND_ERROR = "Failed to find case by receipt id";
  private static final String CASE_CREATED_EVENT_DESCRIPTION = "Case updated";
  private final CaseProcessor caseProcessor;
  private final CaseRepository caseRepository;
  private final UacQidLinkRepository uacQidLinkRepository;
  private final UacProcessor uacProcessor;

  public RefusalProcessor(
      CaseProcessor caseProcessor, CaseRepository caseRepository, UacProcessor uacProcessor,
      UacQidLinkRepository uacQidLinkRepository) {
    this.caseProcessor = caseProcessor;
    this.caseRepository = caseRepository;
    this.uacProcessor = uacProcessor;
    this.uacQidLinkRepository = uacQidLinkRepository;
  }

  public void processRefusal(Refusal refusal, Map<String, String> headers) {

    Optional<Case> cazeOpt = caseRepository.findByCaseId(UUID.fromString(refusal.getCaseId()));

    if (cazeOpt.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException();
    }

    Case caze = cazeOpt.get();
    caze.setRefusalReceived(true);
    caseRepository.saveAndFlush(caze);
    UacQidLink uacQidLink = caze.getUacQidLinks().get(0);
    PayloadDTO casePayloadDTO = caseProcessor.emitCaseUpdatedEvent(caze);
    uacProcessor.logEvent(
        uacQidLink,
        REFUSAL_RECEIVED,
        EventType.CASE_UPDATED,
        casePayloadDTO,
        headers,
        refusal.getResponseDateTime());
  }
}
