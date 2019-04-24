package uk.gov.ons.census.casesvc.utility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Sha256HelperTest {
  @Test
  public void testHashingHappyPath() {
    String testString = "The quick brown fox jumped over the lazy dog";
    String hashResult = Sha256Helper.hash(testString);
    assertEquals("7d38b5cd25a2baf85ad3bb5b9311383e671a8a142eb302b324d4a5fba8748c69", hashResult);
  }
}
