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

package com.cloudera.livy

import java.io.File
import java.lang.{Boolean => JBoolean, Long => JLong}
import java.nio.file.Files

import com.cloudera.livy.client.common.ClientConf
import com.cloudera.livy.client.common.ClientConf.ConfEntry

object LivyConf {

  case class Entry(override val key: String, override val dflt: AnyRef) extends ConfEntry

  object Entry {
    def apply(key: String, dflt: Boolean): Entry = Entry(key, dflt: JBoolean)
    def apply(key: String, dflt: Int): Entry = Entry(key, dflt: Integer)
    def apply(key: String, dflt: Long): Entry = Entry(key, dflt: JLong)
  }

  val TEST_MODE = sys.env.get("livy.test").map(_.toBoolean).getOrElse(false)

  val REQUEST_LOG_DIR = Entry("livy.request-log.dir", null)
  val SESSION_FACTORY = Entry("livy.server.session.factory", "process")
  val SPARK_HOME = Entry("livy.server.spark-home", null)
  val SPARK_SUBMIT_KEY = Entry("livy.server.spark-submit", null)
  val SPARK_MASTER_KEY = Entry("livy.server.master", "local")
  val SPARK_DEPLOY_MODE_KEY = Entry("livy.server.deployMode", "client")
  val INTERACTIVE_HEARTBEAT_TIMEOUT = Entry("livy.server.interactive.heartbeat.timeout", "0")
  val IMPERSONATION_ENABLED = Entry("livy.impersonation.enabled", false)
  val LIVY_HOME = Entry("livy.home", null)
  val FILE_UPLOAD_MAX_SIZE = Entry("livy.file.upload.max.size", 100L * 1024 * 1024)

  lazy val TEST_LIVY_HOME = Files.createTempDirectory("livyTemp").toUri.toString
}

/**
 *
 * @param loadDefaults whether to also load values from the Java system properties
 */
class LivyConf(loadDefaults: Boolean) extends ClientConf[LivyConf](null) {

  import LivyConf._

  /**
   * Create a LivyConf that loads defaults from the system properties and the classpath.
   * @return
   */
  def this() = this(true)

  if (loadDefaults) {
    loadFromMap(sys.props)
  }

  def loadFromFile(name: String): LivyConf = {
    Utils.getLivyConfigFile(name)
      .map(Utils.getPropertiesFromFile)
      .foreach(loadFromMap)
    this
  }

  /** Return true if spark.master is set to YARN */
  def isSparkMasterYarn: Boolean = {
    sparkMaster.startsWith("yarn")
  }

  /** Return the location of the spark home directory */
  def sparkHome(): Option[String] = Option(get(SPARK_HOME)).orElse(sys.env.get("SPARK_HOME"))

  def livyHome(): String = {
    Option(get(LIVY_HOME)).orElse(sys.env.get("LIVY_HOME")).getOrElse {
      if (LivyConf.TEST_MODE) {
        LivyConf.TEST_LIVY_HOME
      } else {
        throw new IllegalStateException("livy.home must be specified!")
      }
    }
  }

  /** Return the path to the spark-submit executable. */
  def sparkSubmit(): String = {
    Option(get(SPARK_SUBMIT_KEY))
      .orElse { sparkHome().map { _ + File.separator + "bin" + File.separator + "spark-submit" } }
      .getOrElse("spark-submit")
  }

  /** Return spark.master Livy should use. */
  def sparkMaster: String = {
    get(SPARK_MASTER_KEY)
  }

  /** Return Spark deploy mode Livy should use. */
  def sparkDeployMode: String = {
    get(SPARK_DEPLOY_MODE_KEY)
  }

  /** Return the path where request log should be storeed. None if it's disabled */
  def requestLogDir(): Option[String] = {
    Option(get(REQUEST_LOG_DIR))
  }

  private def loadFromMap(map: Iterable[(String, String)]): Unit = {
    map.foreach { case (k, v) =>
      if (k.startsWith("livy.")) {
        set(k, v)
      }
    }
  }

}
