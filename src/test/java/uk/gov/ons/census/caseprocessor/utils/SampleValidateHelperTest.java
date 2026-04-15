package uk.gov.ons.census.caseprocessor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.LengthRule;
import uk.gov.ons.ssdc.common.validation.Rule;

class SampleValidateHelperTest {

  @Test
  void testValidateNewValue() {
    // Given
    ColumnValidator columnValidator =
        new ColumnValidator("testSampleField", false, new Rule[] {new LengthRule(60)});

    assertThat(SampleValidateHelper.validateNewValue("testSampleField", "Test", columnValidator))
        .isEmpty();
  }

  @Test
  void testValidateNewValueError() {
    // Given
    ColumnValidator columnValidator =
        new ColumnValidator("testSampleField", true, new Rule[] {new LengthRule(1)});

    // When
    assertThat(SampleValidateHelper.validateNewValue("testSampleField", "Test", columnValidator))
        .isEqualTo(
            Optional.of(
                "Column 'testSampleField' Failed validation for Rule 'LengthRule' validation error: Exceeded max length of 1"));
  }
}
