package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;

import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class QuestionnaireLinkedService {
  private static final String QUESTIONNAIRE_LINKED = "Questionnaire Linked";

  private final UacService uacService;
  private final CaseService caseService;
  private final FulfilmentRequestService fulfilmentRequestService;
  private final EventLogger eventLogger;

  public QuestionnaireLinkedService(
      UacService uacService,
      CaseService caseService,
      FulfilmentRequestService fulfilmentRequestService,
      EventLogger eventLogger) {
    this.uacService = uacService;
    this.caseService = caseService;
    this.fulfilmentRequestService = fulfilmentRequestService;
    this.eventLogger = eventLogger;
  }

  public void processQuestionnaireLinked(ResponseManagementEvent questionnaireLinkedEvent) {
    UacDTO uac = questionnaireLinkedEvent.getPayload().getUac();
    String questionnaireId = uac.getQuestionnaireId();
    UacQidLink uacQidLink = uacService.findByQid(questionnaireId);
    Case caze;

    if (isIndividualQuestionnaireType(questionnaireId)) {
      Case householdCase = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));

      caze = fulfilmentRequestService.prepareIndividualResponseCaseFromParentCase(householdCase);

      caseService.saveAndEmitCaseCreatedEvent(caze);
    } else {
      caze = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));
    }

    // If UAC/QID has been receipted before case, update case
    if (!uacQidLink.isActive() && !caze.isReceiptReceived()) {
      caze.setReceiptReceived(true);
      caseService.saveAndEmitCaseUpdatedEvent(caze);
    }

    uacQidLink.setCaze(caze);
    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

    eventLogger.logUacQidEvent(
        uacQidLink,
        questionnaireLinkedEvent.getEvent().getDateTime(),
        QUESTIONNAIRE_LINKED,
        EventType.QUESTIONNAIRE_LINKED,
        questionnaireLinkedEvent.getEvent(),
        convertObjectToJson(uac));
  }
}
