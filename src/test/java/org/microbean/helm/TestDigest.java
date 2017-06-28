/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017 MicroBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.helm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.net.URL;

import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.DatatypeConverter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestDigest {

  public TestDigest() {
    super();
  }

  @Test
  public void testDigest() throws IOException, NoSuchAlgorithmException {
    final URL redisChart = Thread.currentThread().getContextClassLoader().getResource("redis-0.5.1/redis-0.5.1.tgz");
    assertNotNull(redisChart);
    final URL digest = Thread.currentThread().getContextClassLoader().getResource("redis-0.5.1/digest");
    assertNotNull(digest);
    assertTrue(digest.getProtocol().equals("file"));
    String expectedDigest = null;
    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(digest.openStream(), StandardCharsets.UTF_8))) {
      assertNotNull(reader);
      expectedDigest = reader.readLine();
      assertNotNull(expectedDigest);
      expectedDigest = expectedDigest.toUpperCase();
    }
    final MessageDigest md = MessageDigest.getInstance("SHA-256");
    assertNotNull(md);
    ByteBuffer buffer = null;
    try (final InputStream rawInputStream = redisChart.openStream();
         final BufferedInputStream stream = rawInputStream instanceof BufferedInputStream ? (BufferedInputStream)rawInputStream : new BufferedInputStream(rawInputStream)) {
      assertNotNull(rawInputStream);
      assertNotNull(stream);
      buffer = readByteBuffer(stream);
    }
    assertNotNull(buffer);
    md.update(buffer);
    assertEquals(expectedDigest, DatatypeConverter.printHexBinary(md.digest()));
  }

  private static final ByteBuffer readByteBuffer(final InputStream stream) throws IOException {
    return ByteBuffer.wrap(read(stream));
  }
  
  private static final byte[] read(final InputStream stream) throws IOException {
    byte[] returnValue = null;
    if (stream != null) {
      try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
        int bytesRead;
        final byte[] data = new byte[4096];
        while ((bytesRead = stream.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, bytesRead);
        }
        buffer.flush();
        returnValue = buffer.toByteArray();
      }
    }
    return returnValue;
  }
  
}
