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

    // if the uacqidLink is already linked to a dfiferent case this throws an Exception
    checkQidNotLinkedToAnotherCase(uacDTO, uacQidLink);

    logEvent(questionnaireLinkedEvent, uacQidLink);

    Case caze = getCase(uacDTO);
    uacQidLink.setCaze(caze);

    if (uacQidLink.isBlankQuestionnaireReceived()) {
      processBlankQuestionnaireLinked(uacQidLink);
      return;
    }

    if (caze.isCcsCase()) {
      processCCSCaseLinking(caze, uacQidLink);
      return;
    }

    processStandardLinking(uacQidLink, caze);
  }

  private void processStandardLinking(UacQidLink uacQidLink, Case caze) {
    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);

    // If UAC/QID has been receipted and the case hasn't, receipt the case, save and emit
    if (!uacQidLink.isActive() && !caze.isReceiptReceived()) {
      caze.setReceiptReceived(true);
      caseService.saveAndEmitCaseUpdatedEvent(caze);
    }
  }

  private void processCCSCaseLinking(Case caze, UacQidLink uacQidLink) {
    uacService.saveUacQidLink(uacQidLink);

    // If UAC/QID has been receipted and the case hasn't, update the case and save it
    if (!uacQidLink.isActive() && !caze.isReceiptReceived()) {
      caze.setReceiptReceived(true);
      caseService.saveCase(caze);
    }
  }

  private void processBlankQuestionnaireLinked(UacQidLink uacQidLink) {
    // When linking a UacQidLink that has been marked as BlankQuestionnaire to a case, the case's
    // receipted flag
    // will never be altered.
    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
  }

  private Case getCase(UacDTO uacDto) {
    if (!isIndividualQuestionnaireType(uacDto.getQuestionnaireId())) {
      return caseService.getCaseByCaseId(UUID.fromString(uacDto.getCaseId()));
    }

    return createAndEmitNewIndividualCase(uacDto);
  }

  private Case createAndEmitNewIndividualCase(UacDTO uacDto) {
    // If the Qid is for a new Individual case then create a new IndividualCase linked to the
    // household case,
    // this new case will be used for the linking

    Case householdCase = caseService.getCaseByCaseId(UUID.fromString(uacDto.getCaseId()));
    Case newIndividualCase = caseService.prepareIndividualResponseCaseFromParentCase(householdCase);

    caseService.emitCaseCreatedEvent(newIndividualCase);

    return newIndividualCase;
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
