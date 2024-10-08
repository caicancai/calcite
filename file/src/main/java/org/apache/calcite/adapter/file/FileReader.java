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
package org.apache.calcite.adapter.file;

import org.apache.calcite.util.Source;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Scrapes HTML tables from URLs using Jsoup.
 */
public class FileReader implements Iterable<Elements> {

  private final Source source;
  private final @Nullable String selector;
  private final @Nullable Integer index;
  private final Charset charset = StandardCharsets.UTF_8;
  private @Nullable Element tableElement;
  private @Nullable Elements headings;

  public FileReader(Source source, @Nullable String selector,
      @Nullable Integer index) {
    this.source = requireNonNull(source, "source must not be null");
    this.selector = selector;
    this.index = index;
  }

  public FileReader(Source source, String selector) {
    this(source, selector, null);
  }

  public FileReader(Source source) {
    this(source, null, null);
  }

  private void getTable() throws FileReaderException {
    final Document doc;
    try {
      String proto = source.protocol();
      if ("file".equals(proto)) {
        doc = Jsoup.parse(source.file(), this.charset.name());
      } else if (Arrays.asList("http", "https", "ftp").contains(proto)) {
        // known protocols handled by URL
        doc = Jsoup.parse(source.url(), (int) TimeUnit.SECONDS.toMillis(20));
      } else {
        // generically read this source
        doc = Jsoup.parse(source.openStream(), charset.name(), "");
      }
    } catch (IOException e) {
      throw new FileReaderException("Cannot read " + source, e);
    }

    this.tableElement = (this.selector != null && !this.selector.isEmpty())
        ? getSelectedTable(doc, this.selector) : getBestTable(doc);
  }

  private Element getSelectedTable(Document doc, String selector)
      throws FileReaderException {
    // get selected elements
    Elements list = doc.select(selector);

    // get the element
    Element el;

    if (this.index == null) {
      if (list.size() != 1) {
        throw new FileReaderException(list.size()
            + " HTML element(s) selected");
      }

      el = list.first();
    } else {
      el = list.get(this.index);
    }

    // verify element is a table
    if (el.tag().getName().equals("table")) {
      return el;
    } else {
      throw new FileReaderException("selected (" + selector + ") element is a "
          + el.tag().getName() + ", not a table");
    }
  }

  private static Element getBestTable(Document doc) throws FileReaderException {
    Element bestTable = null;
    int bestScore = -1;

    for (Element t : doc.select("table")) {
      int rows = t.select("tr").size();
      Element firstRow = t.select("tr").get(0);
      int cols = firstRow.select("th,td").size();
      int thisScore = rows * cols;
      if (thisScore > bestScore) {
        bestTable = t;
        bestScore = thisScore;
      }
    }

    if (bestTable == null) {
      throw new FileReaderException("no tables found");
    }

    return bestTable;
  }

  void refresh() throws FileReaderException {
    this.headings = null;
    getTable();
  }

  Elements getHeadings() {
    if (this.headings == null) {
      this.iterator();
    }

    return this.headings;
  }

  @Override public FileReaderIterator iterator() {
    if (this.tableElement == null) {
      try {
        getTable();
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    FileReaderIterator iterator =
        new FileReaderIterator(this.tableElement.select("tr"));

    // if we haven't cached the headings, get them
    // TODO: this needs to be reworked to properly cache the headings
    if (/* this.headings == null */ true) {
      // first row must contain headings
      Elements headings = iterator.next("th");
      // if not, generate some default column names
      if (headings.isEmpty()) {
        // rewind and peek at the first row of data
        iterator = new FileReaderIterator(this.tableElement.select("tr"));
        Elements firstRow = iterator.next("td");
        int i = 0;
        headings = new Elements();
        for (Element td : firstRow) {
          Element th = td.clone();
          th.tagName("th");
          th.html("col" + i++);
          headings.add(th);
        }
        // rewind, so queries see the first row
        iterator = new FileReaderIterator(this.tableElement.select("tr"));
      }
      this.headings = headings;
    }

    return iterator;
  }

  public void close() {
  }

  /** Iterates over HTML tables, returning an Elements per row. */
  private static class FileReaderIterator implements Iterator<Elements> {
    final Iterator<Element> rowIterator;

    FileReaderIterator(Elements rows) {
      this.rowIterator = rows.iterator();
    }

    @Override public boolean hasNext() {
      return this.rowIterator.hasNext();
    }

    Elements next(String selector) {
      Element row = this.rowIterator.next();

      return row.select(selector);
    }

    // return th and td elements by default
    @Override public Elements next() {
      return next("th,td");
    }

    @Override public void remove() {
      throw new UnsupportedOperationException("NFW - can't remove!");
    }
  }
}
