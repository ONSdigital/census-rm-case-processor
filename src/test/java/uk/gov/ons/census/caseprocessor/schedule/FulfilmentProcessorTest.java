package uk.gov.ons.census.caseprocessor.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentToProcessRepository;

@ExtendWith(MockitoExtension.class)
class FulfilmentProcessorTest {
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private FulfilmentToProcessRepository fulfilmentToProcessRepository;

  @InjectMocks private FulfilmentProcessor underTest;

  @Test
  void testHappyPath() {
    // Given
    when(fulfilmentToProcessRepository.findDistinctPackCode())
        .thenReturn(List.of("TEST_PACK_CODE"));

    // When
    underTest.addFulfilmentBatchIdAndQuantity();

    // Then
    String expectedSql =
        "UPDATE casev3.fulfilment_to_process "
            + "SET batch_quantity = (SELECT COUNT(*) FROM casev3.fulfilment_to_process "
            + "WHERE export_file_template_pack_code = ?), batch_id = ? WHERE export_file_template_pack_code = ?";
    verify(jdbcTemplate)
        .update(eq(expectedSql), eq("TEST_PACK_CODE"), any(UUID.class), eq("TEST_PACK_CODE"));
  }
}
