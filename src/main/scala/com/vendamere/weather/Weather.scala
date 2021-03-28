package com.vendamere.weather

import cats.effect.Sync
import cats.implicits._
import io.circe.{Encoder, Decoder}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import org.http4s.{EntityEncoder, EntityDecoder}
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._
import org.http4s.Method.GET

case class Latitude(value: Double) extends AnyVal
case class Longitude(value: Double) extends AnyVal

trait Weather[F[_]] {
  def get(lat: Latitude, lon: Longitude): F[Weather.Report]
}

object Weather {

  final case class OpenWeatherError(e: Throwable) extends RuntimeException

  final case class Kelvin(value: Double) extends AnyVal
  final case class WeatherCondition(main: String, description: String)
  final case class CurrentConditions(feelsLike: Kelvin, weather: List[WeatherCondition])
  final case class Alert(description: String)
  final case class Status(current: CurrentConditions, alerts: List[Alert] = Nil)

  final case class Report(weatherConditions: List[String], feels: String, alerts: List[String])

  object Report {
    implicit val customConfig: Configuration = Configuration.default.withDefaults
    implicit val reportEncoder: Encoder[Report] = deriveConfiguredEncoder
    implicit def reportEntityEncoder[F[_]: Sync]: EntityEncoder[F, Report] = jsonEncoderOf
  }

  object Status {
    implicit val customConfig: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults
    implicit val kelvinDecoder: Decoder[Kelvin] = deriveUnwrappedDecoder
    implicit val weatherConditionDecoder: Decoder[WeatherCondition] = deriveConfiguredDecoder
    implicit val currentConditionsDecoder: Decoder[CurrentConditions] = deriveConfiguredDecoder
    implicit val alertDecoder: Decoder[Alert] = deriveConfiguredDecoder
    implicit val statusDecoder: Decoder[Status] = deriveConfiguredDecoder
    implicit def statusEntityDecoder[F[_]: Sync]: EntityDecoder[F, Status] = jsonOf
  }

  private val toComfort: Kelvin => String = {
    case Kelvin(x) if (x < 294.0) => "Cold"
    case Kelvin(x) if (x > 302.0) => "Hot"
    case _ => "Moderate"
  }

  def of[F[_]: Sync](apiKey: String, client: Client[F]): Weather[F] = new Weather[F] {
    val dsl = new Http4sClientDsl[F] {}
    import dsl._
    def get(lat: Latitude, lon: Longitude): F[Report] = {
      val parameters = Map(
        "lat" -> lat.value.toString,
        "lon" -> lon.value.toString,
        "appid" -> apiKey,
        "exclude" -> "minutely,hourly,daily"
      )
      val requestUri = uri"https://api.openweathermap.org/data/2.5/onecall".withQueryParams(parameters)

      client.expect[Status](GET(requestUri))
        .adaptError { case t => OpenWeatherError(t) }
        .map { status =>
          Report(
            weatherConditions = status.current.weather.map { case WeatherCondition(main, description) =>
              s"$main: $description"
            },
            feels = toComfort(status.current.feelsLike),
            alerts = status.alerts.map(_.description)
          )
        }
    }
  }
}
