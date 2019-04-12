package uk.gov.ons.census.casesvc.utility;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class QidCreatorTest {

  @InjectMocks private QidCreator underTest;

  @Before
  public void setUp() {
    ReflectionTestUtils.setField(underTest, "modulus", 33);
    ReflectionTestUtils.setField(underTest, "factor", 802);
  }

  @Test
  public void testValidQid() {
    // Given

    // When
    long result = underTest.createQid(12, 2, 12345);

    // Then
    assertEquals(1220000001234502L, result);
  }

  @Test
  public void testValidCheckDigits() {
    // Given

    // When
    long result = underTest.createQid(12, 2, 12345);

    // Then
    String stringResult = Long.toString(result);
    assertEquals("02", stringResult.substring(stringResult.length() - 2));
  }
}
