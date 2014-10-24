# Ribbon Module

Ribbon module contains APIs that integrate load balancing, fault tolerance, caching/batching on top of other 
ribbon modules and [Hystrix](https://github.com/netflix/hystrix).

# Ribbon Annotations

A Ribbon client can be created from a Java interface annotated with Ribbon 
[annotations](src/main/java/com/netflix/ribbon/proxy/annotation), like in the example below. A dynamic proxy
is created which translates interface methods invocations into Ribbon requests. Method parameters annotated with
```@Content``` annotation are automatically converted into wire format using provided ```ContentTransformer``` class.
Replies as of now are limited to ```ByteBuf``` type only, which contains exact copy of the HTTP response payload. 
This restrictions are imposed by existing RxNetty capabilities and will be solved once RxNetty serialization
framework is complete.
 
This code snippet is taken from [ribbon-examples](../ribbon-examples) package and can be found 
[here](../ribbon-examples/src/main/java/com/netflix/ribbon/examples/rx/proxy).

```java
@ResourceGroup(resourceGroupClass = SampleHttpResourceGroup.class)
public interface MovieService {
    @TemplateName("recommendations")
    @Http(method = HttpMethod.GET, uri = "/users/{userId}/recommendations")
    @Hystrix( validator = RecommendationServiceResponseValidator.class,
              fallbackHandler = RecommendationServiceFallbackHandler.class)
    @CacheProviders(@Provider(key = "{userId}", provider = InMemoryCacheProviderFactory.class))
    RibbonRequest<ByteBuf> recommendationsByUserId(@Var("userId") String userId);
    
    @TemplateName("registerMovie")
    @Http( method = HttpMethod.POST, uri = "/movies")
    @Hystrix(validator = RecommendationServiceResponseValidator.class)
    @ContentTransformerClass(RxMovieTransformer.class)
    RibbonRequest<ByteBuf> registerMovie(@Content Movie movie);
}

MovieService movieService = Ribbon.from(MovieService.class);
Observable<ByteBuf> result = movieService.recommendationsByUserId("user1").toObservable();
```
