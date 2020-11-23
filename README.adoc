= Hazelcast Cluster Manager

image:https://github.com/vert-x3/vertx-hazelcast/workflows/CI/badge.svg?branch=master["Build Status", link="https://github.com/vert-x3/vertx-hazelcast/actions?query=workflow%3ACI"]

This is a cluster manager implementation for Vert.x that uses http://hazelcast.com[Hazelcast].

It is the default cluster manager in the Vert.x distribution, but it can be replaced with another implementation as Vert.x
cluster managers are pluggable.

Please see the main documentation on the web-site for a full description:

* https://vertx.io/docs/vertx-hazelcast/java/[Web-site documentation]

== Running tests

To run the clustering test suite, open a terminal and type:

[source,shell]
----
mvn test
----

There are additional integration tests in this project.
To run these tests as well:

[source,shell]
----
mvn verify
----
