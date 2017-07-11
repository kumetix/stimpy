import akka.actor.Cancellable
import akka.stream.{ClosedShape, Graph, Materializer}
import com.vatbox.shredder._
import org.scalatest.{FreeSpec, ShouldMatchers}
import play.api.Configuration
import play.api.test.{FakeRequest, WsTestClient}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration

/**
  * Created by erez on 21/07/2016.
  */
class SecuredWSRequestSpec extends FreeSpec with ShouldMatchers {
implicit val materializer = new Materializer {override def withNamePrefix(name: String): Materializer = ???
  override implicit def executionContext: ExecutionContextExecutor = ???
  override def scheduleOnce(delay: FiniteDuration, task: Runnable): Cancellable = ???
  override def schedulePeriodically(initialDelay: FiniteDuration, interval: FiniteDuration, task: Runnable): Cancellable = ???
  override def materialize[Mat](runnable: Graph[ClosedShape, Mat]): Mat = ???
}

  "When we add server credentials headers" - {
    "When we have credentials" - {
      "Creds should be added to the request" in {
        implicit val conf = Configuration(("shredder.this_server_name","testingtesting123"))
        WsTestClient.withClient { client =>
          val request = client.url("").withServerCredentials
          request.headers shouldBe Map("CallingServer" -> Seq("testingtesting123"))
        }
      }
    }

    "When we have credentials and other headers" - {
      "Creds should be added to the request with existing" in {
        implicit val conf = Configuration(("shredder.this_server_name","testingtesting123"))
        WsTestClient.withClient { client =>
          val request = client.url("").withHeaders(("hila", "pretty")).withServerCredentials
          request.headers shouldBe Map("CallingServer" -> Seq("testingtesting123"), "hila" -> Seq("pretty"))
        }
      }
    }

    "And we dont have credentials" - {
      "We should get an exception" in {
        implicit val conf = Configuration()
        intercept[Exception] {
          WsTestClient.withClient { client =>
            val request = client.url("").withServerCredentials
          }
        }
      }
    }
  }

  "When we add user credentials headers" - {
    "When we get credentials" - {
      "Creds should be added to the request" in {
        implicit val conf = Configuration()
        WsTestClient.withClient { client =>

          val request = client.url("").withUserCredentials("hila")
          request.headers shouldBe Map(userHeader -> Seq("hila"))
        }
      }
    }

    "When we have credentials and other headers" - {
      "Creds should be added to the request with existing" in {
        implicit val conf = Configuration()

        WsTestClient.withClient { client =>
          val request = client.url("").withHeaders(("hila", "pretty")).withUserCredentials("hila")
          request.headers shouldBe Map(userHeader -> Seq("hila"), "hila" -> Seq("pretty"))
        }
      }
    }

    "When we have credentials in a request" - {
      "Creds should be added to the request" in {
        implicit val conf = Configuration()

        WsTestClient.withClient { client =>
          val req = FakeRequest().withHeaders((userHeader, "hila"))
          val request = client.url("").withUserCredentials(req)
          request.headers shouldBe Map(userHeader -> Seq("hila"))
        }
      }
    }
  }
}
