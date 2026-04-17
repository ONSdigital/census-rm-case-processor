package uk.gov.ons.census.caseprocessor.model.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uk.gov.ons.census.common.model.entity.ActionRule;

public interface ActionRuleRepository extends JpaRepository<ActionRule, UUID> {
  List<ActionRule> findByTriggerDateTimeBeforeAndHasTriggeredIsFalse(
      OffsetDateTime triggerDateTime);

  @Query(
      value =
          "SELECT * FROM cases.action_rule ar "
              + "WHERE action_rule_status = 'PROCESSING_CASES' "
              + "AND (SELECT COUNT(*) FROM cases.case_to_process WHERE action_rule_id = ar.id) = 0",
      nativeQuery = true)
  List<ActionRule> findCompletedProcessing();
}
