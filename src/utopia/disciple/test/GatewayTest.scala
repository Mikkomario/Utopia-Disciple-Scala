package utopia.disciple.test

import scala.concurrent.ExecutionContext.Implicits.global
import utopia.flow.generic.ValueConversions._
import utopia.access.http.ContentCategory._

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
import utopia.disciple.http.FileBody
import java.io.File

/**
 * Tests the Gateway interface
 * @author Mikko Hilpinen
 * @since 15.3.2018
 */
object GatewayTest extends App
{
    DataType.setup()
    
    val uri = "http://localhost:9999/TestServer/echo"
    def streamToString(stream: InputStream) = Source.fromInputStream(stream).mkString
    def makeRequest(request: Request) = Await.result(Gateway.getResponse(request, streamToString), 
            Duration(10, TimeUnit.SECONDS))
    
    val get1 = new Request(uri, Get)
    
    println("Sending request")
    val response1 = makeRequest(get1)
    
    println(response1.status)
    println(response1.headers)
    println(response1.body)
    
    val post1 = new Request(uri, Post, Model(Vector("test" -> "a", "another" -> 2))).withModifiedHeaders(
            _.withTypeAccepted(Application/"json"))
    
    val response2 = makeRequest(post1)
    
    println(response2.status)
    println(response2.headers)
    println(response2.body)
    
    val file = new File("testData/ankka.jpg")
    val postImage = new Request(requestUri = "http://localhost:9999/TestServer/echo", 
            method = Post, params = Model(Vector("name" -> "ankka")), 
            body = Some(new FileBody(file, Image/"jpg")))
    
    val response3 = makeRequest(postImage)
    
    println(response3.status)
    println(response3.headers)
    println(response3.body)
}