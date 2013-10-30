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

