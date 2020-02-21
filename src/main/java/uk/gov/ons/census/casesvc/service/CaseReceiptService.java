package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.isIndividualQuestionnaireType;
import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
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

  private Map<Key, Rule> rules = new HashMap<>();

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://collaborate2.ons.gov.uk/confluence/pages/viewpage.action?spaceKey=SDC&title=Receipting
     The invalid rows are missing from here and are handled collectively by throwing a " does not map to any valid processing rule" RunTimeException
    */
    rules.put(new Key("HH", "U", "HH"), new Rule(receiptCase, true));
    rules.put(new Key("HH", "U", "Cont"), new Rule(noActionRequired, false));
    rules.put(new Key("HI", "U", "Ind"), new Rule(receiptCase, true));
    rules.put(new Key("CE", "E", "CE1"), new Rule(receiptCase, true));
    rules.put(new Key("CE", "U", "Ind"), new Rule(incrementAndReceipt, true));
    rules.put(new Key("SPG", "E", "HH"), new Rule(noActionRequired, false));
    rules.put(new Key("SPG", "E", "Ind"), new Rule(noActionRequired, false));
    rules.put(new Key("CE", "E", "Ind"), new Rule(incremenNoReceipt, true));
    rules.put(new Key("SPG", "U", "HH"), new Rule(receiptCase, true));
    rules.put(new Key("SPG", "U", "Ind"), new Rule(noActionRequired, false));
    rules.put(new Key("SPG", "U", "Cont"), new Rule(noActionRequired, false));
  }

  public void receiptCase(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any valid processing rule");
    }

    Rule rule = rules.get(ruleKey);

    Case lockedCase = rule.run(caze);

    if (rule.saveAndEmitCase) {
      caseService.saveCaseAndEmitCaseUpdatedEvent(
          lockedCase, buildMetadata(causeEventType, ActionInstructionType.CLOSE));
    }
  }

  private Key makeRulesKey(Case caze, UacQidLink uacQidLink) {
    String formType = "HH";

    if (isIndividualQuestionnaireType(uacQidLink.getQid())) {
      formType = "Ind";
    } else if (caze.getTreatmentCode().startsWith("CE")) {
      formType = "CE1";
    } else if (iscontinuationQuestionnaireTypes(uacQidLink.getQid())) {
      formType = "Cont";
    }

    return new Key(caze.getCaseType(), caze.getAddressLevel(), formType);
  }

  Function<Case, Case> incremenNoReceipt =
      (caze) -> {
        Case lockedCase = getCaseAndLockIt(caze.getCaseId());
        lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

        return lockedCase;
      };

  Function<Case, Case> incrementAndReceipt =
      (caze) -> {
        Case lockedCase = incremenNoReceipt.apply(caze);

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

  Function<Case, Case> noActionRequired = (caze) -> caze;

  private Case getCaseAndLockIt(UUID caseId) {
    Optional<Case> oCase = caseRepository.getCaseAndLockByCaseId(caseId);

    if (!oCase.isPresent()) {
      throw new RuntimeException(
          "Failed to get row to increment responses, row is probably locked and this should resolve itself: "
              + caseId);
    }

    return oCase.get();
  }

  @AllArgsConstructor
  @EqualsAndHashCode
  @ToString
  private class Key {
    private String caseType;
    private String addressLevel;
    private String formType;
  }

  @AllArgsConstructor
  private class Rule {
    private Function<Case, Case> functionToExecute;
    private final boolean saveAndEmitCase;

    public Case run(Case caze) {
      return functionToExecute.apply(caze);
    }
  }
}
