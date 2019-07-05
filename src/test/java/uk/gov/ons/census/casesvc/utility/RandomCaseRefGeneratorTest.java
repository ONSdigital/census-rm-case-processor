package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class RandomCaseRefGeneratorTest {

  @Test
  public void testGetCaseRef() {
    for (int i = 0; i < 10000; i++) {
      int caseRef = RandomCaseRefGenerator.getCaseRef();
      assertThat(caseRef).isBetween(10000000, 99999999);
    }
  }
}
