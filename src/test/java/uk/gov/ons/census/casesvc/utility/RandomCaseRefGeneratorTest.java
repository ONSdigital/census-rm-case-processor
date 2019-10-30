package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.Test;

public class RandomCaseRefGeneratorTest {

  @Test
  public void testGetCaseRef() {
    for (int i = 0; i < 10000; i++) {
      int caseRef = RandomCaseRefGenerator.getCaseRef();
      assertThat(caseRef).isBetween(10000000, 99999999);
    }
  }

  @Test
  public void testGetPseudorandomCaseRef() {
    // Be careful with this - on a fact computer this test is very quick, but on a slow one, v. slow
    int max_num_of_caserefs_to_check = 1000000; // We should probably check 90 million, one day

    int[] pseudorandomCaseRefs = new int[max_num_of_caserefs_to_check];
    IntStream stream = IntStream.range(0, max_num_of_caserefs_to_check);
    AtomicInteger caseRefsGenerated = new AtomicInteger(0);

    System.out.println("About to generate pseudorandom case refs");

    stream
        .parallel()
        .forEach(
            i -> {
              pseudorandomCaseRefs[i] = RandomCaseRefGenerator.getPseudoRandomCaseRef(i);
              if (caseRefsGenerated.incrementAndGet() % 10000 == 0) {
                System.out.println(
                    String.format(
                        "Progress: %.2f",
                        ((double) caseRefsGenerated.get() / (double) max_num_of_caserefs_to_check)
                            * 100.0));
              }
            });

    /* Why would we do this AFTER generating the case refs? Well, because this part is so much
    quicker, but it slows down as the size of the set of unique caserefs grows. This is the
    quickest way to check a very large list has no dupes on it, without slowing down the first
    part of the process very much */
    System.out.println("About to check pseudorandom case refs are unique and within bounds");

    Set<Integer> uniqueCaseRefs = new ConcurrentHashMap<>().newKeySet();
    stream = IntStream.range(0, max_num_of_caserefs_to_check);
    stream
        .parallel()
        .forEach(
            i -> {
              int caseRef = pseudorandomCaseRefs[i];
              assertThat(caseRef).isBetween(10000000, 99999999);

              if (uniqueCaseRefs.contains(caseRef)) {
                fail("Duplicate case ref found " + caseRef);
              }

              uniqueCaseRefs.add(caseRef);

              if (uniqueCaseRefs.size() % 10000 == 0) {
                System.out.println(
                    String.format(
                        "Progress: %.2f",
                        (((double) uniqueCaseRefs.size()) / (double) max_num_of_caserefs_to_check)
                            * 100.0));
              }
            });
  }
}
