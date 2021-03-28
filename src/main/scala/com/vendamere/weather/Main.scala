package com.vendamere.weather

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) = {

    val errorMessage: IO[ExitCode] = IO {
      println("Please provide an Open Weather API key as the first parameter.")
      println("See https://openweathermap.org/")
      println("""If you're running from sbt use `sbt "run <api_key>"`.""")
    }.as(ExitCode.Error)

    args.headOption.fold(errorMessage) { apiKey =>
       WeatherServer.stream[IO](apiKey).compile.drain.as(ExitCode.Success)
    }
  }
}
