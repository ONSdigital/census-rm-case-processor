package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

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
  private static final String DATETIME_NOT_PRESENT = "Date time not in event error";
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
    ReceiptDTO receiptPayload = receiptEvent.getPayload().getReceipt();
    Optional<UacQidLink> uacQidLinkOpt =
        uacQidLinkRepository.findByQid(receiptPayload.getQuestionnaireId());

    if (uacQidLinkOpt.isEmpty()) {
      log.error(QID_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Questionnaire Id '%s' not found!", receiptPayload.getQuestionnaireId()));
    }

    if (receiptEvent.getEvent().getDateTime() == null) {
      log.error(DATETIME_NOT_PRESENT);
      throw new RuntimeException(
          String.format(
              "Date time not found in fulfilment receipt request event for QID '%s",
              receiptPayload.getQuestionnaireId()));
    }

    UacQidLink uacQidLink = uacQidLinkOpt.get();
    uacQidLink.setActive(false);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    Case caze = uacQidLink.getCaze();
    caze.setReceiptReceived(true);
    caseRepository.saveAndFlush(caze);

    uacProcessor.emitUacUpdatedEvent(uacQidLink, caze, uacQidLink.isActive());
    caseProcessor.emitCaseUpdatedEvent(caze);

    eventLogger.logEvent(
        uacQidLink,
        QID_RECEIPTED,
        EventType.RESPONSE_RECEIVED,
        convertObjectToJson(receiptPayload),
        receiptEvent.getEvent());
  }
}
