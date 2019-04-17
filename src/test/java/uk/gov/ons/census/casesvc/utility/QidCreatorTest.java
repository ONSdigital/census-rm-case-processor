package uk.gov.ons.census.casesvc.utility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class QidCreatorTest {

  @InjectMocks private QidCreator underTest;

  @Test
  public void testValidQid() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("HH_LF3R2E", 12345);

    // Then
    assertEquals("0120000001234524", result);
  }

  @Test
  public void testValidCheckDigits() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("HH_LF3R2E", 12345);

    // Then
    assertEquals("24", result.substring(result.length() - 2));
  }

  @Test(expected = IllegalStateException.class)
  public void testTooManyCheckDigits() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 4312);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("HH_LF3R2E", 12345);

    // Then
    // Expected Exception is raised

  }

  @Test
  public void testValidQuestionnaireTypeEnglandHousehold() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("HH_LF3R2E", 12345);

    // Then
    assertEquals("01", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeWalesHousehold() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("HH_LF3R2W", 12345);

    // Then
    assertEquals("02", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandHousehold() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("HH_LF3R2N", 12345);

    // Then
    assertEquals("04", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeEnglandIndividual() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("CI_LF3R2E", 12345);

    // Then
    assertEquals("21", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeWalesIndividual() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("CI_LF3R2W", 12345);

    // Then
    assertEquals("22", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandIndividual() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("CI_LF3R2N", 12345);

    // Then
    assertEquals("24", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeEnglandCommunalEstablishment() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("CE_LF3R2E", 12345);

    // Then
    assertEquals("31", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeWalesCommunalEstablishment() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("CE_LF3R2W", 12345);

    // Then
    assertEquals("32", result.substring(0, 2));
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandCommunalEstablishment() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("CE_LF3R2N", 12345);

    // Then
    assertEquals("34", result.substring(0, 2));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCountryTreatmentCode() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("HH_LF3R2Z", 12345);

    // Then
    // Expected Exception is raised
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCaseType() {
    // Given
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
    ReflectionTestUtils.setField(underTest, "trancheIdentifier", 2);
    // When
    String result = underTest.createQid("ZZ_LF3R2E", 12345);

    // Then
    // Expected Exception is raised
  }
}
