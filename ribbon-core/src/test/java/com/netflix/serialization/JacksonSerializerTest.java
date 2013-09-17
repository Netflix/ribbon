package com.netflix.serialization;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import com.google.common.reflect.TypeToken;
import com.google.common.collect.Lists;

public class JacksonSerializerTest {    
    @Test
    public void testSerializeList() throws Exception {
        List<Person> people = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            people.add(new Person("person " + i, i));
        }
        JacksonSerializationFactory factory = new JacksonSerializationFactory();
        ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey("application/json", new TypeToken<List<Person>>(){});
        Serializer serializer = factory.getSerializer(key).get();
        String content = new String(serializer.serialize(people), "UTF-8");
        Deserializer deserializer = factory.getDeserializer(key).get();
        List<Person> list = deserializer.deserialize(content.getBytes("UTF-8"), new TypeToken<List<Person>>(){});
        assertEquals(people, list);
        Person person = new Person("ribbon", 1);
        byte[] bytes = serializer.serialize(person);
        Person deserialized = deserializer.deserialize(bytes, TypeToken.of(Person.class));
        assertEquals(person, deserialized);
        deserialized = deserializer.deserialize(bytes, Person.class);
        assertEquals(person, deserialized);
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
