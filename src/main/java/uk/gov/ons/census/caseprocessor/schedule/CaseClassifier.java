package uk.gov.ons.census.caseprocessor.schedule;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.common.model.entity.ActionRule;

@Component
public class CaseClassifier {
  private final JdbcTemplate jdbcTemplate;

  public CaseClassifier(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public int enqueueCasesForActionRule(ActionRule actionRule) {
    UUID batchId = UUID.randomUUID();

    return jdbcTemplate.update(
        "INSERT INTO cases.case_to_process (batch_id, batch_quantity, action_rule_id, "
            + "caze_id) SELECT ?, COUNT(*) OVER (), ?, id FROM "
            + "cases.cases "
            + buildWhereClause(
                actionRule.getCollectionExercise().getId(), actionRule.getClassifiers()),
        batchId,
        actionRule.getId());
  }

  private String buildWhereClause(UUID collectionExerciseId, String classifiersClause) {
    StringBuilder whereClause = new StringBuilder();
    whereClause.append(
        String.format("WHERE collection_exercise_id='%s'", collectionExerciseId.toString()));

    if (StringUtils.hasText(classifiersClause)) {
      whereClause.append(" AND ").append(classifiersClause);
    }

    return whereClause.toString();
  }
}
