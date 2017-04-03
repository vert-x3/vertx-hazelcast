/**
 * = Hazelcast Cluster Manager
 *
 * This is a cluster manager implementation for Vert.x that uses http://hazelcast.com[Hazelcast].
 *
 * It is the default cluster manager used in the Vert.x distribution, but it can be replaced with another implementation as Vert.x
 * cluster managers are pluggable.  This implementation is packaged inside:
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <dependency>
 *   <groupId>${maven.groupId}</groupId>
 *   <artifactId>${maven.artifactId}</artifactId>
 *   <version>${maven.version}</version>
 * </dependency>
 * ----
 *
 * In Vert.x a cluster manager is used for various functions including:
 *
 * * Discovery and group membership of Vert.x nodes in a cluster
 * * Maintaining cluster wide topic subscriber lists (so we know which nodes are interested in which event bus addresses)
 * * Distributed Map support
 * * Distributed Locks
 * * Distributed Counters
 *
 * Cluster managers *do not* handle the event bus inter-node transport, this is done directly by Vert.x with TCP connections.
 *
 * == Using this cluster manager
 *
 * If you are using Vert.x from the command line, the jar corresponding to this cluster manager (it will be named `${maven.artifactId}-${maven.version}.jar`
 * should be in the `lib` directory of the Vert.x installation.
 *
 * If you want clustering with this cluster manager in your Vert.x Maven or Gradle project then just add a dependency to
 * the artifact: `${maven.groupId}:${maven.artifactId}:${maven.version}` in your project.
 *
 * If the jar is on your classpath as above then Vert.x will automatically detect this and use it as the cluster manager.
 * Please make sure you don't have any other cluster managers on your classpath or Vert.x might
 * choose the wrong one.
 *
 * You can also specify the cluster manager programmatically if you are embedding Vert.x by specifying it on the options
 * when you are creating your Vert.x instance, for example:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example1()}
 * ----
 *
 * == Configuring this cluster manager
 *
 * Usually the cluster manager is configured by a file
 * https://github.com/vert-x3/vertx-hazelcast/blob/master/src/main/resources/default-cluster.xml[`default-cluster.xml`]
 * which is packaged inside the jar.
 *
 * If you want to override this configuration you can provide a file called `cluster.xml` on your classpath and this
 * will be used instead. If you want to embed the `cluster.xml` file in a fat jar, it must be located at the root of the
 * fat jar. If it's an external file, the **directory** containing the file must be added to the classpath. For
 * example, if you are using the _launcher_ class from Vert.x, the classpath enhancement can be done as follows:
 *
 * [source]
 * ----
 * # If the cluster.xml is in the current directory:
 * java -jar ... -cp . -cluster
 * vertx run MyVerticle -cp . -cluster
 *
 * # If the cluster.xml is in the conf directory
 * java -jar ... -cp conf -cluster
 * ----
 *
 * Another way to override the configuration is by providing the system property `vertx.hazelcast.config` with a
 * location:
 *
 * [source]
 * ----
 * # Use a cluster configuration located in an external file
 * java -Dvertx.hazelcast.config=./config/my-cluster-config.xml -jar ... -cluster
 *
 * # Or use a custom configuration from the classpath
 * java -Dvertx.hazelcast.config=classpath:my/package/config/my-cluster-config.xml -jar ... -cluster
 * ----
 *
 * The `vertx.hazelcast.config` system property, when present, overrides any `cluster.xml` on the classpath, but if
 * loading
 * from this system property fails, then loading falls back to either `cluster.xml` or the Hazelcast default configuration.
 *
 * CAUTION: Configuration of Hazelcast the `-Dhazelcast.config` system property is not supported by Vert.x and should
 * not be used.
 *
 * The xml file is a Hazelcast configuration file and is described in detail in the documentation on the Hazelcast
 * web-site.
 *
 * You can also specify configuration programmatically if embedding:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example2()}
 * ----
 *
 * Hazelcast supports several different transports including multicast and TCP. The default configuration uses
 * multicast so you must have multicast enabled on your network for this to work.
 *
 * For full documentation on how to configure the transport differently or use a different transport please consult the
 * Hazelcast documentation.
 *
 * == Using an existing Hazelcast cluster
 *
 * You can pass an existing `HazelcastInstance` in the cluster manager to reuse an existing cluster:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example3(com.hazelcast.core.HazelcastInstance)}
 * ----
 *
 * In this case, vert.x is not the cluster owner and so do not shutdown the cluster on close.
 *
 * Notice that the custom Hazelcast instance need to be configured with:
 *
 * [source, xml]
 * ----
 * <properties>
 *   <property name="hazelcast.shutdownhook.enabled">false</property>
 * </properties>
 * <multimap name="__vertx.subs">
 *   <backup-count>1</backup-count>
 * </multimap>
 * <map name="__vertx.haInfo">
 *   <time-to-live-seconds>0</time-to-live-seconds>
 *   <max-idle-seconds>0</max-idle-seconds>
 *   <eviction-policy>NONE</eviction-policy>
 *   <max-size policy="PER_NODE">0</max-size>
 *   <eviction-percentage>25</eviction-percentage>
 *   <merge-policy>com.hazelcast.map.merge.LatestUpdateMapMergePolicy</merge-policy>
 * </map>
 * <semaphore name="__vertx.*">
 *   <initial-permits>1</initial-permits>
 * </semaphore>
 * ----
 *
 * **IMPORTANT** Do not use Hazelcast clients or smart clients when using high-availability (HA, or fail-over) in your
 * cluster as they do not notify when they leave the cluster and you may loose data, or leave the cluster in an
 * inconsistent state. See https://github.com/vert-x3/vertx-hazelcast/issues/24 for more details.
 *
 * **IMPORTANT** Make sure Hazelcast is started before and shut down after Vert.x.
 * Also, the Hazelcast shutdown hook should be disabled (see xml example, or via system property).
 *
 * === Changing timeout for failed nodes
 *
 * By default a node will be removed from the cluster if Hazelcast didn't receive a heartbeat for 300 seconds. To change
 * this value `hazelcast.max.no.heartbeat.seconds` system property such as in:
 *
 * ----
 * -Dhazelcast.max.no.heartbeat.seconds=5
 * ----
 *
 * Afterwards a node will be removed from the cluster after 5 seconds without a heartbeat.
 *
 * See http://docs.hazelcast.org/docs/3.6/manual/html-single/index.html#system-properties[Hazelcast
 * system-properties] and
 * http://docs.hazelcast.org/docs/3.6/manual/html-single/index.html#configuring-with-system-properties[configuring Hazelcast
 * with system properties] for the other properties you can configure.
 *
 * == Using Hazelcast async methods
 * Hazelcast's `IMap` and `IAtomicLong` interfaces can be used with async methods returning `ICompletableFuture<V>`,
 * a natural fit for Vert.x threading model. Even though these interfaces have been available for a long time, they are not
 * provided by the public `HazelcastInstance` API.
 *
 * The default behavior of the `HazelcastClusterManager` is to use the public API. Supplying the option
 * `-Dvertx.hazelcast.async-api=true` on JVM startup, will indicate that the async Hazelcast API methods will be used to
 * communicate with the hazelcast cluster. Effectively, this means that when this option is enabled,
 * execution of all `Counter` operations and `AsyncMap.get`,`AsyncMap.put` and `AsyncMap.remove` operations will
 * occur in the calling thread (the event loop), instead of a worker thread with `vertx.executeBlocking`.
 *
 * == Trouble shooting clustering
 *
 * If the default multicast configuration is not working here are some common causes:
 *
 * === Multicast not enabled on the machine.
 *
 * It is quite common in particular on OSX machines for multicast to be disabled by default. Please google for
 * information on how to enable this.
 *
 * === Using wrong network interface
 *
 * If you have more than one network interface on your machine (and this can also be the case if you are running
 * VPN software on your machine), then Hazelcast may be using the wrong one.
 *
 * To tell Hazelcast to use a specific interface you can provide the IP address of the interface in the `interfaces`
 * element of the configuration. Make sure you set the `enabled` attribute to `true`. For example:
 *
 * ----
 * <interfaces enabled="true">
 *   <interface>192.168.1.20</interface>
 * </interfaces>
 * ----
 *
 * When running Vert.x is in clustered mode, you should also make sure that Vert.x knows about the correct interface.
 * When running at the command line this is done by specifying the `cluster-host` option:
 *
 * ----
 * vertx run myverticle.js -cluster -cluster-host your-ip-address
 * ----
 *
 * Where `your-ip-address` is the same IP address you specified in the Hazelcast configuration.
 *
 * If using Vert.x programmatically you can specify this using
 * {@link io.vertx.core.VertxOptions#setClusterHost(java.lang.String)}.
 *
 * === Using a VPN
 *
 * This is a variation of the above case. VPN software often works by creating a virtual network interface which often
 * doesn't support multicast. If you have a VPN running and you do not specify the correct interface to use in both the
 * hazelcast configuration and to Vert.x then the VPN interface may be chosen instead of the correct interface.
 *
 * So, if you have a VPN running you may have to configure both the Hazelcast and Vert.x to use the correct interface as
 * described in the previous section.
 *
 * === When multicast is not available
 *
 * In some cases you may not be able to use multicast as it might not be available in your environment. In that case
 * you should configure another transport, e.g. TCP  to use TCP sockets, or AWS when running on Amazon EC2.
 *
 * For more information on available Hazelcast transports and how to configure them please consult the Hazelcast
 * documentation.
 *
 * === When killing a node makes cluster unbalanced
 * 
 * When a node is killed in not graceful way cluster has to discover node is dead. Hazelcast has a parameter 
 * `hazelcast.max.no.heartbeat.seconds` which defines Maximum timeout of heartbeat in seconds for a node to assume 
 * it is dead. Default value is very long: 300sec. In most cases it should be much below 5sec. 
 * See http://docs.hazelcast.org/docs/3.6/manual/html-single/index.html#system-properties[Hazelcast system-properties] and
 * http://docs.hazelcast.org/docs/3.6/manual/html-single/index.html#configuring-with-system-properties[configuring Hazelcast 
 * with system properties]
 * 
 * === Enabling logging
 *
 * When trouble-shooting clustering issues with Hazelcast it's often useful to get some logging output from Hazelcast
 * to see if it's forming a cluster properly. You can do this (when using the default JUL logging) by adding a file
 * called `vertx-default-jul-logging.properties` on your classpath. This is a standard java.util.logging (JUL)
 * configuration file. Inside it set:
 *
 * ----
 * com.hazelcast.level=INFO
 * ----
 *
 * and also
 *
 * ----
 * java.util.logging.ConsoleHandler.level=INFO
 * java.util.logging.FileHandler.level=INFO
 * ----
 *
 * == Hazelcast logging
 *
 * The logging backend used by Hazelcast is `jdk` by default (understand JUL). If you want to redirect the logging to
 * another library, you need to set the `hazelcast.logging.type` system property such as in:
 *
 * ----
 * -Dhazelcast.logging.type=slf4j
 * ----
 *
 * See the http://docs.hazelcast.org/docs/3.6.1/manual/html-single/index.html#logging-configuration[hazelcast documentation] for more details.
 *
 * == Using a different Hazelcast version
 *
 * You may want to use a different version of Hazelcast. The default version is `${hazelcast.version}`. To do so, you
 * need to:
 *
 * * put the version you want in the application classpath
 * * if you are running a fat jar, configure your build manager to use the right version
 *
 * In this later case, you would need in Maven:
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <dependency>
 *   <groupId>com.hazelcast</groupId>
 *   <artifactId>hazelcast</artifactId>
 *   <version>ENTER_YOUR_VERSION_HERE</version>
 * </dependency>
 * <dependency>
 *   <groupId>${maven.groupId}</groupId>
 *   <artifactId>${maven.artifactId}</artifactId>
 *   <version>${maven.version}</version>
 * </dependency>
 * ----
 *
 * Depending on the version, you may need to exclude some transitive dependencies.
 *
 * On Gradle, you can achieve the same overloading using:
 *
 * [source]
 * ----
 * dependencies {
 *  compile ("${maven.groupId}:${maven.artifactId}:${maven.version}"){
 *    exclude group: 'com.hazelcast', module: 'hazelcast'
 *  }
 *  compile "com.hazelcast:hazelcast:ENTER_YOUR_VERSION_HERE"
 * }
 * ----
 *
 */
@Document(fileName = "index.adoc")
package io.vertx.spi.cluster.hazelcast;

import io.vertx.docgen.Document;
