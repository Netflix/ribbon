This example contains implementation of a simple movie service. Three different clients are implemented with
equivalent functionality, but on top of different Ribbon API layers:

Example | Description
--------|-------------
[RxMovieProxyExample](proxy)         | Ribbon proxy based implementation.
[RxMovieTemplateExample](template)   | Ribbon template based implementation.
[RxMovieTransportExample](transport) | An implementation using directly RxNetty load balancing HTTP client.

Before running any of those examples, [RxMovieServer](RxMovieServer.java) must be started first.
