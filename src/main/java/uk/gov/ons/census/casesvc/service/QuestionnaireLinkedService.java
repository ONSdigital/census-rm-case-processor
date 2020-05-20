package uk.gov.ons.census.casesvc.service;

import static org.springframework.util.StringUtils.isEmpty;
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

    logUnlinkedCaseEventAgainstOriginalCaseIfQidIsNowLinkedToNewCase(
        messageTimestamp, questionnaireLinkedEvent, uac, uacQidLink);

    Case caze = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));

    if (checkRequiredFieldsForIndividualHI(
        questionnaireId, caze.getCaseType(), uac.getIndividualCaseId())) {
      caze =
          caseService.prepareIndividualResponseCaseFromParentCase(
              caze, UUID.fromString(uac.getIndividualCaseId()));
      caze = caseService.saveNewCaseAndStampCaseRef(caze);
      caseService.emitCaseCreatedEvent(caze);
    } else {
      throwIllegalArgumentExceptionIfIndQIDAndIndCaseIdPresentButCaseTypeNotHH(
          caze, questionnaireId, uac.getIndividualCaseId());

      throwIllegalArgumentExceptionIfIndQidAndCaseTypeHHButIndCaseIdNotPresent(
          caze, questionnaireId, uac.getIndividualCaseId());
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
        convertObjectToJson(uac),
        messageTimestamp);
  }

  private void logUnlinkedCaseEventAgainstOriginalCaseIfQidIsNowLinkedToNewCase(
      OffsetDateTime messageTimestamp,
      ResponseManagementEvent questionnaireLinkedEvent,
      UacDTO uac,
      UacQidLink uacQidLink) {
    if (uacQidLink.getCaze() != null
        && !uacQidLink.getCaze().getCaseId().equals(UUID.fromString(uac.getCaseId()))) {
      String eventDescription =
          String.format("Questionnaire unlinked from case with QID %s", uac.getQuestionnaireId());
      eventLogger.logCaseEvent(
          uacQidLink.getCaze(),
          questionnaireLinkedEvent.getEvent().getDateTime(),
          eventDescription,
          EventType.QUESTIONNAIRE_UNLINKED,
          questionnaireLinkedEvent.getEvent(),
          convertObjectToJson(uac),
          messageTimestamp);
    }
  }

  private boolean checkRequiredFieldsForIndividualHI(
      String questionnaireId, String caseType, String individualCaseId) {
    return (isIndividualQuestionnaireType(questionnaireId)
        && caseType.equals("HH")
        && individualCaseId != null);
  }

  private void throwIllegalArgumentExceptionIfIndQIDAndIndCaseIdPresentButCaseTypeNotHH(
      Case caze, String questionnaireId, String individualCaseId) {
    if (isIndividualQuestionnaireType(questionnaireId)
        && !caze.getCaseType().equals("HH")
        && individualCaseId != null) {
      throw new IllegalArgumentException(
          String.format(
              "CaseType on '%s' not HH where QID is individual on PQ link request where individualCaseId provided",
              caze.getCaseId()));
    }
  }

  private void throwIllegalArgumentExceptionIfIndQidAndCaseTypeHHButIndCaseIdNotPresent(
      Case caze, String questionnaireId, String individualCaseId) {
    if (isIndividualQuestionnaireType(questionnaireId)
        && caze.getCaseType().equals("HH")
        && isEmpty(individualCaseId)) {
      throw new IllegalArgumentException(
          String.format(
              "No individualCaseId present on PQ link request where QID is individual and caseType for '%s' is HH",
              caze.getCaseId()));
    }
  }
}
