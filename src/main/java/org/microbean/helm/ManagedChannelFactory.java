package org.microbean.helm;

import io.fabric8.kubernetes.client.LocalPortForward;
import io.grpc.ManagedChannel;

/**
 * An interface whose implementations create a {@link ManagedChannel} from a
 * {@link LocalPortForward} to be used to communicate with Tiller.
 */
public interface ManagedChannelFactory {

  /**
   * Creates a {@link ManagedChannel} for communication with Tiller
   * from the information contained in the supplied {@link
   * LocalPortForward}.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>Overrides of this method must not return {@code null}.</p>
   *
   * @param portForward a {@link LocalPortForward}; must not be {@code
   * null}
   * @return a non-{@code null} {@link ManagedChannel}
   * @throws NullPointerException if {@code portForward} is {@code
   * null}
   * @throws IllegalArgumentException if {@code portForward}'s
   * {@link LocalPortForward#getLocalAddress()} method returns {@code
   * null}
   */
  ManagedChannel create(final LocalPortForward portForward);

}
