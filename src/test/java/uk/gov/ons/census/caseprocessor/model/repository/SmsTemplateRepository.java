package uk.gov.ons.census.caseprocessor.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.ons.ssdc.common.model.entity.SmsTemplate;

@Component
@ActiveProfiles("test")
public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, String> {}
