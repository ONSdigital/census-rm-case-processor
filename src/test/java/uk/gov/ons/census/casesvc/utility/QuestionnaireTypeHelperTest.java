package uk.gov.ons.census.casesvc.utility;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuestionnaireTypeHelperTest {
  @Test
  public void testValidQuestionnaireTypeEnglandHousehold() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("HH_LF2R3BE");

    // Then
    assertEquals(1, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesHousehold() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("HH_LF2R1W");

    // Then
    assertEquals(2, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandHousehold() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("HH_1LSFN");

    // Then
    assertEquals(4, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeEnglandIndividual() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("CI_L666E");

    // Then
    assertEquals(21, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesIndividual() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("CI_L666W");

    // Then
    assertEquals(22, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandIndividual() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("CI_L666N");

    // Then
    assertEquals(24, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeEnglandCommunalEstablishment() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("CE_L666E");

    // Then
    assertEquals(31, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesCommunalEstablishment() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("CE_L666W");

    // Then
    assertEquals(32, actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandCommunalEstablishment() {
    // Given

    // When
    int actualQuestionnaireType = QuestionnaireTypeHelper.calculateQuestionnaireType("CE_L666N");

    // Then
    assertEquals(34, actualQuestionnaireType);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCountryTreatmentCode() {
    // Given

    // When
    QuestionnaireTypeHelper.calculateQuestionnaireType("CE_L666X");

    // Then
    // Exception thrown - expected
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCaseType() {
    // Given

    // When
    QuestionnaireTypeHelper.calculateQuestionnaireType("ZZ_L666E");

    // Then
    // Exception thrown - expected
  }
}
