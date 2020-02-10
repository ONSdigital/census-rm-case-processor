package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;

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

  private static final String QUESTIONNAIRE_LINKED = "Questionnaire Linked";

  private final UacService uacService;
  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final CaseReceipter caseReceipter;

  public QuestionnaireLinkedService(
      UacService uacService,
      CaseService caseService,
      EventLogger eventLogger,
      CaseReceipter caseReceipter) {
    this.uacService = uacService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.caseReceipter = caseReceipter;
  }

  public void processQuestionnaireLinked(
      ResponseManagementEvent questionnaireLinkedEvent, OffsetDateTime messageTimestamp) {
    UacDTO uac = questionnaireLinkedEvent.getPayload().getUac();
    String questionnaireId = uac.getQuestionnaireId();
    UacQidLink uacQidLink = uacService.findByQid(questionnaireId);

    checkQidNotLinkedToAnotherCase(uac, uacQidLink);

    Case caze = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));

    if (isIndividualQuestionnaireType(questionnaireId) && caze.getCaseType().equals("HH")) {
      caze = caseService.prepareIndividualResponseCaseFromParentCase(caze);
      caseService.emitCaseCreatedEvent(caze);
    }

    if (!uacQidLink.isActive()) {
      caseReceipter.handleReceipting(caze, uacQidLink);
    }

    uacQidLink.setCaze(caze);
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

  private void checkQidNotLinkedToAnotherCase(UacDTO uac, UacQidLink uacQidLink) {
    if (uacQidLink.getCaze() != null
        && !uacQidLink.getCaze().getCaseId().equals(UUID.fromString(uac.getCaseId()))) {
      throw new RuntimeException(
          "UacQidLink already linked to case id: " + uacQidLink.getCaze().getCaseId());
    }
  }
}
