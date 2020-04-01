package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
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
  private static final Logger log = LoggerFactory.getLogger(QuestionnaireLinkedService.class);
  private static final String QUESTIONNAIRE_LINKED = "Questionnaire Linked";

  private final UacService uacService;
  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final CaseReceiptService caseReceiptService;
  private final BlankQuestionnaireService blankQuestionnaireService;

  public QuestionnaireLinkedService(
      UacService uacService,
      CaseService caseService,
      EventLogger eventLogger,
      CaseReceiptService caseReceiptService,
      BlankQuestionnaireService blankQuestionnaireService) {
    this.uacService = uacService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.caseReceiptService = caseReceiptService;
    this.blankQuestionnaireService = blankQuestionnaireService;
  }

  public void processQuestionnaireLinked(
      ResponseManagementEvent questionnaireLinkedEvent, OffsetDateTime messageTimestamp) {
    UacDTO uac = questionnaireLinkedEvent.getPayload().getUac();
    String questionnaireId = uac.getQuestionnaireId();
    UacQidLink uacQidLink = uacService.findByQid(questionnaireId);

    logErrorIfQidIsLinkedToAnotherCase(uac, uacQidLink, uac.getCaseId());

    Case caze = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));

    if (isIndividualQuestionnaireType(questionnaireId) && caze.getCaseType().equals("HH")) {
      caze = caseService.prepareIndividualResponseCaseFromParentCase(caze, UUID.randomUUID());
      caze = caseService.saveNewCaseAndStampCaseRef(caze);
      caseService.emitCaseCreatedEvent(caze);
    }

    uacQidLink.setCaze(caze);

    if (!uacQidLink.isActive()) {
      if (uacQidLink.isBlankQuestionnaire()) {
        blankQuestionnaireService.handleBlankQuestionnaire(
            caze, uacQidLink, questionnaireLinkedEvent.getEvent().getType());
      } else {
        caseReceiptService.receiptCase(uacQidLink, questionnaireLinkedEvent.getEvent().getType());
      }
    }

    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

    eventLogger.logUacQidEvent(
        uacQidLink,
        questionnaireLinkedEvent.getEvent().getDateTime(),
        QUESTIONNAIRE_LINKED,
        EventType.QUESTIONNAIRE_LINKED,
        questionnaireLinkedEvent.getEvent(),
        convertObjectToJson(uac),
        messageTimestamp);
  }

  private void logErrorIfQidIsLinkedToAnotherCase(
      UacDTO uac, UacQidLink uacQidLink, String newLinkedCaseId) {
    if (uacQidLink.getCaze() != null
        && !uacQidLink.getCaze().getCaseId().equals(UUID.fromString(uac.getCaseId()))) {
      log.with("qid", uacQidLink.getQid())
          .with("previous_case_id", uacQidLink.getCaze().getCaseId())
          .with("new_case_id", newLinkedCaseId)
          .error(
              "Received QID link for QID that was previously linked to a different case, re-linking to new case");
    }
  }
}
