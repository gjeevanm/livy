/*
 * Licensed to Cloudera, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.livy.test.framework

import java.io.File

import scala.annotation.tailrec
import scala.concurrent.duration._

import org.apache.commons.io.FileUtils

object TestUtils {
  @tailrec
  def retry[R](deadline: Deadline, f: () => R): R = {
    try {
      f()
    } catch {
      case e: Exception =>
        if (deadline.isOverdue()) {
          throw e
        }
        Thread.sleep(1.second.toMillis)
        retry(deadline, f)
    }
  }

  def saveTestSourceToTempFile(fileName: String): File = {
    val testResourceStream = getClass.getClassLoader.getResourceAsStream(fileName)
    assert(testResourceStream != null, "Cannot find $fileName in test resource.")

    val tmpFile = File.createTempFile("livy-int-test-", fileName)
    FileUtils.copyInputStreamToFile(testResourceStream, tmpFile)
    tmpFile.deleteOnExit()

    tmpFile
  }
}
