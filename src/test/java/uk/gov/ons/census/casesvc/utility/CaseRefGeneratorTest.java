package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.Ignore;
import org.junit.Test;

public class CaseRefGeneratorTest {

  // Marked ignored as it takes a couple of minutes to run
  @Test
  @Ignore
  public void testGetCaseRef() {
    // Be careful - on a fast multi-core CPU this test takes minutes, but could be very slow
    int max_num_of_caserefs_to_check = 89999998;

    long[] pseudorandomCaseRefs = new long[max_num_of_caserefs_to_check];
    IntStream stream = IntStream.range(0, max_num_of_caserefs_to_check);
    AtomicInteger caseRefsGenerated = new AtomicInteger(0);

    System.out.println("About to generate pseudorandom case refs");

    final byte[] caserefgeneratorkey = new byte[] {0x10, 0x20, 0x10, 0x20, 0x10, 0x20, 0x10, 0x20};

    stream
        .parallel()
        .forEach(
            i -> {
              pseudorandomCaseRefs[i] = CaseRefGenerator.getCaseRef(i, caserefgeneratorkey);
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

    Set<Long> uniqueCaseRefs = new HashSet<>();
    stream = IntStream.range(0, max_num_of_caserefs_to_check);
    stream
        .parallel()
        .forEach(
            i -> {
              long caseRef = pseudorandomCaseRefs[i];
              assertThat(caseRef).isBetween(1000000000L, 9999999999L);

              synchronized (uniqueCaseRefs) {
                if (uniqueCaseRefs.contains(caseRef)) {
                  throw new RuntimeException("Duplicate case ref found " + caseRef);
                }

                uniqueCaseRefs.add(caseRef);
              }

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
