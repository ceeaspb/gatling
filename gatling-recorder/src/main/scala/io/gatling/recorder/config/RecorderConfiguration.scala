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
package io.gatling.recorder.config

import java.io.{ File => JFile }

import scala.collection.JavaConversions._
import scala.concurrent.duration.{ Duration, DurationInt }
import scala.reflect.io.Path.jfile2path
import scala.tools.nsc.io.File
import scala.util.Properties.userHome

import com.typesafe.config.{ ConfigFactory, Config, ConfigRenderOptions }
import com.typesafe.scalalogging.slf4j.StrictLogging

import io.gatling.core.config.{ GatlingConfiguration, GatlingFiles }
import io.gatling.core.filter.{ BlackList, Filters, WhiteList }
import io.gatling.core.util.IO
import io.gatling.core.util.StringHelper.RichString
import io.gatling.recorder.enumeration.FilterStrategy
import io.gatling.recorder.enumeration.FilterStrategy.FilterStrategy

object RecorderConfiguration extends IO with StrictLogging {

  implicit class IntOption(val value: Int) extends AnyVal {
    def toOption = if (value != 0) Some(value) else None
  }

  val remove4Spaces = """\s{4}""".r

  val renderOptions = ConfigRenderOptions.concise.setFormatted(true).setJson(false)

  var configFile: Option[JFile] = None

  var configuration: RecorderConfiguration = _

  GatlingConfiguration.setUp()

  private[this] def getClassLoader = Thread.currentThread.getContextClassLoader
  private[this] def getDefaultConfig(classLoader: ClassLoader) = ConfigFactory.parseResources(classLoader, "recorder-defaults.conf")

  def fakeConfig(props: Map[String, _]): RecorderConfiguration = {
    val defaultConfig = getDefaultConfig(getClassLoader)
    buildConfig(ConfigFactory.parseMap(props).withFallback(defaultConfig))
  }

  def initialSetup(props: Map[String, _], recorderConfigFile: Option[File] = None) {
    val classLoader = getClassLoader
    val defaultConfig = getDefaultConfig(classLoader)
    configFile = recorderConfigFile.map(_.jfile).orElse(Option(classLoader.getResource("recorder.conf")).map(url => new JFile(url.getFile)))

    val customConfig = configFile.map(ConfigFactory.parseFile).getOrElse {
      // Should only happens with a manually (and incorrectly) updated Maven archetype or SBT template
      println("Maven archetype or SBT template outdated: Please create a new one or check the migration guide on how to update it.")
      println("Recorder preferences won't be saved until then.")
      ConfigFactory.empty
    }
    val propertiesConfig = ConfigFactory.parseMap(props)

    try {
      configuration = buildConfig(ConfigFactory.systemProperties
        .withFallback(propertiesConfig).withFallback(customConfig).withFallback(defaultConfig))
      logger.debug(s"configured $configuration")
    } catch {
      case e: Exception =>
        logger.warn(s"Loading configuration crashed: ${e.getMessage}. Probable cause is a format change, resetting.")
        configFile.foreach(_.delete)
        configuration = buildConfig(ConfigFactory.systemProperties.withFallback(propertiesConfig).withFallback(defaultConfig))
    }
  }

  def reload(props: Map[String, _]) {
    val frameConfig = ConfigFactory.parseMap(props)
    configuration = buildConfig(frameConfig.withFallback(configuration.config))
  }

  def saveConfig() {
    // Remove request bodies folder configuration (transient), keep only Gatling-related properties
    val configToSave = configuration.config.withoutPath(ConfigKeys.core.RequestBodiesFolder).root.withOnlyKey(ConfigKeys.ConfigRoot)
    configFile.foreach(file => withCloseable(File(file).bufferedWriter)(_.write(configToSave.render(renderOptions))))
  }

  private def buildConfig(config: Config): RecorderConfiguration = {
    import ConfigKeys._

      def getOutputFolder(folder: String) = {
        folder.trimToOption match {
          case Some(f)                               => f
          case _ if sys.env.contains("GATLING_HOME") => GatlingFiles.sourcesDirectory.toString
          case _                                     => userHome
        }
      }

      def getRequestBodiesFolder =
        if (config.hasPath(core.RequestBodiesFolder))
          config.getString(core.RequestBodiesFolder)
        else
          GatlingFiles.requestBodiesDirectory.toString

    RecorderConfiguration(
      core = CoreConfiguration(
        encoding = config.getString(core.Encoding),
        outputFolder = getOutputFolder(config.getString(core.SimulationOutputFolder)),
        requestBodiesFolder = getRequestBodiesFolder,
        pkg = config.getString(core.Package),
        className = config.getString(core.ClassName),
        thresholdForPauseCreation = config.getInt(core.ThresholdForPauseCreation) milliseconds,
        saveConfig = config.getBoolean(core.SaveConfig)),
      filters = FiltersConfiguration(
        filterStrategy = FilterStrategy.withName(config.getString(filters.FilterStrategy)),
        whiteList = WhiteList(config.getStringList(filters.WhitelistPatterns).toList),
        blackList = BlackList(config.getStringList(filters.BlacklistPatterns).toList)),
      http = HttpConfiguration(
        automaticReferer = config.getBoolean(http.AutomaticReferer),
        followRedirect = config.getBoolean(http.FollowRedirect),
        fetchHtmlResources = config.getBoolean(http.FetchHtmlResources)),
      proxy = ProxyConfiguration(
        port = config.getInt(proxy.Port),
        sslPort = config.getInt(proxy.SslPort),
        outgoing = OutgoingProxyConfiguration(
          host = config.getString(proxy.outgoing.Host).trimToOption,
          username = config.getString(proxy.outgoing.Username).trimToOption,
          password = config.getString(proxy.outgoing.Password).trimToOption,
          port = config.getInt(proxy.outgoing.Port).toOption,
          sslPort = config.getInt(proxy.outgoing.SslPort).toOption)),
      config)
  }
}

case class FiltersConfiguration(
    filterStrategy: FilterStrategy,
    whiteList: WhiteList,
    blackList: BlackList) {

  def filters: Option[Filters] = filterStrategy match {
    case FilterStrategy.DISABLED        => None
    case FilterStrategy.BLACKLIST_FIRST => Some(Filters(blackList, whiteList))
    case FilterStrategy.WHITELIST_FIRST => Some(Filters(whiteList, blackList))
  }
}

case class CoreConfiguration(
  encoding: String,
  outputFolder: String,
  requestBodiesFolder: String,
  pkg: String,
  className: String,
  thresholdForPauseCreation: Duration,
  saveConfig: Boolean)

case class HttpConfiguration(
  automaticReferer: Boolean,
  followRedirect: Boolean,
  fetchHtmlResources: Boolean)

case class OutgoingProxyConfiguration(
  host: Option[String],
  username: Option[String],
  password: Option[String],
  port: Option[Int],
  sslPort: Option[Int])

case class ProxyConfiguration(
  port: Int,
  sslPort: Int,
  outgoing: OutgoingProxyConfiguration)

case class RecorderConfiguration(
  core: CoreConfiguration,
  filters: FiltersConfiguration,
  http: HttpConfiguration,
  proxy: ProxyConfiguration,
  config: Config)
