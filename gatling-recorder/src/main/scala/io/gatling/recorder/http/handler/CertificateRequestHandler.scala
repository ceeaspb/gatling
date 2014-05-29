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
package io.gatling.recorder.http.handler

import scala.collection.JavaConversions.asScalaBuffer
import com.ning.http.util.Base64
import com.typesafe.scalalogging.slf4j.StrictLogging
import java.net.{ URI, InetSocketAddress }
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.channel.{ Channel, ChannelHandlerContext, ExceptionEvent, MessageEvent, SimpleChannelHandler }
import org.jboss.netty.handler.codec.http.{ DefaultHttpRequest, HttpRequest }
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.handler.stream.ChunkedFile
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import io.gatling.recorder.http.channel.BootstrapFactory
import io.gatling.http.HeaderNames
import io.gatling.recorder.http.HttpProxy
import io.gatling.recorder.util.URIHelper

class CertificateRequestHandler() extends SimpleChannelUpstreamHandler with StrictLogging {

  val path = "gatlingCA.crt"
  val size = 8192

  override def messageReceived(context: ChannelHandlerContext, event: MessageEvent) {

    try {
      event.getMessage match {
        case request: HttpRequest => {

          if (request.getUri().endsWith(path)) {

            try {
              // as the request is for the cert we don't want the proxy logic to be hit
              context.getPipeline().remove(BootstrapFactory.ConditionalHandlerName);
            } catch { case ignore: Exception => logger.debug("tried to remove non existent conditional handler" + ignore) }

            val response = new DefaultHttpResponse(HTTP_1_1, OK)
            context.getChannel().write(response)

            try {
              val url = this.getClass().getClassLoader.getResource(path)
              val file = new File(url.toURI())

              val raf = new RandomAccessFile(file, "r");
              val fileLength = raf.length();
              val writeFuture = context.getChannel().write(new ChunkedFile(raf, 0, fileLength, size))
              writeFuture.addListener { ChannelFutureListener.CLOSE }

            } catch {
              case e: Exception =>
                println("error with cert file handling : " + e)
            }
          }

        }
      }
    } finally {
      Channels.fireMessageReceived(context, event.getMessage)
    }
  }

}
