package uk.gov.ons.census.caseprocessor.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.ons.ssdc.common.model.entity.ActionRuleSurveyExportFileTemplate;

@Component
@ActiveProfiles("test")
public interface ActionRuleSurveyExportFileTemplateRepository
    extends JpaRepository<ActionRuleSurveyExportFileTemplate, UUID> {}
