package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
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
public class ReceiptProcessor {
  private static final Logger log = LoggerFactory.getLogger(ReceiptProcessor.class);
  public static final String QID_RECEIPTED = "QID Receipted";
  private static final String QID_NOT_FOUND_ERROR = "Qid not found error";
  private final CaseProcessor caseProcessor;
  private final UacQidLinkRepository uacQidLinkRepository;
  private final CaseRepository caseRepository;
  private final UacProcessor uacProcessor;
  private final EventLogger eventLogger;

  public ReceiptProcessor(
      CaseProcessor caseProcessor,
      UacQidLinkRepository uacQidLinkRepository,
      CaseRepository caseRepository,
      UacProcessor uacProcessor,
      EventLogger eventLogger) {
    this.caseProcessor = caseProcessor;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.caseRepository = caseRepository;
    this.uacProcessor = uacProcessor;
    this.eventLogger = eventLogger;
  }

  public void processReceipt(ResponseManagementEvent receiptEvent) {
    ReceiptDTO receipt = receiptEvent.getPayload().getReceipt();
    Optional<UacQidLink> uacQidLinkOpt =
        uacQidLinkRepository.findByQid(receipt.getQuestionnaireId());

    if (uacQidLinkOpt.isEmpty()) {
      log.error(QID_NOT_FOUND_ERROR);
      throw new RuntimeException();
    }

    UacQidLink uacQidLink = uacQidLinkOpt.get();
    Case caze = uacQidLink.getCaze();

    uacProcessor.emitUacUpdatedEvent(uacQidLink, caze, false);
    caze.setReceiptReceived(true);
    caseRepository.saveAndFlush(caze);
    caseProcessor.emitCaseUpdatedEvent(caze);
    eventLogger.logReceiptEvent(
        uacQidLink,
        QID_RECEIPTED,
        EventType.UAC_UPDATED,
        receipt,
        receiptEvent.getEvent(),
        receipt.getResponseDateTime());
  }
}
