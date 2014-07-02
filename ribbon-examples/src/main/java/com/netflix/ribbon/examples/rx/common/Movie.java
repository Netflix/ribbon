/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.ribbon.examples.rx.common;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Tomasz Bak
 */
public class Movie {

    private static final Pattern FORMAT_RE = Pattern.compile("\\{id='([^']*)', name='([^']*)', category='([^']*)', ageGroup='([^']*)', contentURI='([^']*)'\\}");

    public static final Movie ORANGE_IS_THE_NEW_BLACK = new Movie("1", "Orange is the New Black", "Drama", "Adults", "http://streaming.netflix.com/movies?id=1");
    public static final Movie BREAKING_BAD = new Movie("2", "Breaking Bad", "Crime", "Adults", "http://streaming.netflix.com/movies?id=2");
    public static final Movie HOUSE_OF_CARDS = new Movie("3", "House of Cards", "Political", "Adults", "http://streaming.netflix.com/movies?id=3");

    private final String id;
    private final String name;
    private final String category;
    private final String ageGroup;
    private final String contentURI;

    public Movie(String id, String name, String category, String ageGroup, String contentURI) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.ageGroup = ageGroup;
        this.contentURI = contentURI;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getContentURI() {
        return contentURI;
    }

    public String getAgeGroup() {
        return ageGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Movie movie = (Movie) o;

        if (ageGroup != null ? !ageGroup.equals(movie.ageGroup) : movie.ageGroup != null) return false;
        if (category != null ? !category.equals(movie.category) : movie.category != null) return false;
        if (contentURI != null ? !contentURI.equals(movie.contentURI) : movie.contentURI != null) return false;
        if (id != null ? !id.equals(movie.id) : movie.id != null) return false;
        if (name != null ? !name.equals(movie.name) : movie.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (ageGroup != null ? ageGroup.hashCode() : 0);
        result = 31 * result + (contentURI != null ? contentURI.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return '{' +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", ageGroup='" + ageGroup + '\'' +
                ", contentURI='" + contentURI + '\'' +
                '}';
    }

    public static Movie from(String formatted) {
        Matcher matcher = FORMAT_RE.matcher(formatted);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Syntax error in movie string: " + formatted);
        }
        return new Movie(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5));
    }

    public static Movie from(ByteBuf byteBuf) {
        return from(byteBuf.toString(Charset.defaultCharset()));
    }
}
