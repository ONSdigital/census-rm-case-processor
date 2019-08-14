package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Component
public class QuestionnaireLinkedProcessor {

  private static final Logger log = LoggerFactory.getLogger(QuestionnaireLinkedProcessor.class);
  private static final String QID_NOT_FOUND_ERROR = "Qid not found error";
  private static final String CASE_NOT_FOUND_ERROR = "Case not found error";
  private static final String QUESTIONNAIRE_LINKED = "Questionnaire Linked";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final CaseRepository caseRepository;
  private final UacProcessor uacProcessor;
  private final CaseProcessor caseProcessor;
  private final EventLogger eventLogger;

  public QuestionnaireLinkedProcessor(
      UacQidLinkRepository uacQidLinkRepository,
      CaseRepository caseRepository,
      UacProcessor uacProcessor,
      CaseProcessor caseProcessor,
      EventLogger eventLogger) {
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.caseRepository = caseRepository;
    this.uacProcessor = uacProcessor;
    this.caseProcessor = caseProcessor;
    this.eventLogger = eventLogger;
  }

  public void processQuestionnaireLinked(ResponseManagementEvent questionnaireLinkedEvent) {
    UacDTO uac = questionnaireLinkedEvent.getPayload().getUac();
    Optional<UacQidLink> uacQidLinkOpt = uacQidLinkRepository.findByQid(uac.getQuestionnaireId());

    if (uacQidLinkOpt.isEmpty()) {
      log.error(QID_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Questionnaire Id '%s' not found!", uac.getQuestionnaireId()));
    }

    Optional<Case> caseOpt = caseRepository.findByCaseId(UUID.fromString(uac.getCaseId()));

    if (caseOpt.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(String.format("Case Id '%s' not found!", uac.getCaseId()));
    }

    UacQidLink uacQidLink = uacQidLinkOpt.get();
    Case caze = caseOpt.get();

    // If already receipted
    if (!uacQidLink.isActive()) {
      caze.setResponseReceived(true);
      caseRepository.saveAndFlush(caze);

      caseProcessor.emitCaseUpdatedEvent(caze);
    }

    uacQidLink.setCaze(caze);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);

    eventLogger.logQuestionnaireLinkedEvent(
        uacQidLink,
        QUESTIONNAIRE_LINKED,
        EventType.QUESTIONNAIRE_LINKED,
        uac,
        questionnaireLinkedEvent.getEvent());
  }
}
