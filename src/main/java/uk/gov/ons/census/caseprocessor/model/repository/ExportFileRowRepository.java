package uk.gov.ons.census.caseprocessor.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.common.model.entity.ExportFileRow;

public interface ExportFileRowRepository extends JpaRepository<ExportFileRow, UUID> {}
