package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isCCSQuestionnaireType;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class ReceiptService {
  private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
  public static final String QID_RECEIPTED = "QID Receipted";
  private final CaseService caseService;
  private final UacService uacService;
  private final EventLogger eventLogger;
  private final FieldworkFollowupService fieldworkFollowupService;

  public ReceiptService(
      CaseService caseService,
      UacService uacService,
      EventLogger eventLogger,
      FieldworkFollowupService fieldworkFollowupService) {
    this.caseService = caseService;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.fieldworkFollowupService = fieldworkFollowupService;
  }

  public void processReceipt(ResponseManagementEvent receiptEvent) {
    ResponseDTO receiptPayload = receiptEvent.getPayload().getResponse();
    UacQidLink uacQidLink = uacService.findByQid(receiptPayload.getQuestionnaireId());

    logEvent(receiptEvent, receiptPayload, uacQidLink);

    // has this UacQidLink Already been linked as Blank, in which case just return
    if (uacQidLink.isBlankQuestionnaireReceived()) return;

    if (receiptPayload.isUnreceipt()) {
      uacQidLink.setBlankQuestionnaireReceived(true);
      uacQidLink.setReceipted(false);

      if (hasCaseAlreadyBeenReceiptedByAnotherQidOrIsCaseNull(uacQidLink)) {
        uacService.saveAndEmitUacUpdatedEvent(uacQidLink, true);
        return;
      }
    } else {
      uacQidLink.setActive(false);
      uacQidLink.setReceipted(true);
    }

    if (isCCSQuestionnaireType(uacQidLink.getQid())) {
      uacService.saveUacQidLink(uacQidLink);
    } else {
      uacService.saveAndEmitUacUpdatedEvent(uacQidLink, receiptPayload.isUnreceipt());

      if (receiptEvent.getPayload().getResponse().isUnreceipt()) {
        fieldworkFollowupService.buildAndSendFieldWorkFollowUp(uacQidLink.getCaze());
      }
    }

    saveAndEmitCaseOrLogIfCaseIsNull(receiptEvent, receiptPayload, uacQidLink.getCaze());
  }

  private void logEvent(
      ResponseManagementEvent receiptEvent, ResponseDTO receiptPayload, UacQidLink uacQidLink) {
    eventLogger.logUacQidEvent(
        uacQidLink,
        receiptEvent.getEvent().getDateTime(),
        QID_RECEIPTED,
        EventType.RESPONSE_RECEIVED,
        receiptEvent.getEvent(),
        convertObjectToJson(receiptPayload));
  }

  private void saveAndEmitCaseOrLogIfCaseIsNull(
      ResponseManagementEvent receiptEvent, ResponseDTO receiptPayload, Case caze) {
    if (caze != null) {
      caze.setReceiptReceived(!receiptPayload.isUnreceipt());

      if (caze.isCcsCase()) {
        caseService.saveCase(caze);
      } else {
        caseService.saveAndEmitCaseUpdatedEvent(caze);
      }
    } else {
      log.with("qid", receiptPayload.getQuestionnaireId())
          .with("tx_id", receiptEvent.getEvent().getTransactionId())
          .with("channel", receiptEvent.getEvent().getChannel())
          .warn("Receipt received for unaddressed UAC/QID pair not yet linked to a case");
    }
  }

  private boolean hasCaseAlreadyBeenReceiptedByAnotherQidOrIsCaseNull(
      UacQidLink receivedUacQidLink) {

    Case caze = receivedUacQidLink.getCaze();

    // If null then it's unlinked so we'll return true as we don't want to update the case, or lack
    // thereof
    if (caze == null) return true;

    return caze.getUacQidLinks().stream()
        .anyMatch(u -> u.isReceipted() && !u.getQid().equals(receivedUacQidLink.getQid()));
  }
}
