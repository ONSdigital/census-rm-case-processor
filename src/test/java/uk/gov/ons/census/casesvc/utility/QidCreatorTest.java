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
    String result = underTest.createQid(1, 12345);

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
    String result = underTest.createQid(1, 12345);

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
    underTest.createQid(1, 12345);

    // Then
    // Expected Exception is raised

  }
}
