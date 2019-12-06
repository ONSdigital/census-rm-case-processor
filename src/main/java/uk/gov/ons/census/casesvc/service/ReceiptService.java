package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isCCSQuestionnaireType;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.FieldWorkFollowup;
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
  private RabbitTemplate rabbitTemplate;

  @Value("${queueconfig.outbound-field-exchange}")
  private String outboundExchange;

  @Value("${queueconfig.field-binding}")
  private String actionFieldBinding;

  public ReceiptService(CaseService caseService, UacService uacService, EventLogger eventLogger, RabbitTemplate rabbitTemplate) {
    this.caseService = caseService;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.rabbitTemplate = rabbitTemplate;
  }

  public void processReceipt(ResponseManagementEvent receiptEvent) {
    ResponseDTO receiptPayload = receiptEvent.getPayload().getResponse();
    UacQidLink uacQidLink = uacService.findByQid(receiptPayload.getQuestionnaireId());
    uacQidLink.setActive(false);

    Case caze = uacQidLink.getCaze();

    if (caze != null) {
      caze.setReceiptReceived(true);
    }

    if (isCCSQuestionnaireType(uacQidLink.getQid())) {
      uacService.saveUacQidLink(uacQidLink);
    } else {
      uacService.saveAndEmitUacUpdatedEvent(uacQidLink, receiptEvent.getPayload().getResponse().getUnreceipt());

      ifIUnreceiptedNeedsNewFieldWorkFolloup(caze,receiptEvent.getPayload().getResponse().getUnreceipt());
    }

    if (caze != null) {
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

    eventLogger.logUacQidEvent(
        uacQidLink,
        receiptEvent.getEvent().getDateTime(),
        QID_RECEIPTED,
        EventType.RESPONSE_RECEIVED,
        receiptEvent.getEvent(),
        convertObjectToJson(receiptPayload));
  }

  private void ifIUnreceiptedNeedsNewFieldWorkFolloup(Case caze, boolean unreceipted) {
     if( !unreceipted )
       return;

     // in future look at case table and use black magic to decide if it needs a followup.

     //buildAFieldworkFollowup
    FieldWorkFollowup followup = buildFieldworkFollowup(caze);

    //sendfieldworkFollowup
    rabbitTemplate.convertAndSend(
            outboundExchange,actionFieldBinding, followup);

    caze.setReceiptReceived(false);

  }

  public FieldWorkFollowup buildFieldworkFollowup(Case caze) {

    FieldWorkFollowup followup = new FieldWorkFollowup();
    followup.setAddressLine1(caze.getAddressLine1());
    followup.setAddressLine2(caze.getAddressLine2());
    followup.setAddressLine3(caze.getAddressLine3());
    followup.setTownName(caze.getTownName());
    followup.setPostcode(caze.getPostcode());
    followup.setEstabType(caze.getEstabType());
    followup.setOrganisationName(caze.getOrganisationName());
    followup.setArid(caze.getArid());
    followup.setUprn(caze.getUprn());
    followup.setOa(caze.getOa());
    followup.setArid(caze.getArid());
    followup.setLatitude(caze.getLatitude());
    followup.setLongitude(caze.getLongitude());
    followup.setActionPlan(caze.getActionPlanId());
    followup.setActionType("dummy");
    followup.setCaseId(caze.getCaseId().toString());
    followup.setCaseRef(Integer.toString(caze.getCaseRef()));
    followup.setAddressType(caze.getAddressType());
    followup.setAddressLevel(caze.getAddressLevel());
    followup.setTreatmentCode(caze.getTreatmentCode());
    followup.setFieldOfficerId(caze.getFieldOfficerId());
    followup.setFieldCoordinatorId(caze.getFieldCoordinatorId());
    followup.setCeExpectedCapacity(caze.getCeExpectedCapacity());
    followup.setUndeliveredAsAddress(caze.isUndeliveredAsAddressed());

    // TODO: set surveyName, undeliveredAsAddress and blankQreReturned from caze
    followup.setSurveyName("CENSUS");
    followup.setBlankQreReturned(true);

    // TODO: ccsQuestionnaireUrl, ceDeliveryReqd,
    // ceCE1Complete, ceActualResponses

    return followup;
  }
}
