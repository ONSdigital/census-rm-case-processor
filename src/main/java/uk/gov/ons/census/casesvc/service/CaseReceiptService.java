package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Component
public class CaseReceiptService {
  private final CaseService caseService;
  private final CaseRepository caseRepository;

  private Map<String, Function<Case, Case>> processingRules = new HashMap<>();

  //  We could in theory put everything in here and have Function throwing Exception invalid etc,
  // but seems OTT
  private void setUpRules() {
    processingRules.put("HH_U_HH", receiptCase);
    processingRules.put("HI_U_I", receiptCase);
    processingRules.put("CE_E_CE1", receiptCase);
    processingRules.put("CE_U_I", incrementAndReceiptIfGreaterEqualExpected);
    processingRules.put("CE_E_I", incrementWithNoReceipting);
    processingRules.put("SPG_U_HH", receiptCase);
  }

  private List<String> continuationsToIgnore =
      List.of("HH_U_Cont", "SPG_E_HH", "SPG_E_I", "SPG_U_I");

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    setUpRules();
  }

  public void receiptCase(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    String compositeKey = makeRulesKey(caze, uacQidLink);

    if (continuationsToIgnore.contains(compositeKey)) return;

    if (processingRules.containsKey(compositeKey)) {
      Case lockedCase = processingRules.get(compositeKey).apply(caze);

      caseService.saveCaseAndEmitCaseUpdatedEvent(
          lockedCase, buildMetadata(causeEventType, ActionInstructionType.CLOSE));

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

  Function<Case, Case> incrementAndReceiptIfGreaterEqualExpected =
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
}
