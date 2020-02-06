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
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class ReceiptService {

  private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
  public static final String QID_RECEIPTED = "QID Receipted";
  private final UacService uacService;
  private final EventLogger eventLogger;
  private final CaseReceipter caseReceipter;

  public ReceiptService(
      UacService uacService, EventLogger eventLogger, CaseReceipter caseReceipter) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseReceipter = caseReceipter;
  }

  public void processReceipt(
      ResponseManagementEvent receiptEvent, OffsetDateTime messageTimestamp) {
    ResponseDTO receiptPayload = receiptEvent.getPayload().getResponse();
    UacQidLink uacQidLink = uacService.findByQid(receiptPayload.getQuestionnaireId());
    uacQidLink.setActive(false);

    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

    Case caze = uacQidLink.getCaze();

    if (caze != null) {
      caseReceipter.handleReceipting(caze, uacQidLink);
    } else {
      log.with("qid", receiptPayload.getQuestionnaireId())
          .with("tx_id", receiptEvent.getEvent().getTransactionId())
          .with("channel", receiptEvent.getEvent().getChannel())
          .warn("Receipt received for unaddressed UAC/QID pair not yet linked to a case");
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
}
