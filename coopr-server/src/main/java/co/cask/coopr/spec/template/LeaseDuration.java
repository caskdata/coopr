/*
 * Copyright © 2012-2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.cask.coopr.spec.template;

import co.cask.coopr.layout.InvalidClusterException;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.util.concurrent.TimeUnit;

/**
 * Defines lease duration for a cluster. 0 for initial or max means forever;
 */
public final class LeaseDuration {
  public static final LeaseDuration FOREVER_LEASE_DURATION = new LeaseDuration(0, 0, 0);

  private final long initial;
  private final long max;
  private final long step;

  public LeaseDuration(long initial, long max, long step) {
    Preconditions.checkArgument(initial >= 0, "initial lease duration should be >=0");
    Preconditions.checkArgument(max >= 0, "max lease duration should be >=0");
    Preconditions.checkArgument(step >= 0, "step should be >=0");
    this.initial = initial;
    this.max = max;
    this.step = step;
  }

  public LeaseDuration(String initial, String max, String step) {
    this(getTimestamp(initial), getTimestamp(max), getTimestamp(step));
  }

  /**
   * Returns a timestamp in milliseconds.
   *
   * @param base The string argument user provided.
   * @return Timestamp in milliseconds
   */
  private static long getTimestamp(String base) {
    if (base.equals("min") || base.equals("0")) {
      return 0L;
    }
    if (base.equals("max")) {
      return Long.MAX_VALUE;
    }
    try {
      char type = base.charAt(base.length() - 1);
      int offset = Integer.parseInt(base.substring(0, base.length() - 1));
      switch (type) {
        case 's':
          return TimeUnit.SECONDS.toMillis(offset);
        case 'm':
          return TimeUnit.MINUTES.toMillis(offset);
        case 'h':
          return TimeUnit.HOURS.toMillis(offset);
        case 'd':
          return TimeUnit.DAYS.toMillis(offset);
        default:
          throw new RuntimeException("Unsupported relative time format: " + type);
      }
    } catch (NumberFormatException e) {
      throw new RuntimeException("Invalid number value: " + base + ". Reason: " + e.getMessage());
    }
  }

  /**
   * Get the initial lease time in seconds, with 0 meaning forever.
   *
   * @return Initial lease time in seconds.
   */
  public long getInitial() {
    return initial;
  }

  /**
   * Get the maximum lease time in seconds, with 0 meaning forever.
   *
   * @return Maximum lease time in seconds.
   */
  public long getMax() {
    return max;
  }

  /**
   * Get the step size in seconds to use when extending a lease, with 0 meaning any step size.
   *
   * @return Step size in seconds to use when extending a lease, with 0 meaning any step size.
   */
  public long getStep() {
    return step;
  }

  /**
   * Calculate the initial lease to use given the initial lease here and a requested initial lease. The requested
   * lease must be equal to or less than the initial lease here. Takes into account that a lease of 0 is an infinite
   * lease.
   *
   * @param requestedInitialLease Requested initial lease.
   * @return The smaller of the leases.
   * @throws InvalidClusterException if the requested lease is larger than the allowed initial lease, or if it is
   *                                 less than negative one.
   */
  public long calcInitialLease(String requestedInitialLease) throws InvalidClusterException {
    return calcInitialLease(getTimestamp(requestedInitialLease));
  }

  /**
   * Calculate the initial lease to use given the initial lease here and a requested initial lease. The requested
   * lease must be equal to or less than the initial lease here. Takes into account that a lease of 0 is an infinite
   * lease.
   *
   * @param requestedInitialLease Requested initial lease.
   * @return The smaller of the leases.
   * @throws InvalidClusterException if the requested lease is larger than the allowed initial lease, or if it is
   *                                 less than negative one.
   */
  public long calcInitialLease(long requestedInitialLease) throws InvalidClusterException {
    // Determine valid lease duration for the cluster.
    // It has to be less than the initial lease duration set in template.
    long leaseDuration;

    // if it's -1, use the lease specified in the template
    if (requestedInitialLease == -1) {
      leaseDuration = initial;
    } else if (initial == 0) {
      // lease of 0 means it's an unlimited lease, so anything in the request is valid
      leaseDuration = requestedInitialLease;
    } else if (initial >= requestedInitialLease && requestedInitialLease != 0) {
      // initial lease is bigger than the requested one so its ok. requested lease of 0 is an unlimited
      // lease, so need to check for that explicitly.
      leaseDuration = requestedInitialLease;
    } else {
      // this happens if the requested lease is greater than the template lease
      throw new InvalidClusterException("lease duration cannot be greater than duration specified in template");
    }

    if (leaseDuration < 0) {
      throw new InvalidClusterException("invalid lease duration: " + leaseDuration);
    }
    return leaseDuration;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("initial", initial)
      .add("max", max)
      .add("step", step)
      .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LeaseDuration that = (LeaseDuration) o;

    return Objects.equal(this.initial, that.initial) &&
      Objects.equal(this.max, that.max) &&
      Objects.equal(this.step, that.step);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(initial, max, step);
  }
}
