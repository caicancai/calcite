/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.test;

import org.apache.calcite.util.Filterator;
import org.apache.calcite.util.Util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link Filterator}.
 */
class FilteratorTest {
  //~ Methods ----------------------------------------------------------------

  @Test void testOne() {
    final List<String> tomDickHarry = Arrays.asList("tom", "dick", "harry");
    final Filterator<String> filterator =
        new Filterator<String>(tomDickHarry.iterator(), String.class);

    // call hasNext twice
    assertTrue(filterator.hasNext());
    assertTrue(filterator.hasNext());
    assertThat(filterator.next(), is("tom"));

    // call next without calling hasNext
    assertThat(filterator.next(), is("dick"));
    assertTrue(filterator.hasNext());
    assertThat(filterator.next(), is("harry"));
    assertFalse(filterator.hasNext());
    assertFalse(filterator.hasNext());
  }

  @Test void testNulls() {
    // Nulls don't cause an error - but are not emitted, because they
    // fail the instanceof test.
    final List<String> tomDickHarry = Arrays.asList("paul", null, "ringo");
    final Filterator<String> filterator =
        new Filterator<String>(tomDickHarry.iterator(), String.class);
    assertThat(filterator.next(), is("paul"));
    assertThat(filterator.next(), is("ringo"));
    assertFalse(filterator.hasNext());
  }

  @Test void testSubtypes() {
    final ArrayList arrayList = new ArrayList();
    final HashSet hashSet = new HashSet();
    final LinkedList linkedList = new LinkedList();
    Collection[] collections = {
        null,
        arrayList,
        hashSet,
        linkedList,
        null,
    };
    final Filterator<List> filterator =
        new Filterator<List>(
            Arrays.asList(collections).iterator(),
            List.class);
    assertTrue(filterator.hasNext());

    // skips null
    assertThat(arrayList, is(filterator.next()));

    // skips the HashSet
    assertThat(linkedList, is(filterator.next()));
    assertFalse(filterator.hasNext());
  }

  @Test void testBox() {
    final Number[] numbers = {1, 2, 3.14, 4, null, 6E23};
    List<Integer> result = new ArrayList<Integer>();
    for (int i : Util.filter(Arrays.asList(numbers), Integer.class)) {
      result.add(i);
    }
    assertThat(result, hasToString("[1, 2, 4]"));
  }
}
