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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Tomasz Bak
 */
public class Recommendations {
    private static final Pattern FORMAT_RE = Pattern.compile("\\{movies=\\[(\\{[^\\}]*\\})?(, \\{[^\\}]*\\})*\\]\\}");

    private final List<Movie> movies;

    public Recommendations(List<Movie> movies) {
        this.movies = Collections.unmodifiableList(movies);
    }

    public List<Movie> getMovies() {
        return movies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Recommendations that = (Recommendations) o;

        if (movies != null ? !movies.equals(that.movies) : that.movies != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return movies != null ? movies.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "{movies=" + movies + '}';
    }

    public static Recommendations from(String formatted) {
        Matcher matcher = FORMAT_RE.matcher(formatted);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Syntax error in recommendations string: " + formatted);
        }
        List<Movie> movies = new ArrayList<Movie>();
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String movie = matcher.group(i);
            if (movie.startsWith(",")) {
                movie = movie.substring(1).trim();
            }
            movies.add(Movie.from(movie));
        }
        return new Recommendations(movies);
    }
}
