ribbon
======

Ribbon plays an critical role in supporting inter-process communication in the cloud. The library includes the Netflix' client side load balancers and clients for middle tier services. 

Ribbon provides the following features:

* Multiple and pluggable load balancing rules
* Integration with service discovery
* Built-in failure resiliency
* Cloud enabled
* Clients integrated with load balancers
* Configuration based client factory

There are three sub projects:

* ribbon-core: includes load balancer and client interface definitions, common load balancer implementations, integration of client with load balancers and client factory.
* ribbon-eureka: includes load balancer implementations based on Eureka client, which is the library for service registration and discovery.
* ribbon-httpclient: includes the implementation of REST client integrated with load balancers.
