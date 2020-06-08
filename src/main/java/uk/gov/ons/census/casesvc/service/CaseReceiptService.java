package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.mapQuestionnaireTypeToFormType;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Component
public class CaseReceiptService {

  private final CaseService caseService;
  private final CaseRepository caseRepository;
  private static final String HH = "H";
  private static final String IND = "I";
  private static final String CE1 = "C";
  private static final String CONT = "Cont";
  private static final String EQ_EVENT_CHANNEL = "EQ";

  private Map<Key, UpdateAndEmitCaseRule> rules = new HashMap<>();

  public CaseReceiptService(CaseService caseService, CaseRepository caseRepository) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://collaborate2.ons.gov.uk/confluence/pages/viewpage.action?spaceKey=SDC&title=Receipting
    */

    rules.put(new Key("HH", "U", HH), new Rule(receiptCase, ActionInstructionType.CANCEL));
    rules.put(new Key("HH", "U", CONT), new NoActionRequired());
    rules.put(new Key("HI", "U", IND), new Rule(receiptCase, null));
    rules.put(new Key("CE", "E", IND), new Rule(incrementNoReceipt, ActionInstructionType.UPDATE));
    rules.put(new Key("CE", "E", CE1), new Rule(receiptCase, ActionInstructionType.UPDATE));
    rules.put(new Key("CE", "U", IND), new CeUnitRule());
    rules.put(new Key("SPG", "E", HH), new NoActionRequired());
    rules.put(new Key("SPG", "E", IND), new NoActionRequired());
    rules.put(new Key("SPG", "U", HH), new Rule(receiptCase, ActionInstructionType.CANCEL));
    rules.put(new Key("SPG", "U", IND), new NoActionRequired());
    rules.put(new Key("SPG", "U", CONT), new NoActionRequired());
  }

  public void receiptCase(UacQidLink uacQidLink, EventDTO causeEvent) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()
        || (uacQidLink.isBlankQuestionnaire()
            && !causeEvent.getChannel().equals(EQ_EVENT_CHANNEL))) {
      return;
    }

    Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any known processing rule");
    }

    UpdateAndEmitCaseRule rule = rules.get(ruleKey);
    rule.run(caze, causeEvent.getType());
  }

  private Key makeRulesKey(Case caze, UacQidLink uacQidLink) {
    String formType = mapQuestionnaireTypeToFormType(uacQidLink.getQid());
    return new Key(caze.getCaseType(), caze.getAddressLevel(), formType);
  }

  private Case getCaseAndLockIt(UUID caseId) {
    Case caze = caseService.getCaseAndLockIt(caseId);
    return caze;
  }

  Function<Case, Case> incrementNoReceipt =
      (caze) -> {
        Case lockedCase = getCaseAndLockIt(caze.getCaseId());
        lockedCase.setCeActualResponses(lockedCase.getCeActualResponses() + 1);

        return lockedCase;
      };

  Function<Case, Case> receiptCase =
      (caze) -> {
        caze.setReceiptReceived(true);
        return caze;
      };

  @AllArgsConstructor
  @EqualsAndHashCode
  @ToString
  private class Key {

    private String caseType;
    private String addressLevel;
    private String formType;
  }

  private interface UpdateAndEmitCaseRule {

    void run(Case caze, EventTypeDTO causeEventType);
  }

  @AllArgsConstructor
  private class Rule implements UpdateAndEmitCaseRule {

    private Function<Case, Case> functionToExecute;
    private ActionInstructionType fieldInstruction;

    public void run(Case caze, EventTypeDTO causeEventType) {
      Case updatedCase = functionToExecute.apply(caze);
      caseService.saveCaseAndEmitCaseUpdatedEvent(
          updatedCase, buildMetadata(causeEventType, fieldInstruction));
    }
  }

  private class CeUnitRule implements UpdateAndEmitCaseRule {

    public void run(Case caze, EventTypeDTO causeEventType) {
      ActionInstructionType fieldInstruction = ActionInstructionType.UPDATE;

      Case lockedCase = incrementNoReceipt.apply(caze);

      if (lockedCase.getCeActualResponses() >= lockedCase.getCeExpectedCapacity()) {
        lockedCase.setReceiptReceived(true);
        fieldInstruction = ActionInstructionType.CANCEL;
      }

      caseService.saveCaseAndEmitCaseUpdatedEvent(
          lockedCase, buildMetadata(causeEventType, fieldInstruction));
    }
  }

  private class NoActionRequired implements UpdateAndEmitCaseRule {

    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      // No Updating, saving or emitting required
    }
  }
}
