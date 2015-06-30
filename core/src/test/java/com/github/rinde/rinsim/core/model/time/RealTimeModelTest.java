/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.rinsim.core.model.time;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.rinsim.core.model.time.TimeModel.Builder;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.math.DoubleMath;

/**
 * @author Rinde van Lon
 *
 */
public class RealTimeModelTest extends TimeModelTest<RealTimeModel> {

  /**
   * @param sup The supplier to use for creating model instances.
   */
  public RealTimeModelTest(Builder sup) {
    super(sup);
  }

  /**
   * @return The models to test.
   */
  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        { TimeModel.builder().withRealTime().withTickLength(100L) }
    });
  }

  /**
   * Tests the actual elapsed time.
   */
  @Test
  public void testRealTime() {
    getModel().register(new LimitingTickListener(getModel(), 3));
    final long start = System.nanoTime();
    getModel().start();
    final long duration = System.nanoTime() - start;
    // duration should be somewhere between 200 and 300 ms
    assertThat(duration).isAtLeast(200000000L);
    assertThat(duration).isLessThan(300000000L);
    assertThat(getModel().getCurrentTime()).isEqualTo(300);
  }

  /**
   * Tests that restarting the time is forbidden.
   */
  @Test
  public void testStartStopStart() {
    final LimitingTickListener ltl = new LimitingTickListener(getModel(), 3);
    getModel().register(ltl);
    getModel().start();
    boolean fail = false;
    try {
      getModel().start();
    } catch (final IllegalStateException e) {
      fail = true;
      assertThat(e.getMessage()).contains("can be started only once");
    }
    assertThat(fail).isTrue();
  }

  /**
   * Tests that calling tick is unsupported.
   */
  @SuppressWarnings("deprecation")
  @Test
  public void testTick() {
    boolean fail = false;
    try {
      getModel().tick();
    } catch (final UnsupportedOperationException e) {
      fail = true;
      assertThat(e.getMessage()).contains("not supported");
    }
    assertThat(fail).isTrue();
  }

  /**
   * Tests that a sudden delay in computation time is detected.
   */
  @Test
  public void testConsistencyCheck() {
    getModel().register(limiter(150));

    final int t = RealTimeModel.RealTime.CONSISTENCY_CHECK_LENGTH + DoubleMath
      .roundToInt(.5 * RealTimeModel.RealTime.CONSISTENCY_CHECK_LENGTH,
        RoundingMode.HALF_DOWN);

    getModel().register(new TickListener() {
      @Override
      public void tick(TimeLapse timeLapse) {
        if (timeLapse.getStartTime() == timeLapse.getTickLength() * t) {
          try {
            Thread.sleep(150);
          } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
          }
        }
      }

      @Override
      public void afterTick(TimeLapse timeLapse) {}
    });
    boolean fail = false;
    try {
      getModel().start();
    } catch (final IllegalStateException e) {
      fail = true;
    }
    assertThat(fail).isTrue();
  }

  /**
   * Tests repeatedly switching between fast forward and real time mode.
   */
  @Test
  public void testSwitching() {
    getModel().switchToSimulatedTime();

    final List<Long> times = new ArrayList<>();
    final List<Long> timeLapseTimes = new ArrayList<>();
    getModel().register(new TickListener() {
      @Override
      public void tick(TimeLapse timeLapse) {
        timeLapseTimes.add(timeLapse.getStartTime());
        if (timeLapse.getTime() == 100000 || timeLapse.getTime() == 200000) {
          times.add(System.nanoTime());
          getModel().switchToRealTime();
          getModel().switchToSimulatedTime();
          getModel().switchToRealTime();
        }
        // this switch should not have any effect
        if (timeLapse.getTime() == 50000) {
          getModel().switchToRealTime();
          getModel().switchToSimulatedTime();
        }
        if (timeLapse.getTime() == 100500 || timeLapse.getTime() == 200500) {
          times.add(System.nanoTime());
          getModel().switchToSimulatedTime();
          getModel().switchToRealTime();
          getModel().switchToSimulatedTime();
        }
        // this switch should not have any effect
        if (timeLapse.getTime() == 100200) {
          getModel().switchToSimulatedTime();
          getModel().switchToRealTime();
        }
        if (timeLapse.getTime() >= 300000) {
          times.add(System.nanoTime());
          getModel().stop();
        }
      }

      @Override
      public void afterTick(TimeLapse timeLapse) {}
    });

    assertThat(times).isEmpty();
    assertThat(timeLapseTimes).isEmpty();
    getModel().start();

    assertThat(times).hasSize(5);
    assertThat(timeLapseTimes).hasSize(3001);

    final PeekingIterator<Long> it = Iterators
      .peekingIterator(times.iterator());

    final List<Double> interArrivalTimes = new ArrayList<>();
    for (long l1 = it.next(); it.hasNext(); l1 = it.next()) {
      final Long l2 = it.peek();
      interArrivalTimes.add((l2 - l1) / 1000000d);
    }
    assertThat(interArrivalTimes.get(0)).isAtLeast(400d);
    assertThat(interArrivalTimes.get(0)).isAtMost(500d);

    assertThat(interArrivalTimes.get(1)).isAtMost(500d);

    assertThat(interArrivalTimes.get(2)).isAtLeast(400d);
    assertThat(interArrivalTimes.get(2)).isAtMost(500d);

    assertThat(interArrivalTimes.get(3)).isAtMost(500d);
  }

  /**
   * Test that a tick listener that takes too much time is detected.
   */
  @Test
  public void testTimingChecker() {
    getModel().register(new TickListener() {
      @Override
      public void tick(TimeLapse timeLapse) {
        try {
          Thread.sleep(101L);
        } catch (final InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }

      @Override
      public void afterTick(TimeLapse timeLapse) {}
    });
    boolean fail = false;
    try {
      getModel().start();
    } catch (final IllegalStateException e) {
      assertThat(e.getMessage()).contains("took too much time");
      fail = true;
    }
    assertThat(fail).isTrue();
  }

}
