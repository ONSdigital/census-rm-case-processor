package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class ReceiptService {
  private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
  public static final String QID_RECEIPTED = "QID Receipted";
  private static final String QID_NOT_FOUND_ERROR = "Qid not found error";
  private final CaseService caseService;
  private final UacQidLinkRepository uacQidLinkRepository;
  private final CaseRepository caseRepository;
  private final UacService uacService;
  private final EventLogger eventLogger;

  public ReceiptService(
      CaseService caseService,
      UacQidLinkRepository uacQidLinkRepository,
      CaseRepository caseRepository,
      UacService uacService,
      EventLogger eventLogger) {
    this.caseService = caseService;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.caseRepository = caseRepository;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  public void processReceipt(ResponseManagementEvent receiptEvent) {
    ReceiptDTO receiptPayload = receiptEvent.getPayload().getReceipt();
    Optional<UacQidLink> uacQidLinkOpt =
        uacQidLinkRepository.findByQid(receiptPayload.getQuestionnaireId());

    if (uacQidLinkOpt.isEmpty()) {
      log.error(QID_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Questionnaire Id '%s' not found!", receiptPayload.getQuestionnaireId()));
    }

    UacQidLink uacQidLink = uacQidLinkOpt.get();
    uacQidLink.setActive(false);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    Case caze = uacQidLink.getCaze();
    caze.setReceiptReceived(true);
    caseRepository.saveAndFlush(caze);

    uacService.emitUacUpdatedEvent(uacQidLink, caze, uacQidLink.isActive());
    caseService.emitCaseUpdatedEvent(caze);

    eventLogger.logUacQidEvent(
        uacQidLink,
        receiptEvent.getEvent().getDateTime(),
        OffsetDateTime.now(),
        QID_RECEIPTED,
        EventType.RESPONSE_RECEIVED,
        receiptEvent.getEvent(),
        convertObjectToJson(receiptPayload));
  }
}
