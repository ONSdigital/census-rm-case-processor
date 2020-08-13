package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class QidReceiptService {

  private static final Logger log = LoggerFactory.getLogger(QidReceiptService.class);
  public static final String QID_RECEIPTED = "QID Receipted";
  public static final String BLANK_QUESTIONNAIRE_RECEIVED = "Blank questionnaire received";
  private final UacService uacService;
  private final EventLogger eventLogger;
  private final CaseReceiptService caseReceiptService;
  private final BlankQuestionnaireService blankQuestionnaireService;

  public QidReceiptService(
      UacService uacService,
      EventLogger eventLogger,
      CaseReceiptService caseReceiptService,
      BlankQuestionnaireService blankQuestionnaireService) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseReceiptService = caseReceiptService;
    this.blankQuestionnaireService = blankQuestionnaireService;
  }

  public void processReceipt(
      ResponseManagementEvent receiptEvent, OffsetDateTime messageTimestamp) {
    ResponseDTO receiptPayload = receiptEvent.getPayload().getResponse();
    UacQidLink uacQidLink = uacService.findByQid(receiptPayload.getQuestionnaireId());
    if (receiptPayload.getUnreceipt()) {
      processUnreceipt(receiptEvent, messageTimestamp, uacQidLink);
      return;
    }

    if (uacQidLink.isActive()) {
      uacQidLink.setActive(false);
      uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

      Case caze = uacQidLink.getCaze();

      if (caze != null) {
        caseReceiptService.receiptCase(uacQidLink, receiptEvent.getEvent());
      } else {
        log.with("qid", receiptPayload.getQuestionnaireId())
            .with("tx_id", receiptEvent.getEvent().getTransactionId())
            .with("channel", receiptEvent.getEvent().getChannel())
            .warn("Receipt received for unaddressed UAC/QID pair not yet linked to a case");
      }
    }

    eventLogger.logUacQidEvent(
        uacQidLink,
        receiptEvent.getEvent().getDateTime(),
        QID_RECEIPTED,
        EventType.RESPONSE_RECEIVED,
        receiptEvent.getEvent(),
        convertObjectToJson(receiptPayload),
        messageTimestamp);
  }

  public void processUnreceipt(
      ResponseManagementEvent unreceiptEvent,
      OffsetDateTime messageTimestamp,
      UacQidLink uacQidLink) {

    ResponseDTO receiptPayload = unreceiptEvent.getPayload().getResponse();

    if (!uacQidLink.isActive() && hasEqReceipt(uacQidLink)) {
      // If we have an EQ receipt for this QID then we must have a response for the case so we do
      // not want to action the unreceipt, we just log it

      log.with("qid", uacQidLink.getQid())
          .with("tx_id", unreceiptEvent.getEvent().getTransactionId())
          .with("channel", unreceiptEvent.getEvent().getChannel())
          .warn("Unreceipt received for QID which was already receipted via EQ");
    } else {

      handleBlankQuestionnaire(unreceiptEvent, uacQidLink, receiptPayload);
    }

    eventLogger.logUacQidEvent(
        uacQidLink,
        unreceiptEvent.getEvent().getDateTime(),
        BLANK_QUESTIONNAIRE_RECEIVED,
        EventType.RESPONSE_RECEIVED,
        unreceiptEvent.getEvent(),
        convertObjectToJson(receiptPayload),
        messageTimestamp);
  }

  private void handleBlankQuestionnaire(
      ResponseManagementEvent unreceiptEvent, UacQidLink uacQidLink, ResponseDTO receiptPayload) {
    uacQidLink.setActive(false);
    uacQidLink.setBlankQuestionnaire(true);

    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

    Case caze = uacQidLink.getCaze();

    if (caze != null) {
      blankQuestionnaireService.handleBlankQuestionnaire(
          caze, uacQidLink, unreceiptEvent.getEvent().getType());
    } else {
      log.with("qid", receiptPayload.getQuestionnaireId())
          .with("tx_id", unreceiptEvent.getEvent().getTransactionId())
          .with("channel", unreceiptEvent.getEvent().getChannel())
          .warn("Unreceipt received for unaddressed UAC/QID pair not yet linked to a case");
    }
  }

  private boolean hasEqReceipt(UacQidLink uacQidLink) {
    if (uacQidLink.getEvents() == null) {
      return false;
    }

    for (Event event : uacQidLink.getEvents()) {
      if (event.getEventType() == EventType.RESPONSE_RECEIVED
          && event.getEventChannel().equalsIgnoreCase("EQ")) {
        return true;
      }
    }

    return false;
  }
}
