package uk.gov.ons.census.caseprocessor.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HashHelperTest {

  @Test
  void testStringToHash() {
    String testString = "This is a test for String to Hash! I hope this works!!!";
    String hashStringResult = HashHelper.hash(testString);
    assertEquals(
        "4a1cd818b28d3278cdf5116ee8587b0178b9041b39134ca6409cd22247a419f2", hashStringResult);
  }

  @Test
  void testBytesToHash() {
    String testString = "This is a test for Bytes to Hash! I hope this works!!!";
    byte[] testBytes = testString.getBytes();

    String hashStringResult = HashHelper.hash(testBytes);
    assertEquals(
        "0199aeca2fb522ba11eb84bd9331949186555960a28c6fe463347261dc76fef9", hashStringResult);
  }
}
