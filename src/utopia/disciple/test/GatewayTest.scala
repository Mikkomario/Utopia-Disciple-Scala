package utopia.disciple.test

import scala.concurrent.ExecutionContext.Implicits.global
import utopia.flow.generic.ValueConversions._

import utopia.flow.generic.DataType
import utopia.disciple.http.Request
import utopia.access.http.Method._
import java.io.InputStream
import scala.io.Source
import utopia.disciple.apache.Gateway
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import utopia.flow.datastructure.immutable.Model

/**
 * Tests the Gateway interface
 * @author Mikko Hilpinen
 * @since 15.3.2018
 */
object GatewayTest extends App
{
    DataType.setup()
    
    val get1 = new Request("http://localhost:9999/TestServer/scala", Get)
    
    def streamToString(stream: InputStream) = Source.fromInputStream(stream).mkString
    
    println("Sending request")
    val response1 = Await.result(Gateway.getResponse(get1, streamToString), 
            Duration(10, TimeUnit.SECONDS));
    
    println(response1.status)
    println(response1.headers)
    println(response1.body)
    
    val post1 = new Request("http://localhost:9999/TestServer/scala", Post, Model(Vector("test" -> "a", "another" -> 2)))
    
    val response2 = Await.result(Gateway.getResponse(post1, streamToString), 
            Duration(10, TimeUnit.SECONDS));
    
    println(response2.status)
    println(response2.headers)
    println(response2.body)
}