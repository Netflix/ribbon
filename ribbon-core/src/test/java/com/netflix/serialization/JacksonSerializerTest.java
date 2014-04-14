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
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import com.google.common.reflect.TypeToken;
import com.google.common.collect.Lists;

public class JacksonSerializerTest {    
    @SuppressWarnings("serial")
    @Test
    public void testSerializeList() throws Exception {
        List<Person> people = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            people.add(new Person("person " + i, i));
        }
        JacksonSerializationFactory factory = new JacksonSerializationFactory();
        ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey("application/json", new TypeToken<List<Person>>(){});
        Serializer serializer = factory.getSerializer(key).get();
        String content = new String(serializeToBytes(people, serializer), "UTF-8");
        Deserializer deserializer = factory.getDeserializer(key).get();
        List<Person> list = deserializer.deserialize(new ByteArrayInputStream(content.getBytes("UTF-8")), new TypeToken<List<Person>>(){});
        assertEquals(people, list);
        Person person = new Person("ribbon", 1);
        byte[] bytes = serializeToBytes(person, serializer);
        Person deserialized = deserializer.deserialize(new ByteArrayInputStream(bytes), TypeToken.of(Person.class));
        assertEquals(person, deserialized);
        deserialized = deserializer.deserialize(new ByteArrayInputStream(bytes), TypeToken.of(Person.class));
        assertEquals(person, deserialized);
        
        ObjectMapper mapper = new ObjectMapper();
        deserialized = (Person) mapper.readValue(bytes, TypeToken.of(Person.class).getRawType());
        assertEquals(person, deserialized);
    }
    
    
    private byte[] serializeToBytes(Object obj, Serializer serializer) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        serializer.serialize(bout, obj);
        return bout.toByteArray();
    }
}

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
