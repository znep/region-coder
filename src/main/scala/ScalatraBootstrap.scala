import com.socrata.regioncoder._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext): Unit = {
    context.mount(new RegionCoderServlet, "/*")
  }
}