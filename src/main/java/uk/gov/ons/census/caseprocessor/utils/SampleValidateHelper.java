package uk.gov.ons.census.caseprocessor.utils;

import java.util.Optional;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;

public class SampleValidateHelper {

  public static Optional<String> validateNewValue(
      String columnName, String validateThis, ColumnValidator columnValidator) {

    if (columnValidator.getColumnName().equals(columnName)) {
      return columnValidator.validateData(validateThis, true);
    }

    return Optional.empty();
  }
}
