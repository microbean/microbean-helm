# microbean-helm

[![Build Status](https://travis-ci.org/microbean/microbean-helm.svg?branch=master)](https://travis-ci.org/microbean/microbean-helm)

The [microbean-helm project][12] lets you work with the server-side
componentry of [Helm][0] from Java.

This means your Java applications can now manage applications in your
Kubernetes cluster using the [Helm][0] notions of [charts][3]
and [releases][9].

Until now, Java developers had to use the `helm` command line client
to do these operations.

# Status

The microbean-helm project is currently in an **alpha** state.

**Version 2 and later of this project are not backwards-compatible
with earlier versions.**

# Versioning

The microbean-helm project's version number tracks the Helm and Tiller
release it works with, together with its own version semantics.  For
example, a microbean-helm version of `2.6.2.1.1.16` means that the Helm
version it tracks is `2.6.2` and the (SemVer-compatible) version of
the non-generated code that is part of _this_ project is `1.1.16`.

# Installation

To install microbean-helm, simply include it as a dependency in your
project.  If you're using Maven, the dependency stanza should look
like this:

    <dependency>
      <groupId>org.microbean</groupId>
      <artifactId>microbean-helm</artifactId>
      <!-- See http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.microbean%22%20AND%20a%3A%22microbean-helm%22 for available releases. -->
      <version>2.6.2.1.1.16</version>
      <type>jar</type>
    </dependency>
    
Releases are [available in Maven Central][10].  Snapshots are available
in [Sonatype Snapshots][11].

# Documentation

The microbean-helm project [documentation is online][8].

# Helm

[Helm][0] is the package manager for [Kubernetes][1].  It consists of
a command line program named `helm` and a server-side component named
Tiller.  `helm` serves as a Tiller client.

# Tiller

[Tiller][2] is the server-side component of Helm.  Tiller accepts and
works with Helm [_charts_][3]&mdash;packaged Kubernetes manifest
templates together with their values.  microbean-helm lets
you
[build and work with those charts and the _releases_ they produce from Java][4] and
send them back and forth to and from Tiller.

## Tiller Installation

In a normal Helm usage scenario, Tiller
is
[installed just-in-time by the `helm` command line client (via the `helm init` subcommand)][5].
It runs as a Pod in a Kubernetes cluster.  microbean-helm features
the [`TillerInstaller` class][13] that can do the same thing from
Java.

## Tiller Connectivity

Because Tiller normally runs as a Pod, communicating with it from
outside the cluster is not straightforward.  The `helm` command line
client internally forwards a local port to a port on the Tiller Pod
and, via this tunnel, establishes communication with the Tiller
server.  The microbean-helm project does the same thing but via a Java
library.

## Tiller Communication

Tiller is fundamentally a [gRPC][6] application.  The microbean-helm
project [generates the Java bindings][7] to its gRPC API, allowing
applications to communicate with Tiller using Java classes.

# [`ReleaseManager`][4]

Ideally, business logic for installing and updating releases would be
entirely encapsulated within the Tiller server.  Unfortunately, this
is not the case.  The `helm` command-line program investigates and
processes a Helm chart's `requirements.yaml` file at installation time
and uses it to alter what is actually dispatched to Tiller.  For this
reason, if you are using the microbean-helm project, you must use the
_non_-generated [`ReleaseManager` class][4] to perform your
Helm-related operations, since it contains a port of the business
logic embedded in the `helm` program.

[0]: https://helm.sh/
[1]: https://kubernetes.io/
[2]: https://docs.helm.sh/glossary/#tiller
[3]: https://docs.helm.sh/developing_charts/#
[4]: https://microbean.github.io/microbean-helm/apidocs/org/microbean/helm/ReleaseManager.html
[5]: https://docs.helm.sh/using_helm/#easy-in-cluster-installation
[6]: http://www.grpc.io/
[7]: https://microbean.github.io/microbean-helm/apidocs/index.html
[8]: https://microbean.github.io/microbean-helm/
[9]: https://docs.helm.sh/glossary/#release
[10]: http://search.maven.org/#search%7Cga%7C1%7Ca%3Amicrobean-helm
[11]: https://oss.sonatype.org/content/repositories/snapshots/org/microbean/microbean-helm/
[12]: https://github.com/microbean/microbean-helm
[13]:https://microbean.github.io/microbean-helm/apidocs/org/microbean/helm/TillerInstaller.html
