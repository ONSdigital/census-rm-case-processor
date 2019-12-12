package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isCCSQuestionnaireType;
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
  private final EventLogger eventLogger;

  public QuestionnaireLinkedService(
          UacService uacService, CaseService caseService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processQuestionnaireLinked(ResponseManagementEvent questionnaireLinkedEvent) {
    UacDTO uacDTO = questionnaireLinkedEvent.getPayload().getUac();
    UacQidLink uacQidLink = uacService.findByQid(uacDTO.getQuestionnaireId());

    checkQidNotLinkedToAnotherCase(uacDTO, uacQidLink);

    logEvent(questionnaireLinkedEvent, uacQidLink);

    Case caze = getaCase(uacDTO);
    uacQidLink.setCaze(caze);

    if (uacQidLink.isBlankQuestionnaireReceived()) {
      processBlankQuestionnaireLinked(uacQidLink, caze);
      return;
    }

    if (caze.isCcsCase()) {
      processCCSCaseLinking(caze, uacQidLink);
      return;
    }

    processStandardLinking(uacQidLink, caze);
  }

  private void processStandardLinking(UacQidLink uacQidLink, Case caze) {
    // If UAC/QID has been receipted and the case hasn't, update the case
    if (!uacQidLink.isActive() && !caze.isReceiptReceived()) {
      caze.setReceiptReceived(true);
      caseService.saveAndEmitCaseUpdatedEvent(caze);
    }

    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
  }

  private void processCCSCaseLinking(Case caze, UacQidLink uacQidLink) {
    uacService.saveUacQidLink(uacQidLink);

    if (caze.isReceiptReceived() || uacQidLink.isActive()) {
      return;
    }

    caze.setReceiptReceived(true);
    caseService.saveCase(caze);
  }

  private Case getaCase(UacDTO uac) {
    Case caze;

    if (isIndividualQuestionnaireType(uac.getQuestionnaireId())) {
      Case householdCase = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));
      caze = caseService.prepareIndividualResponseCaseFromParentCase(householdCase);

      caseService.emitCaseCreatedEvent(caze);
    } else {
      caze = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));
    }
    return caze;
  }

  private void processBlankQuestionnaireLinked(UacQidLink uacQidLink, Case caze) {

    if (isCCSQuestionnaireType(uacQidLink.getQid())) {
      uacService.saveUacQidLink(uacQidLink);
    } else {
      uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
    }

  }

  public void logEvent(ResponseManagementEvent questionnaireLinkedEvent, UacQidLink uacQidLink) {
    eventLogger.logUacQidEvent(
            uacQidLink,
            questionnaireLinkedEvent.getEvent().getDateTime(),
            QUESTIONNAIRE_LINKED,
            EventType.QUESTIONNAIRE_LINKED,
            questionnaireLinkedEvent.getEvent(),
            convertObjectToJson(questionnaireLinkedEvent.getPayload().getUac()));
  }

  private void checkQidNotLinkedToAnotherCase(UacDTO uac, UacQidLink uacQidLink) {
    if (uacQidLink.getCaze() != null
            && !uacQidLink.getCaze().getCaseId().equals(UUID.fromString(uac.getCaseId()))) {
      throw new RuntimeException(
              "UacQidLink already linked to case id: " + uacQidLink.getCaze().getCaseId());
    }
  }
}
