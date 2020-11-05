package uk.gov.ons.census.casesvc.service;

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

    logUnlinkedCaseEventAgainstOriginalCaseIfQidIsNowLinkedToNewCase(
        messageTimestamp, questionnaireLinkedEvent, uac, uacQidLink);

    Case caze = caseService.getCaseByCaseId(uac.getCaseId());

    if (checkRequiredFieldsForIndividualHI(questionnaireId, caze.getCaseType())) {
      if (uac.getIndividualCaseId() == null) {
        caze =
            caseService.prepareIndividualResponseCaseFromParentCase(
                caze, UUID.randomUUID(), questionnaireLinkedEvent.getEvent().getChannel());
      } else {
        caze =
            caseService.prepareIndividualResponseCaseFromParentCase(
                caze, uac.getIndividualCaseId(), questionnaireLinkedEvent.getEvent().getChannel());
      }
      caze = caseService.saveNewCaseAndStampCaseRef(caze);
      caseService.emitCaseCreatedEvent(caze);
    }

    uacQidLink.setCaze(caze);

    if (!uacQidLink.isActive()) {
      if (uacQidLink.isBlankQuestionnaire()) {
        blankQuestionnaireService.handleBlankQuestionnaire(
            caze, uacQidLink, questionnaireLinkedEvent.getEvent().getType());
      } else {
        caseReceiptService.receiptCase(uacQidLink, questionnaireLinkedEvent.getEvent());
      }
    }

    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

    eventLogger.logUacQidEvent(
        uacQidLink,
        questionnaireLinkedEvent.getEvent().getDateTime(),
        QUESTIONNAIRE_LINKED,
        EventType.QUESTIONNAIRE_LINKED,
        questionnaireLinkedEvent.getEvent(),
        uac,
        messageTimestamp);
  }

  private void logUnlinkedCaseEventAgainstOriginalCaseIfQidIsNowLinkedToNewCase(
      OffsetDateTime messageTimestamp,
      ResponseManagementEvent questionnaireLinkedEvent,
      UacDTO uac,
      UacQidLink uacQidLink) {
    if (uacQidLink.getCaze() != null && !uacQidLink.getCaze().getCaseId().equals(uac.getCaseId())) {
      String eventDescription =
          String.format("Questionnaire unlinked from case with QID %s", uac.getQuestionnaireId());
      eventLogger.logCaseEvent(
          uacQidLink.getCaze(),
          questionnaireLinkedEvent.getEvent().getDateTime(),
          eventDescription,
          EventType.QUESTIONNAIRE_UNLINKED,
          questionnaireLinkedEvent.getEvent(),
          uac,
          messageTimestamp);
    }
  }

  private boolean checkRequiredFieldsForIndividualHI(String questionnaireId, String caseType) {
    return (isIndividualQuestionnaireType(questionnaireId) && caseType.equals("HH"));
  }
}
