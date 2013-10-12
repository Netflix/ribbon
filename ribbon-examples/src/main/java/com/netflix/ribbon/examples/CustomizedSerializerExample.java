package com.netflix.ribbon.examples;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.netflix.client.BufferedResponseCallback;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.ribbon.examples.server.ServerResources.Person;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.Serializer;

public class CustomizedSerializerExample extends ExampleAppWithLocalResource {
    @Override
    public void run() throws Exception {
        URI uri = new URI(SERVICE_URI + "testAsync/person");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        client.setSerializationFactory(new GsonSerializationFactory());
        Future<HttpResponse> future = client.execute(request, new BufferedResponseCallback<HttpResponse>() {
            @Override
            public void failed(Throwable e) {
            }
            
            @Override
            public void completed(HttpResponse response) {
                try {
                    Person person = response.getEntity(Person.class);
                    System.out.println(person);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    response.close();
                }
            }
            
            @Override
            public void cancelled() {
            }
        });
        future.get();
        
    }

    public static void main(String[] args) throws Exception {
        CustomizedSerializerExample app = new CustomizedSerializerExample();
        app.runApp();
    }

}

class GsonSerializationFactory implements SerializationFactory<ContentTypeBasedSerializerKey>{

    static final GsonCodec instance = new GsonCodec();
    @Override
    public Optional<Deserializer> getDeserializer(ContentTypeBasedSerializerKey key) {
        if (key.getContentType().equalsIgnoreCase("application/json")) {
            return Optional.<Deserializer>of(instance);
        }
        return Optional.absent();
    }

    @Override
    public Optional<Serializer> getSerializer(ContentTypeBasedSerializerKey key) {
        if (key.getContentType().equalsIgnoreCase("application/json")) {
            return Optional.<Serializer>of(instance);
        }
        return Optional.absent();
    }

}

class GsonCodec implements Serializer, Deserializer {
    static Gson gson = new Gson();

    @Override
    public <T> T deserialize(InputStream in, TypeToken<T> type)
            throws IOException {
        System.out.println("Deserializing using Gson");
        return gson.fromJson(new InputStreamReader(in), type.getType());
    }
    
    @Override
    public void serialize(OutputStream out, Object object) throws IOException {
        gson.toJson(object, new OutputStreamWriter(out));
    }
}

