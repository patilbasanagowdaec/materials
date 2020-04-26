package app

import utest._
import scala.concurrent._, duration.Duration._
import castor.Context.Simple.global, cask.util.Logger.Console._

object ExampleTests extends TestSuite {
  def withServer[T](example: cask.main.Main)(f: String => T): T = {
    val server = io.undertow.Undertow.builder
      .addHttpListener(8080, "localhost")
      .setHandler(example.defaultHandler)
      .build
    server.start()
    val res =
      try f("http://localhost:8080")
      finally server.stop()
    res
  }

  val tests = Tests {
    test("success") - withServer(MinimalApplication) { host =>
      var wsPromise = scala.concurrent.Promise[String]
      val wsClient = cask.util.WsClient.connect(s"$host/subscribe") {
        case cask.Ws.Text(msg) => wsPromise.success(msg)
      }
      val success = requests.get(host)

      assert(success.text().contains("Scala Chat!"))
      assert(success.text().contains("alice"))
      assert(success.text().contains("Hello World!"))
      assert(success.text().contains("bob"))
      assert(success.text().contains("I am cow, hear me moo"))
      assert(success.statusCode == 200)

      val wsMsg = Await.result(wsPromise.future, Inf)

      assert(wsMsg.contains("alice"))
      assert(wsMsg.contains("Hello World!"))
      assert(wsMsg.contains("bob"))
      assert(wsMsg.contains("I am cow, hear me moo"))

      wsPromise = scala.concurrent.Promise[String]
      val response = requests.post(host, data = ujson.Obj("name" -> "haoyi", "msg" -> "Test Message!"))

      val parsed = ujson.read(response.text())
      assert(parsed("success") == ujson.True)
      assert(parsed("err") == ujson.Str(""))

      assert(response.statusCode == 200)
      val wsMsg2 = Await.result(wsPromise.future, Inf)
      assert(wsMsg2.contains("alice"))
      assert(wsMsg2.contains("Hello World!"))
      assert(wsMsg2.contains("bob"))
      assert(wsMsg2.contains("I am cow, hear me moo"))
      assert(wsMsg2.contains("haoyi"))
      assert(wsMsg2.contains("Test Message!"))

      val success2 = requests.get(host)

      assert(success2.text().contains("Scala Chat!"))
      assert(success2.text().contains("alice"))
      assert(success2.text().contains("Hello World!"))
      assert(success2.text().contains("bob"))
      assert(success2.text().contains("I am cow, hear me moo"))
      assert(success2.text().contains("haoyi"))
      assert(success2.text().contains("Test Message!"))
      assert(success2.statusCode == 200)
    }
    test("failure") - withServer(MinimalApplication) { host =>
      val response1 = requests.post(host, data = ujson.Obj("name" -> "haoyi"), check = false)
      assert(response1.statusCode == 400)
      val response2 = requests.post(host, data = ujson.Obj("name" -> "haoyi", "msg" -> ""))
      assert(
        ujson.read(response2.text()) ==
        ujson.Obj("success" -> false, "err" -> "Message cannot be empty")
      )
      val response3 = requests.post(host, data = ujson.Obj("name" -> "", "msg" -> "Test Message!"))
      assert(
        ujson.read(response3.text()) ==
        ujson.Obj("success" -> false, "err" -> "Name cannot be empty")
      )
    }
  }
}