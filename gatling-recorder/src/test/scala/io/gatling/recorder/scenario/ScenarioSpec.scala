/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.recorder.scenario

import java.net.URI

import org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import io.gatling.http.fetch.{ CssResource, RegularResource }
import io.gatling.recorder.config.ConfigKeys.http.{ FetchHtmlResources, FollowRedirect }
import io.gatling.recorder.config.RecorderConfiguration.fakeConfig

@RunWith(classOf[JUnitRunner])
class ScenarioSpec extends Specification {

  "Scenario" should {

    implicit val config = fakeConfig(Map(FollowRedirect -> true, FetchHtmlResources -> true))

    "remove HTTP redirection " in {
      val r1 = RequestElement("http://gatling.io/", "GET", Map.empty, None, 200, List.empty)
      val r2 = RequestElement("http://gatling.io/rn1.html", "GET", Map.empty, None, 302, List.empty)
      val r3 = RequestElement("http://gatling.io/release-note-1.html", "GET", Map.empty, None, 200, List.empty)
      val r4 = RequestElement("http://gatling.io/details.html", "GET", Map.empty, None, 200, List.empty)

      val scn = ScenarioDefinition(List(1l -> r1, 2l -> r2, 3l -> r3, 4l -> r4), List.empty)
      scn.elements should beEqualTo(List(r1, r2.copy(statusCode = 200), r4))
    }

    "filter out embedded resources of HTML documents" in {
      val r1 = RequestElement("http://gatling.io", "GET", Map.empty, None, 200,
        List(CssResource(new URI("http://gatling.io/main.css")), RegularResource(new URI("http://gatling.io/img.jpg"))))
      val r2 = RequestElement("http://gatling.io/main.css", "GET", Map.empty, None, 200, List.empty)
      val r3 = RequestElement("http://gatling.io/details.html", "GET", Map(CONTENT_TYPE -> "text/html;charset=UTF-8"), None, 200, List.empty)
      val r4 = RequestElement("http://gatling.io/img.jpg", "GET", Map.empty, None, 200, List.empty)
      val r5 = RequestElement("http://gatling.io", "GET", Map.empty, None, 200,
        List(CssResource(new URI("http://gatling.io/main.css"))))
      val r6 = RequestElement("http://gatling.io/main.css", "GET", Map.empty, None, 200, List.empty)

      val scn = ScenarioDefinition(List(1l -> r1, 2l -> r2, 3l -> r3, 4l -> r4, 5l -> r5, 6l -> r6), List.empty)
      scn.elements should beEqualTo(List(r1, r3, r5))
    }
  }

}
