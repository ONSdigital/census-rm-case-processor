package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.model.dto.Receipt;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class ReceiptProcessor {
  private static final Logger log = LoggerFactory.getLogger(ReceiptProcessor.class);
  public static final String QID_RECEIPTED = "QID Receipted";
  private static final String CASE_NOT_FOUND_ERROR = "Failed to find case by receipt id";
  private static final String CASE_CREATED_EVENT_DESCRIPTION = "Case updated";
  private final CaseProcessor caseProcessor;
  private final CaseRepository caseRepository;
  private final UacProcessor uacProcessor;

  public ReceiptProcessor(CaseProcessor caseProcessor, CaseRepository caseRepository, UacProcessor uacProcessor) {
    this.caseProcessor = caseProcessor;
    this.caseRepository = caseRepository;
    this.uacProcessor = uacProcessor;
  }

  public void processReceipt(Receipt receipt) {
    // HERE BE DRAGONS, THIS IS A HACK.  IN THE LONG RUN WE WILL RECEIVE JUST A QID
    // HOWEVER THIS CODE IS WRITTEN IN A WAY TO MAKE THE PROMISED LAND OF RECEIVING A QID EASY
    // JUST HAVE A QIDREPOSITORY RATHER THAN A CASE RESPOSITORY AND WORK OF THAT (AND THE QID)

    Optional<Case> cazeOpt = caseRepository.findByCaseId(UUID.fromString(receipt.getCaseId()));

    if (cazeOpt.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException();
    }

    // This nice long path and the 'random' get(0) will dissapear when we get QID
    UacQidLink uacQidLink = cazeOpt.get().getUacQidLinks().get(0);
    uacProcessor.emitUacUpdatedEvent(uacQidLink, cazeOpt.get(), false);
    caseRepository.setReceiptReceived(UUID.fromString(receipt.getCaseId()));
    caseProcessor.emitCaseUpdatedEvent(cazeOpt.get());
    uacProcessor.logEvent(uacQidLink, QID_RECEIPTED, EventType.UAC_UPDATED, receipt.getResponseDateTime());
  }
}
