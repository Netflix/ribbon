package com.netflix.ribbon.examples.netty.http;


public class LoadBalancingExample {
    /*
    public static void main(String[] args) throws Exception {
        NettyHttpLoadBalancingClient client = NettyHttpClientBuilder.newBuilder()
                .withFixedServerList(Lists.newArrayList(new Server("www.google.com:80"), new Server("www.microsoft.com:80"), new Server("www.yahoo.com:80")))
                .build();
        final CountDownLatch latch = new CountDownLatch(3); 
        Observer<HttpResponse<ByteBuf>> observer = new Observer<HttpResponse<ByteBuf>>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(HttpResponse<ByteBuf> args) {
                latch.countDown();
                System.err.println("Got response: " + args.getStatus()); 
            }
        };
        for (int i = 0; i < 3; i++) {
            // The request is not reusable in RxNetty as its state will be altered. Hence create new 
            // request for each server before the issue is addressed in RxNetty
            HttpRequest<ByteBuf> request = HttpRequest.createGet("/");
            client.createFullHttpResponseObservable(request, null, null).subscribe(observer);
        }
        latch.await();        
        NettyHttpLoadBalancingClient lbClient = (NettyHttpLoadBalancingClient) client;
        System.out.println(((AbstractLoadBalancer) lbClient.getLoadBalancer()).getLoadBalancerStats());
    }
    */
}
