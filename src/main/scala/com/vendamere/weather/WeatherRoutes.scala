package com.vendamere.weather

import cats.effect.Sync
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import scala.util.Try

object WeatherRoutes {

  object LatitudeVar {
    def unapply(s: String): Option[Latitude] =
      Try(Latitude(s.toDouble)).toOption.filter { case Latitude(value) => value >= -90.0 && value <= 90.0 }
  }

  object LongitudeVar {
    def unapply(s: String): Option[Longitude] =
      Try(Longitude(s.toDouble)).toOption.filter { case Longitude(value) => value >= -180.0 && value <= 180.0 }
  }

  def weatherRoutes[F[_]: Sync](weather: Weather[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "weather" / "lat" / LatitudeVar(lat) / "lon" / LongitudeVar(lon) =>
        weather.get(lat, lon).flatMap(Ok(_))
    }
  }
}
