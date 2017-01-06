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

import com.kenshoo.play.metrics.{Metrics, MetricsImpl}
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import uk.gov.hmrc.play.events.EventRecorder
import uk.gov.hmrc.play.events.handlers.{AlertEventHandler, AuditEventHandler, LoggerEventHandler, MetricsEventHandler}

class TxmEventRecorderModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[Metrics].to[MetricsImpl],
      bind[LoggerEventHandler].to[GuiceLoggerEventHandler],
      bind[AlertEventHandler].to[GuiceAlertEventHandler],
      bind[MetricsEventHandler].to[KenshooMetricsEventHandler],
      bind[AuditEventHandler].to[GuiceAuditEventHandler],
      bind[EventRecorder].to[TxmEventRecorder]
    )
  }

}
