package com.socrata.regioncoder

import com.socrata.geospace.lib.Utils._
import com.socrata.regioncoder.config.RegionCoderConfig
import com.socrata.soda.external.SodaFountainClient
import javax.servlet.http.{HttpServletResponse => HttpStatus}
import org.scalatra.{BadRequest, Ok, AsyncResult}

class RegionCoderServlet(rcConfig: RegionCoderConfig, val sodaFountain: SodaFountainClient)
  extends RegionCoderStack with RegionCoder {

  val cacheConfig = rcConfig.cache
  val partitionXsize = rcConfig.partitioning.sizeX
  val partitionYsize = rcConfig.partitioning.sizeY

  get("/") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say <a href="hello-scalate">hello to Scalate</a>.
      </body>
    </html>
  }

  get("/version") {
    Map("version" -> BuildInfo.version,
      "scalaVersion" -> BuildInfo.scalaVersion,
      "dependencies" -> BuildInfo.libraryDependencies,
      "buildTime" -> new org.joda.time.DateTime(BuildInfo.buildTime).toString())
  }

  // Request body is a JSON array of points. Each point is an array of length 2.
  // Example: [[-87.6847,41.8369],[-122.3331,47.6097],...]
  post("/v1/regions/:resourceName/pointcode") {
    val points = parsedBody.extract[Seq[Seq[Double]]]
    if (points.isEmpty) {
      halt(HttpStatus.SC_BAD_REQUEST, s"Could not parse '${request.body}'.  Must be in the form [[x, y]...]")
    }
    new AsyncResult {
      override val timeout = rcConfig.shapePayloadTimeout
      val is = timer.time { regionCodeByPoint(params("resourceName"), points) } // scalastyle:ignore
    }
  }

  post("/v1/regions/:resourceName/stringcode") {
    val strings = parsedBody.extract[Seq[String]]
    if (strings.isEmpty) halt(HttpStatus.SC_BAD_REQUEST,
      s"""Could not parse '${request.body}'.  Must be in the form ["98102","98101",...]""")
    val column = params.getOrElse("column", halt(BadRequest("Missing param 'column'")))

    new AsyncResult {
      override val timeout = rcConfig.shapePayloadTimeout
      val is = timer.time { regionCodeByString(params("resourceName"), column, strings) }
    }
  }

  // scalastyle:off multiple.string.literals
  // DEBUGGING ROUTE : Returns a JSON blob with info about all currently cached regions
  get("/v1/regions") {
    Map("spatialCache" -> spatialCache.indicesBySizeDesc().map {
      case (key, size) => Map("resource" -> key, "numCoordinates" -> size) },
      "stringCache"  -> stringCache.indicesBySizeDesc().map {
        case (key, size) => Map("resource" -> key, "numRows" -> size) })
  }

  // DEBUGGING ROUTE : Clears the region cache
  delete("/v1/regions") {
    resetRegionState()
    logMemoryUsage("After clearing region caches")
    Ok("Done")
  }
}
