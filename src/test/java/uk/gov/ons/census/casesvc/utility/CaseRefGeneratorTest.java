package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.Test;

public class CaseRefGeneratorTest {

  @Test
  public void testGetCaseRef() {
    // Be careful - on a fast multi-core CPU this test's very quick, but can be very slow
    int max_num_of_caserefs_to_check = 1000000; // We should probably check all 90 million... one day

    int[] pseudorandomCaseRefs = new int[max_num_of_caserefs_to_check];
    IntStream stream = IntStream.range(0, max_num_of_caserefs_to_check);
    AtomicInteger caseRefsGenerated = new AtomicInteger(0);

    System.out.println("About to generate pseudorandom case refs");

    stream
        .parallel()
        .forEach(
            i -> {
              pseudorandomCaseRefs[i] = CaseRefGenerator.getCaseRef(i);
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
