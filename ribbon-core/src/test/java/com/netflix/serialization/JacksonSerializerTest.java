/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.serialization;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.type.TypeReference;
import org.junit.Test;
import com.google.common.reflect.TypeToken;
import com.google.common.collect.Lists;
import com.netflix.client.http.CaseInsensitiveMultiMap;
import com.netflix.client.http.HttpHeaders;

public class JacksonSerializerTest {    
    @SuppressWarnings("serial")
    @Test
    public void testSerializeList() throws Exception {
        List<Person> people = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            people.add(new Person("person " + i, i));
        }
        JacksonSerializationFactory factory = new JacksonSerializationFactory();
        TypeDef<List<Person>> typeDef = new TypeDef<List<Person>>(){};
        CaseInsensitiveMultiMap headers = new CaseInsensitiveMultiMap();
        headers.addHeader("Content-tYpe", "application/json");
        HttpSerializationContext key = new HttpSerializationContext(headers, null);
        Serializer<List<Person>> serializer = factory.getSerializer(key, typeDef);
        String content = new String(serializeToBytes(people, serializer, null), "UTF-8");
        Deserializer<List<Person>> deserializer = factory.getDeserializer(key, typeDef);
        List<Person> list = deserializer.deserialize(new ByteArrayInputStream(content.getBytes("UTF-8")), new TypeDef<List<Person>>(){});
        assertEquals(people, list);
        Type type = (new TypeToken<List<Person>>(){}).getType();
        list = (List<Person>) deserializer.deserialize(new ByteArrayInputStream(content.getBytes("UTF-8")), 
                (TypeDef<List<Person>>) TypeDef.fromType(type));
        assertEquals(people, list);
        
        Person person = new Person("ribbon", 1);
        Deserializer<Person> personDeserializer = factory.getDeserializer(key, TypeDef.fromClass(Person.class));
        Serializer<Person> personSerializer = factory.getSerializer(key, TypeDef.fromClass(Person.class));
        byte[] bytes = serializeToBytes(person, personSerializer, null);
        Person deserialized = personDeserializer.deserialize(new ByteArrayInputStream(bytes), TypeDef.fromClass(Person.class));
        assertEquals(person, deserialized);
        deserialized = personDeserializer.deserialize(new ByteArrayInputStream(bytes), TypeDef.fromClass(Person.class));
        assertEquals(person, deserialized);
        
        Deserializer<String> stringDeserializer = factory.getDeserializer(key, TypeDef.fromClass(String.class));
        String raw = stringDeserializer.deserialize(new ByteArrayInputStream(bytes), TypeDef.fromClass(String.class));
        assertEquals("{\"name\":\"ribbon\",\"age\":1}", raw);
        
        ObjectMapper mapper = new ObjectMapper();
        deserialized = (Person) mapper.readValue(bytes, TypeDef.fromClass(Person.class).getRawType());
        assertEquals(person, deserialized);
    }
    
    
    private byte[] serializeToBytes(Object obj, Serializer serializer, TypeDef<?> type) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        serializer.serialize(bout, obj, type);
        return bout.toByteArray();
    }
    
    @Test
    public void testTypeSpec() throws Exception {
        TypeDef<Person> spec = TypeDef.fromClass(Person.class);
        assertEquals(Person.class, spec.getRawType());
        Class<?> classDef = spec.getRawType();
        Annotation[] annotations = classDef.getAnnotations();
        assertEquals(1, annotations.length);
        assertEquals(XmlRootElement.class, annotations[0].annotationType());
        System.out.println(spec.getRawType());
        Person person = new Person("ribbon", 1);
        JacksonSerializationFactory factory = new JacksonSerializationFactory();
        CaseInsensitiveMultiMap headers = new CaseInsensitiveMultiMap();
        headers.addHeader("Content-tYpe", "application/json");
        HttpSerializationContext key = new HttpSerializationContext(headers, null);
        Serializer<List<Person>> serializer = factory.getSerializer(key, new TypeDef<List<Person>>(){});
        byte[] bytes = serializeToBytes(person, serializer, null);
        ObjectMapper mapper = new ObjectMapper();
        Person p = (Person) mapper.readValue(bytes, spec.getRawType());
        assertEquals(person, p);
    }
    
    @Test
    public void testSerializeAsSuperType() throws Exception {
        MyObj obj = new MyObj("ribbon", 1, true);
        JacksonSerializationFactory factory = new JacksonSerializationFactory();
        CaseInsensitiveMultiMap headers = new CaseInsensitiveMultiMap();
        headers.addHeader("Content-tYpe", "application/json");
        HttpSerializationContext key = new HttpSerializationContext(headers, null);
        Serializer<Person> serializer = factory.getSerializer(key, TypeDef.fromClass(Person.class));
        String content = new String(serializeToBytes(obj, serializer, TypeDef.fromClass(Person.class)), "UTF-8");
        assertEquals("{\"name\":\"ribbon\",\"age\":1}", content);

        content = new String(serializeToBytes(obj, serializer, null), "UTF-8");
        assertEquals("{\"name\":\"ribbon\",\"age\":1,\"child\":true}", content);
    }
}

@XmlRootElement
class Person {
    public String name;
    public int age;
    public Person() {}
    public Person(String name, int age) {
        super();
        this.name = name;
        this.age = age;
    }
    @Override
    public String toString() {
        return "Person [name=" + name + ", age=" + age + "]";
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + age;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Person other = (Person) obj;
        if (age != other.age)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}

@edu.umd.cs.findbugs.annotations.SuppressWarnings
class MyObj extends Person {
    
    private boolean child;
    
    public MyObj(String name, int age, boolean child) {
        super(name, age);
        this.child = child;
    }
    
    public MyObj() {
        super();
        this.child = false;
    }

    public final boolean isChild() {
        return child;
    }

    public final void setChild(boolean child) {
        this.child = child;
    }
}