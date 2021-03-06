/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.txm.events

import java.time.{Clock, Duration, Instant}
import javax.inject.{Inject, Singleton}

import play.api.http.HeaderNames
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.events.monitoring.EventSource
import uk.gov.hmrc.play.events.{Auditable, EventRecorder}
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


trait TxmMonitor extends EventSource {

  def monitor[T](componentName: String, targetServiceName: String)
                (future: Future[T])
                (implicit hc: HeaderCarrier,
                 ec: ExecutionContext,
                 req: Request[AnyContent],
                 aud: AuditStrategy[T]): Future[T] = future

}


@Singleton
class TxmThirdPartWebServiceCallMonitor @Inject()(eventRecorder: EventRecorder, clock: Clock) extends TxmMonitor with AppName {

  override def source: String = appName

  override def monitor[T](componentName: String, targetServiceName: String)
                         (future: Future[T])
                         (implicit hc: HeaderCarrier,
                          ec: ExecutionContext,
                          req: Request[AnyContent],
                          aud: AuditStrategy[T]): Future[T] = {
    super.monitor(componentName, targetServiceName) {
      val ua = hc.headers.toMap.getOrElse(HeaderNames.USER_AGENT, "undefined")
      val t0 = clock.instant()
      future andThen {
        case Success(t) =>
          recordTime(componentName, targetServiceName, ua, "success", t0)
          recordCount(componentName, targetServiceName, ua, "success")
          recordAudit(componentName, targetServiceName, aud.auditDataOnSuccess(t), aud.auditTagsOnSuccess(t))
        case Failure(e: Exception) =>
          val status = exceptionToStatus(e)
          if (time(e)) recordTime(componentName, targetServiceName, ua, status, t0)
          recordCount(componentName, targetServiceName, ua, status)
          recordAudit(componentName, targetServiceName, aud.auditDataOnFailure(e), aud.auditTagsOnFailure(e))
      }
    }
  }

  private def exceptionToStatus(e: Exception): Any = e match {
    case http: HttpException => http.responseCode
    case _4xx: Upstream4xxResponse => _4xx.reportAs
    case _5xx: Upstream5xxResponse => _5xx.reportAs
    case e: Exception => e.getClass.getSimpleName
  }

  private def time(e: Exception): Boolean = e match {
    case no@(_: BadGatewayException | _: GatewayTimeoutException | _: ServiceUnavailableException) => false
    case _ => true
  }

  private def recordCount(componentName: String, targetServiceName: String, ua: String, status: Any)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext) {
    val event = SimpleKenshooCounterEvent(source, s"$componentName.$targetServiceName.$ua.$status.count")
    eventRecorder.record(event)
  }

  private def recordTime(componentName: String, targetServiceName: String, ua: String, status: Any, startTime: Instant)
                        (implicit hc: HeaderCarrier, ec: ExecutionContext) {
    val duration = Duration.between(startTime, clock.instant())
    val event = SimpleKenshooTimerEvent(source, s"$componentName.$targetServiceName.$ua.$status.time", duration)
    eventRecorder.record(event)
  }

  private def recordAudit(componentName: String, targetServiceName: String,
                          auditData: Map[String, String] = Map.empty, additionalTags: Map[String, String] = Map.empty)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext, req: Request[AnyContent]) {
    if (auditData.nonEmpty) {
      val tags = hc.toAuditTags(targetServiceName, req.uri) ++ additionalTags
      val message = DefaultAuditMessage(source, componentName, tags, auditData)
      eventRecorder.record(message)
    }
  }
}


case class DefaultAuditMessage(source: String,
                               name: String,
                               tags: Map[String, String],
                               privateData: Map[String, String]) extends Auditable


trait AuditStrategy[T] {

  // return a non-empty map to trigger an audit
  def auditDataOnSuccess(resp: T)
                        (implicit hc: HeaderCarrier,
                         req: Request[AnyContent]): Map[String, String] = Map.empty

  // optional additional tags on success audit
  def auditTagsOnSuccess(resp: T)
                        (implicit hc: HeaderCarrier,
                         req: Request[AnyContent]): Map[String, String] = Map.empty

  // return a non-empty map to trigger an audit
  def auditDataOnFailure(e: Exception)
                        (implicit hc: HeaderCarrier,
                         req: Request[AnyContent]): Map[String, String] = Map.empty

  // optional additional tags on failure audit
  def auditTagsOnFailure(e: Exception)
                        (implicit hc: HeaderCarrier,
                         req: Request[AnyContent]): Map[String, String] = Map.empty

}
