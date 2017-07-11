import java.io.File

import akka.actor.Cancellable
import akka.stream.{ClosedShape, Graph, Materializer}
import com.github.tototoshi.play2.json4s.core.Json4sParser
import com.vatbox.shredder.{Permission, SecuredAction, Statement, _}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods
import org.json4s.native.Serialization.write
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, FreeSpec, ShouldMatchers}
import play.api
import play.api.mvc._
import play.api.routing.sird._
import play.api.test._
import play.api.{Configuration, Environment}
import play.core.server.Server

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/**
  * Created by erez on 20/07/2016.
  */
class SecuredActionSpec extends FreeSpec with ShouldMatchers with BeforeAndAfterEach with ScalaFutures {
  implicit val materializer = new Materializer {override def withNamePrefix(name: String): Materializer = ???
    override implicit def executionContext: ExecutionContextExecutor = ???
    override def scheduleOnce(delay: FiniteDuration, task: Runnable): Cancellable = ???
    override def schedulePeriodically(initialDelay: FiniteDuration, interval: FiniteDuration, task: Runnable): Cancellable = ???
    override def materialize[Mat](runnable: Graph[ClosedShape, Mat]): Mat = ???
  }

  implicit val json4s = new Json4sParser(Configuration(), JsonMethods)
  implicit val formats = DefaultFormats
  implicit val env = Environment(new File(""), new ClassLoader() {}, api.Mode.Test)

  // without limitations with one, with some

  override protected def afterEach(): Unit =  SecuredAction.clearCache()

  override protected def beforeEach(): Unit = SecuredAction.clearCache()

  "When we have authorization" - {
    "From the client" - {
      "We should run the action" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if (name == "Erez") {
              val res = List(Statement(allow = true, List(ActionData("Badass Server","Desert Storm")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))

            Await.result(t, Duration.Inf) shouldBe Results.Ok
          }
        }
      }
    }
    "From the server" - {
      "We should run the action" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if (name == "Hila") {
              val res = List(Statement(allow = true, List(ActionData("Badass Server","Desert Storm")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((serverHeader, "Hila")))

            Await.result(t, Duration.Inf) shouldBe Results.Ok
          }
        }
      }
    }
    "Saved in the cache" - {
      "We should run the action" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        var alreadyCalled = false
        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if ((name == "Hila") && (!alreadyCalled)) {
              alreadyCalled = true
              val res = List(Statement(allow = true, List(ActionData("Badass Server","Desert Storm")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((serverHeader, "Hila")))
            Thread.sleep(200) // sleeping to allow cache to be updated
            val r = someAction.apply(FakeRequest().withHeaders((serverHeader, "Hila")))

            Await.result(t, Duration.Inf) shouldBe Results.Ok
            Await.result(r, Duration.Inf) shouldBe Results.Ok
          }
        }
      }
    }
    "From client with all actions allowed in the service" - {
      "We should run the action" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if (name == "Erez") {
              val res = List(Statement(allow = true, List(ActionData("Badass Server","*")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))

            Await.result(t, Duration.Inf) shouldBe Results.Ok
          }
        }
      }
    }
    "From client with a specific action allowed in all services" - {
      "We should run the action" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if (name == "Erez") {
              val res = List(Statement(allow = true, List(ActionData("*","Desert Storm")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))

            Await.result(t, Duration.Inf) shouldBe Results.Ok
          }
        }
      }
    }
    "From client with total admin permission" - {
      "We should run the action" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if (name == "Erez") {
              val res = List(Statement(allow = true, List(ActionData("*","*")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t: Future[Result] = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))
            Await.result(t, Duration.Inf) shouldBe Results.Ok
          }
        }
      }
    }
  }

  "When we dont have authorization to run the action" - {
    "When we have credentials for other actions but not this one" - {
      "We should get unauthorized" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if (name == "Erez") {
              val res = List(Statement(allow = true, List(ActionData("Badass Server","Other Action")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))
            t.futureValue.header.status shouldBe Results.Forbidden.header.status
          }
        }
      }
    }

    "When we have a denying permission" - {
      "We should get unauthorized" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            if (name == "Erez") {

              val res = List(Statement(allow = false, List(ActionData("Badass Server","Desert Storm")), List(Permission())),
                        Statement(allow = true, List(ActionData("Badass Server","Desert Storm")), List(Permission())))
              Results.Ok(write(res))
            }
            else Results.Ok
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))

            t.futureValue.header.status shouldBe Results.Forbidden.header.status

          }
        }
      }
    }
    "When we dont have permissions for anything" - {
      "We should get unauthorized" in {
        implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

        Server.withRouter() {
          case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
            val res = List()
            Results.Ok(write(res))
          }
        } { implicit port =>
          WsTestClient.withClient { implicit client =>
            def someAction = new SecuredAction("Desert Storm").async { request =>
              Future.successful(Results.Ok)
            }

            val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))

            t.futureValue.header.status shouldBe Results.Forbidden.header.status
          }
        }
      }
    }
    "When we run tests" - {
      "And specify to ignore permissions" - {
        "We should be able to do anything" in {
          implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""), ("shredder.ignore_in_test", true))

          Server.withRouter() {
            case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
              val res = List()
              Results.Ok(write(res))
            }
          } { implicit port =>
            WsTestClient.withClient { implicit client =>
              def someAction = new SecuredAction("Desert Storm").async { request =>
                Future.successful(Results.Ok)
              }

              val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))

              Await.result(t, Duration.Inf) shouldBe Results.Ok
            }
          }
        }
      }
      "And we do not specify to ignore permissions" - {
        "We should still be checked" in {
          implicit val conf = Configuration(("shredder.this_server_name", "Badass Server"), ("shredder.krang_url", ""))

          Server.withRouter() {
            case GET(p"/internal/api/v1/credentials" ? q"name=$name") => Action { request =>
              val res = List()
              Results.Ok(write(res))
            }
          } { implicit port =>
            WsTestClient.withClient { implicit client =>
              def someAction = new SecuredAction("Desert Storm").async { request =>
                Future.successful(Results.Ok)
              }

              val t = someAction.apply(FakeRequest().withHeaders((userHeader, "Erez")))

              t.futureValue.header.status shouldBe Results.Forbidden.header.status
            }
          }
        }
      }
    }
  }
}
