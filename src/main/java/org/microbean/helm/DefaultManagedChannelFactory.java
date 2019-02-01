package org.microbean.helm;

import io.fabric8.kubernetes.client.LocalPortForward;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.microbean.development.annotation.Issue;

/**
 * A default implementation of the {@link ManagedChannelFactory} that creates a {@link ManagedChannel}
 * from a {@link LocalPortForward}.
 *
 * Allows additional customization of the {@link ManagedChannelBuilder} by supplying a
 * {@link ManagedChannelConfigurer} to the constructor.
 */
public class DefaultManagedChannelFactory implements ManagedChannelFactory {

  private static final ManagedChannelConfigurer NOOP_CONFIGURER = (builder) -> {};
  private final ManagedChannelConfigurer configurer;

  public DefaultManagedChannelFactory() {
    this.configurer = NOOP_CONFIGURER;
  }

  /**
   * @param configurer a {@link ManagedChannelConfigurer} to allow overriding of the default
   * configuration of the {@link ManagedChannel}
   *
   * @throws NullPointerException if the configurer is null
   */
  public DefaultManagedChannelFactory(final ManagedChannelConfigurer configurer) {
    Objects.requireNonNull(configurer);
    this.configurer = configurer;
  }

  @Issue(id = "42", uri = "https://github.com/microbean/microbean-helm/issues/42")
  @Override public ManagedChannel create(final LocalPortForward portForward) {
    Objects.requireNonNull(portForward);
    @Issue(id = "43", uri = "https://github.com/microbean/microbean-helm/issues/43")
    final InetAddress localAddress = portForward.getLocalAddress();
    if (localAddress == null) {
      throw new IllegalArgumentException("portForward",
          new IllegalStateException("portForward.getLocalAddress() == null"));
    }
    final String hostAddress = localAddress.getHostAddress();
    if (hostAddress == null) {
      throw new IllegalArgumentException("portForward",
          new IllegalStateException("portForward.getLocalAddress().getHostAddress() == null"));
    }
    final ManagedChannelBuilder builder =
        ManagedChannelBuilder.forAddress(hostAddress, portForward.getLocalPort())
            .idleTimeout(5L, TimeUnit.SECONDS)
            .keepAliveTime(30L, TimeUnit.SECONDS)
            .maxInboundMessageSize(Tiller.MAX_MESSAGE_SIZE)
            .usePlaintext(true);
    configurer.configure(builder);
    return builder.build();
  }
}
