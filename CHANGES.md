### Version 0.3.4

https://github.com/Netflix/ribbon/pull/62
* Fix Issue #61: FollowRedirects impossible to turn off.

### Version 0.3.3

https://github.com/Netflix/ribbon/pull/60
* add hook for custom ssl factory and AcceptAllSocketFactory implementation

### Version 0.3.2

[pull request 57](https://github.com/Netflix/ribbon/pull/57)
* Make sure NamedConnectionPool instances register with Servo with unique names.

### Version 0.3.1

[pull request 56](https://github.com/Netflix/ribbon/pull/56)
* Fix connection leak when RestClient throws exception on server throttle


### Version 0.3.0 

[pull request 51](https://github.com/Netflix/ribbon/pull/51) 
* Added asynchronous client based on Apache's HttpAsyncClient
* API to support async cancellable back up requests 
* Support Observable APIs from https://github.com/Netflix/RxJava
* Created unified serialization interface to be used by all clients
* Builders for async clients
* ribbion-examples sub-project created
* refactoring of load balancer APIs
* Unify the request and response object used by RestClient to be the same as RibbonHttpAsyncClient
* Replaced dependency on jersey-bundle with jersey-client.

[pull request 54](https://github.com/Netflix/ribbon/pull/54)
* Changed the dependency of HttpAsyncClient to be 4.0 GA
* Enhanced JUnit tests

