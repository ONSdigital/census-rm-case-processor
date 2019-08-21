package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class ReceiptService {
  public static final String QID_RECEIPTED = "QID Receipted";
  private final CaseService caseService;
  private final UacService uacService;
  private final EventLogger eventLogger;

  public ReceiptService(CaseService caseService, UacService uacService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  public void processReceipt(ResponseManagementEvent receiptEvent) {
    ReceiptDTO receiptPayload = receiptEvent.getPayload().getReceipt();
    UacQidLink uacQidLink = uacService.findByQid(receiptPayload.getQuestionnaireId());
    uacQidLink.setActive(false);

    Case caze = uacQidLink.getCaze();
    caze.setReceiptReceived(true);

    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
    caseService.saveAndEmitCaseUpdatedEvent(caze);

    eventLogger.logUacQidEvent(
        uacQidLink,
        receiptEvent.getEvent().getDateTime(),
        QID_RECEIPTED,
        EventType.RESPONSE_RECEIVED,
        receiptEvent.getEvent(),
        convertObjectToJson(receiptPayload));
  }
}
