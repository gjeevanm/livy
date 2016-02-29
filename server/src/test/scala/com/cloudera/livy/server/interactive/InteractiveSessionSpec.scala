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

package com.cloudera.livy.server.interactive

import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import com.fasterxml.jackson.databind.ObjectMapper
import org.json4s.{DefaultFormats, Extraction}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import org.scalatra.test.scalatest.ScalatraSpec

import com.cloudera.livy.{ExecuteRequest, LivyConf}
import com.cloudera.livy.sessions.{PySpark, SessionState}

class InteractiveSessionSpec extends ScalatraSpec with Matchers with BeforeAndAfterAll {

  private val livyConf = new LivyConf()
  livyConf.set("livy.repl.driverClassPath", sys.props("java.class.path"))

  {
    val livyReplJarsPath = s"${System.getProperty("user.dir")}/../repl/target/jars"
    val jars = new File(livyReplJarsPath).listFiles().mkString(",")
    livyConf.set(InteractiveSession.LivyReplJars, jars)
  }

  implicit val formats = DefaultFormats

  private var session: InteractiveSession = null

  private val mapper = new ObjectMapper()
    .registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)

  addServlet(new HttpServlet {
    override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
      try {
        val reqBody = mapper.readValue(req.getInputStream, classOf[CallbackRequest])
        session.url = new URL(reqBody.url)
        info(s"Livy driver is running at ${reqBody.url}")
        resp.setStatus(HttpServletResponse.SC_OK)
      } catch {
        case e: Throwable =>
          info(s"Error in callback servlet. ${e}")
      }
    }
  }, "/callback")

  private def createSession(): InteractiveSession = {
    assume(sys.env.get("SPARK_HOME").isDefined, "SPARK_HOME is not set.")

    val req = new CreateInteractiveRequest()
    req.kind = PySpark()
    req.conf = Map("spark.livy.callbackUrl" -> s"${server.getURI.toString}callback")
    new InteractiveSession(0, null, livyConf, req)
  }

  override def afterAll(): Unit = {
    if (session != null) {
      Await.ready(session.stop(), Duration.Inf)
      session = null
    }
    super.afterAll()
  }

  describe("A spark session") {
    it("should start in the starting or idle state") {
      session = createSession()
      session.state should (equal (SessionState.Starting()) or equal (SessionState.Idle()))
    }

    it("should eventually become the idle state") {
      assume(session != null, "Session not started.")
      session.waitForStateChange(SessionState.Starting(), Duration(30, TimeUnit.SECONDS))
      session.state should equal (SessionState.Idle())
    }

    it("should execute `1 + 2` == 3") {
      assume(session != null, "Session not started.")
      session.waitForStateChange(SessionState.Starting(), Duration(30, TimeUnit.SECONDS))
      val stmt = session.executeStatement(ExecuteRequest("1 + 2"))
      val result = Await.result(stmt.output(), Duration.Inf)

      val expectedResult = Extraction.decompose(Map(
        "status" -> "ok",
        "execution_count" -> 0,
        "data" -> Map(
          "text/plain" -> "3"
        )
      ))

      result should equal (expectedResult)
    }

    it("should report an error if accessing an unknown variable") {
      assume(session != null, "Session not started.")
      session.waitForStateChange(SessionState.Starting(), Duration(30, TimeUnit.SECONDS))
      val stmt = session.executeStatement(ExecuteRequest("x"))
      val result = Await.result(stmt.output(), Duration.Inf)
      val expectedResult = Extraction.decompose(Map(
        "status" -> "error",
        "execution_count" -> 1,
        "ename" -> "NameError",
        "evalue" -> "name 'x' is not defined",
        "traceback" -> List(
          "Traceback (most recent call last):\n",
          "NameError: name 'x' is not defined\n"
        )
      ))

      result should equal (expectedResult)
      session.state should equal (SessionState.Idle())
    }

    it("should error out the session if the interpreter dies") {
      assume(session != null, "Session not started.")
      session.waitForStateChange(SessionState.Starting(), Duration(30, TimeUnit.SECONDS))
      val stmt = session.executeStatement(ExecuteRequest("import os; os._exit(1)"))
      val result = Await.result(stmt.output(), Duration.Inf)
      (session.state match {
        case SessionState.Error(_) => true
        case _ => false
      }) should equal (true)
    }
  }

}
