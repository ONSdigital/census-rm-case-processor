package uk.gov.ons.census.casesvc.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;

@RunWith(MockitoJUnitRunner.class)
public class UacQidCacheTest {
  private static final int CACHE_FETCH = 5;
  private static final int CACHE_MIN = 2;
  private static final int NUMBER_PER_TYPE = 1000;

  @Mock UacQidServiceClient uacQidServiceClient;

  @InjectMocks UacQidCache underTest;

  @Test
  public void reallySimpleTestForCoverage() {
    ReflectionTestUtils.setField(underTest, "cacheFetch", CACHE_FETCH);
    ReflectionTestUtils.setField(underTest, "cacheMin", CACHE_MIN);
    ReflectionTestUtils.setField(underTest, "uacQidGetTimout", 10);

    List<UacQidDTO> uacQids1 = populateUacQidList(1, CACHE_FETCH);
    when(uacQidServiceClient.getUacQids(1, CACHE_FETCH)).thenReturn(uacQids1);

    UacQidDTO actualPair = underTest.getUacQidPair(1);
    assertThat(actualPair).isEqualTo(uacQids1.get(0));
  }

  // This test exercise the code well, however Travis fails as the production code spawns another
  // thread that doesn't get run in travis
  @Test
  public void testCachingTopUp() {
    // given
    ReflectionTestUtils.setField(underTest, "cacheFetch", CACHE_FETCH);
    ReflectionTestUtils.setField(underTest, "cacheMin", CACHE_MIN);
    ReflectionTestUtils.setField(underTest, "uacQidGetTimout", 10);

    List<UacQidDTO> uacQids1 = populateUacQidList(1, CACHE_FETCH);
    when(uacQidServiceClient.getUacQids(any(), any())).thenReturn(uacQids1);
    //    List<UacQidDTO> uacQids2 = populateUacQidList(2, CACHE_FETCH);
    //    when(uacQidServiceClient.getUacQids(2, CACHE_FETCH)).thenReturn(uacQids2);

    List<UacQidDTO> actualUacQidDtos1 = new ArrayList<>();
    //    List<UacQidDTO> actualUacQidDtos2 = new ArrayList<>();

    IntStream stream = IntStream.range(0, NUMBER_PER_TYPE);

    // when
    stream
        .parallel()
        .forEach(
            i -> {
              actualUacQidDtos1.add(underTest.getUacQidPair(1));
              //              actualUacQidDtos2.add(underTest.getUacQidPair(2));
            });

    // Then
    // As we're dealing with different Threads and it can be called a slightly different number of
    // times
    verify(uacQidServiceClient, atLeast(200)).getUacQids(1, CACHE_FETCH);
    //    verify(uacQidServiceClient, atLeast(200)).getUacQids(1, CACHE_FETCH);
    assertThat(actualUacQidDtos1.get(0)).isEqualTo(uacQids1.get(0));
    //    assertThat(actualUacQidDtos2.get(0)).isEqualTo(uacQids2.get(0));
  }

  // This test exercise the code well, however Travis fails as the production code spawns another
  // thread that doesn't get run in travis
  @Test
  public void testToppingUpRecoversFromFailure() {
    // given
    ReflectionTestUtils.setField(underTest, "cacheFetch", CACHE_FETCH);
    ReflectionTestUtils.setField(underTest, "cacheMin", CACHE_MIN);
    ReflectionTestUtils.setField(underTest, "uacQidGetTimout", 2);

    List<UacQidDTO> uacQids1 = populateUacQidList(1, CACHE_FETCH);

    when(uacQidServiceClient.getUacQids(1, CACHE_FETCH))
        .thenThrow(new RuntimeException("api failed"))
        .thenReturn(uacQids1);

    // when
    try {
      underTest.getUacQidPair(1);
    } catch (RuntimeException e) {
      // then
      UacQidDTO actualUacQidDTO = underTest.getUacQidPair(1);
      assertThat(actualUacQidDTO).isEqualTo(uacQids1.get(0));

      return;
    }

    fail("Expected Exception");
  }

  private List<UacQidDTO> populateUacQidList(int questionnaireType, int cacheSize) {
    EasyRandom easyRandom = new EasyRandom();
    List<UacQidDTO> uacQidDTOS = new ArrayList<>();

    for (int i = 0; i < cacheSize; i++) {
      UacQidDTO uacQidDTO = new UacQidDTO();
      uacQidDTO.setQid(questionnaireType + easyRandom.nextObject(String.class));
      uacQidDTO.setUac(easyRandom.nextObject(String.class));
      uacQidDTOS.add(uacQidDTO);
    }

    return uacQidDTOS;
  }
}
