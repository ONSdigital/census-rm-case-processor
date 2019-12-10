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

    Case caze = uacQidLink.getCaze();

    // An unreceipt doesn't un-un-active a uacQidPair
    if (!receiptPayload.getUnreceipt()) {
      // Has this uacQidLink Already been set to unreceipted, if so log it and leave.
      if (uacQidLink.isBlankQuestionnaireReceived()) {
        return;
      }
      uacQidLink.setActive(false);
      uacQidLink.setReceipted(true);

      if (caze != null) {
        caze.setReceiptReceived(true);
      }
    } else {
      uacQidLink.setBlankQuestionnaireReceived(true);
      uacQidLink.setReceipted(false);

      if (hasCaseAlreadyBeenReceiptedByAnotherQid(caze, uacQidLink)) {
        uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
        return;
      }

      caze.setReceiptReceived(false);
    }

    if (isCCSQuestionnaireType(uacQidLink.getQid())) {
      uacService.saveUacQidLink(uacQidLink);
    } else {
      uacService.saveAndEmitUacUpdatedEvent(uacQidLink, receiptPayload.getUnreceipt());

      if (receiptEvent.getPayload().getResponse().getUnreceipt()) {
        fieldworkFollowupService.buildAndSendFieldWorkFollowUp(caze);
      }
    }

    saveAndEmitNonNullCase(receiptEvent, receiptPayload, caze);
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

  private void saveAndEmitNonNullCase(
      ResponseManagementEvent receiptEvent, ResponseDTO receiptPayload, Case caze) {
    if (caze != null) {
      if (caze.isCcsCase()) {
        caze.setReceiptReceived(true);
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

  private boolean hasCaseAlreadyBeenReceiptedByAnotherQid(
      Case caze, UacQidLink receivedUacQidLink) {
    return caze.getUacQidLinks().stream()
        .anyMatch(u -> u.isReceipted() && !u.getQid().equals(receivedUacQidLink.getQid()));
  }
}
