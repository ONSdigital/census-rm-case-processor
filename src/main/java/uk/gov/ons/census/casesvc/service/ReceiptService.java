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

    // If the uacQidLink is already marked as a blank questionnaire, the uacQidLink is inactive and can't be changed
    if (uacQidLink.isBlankQuestionnaireReceived()) {
      return;
    }

    if (receiptPayload.isUnreceipt()) {
      processUnreceiptingEvent(uacQidLink, receiptEvent);
      return;
    }

    if (isCCSQuestionnaireType(uacQidLink.getQid())) {
      processCCSQid(uacQidLink, receiptEvent);
      return;
    }

    processReceiptForStandardCase(uacQidLink, receiptEvent);
  }

  private void processReceiptForStandardCase(
      UacQidLink uacQidLink, ResponseManagementEvent receiptEvent) {
    uacQidLink.setActive(false);

    uacService.saveAndEmitUacUpdatedEvent(uacQidLink, false);

    Case caze = uacQidLink.getCaze();

    if (caze != null) {
      caze.setReceiptReceived(true);
      caseService.saveAndEmitCaseUpdatedEvent(caze);
    } else {
      logUnlinkedUacQidLink(uacQidLink, receiptEvent);
    }
  }

  private void processCCSQid(UacQidLink uacQidLink, ResponseManagementEvent receiptEvent) {
    uacQidLink.setActive(false);

    uacService.saveUacQidLink(uacQidLink);

    Case caze = uacQidLink.getCaze();

    if (caze != null) {
      // Not currently testing for horror of unreceipted CCS? is this possible?

      caze.setReceiptReceived(true);
      caseService.saveCase(caze);
    } else {
      logUnlinkedUacQidLink(uacQidLink, receiptEvent);
    }
  }

  private void processUnreceiptingEvent(
      UacQidLink uacQidLink, ResponseManagementEvent receiptEvent) {

    uacQidLink.setBlankQuestionnaireReceived(true);
    uacService.saveAndEmitUacUpdatedEvent(uacQidLink, true);

    Case caze = uacQidLink.getCaze();

    if (caze != null) {
      if (hasCaseAlreadyBeenReceiptedByAnotherQidOrIsCaseNull(uacQidLink)) {
        return;
      }

      caze.setReceiptReceived(false);
      fieldworkFollowupService.buildAndSendFieldWorkFollowUp(caze);
      caseService.saveAndEmitCaseUpdatedEvent(caze);
    } else {
      log.with("qid", receiptEvent.getPayload().getResponse().getQuestionnaireId())
          .with("tx_id", receiptEvent.getEvent().getTransactionId())
          .with("channel", receiptEvent.getEvent().getChannel())
          .warn("UnReceipt received for unaddressed UAC/QID pair not yet linked to a case, ");
    }
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

  private void logUnlinkedUacQidLink(UacQidLink uacQidLink, ResponseManagementEvent receiptEvent) {}

  private boolean hasCaseAlreadyBeenReceiptedByAnotherQidOrIsCaseNull(
      UacQidLink receivedUacQidLink) {

    Case caze = receivedUacQidLink.getCaze();

    // If null then it's unlinked so we'll return true as we don't want to update the case, or lack
    // thereof
    if (caze == null) return true;

    // Check other qids linked to the case that might have receipted the case, in which case we don't want to unreceipt the case
    // Ignore the same qid
    // A valid receipting qid should be marked active = false, BlankQuestionnaireReceived = false
    return caze.getUacQidLinks().stream()
        .anyMatch(u -> !u.getQid().equals(receivedUacQidLink.getQid()) &&  !u.isActive() && !u.isBlankQuestionnaireReceived() );
  }
}
