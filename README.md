ribbon
======

Ribbon plays an critical role in supporting inter-process communication in the cloud. The library includes Netflix's client side load balancers and clients for middle tier services. 

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
* ribbon-httpclient: includes the implementation of REST client integrated with load balancers. It also includes the sample application in its test sources.

Please note that the default client configured in ribbon-core is the REST client in ribbon-httpclient. 
Unless this is changed in client applications, ribbon-core has runtime dependency on ribbon-httpclient.
