Ribbon
======

Ribbon is a client side IPC library that is battle-tested in cloud. It provides the following features

* Load balancing
* Fault tolerance
* Multiple protocol (HTTP, TCP, UDP) support in an asynchronous and reactive model
* Caching and batching

To get ribbon binaries, go to [maven central](http://search.maven.org/#search%7Cga%7C1%7Cribbon). Here is an example to add dependency in Maven:

```xml
<dependency>
    <groupId>com.netflix.ribbon</groupId>
    <artifactId>ribbon</artifactId>
    <version>2.0-RC1</version>
</dependency>
```

## Modules

* ribbon: APIs that integrate load balancing, fault tolerance, caching/batching on top of other ribbon modules and [Hystrix](https://github.com/netflix/hystrix)
* ribbon-loadbalancer: Load balancer APIs that can be used independently or with other modules
* ribbon-eureka: APIs using [Eureka client](https://github.com/netflix/eureka) to provide dynamic server list for cloud
* ribbon-transport: Transport clients that support HTTP, TCP and UDP protocols using [RxNetty](https://github.com/netflix/rxnetty) with load balancing capability
* ribbon-httpclient: REST client built on top of Apache HttpClient integrated with load balancers (deprecated and being replaced by ribbon module)
* ribbon-example: Examples
* ribbon-core: Client configuration APIs and other shared APIs

## Release notes

See https://github.com/Netflix/ribbon/releases

## Code example

### Access HTTP resource using template

```java
HttpResourceGroup httpResourceGroup = Ribbon.createHttpResourceGroup("movieServiceClient",
            ClientOptions.create()
                    .withMaxAutoRetriesNextServer(3)
                    .withConfigurationBasedServerList("localhost:8080,localhost:8088"));
HttpRequestTemplate<ByteBuf> recommendationsByUserIdTemplate = httpResourceGroup.newRequestTemplate("recommendationsByUserId", ByteBuf.class)
            .withMethod("GET")
            .withUriTemplate("/users/{userId}/recommendations")
            .withFallbackProvider(new RecommendationServiceFallbackHandler())
            .withResponseValidator(new RecommendationServiceResponseValidator());
Observable<ByteBuf> result = recommendationsByUserIdTemplate.requestBuilder()
                        .withRequestProperty("userId", “user1")
                        .build()
                        .observe();
```

### Access HTTP resource using annotations

```java
public interface MovieService {
    @Http(
            method = HttpMethod.GET,
            uriTemplate = "/users/{userId}/recommendations",
            )
    @Hystrix(
            validator = RecommendationServiceResponseValidator.class,
            fallbackHandler = RecommendationServiceFallbackHandler.class)
    @CacheProviders(@Provider(key = "{userId}", provider = InMemoryCacheProviderFactory.class))
    RibbonRequest<ByteBuf> recommendationsByUserId(@Var("userId") String userId);
}

MovieService movieService = Ribbon.from(MovieService.class);
Observable<ByteBuf> result = movieService.recommendationsByUserId("user1").observe();
```

### Create a load balancer with Eureka dynamic server list and zone affinity enabled

```java
        IRule rule = new AvailabilityFilteringRule();
        ServerList<DiscoveryEnabledServer> list = new DiscoveryEnabledNIWSServerList("MyVIP:7001");
        ServerListFilter<DiscoveryEnabledServer> filter = new ZoneAffinityServerListFilter<DiscoveryEnabledServer>();
        ZoneAwareLoadBalancer<DiscoveryEnabledServer> lb = LoadBalancerBuilder.<DiscoveryEnabledServer>newBuilder()
                .withDynamicServerList(list)
                .withRule(rule)
                .withServerListFilter(filter)
                .buildDynamicServerListLoadBalancer();   
        DiscoveryEnabledServer server = lb.chooseServer();         
```

### Use load balancing APIs with HttpURLConnection

```java
LoadBalancerExecutor lbExecutor = LoadBalancerBuilder.newBuilder()
	.buildFixedServerListLoadBalancerExecutor(
		Lists.newArrayList(
                new Server("www.google.com", 80),
                new Server("www.linkedin.com", 80),
                new Server("www.yahoo.com", 80))
		);
String statusLine = lbExecutor.execute(new LoadBalancerCommand<String>() {
            @Override
            public String run(Server server) throws Exception {
                URL url = new URL("http://" + server.getHost() + ":" + server.getPort() + "/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                return conn.getResponseMessage();
            }
        });
```

## Questions?

Email ribbon-users@googlegroups.com or [join us](https://groups.google.com/forum/#!forum/ribbon-users)


