package uk.gov.ons.census.casesvc.service;

import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

@Component
public class CaseReceiptService {
  private final CaseService caseService;
  private final CaseRepository caseRepository;

  private Map<String, ProcessingRule> processingRules = new HashMap<>();

  private void setUpRules() {
    processingRules.put("HH_U_HH", new ProcessingRule(receiptCase, true));
    processingRules.put("HH_U_Cont", new ProcessingRule(noActionRequired, false));
    processingRules.put("HI_U_I", new ProcessingRule(receiptCase, true));
    processingRules.put("CE_E_CE1", new ProcessingRule(receiptCase, true));
    processingRules.put("CE_U_I", new ProcessingRule(incrementAndReceiptIfExceedsCapacity, true));
    processingRules.put("SPG_E_HH", new ProcessingRule(noActionRequired, false));
    processingRules.put("SPG_E_I", new ProcessingRule(noActionRequired, false));
    processingRules.put("CE_E_I", new ProcessingRule(incrementWithNoReceipting, true));
    processingRules.put("SPG_U_HH", new ProcessingRule(receiptCase, true));
    processingRules.put("SPG_U_I", new ProcessingRule(noActionRequired, false));
    processingRules.put("SPG_U_Cont", new ProcessingRule(noActionRequired, false));
  }

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    setUpRules();
  }

  public void receiptCase(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    String compositeKey = makeRulesKey(caze, uacQidLink);

    //    if (continuationsToIgnore.contains(compositeKey)) return;

    if (processingRules.containsKey(compositeKey)) {
      ProcessingRule processingRule = processingRules.get(compositeKey);

      Case lockedCase = processingRule.run(caze);

      if (processingRule.saveAndEmitCase) {
        caseService.saveCaseAndEmitCaseUpdatedEvent(
            lockedCase, buildMetadata(causeEventType, ActionInstructionType.CLOSE));
      }

      return;
    }

    throw new RuntimeException(compositeKey + " does not map to any processing rule");
  }

  private String makeRulesKey(Case caze, UacQidLink uacQidLink) {
    String qidtype = "HH";

    if (isIndividualQuestionnaireType(uacQidLink.getQid())) {
      qidtype = "I";
    } else if (caze.getTreatmentCode().startsWith("CE")) {
      qidtype = "CE1";
    } else if (iscontinuationQuestionnaireTypes(uacQidLink.getQid())) {
      qidtype = "Cont";
    }

    return caze.getCaseType() + "_" + caze.getAddressLevel() + "_" + qidtype;
  }

  Function<Case, Case> incrementWithNoReceipting =
      (caze) -> {
        Case lockedCase = getCaseAndLockIt(caze.getCaseId());
        lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

        return lockedCase;
      };

  Function<Case, Case> incrementAndReceiptIfExceedsCapacity =
      (caze) -> {
        Case lockedCase = incrementWithNoReceipting.apply(caze);

        if (lockedCase.getCeActualResponses() >= lockedCase.getCeExpectedCapacity()) {
          lockedCase.setReceiptReceived(true);
        }

        return lockedCase;
      };

  Function<Case, Case> receiptCase =
      (caze) -> {
        caze.setReceiptReceived(true);
        return caze;
      };

  Function<Case, Case> noActionRequired = (caze) -> null;

  private Case getCaseAndLockIt(UUID caseId) {
    /*
      This stops the actualResponses being updated by another thread for another receipt or linking event
    */
    Optional<Case> oCase = caseRepository.getCaseAndLockByCaseId(caseId);

    if (!oCase.isPresent()) {
      throw new RuntimeException(
          "Failed to get row to increment responses, row is probably locked and this should resolve itself: "
              + caseId);
    }

    return oCase.get();
  }

  // This is easily extendable for things like ActionInstruction Types
  private class ProcessingRule {
    private Function<Case, Case> functionToExecute;
    public final boolean saveAndEmitCase;

    public ProcessingRule(Function<Case, Case> functionToExecute, boolean saveAndEmitCase) {
      this.functionToExecute = functionToExecute;
      this.saveAndEmitCase = saveAndEmitCase;
    }

    public Case run(Case caze) {
      return functionToExecute.apply(caze);
    }
  }
}
