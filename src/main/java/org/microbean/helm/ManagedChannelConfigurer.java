package org.microbean.helm;

import io.grpc.ManagedChannelBuilder;

/**
 * An interface whose implementations configure options on the supplied
 * {@link ManagedChannelBuilder} to override defaults.
 */
public interface ManagedChannelConfigurer {
  void configure(final ManagedChannelBuilder<?> managedChannelBuilder);
}
