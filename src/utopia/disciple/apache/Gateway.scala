package utopia.disciple.apache

import scala.collection.JavaConverters._
import utopia.access.http.Method._

import scala.language.implicitConversions
import scala.language.postfixOps
import utopia.access.http.Method
import org.apache.http.client.methods.{CloseableHttpResponse, HttpDelete, HttpGet, HttpPatch, HttpPost, HttpPut}
import org.apache.http.impl.client.HttpClients
import utopia.disciple.http.Request
import utopia.disciple.http.StreamedResponse
import utopia.access.http.Status
import utopia.access.http.Status._
import utopia.access.http.Headers

import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.io.InputStream

import scala.concurrent.Promise
import utopia.flow.util.AutoClose._
import utopia.disciple.http.BufferedResponse
import utopia.flow.datastructure.immutable.Model
import utopia.flow.datastructure.immutable.Constant
import org.apache.http.message.BasicNameValuePair
import org.apache.http.Consts
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.HttpEntity
import org.apache.http.client.utils.URIBuilder
import utopia.disciple.http.Body
import org.apache.http.message.BasicHeader
import java.io.OutputStream

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.impl.client.CloseableHttpClient
import utopia.flow.parse.{JSONReader, XmlReader}

import scala.io.Source


/**
* Gateway is the singular instance, through which simple http requests can be made
* @author Mikko Hilpinen
* @since 22.2.2018
**/
object Gateway
{
    // ATTRIBUTES    -------------------------
    
    private val _introducedStatuses = Vector[Status](OK, Created, Accepted, NoContent, NotModified, 
            BadRequest, Unauthorized, Forbidden, NotFound, MethodNotAllowed, 
            InternalServerError, NotImplemented, ServiceUnavailable)
    
    private val connectionManager = new PoolingHttpClientConnectionManager()
    
    private var _client: Option[CloseableHttpClient] = None
    private def client = _client.getOrElse(HttpClients.custom().setConnectionManager(
                connectionManager).setConnectionManagerShared(true).build())
    
    
    // COMPUTED PROPERTIES    ----------------
    
    /**
     * The maximum number of simultaneous connections to a single route
     */
    def maxConnectionsPerRoute = connectionManager.getDefaultMaxPerRoute
    def maxConnectionsPerRoute_=(max: Int) = 
    {
        connectionManager.setDefaultMaxPerRoute(max)
        invalidateClient()
    }
    /**
     * The maximum number of simultaneous connections in total
     */
    def maxConnectionsTotal = connectionManager.getMaxTotal
    def maxConnectionsTotal_=(max: Int) = 
    {
        connectionManager.setMaxTotal(max)
        invalidateClient()
    }
    
    
    // OTHER METHODS    ----------------------
    
    // TODO: Add support for multipart body:
    // https://stackoverflow.com/questions/2304663/apache-httpclient-making-multipart-form-post
    
    /**
     * Performs a synchronous request over a HTTP connection, calling the provided function 
     * when a response is received
     * @param request the request that is sent to the server
     * @param consumeResponse the function that handles the server response (or the lack of it)
     */
    def makeRequest(request: Request)(consumeResponse: Try[StreamedResponse] => Unit) =
    {
        try
        {
            // Makes the base request (uri + params + body)
            val base = makeRequestBase(request.method, request.requestUri, request.params, request.body,
				request.supportsBodyParameters)
            
            // Adds the headers
            request.headers.fields.foreach { case (key, value) => base.addHeader(key, value) }
            
            // Performs the request and consumes any response
			client.execute(base).consume { response => consumeResponse(Success(wrapResponse(response))) }
        }
        catch
        {
            case e: Exception => consumeResponse(Failure(e))
        }
    }
    
    /**
     * Performs an asynchronous request over a http(s) connection, calling the provided function
     * when a response is received
     * @param request the request that is sent to the server
     * @param consumeResponse the function that handles the server response (or the lack of it)
     */
    def makeAsyncRequest(request: Request)(consumeResponse: Try[StreamedResponse] => Unit)
            (implicit context: ExecutionContext): Unit = Future(makeRequest(request)(consumeResponse))
    
    /**
     * Performs a request and buffers / parses it to the program memory
     * @param request the request that is sent to the server
     * @param parseResponse the function that parses the response stream contents
     * @return A future that holds the request results. Please note that the Future is a failure if no data was received.
     */
    def getResponse[A](request: Request)(parseResponse: InputStream => Try[A])(implicit context: ExecutionContext) =
    {
        val response = Promise[BufferedResponse[Try[A]]]()
        makeAsyncRequest(request)(result => response.complete(result.map { _.buffered(parseResponse) }))
        response.future
    }
	
	/**
	  * Performs a request and buffers / parses it to the program memory
	  * @param request the request that is sent to the server
	  * @param contentOnEmptyResponse The content that replaces empty result contents
	  * @param parseResponse the function that parses the response stream contents
	  * @return A future that holds the request results. Please note that the Future is a failure if no data was received.
	  */
	def getResponse[A](request: Request, contentOnEmptyResponse: => A)(parseResponse: InputStream => Try[A])
					  (implicit context: ExecutionContext) =
	{
		val response = Promise[BufferedResponse[Try[A]]]()
		makeAsyncRequest(request)(result => response.complete(result.map { _.bufferedOr(contentOnEmptyResponse)(parseResponse) }))
		response.future
	}
	
	/**
	  * Performs an asynchronous request and parses the response to program memory as string
	  * @param request The request sent to the server
	  * @param context An implicit execution context
	  * @return A future for the parsed response
	  */
	def getStringResponse(request: Request)(implicit context: ExecutionContext) =
		getResponse(request, "") { stream =>
			Try(Source.fromInputStream(stream).consume { _.getLines.mkString }) }
	
	/**
	  * Performs an asynchronous request and parses the response from JSON
	  * @param request Request sent to the server
	  * @param context Implicit execution context
	  * @return A future for the parsed response
	  */
	def getJSONResponse(request: Request)(implicit context: ExecutionContext) =
		getResponse(request, Model.empty) { stream => JSONReader.parseStream(stream) }
	
	/**
	  * Performs an asynchronous request and parses the response to Xml
	  * @param request Request sent to the server
	  * @param context Implicit execution context
	  * @return A future for the parsed response
	  */
	def getXmlResponse(request: Request)(implicit context: ExecutionContext) =
		getResponse(request) { stream => XmlReader.parseStream(stream) }
    
    private def invalidateClient() = 
    {
        _client.foreach(_.close())
        _client = None
    }
    
    // Adds parameters and body to the request base. No headers are added at this point
	private def makeRequestBase(method: Method, baseUri: String, params: Model[Constant] = Model.empty, 
	        body: Option[HttpEntity] = None, supportBodyParameters: Boolean = true) =
	{
	    if (method == Get || method == Delete)
	    {
	        // Adds the parameters to uri, no body is supported
	        val uri = makeUriWithParams(baseUri, params)
	        if (method == Get) new HttpGet(uri) else new HttpDelete(uri)
	    }
	    else if (body.isEmpty && supportBodyParameters)
	    {
	        // If there is no body, adds the parameters as a body entity instead
	        val base =
			{
				if (method == Post) new HttpPost(baseUri)
				else if (method == Put) new HttpPut(baseUri)
				else new HttpPatch(baseUri)
			}
	        makeParametersEntity(params).foreach(base.setEntity)
	        base
	    }
	    else
	    {
	        // If both a body and parameters were provided, adds params to uri
	        val uri = makeUriWithParams(baseUri, params)
	        val base = if (method == Post) new HttpPost(uri) else new HttpPut(uri)
	        base.setEntity(body.get)
	        base
	    }
	}
	
	// Adds parameter values in JSON format to request uri, returns combined uri
	private def makeUriWithParams(baseUri: String, params: Model[Constant]) = 
	{
	    val builder = new URIBuilder(baseUri)
	    params.attributes.foreach(a => builder.addParameter(a.name, a.value.toJSON))
	    builder.build()
	}
	
	private def makeParametersEntity(params: Model[Constant]) = 
	{
	    if (params.isEmpty)
	        None
	    else
	    {
	        val paramsList = params.attributes.map(c => new BasicNameValuePair(c.name, c.value.stringOr()))
	        Some(new UrlEncodedFormEntity(paramsList.asJava, Consts.UTF_8))
	    }
	}
	
	private def wrapResponse(response: CloseableHttpResponse) = 
	{
	    val status = statusForCode(response.getStatusLine.getStatusCode)
	    val headers = new Headers(response.getAllHeaders.map(h => (h.getName, h.getValue)).toMap)
	    
	    new StreamedResponse(status, headers, () => response.getEntity.getContent)
	}
	
	private def statusForCode(code: Int) = _introducedStatuses.find(
	        _.code == code).getOrElse(new Status("Other", code))
	
	
	// IMPLICIT CASTS    ------------------------
	
	private implicit def convertOption[T](option: Option[T])
	        (implicit f: T => HttpEntity): Option[HttpEntity] = option.map(f)
	
	//noinspection JavaAccessorMethodOverriddenAsEmptyParen
	private implicit class EntityBody(val b: Body) extends HttpEntity
	{
		override def consumeContent() =
	    {
	        b.stream.foreach(input => 
	        {
    	        val bytes = new Array[Byte](1024) //1024 bytes - Buffer size
                Iterator
                .continually (input.read(bytes))
                .takeWhile (-1 !=)
	        })
	    }
	    
        override def getContent() = b.stream.getOrElse(null)
        
        override def getContentEncoding() = b.contentEncoding.map(new BasicHeader("Content-Encoding", _)).orNull
		
		override def getContentLength() = b.contentLength.getOrElse(-1)
		
		override def getContentType() = new BasicHeader("Content-Type", b.contentType.toString() + b.charset.map(_.name()).getOrElse(""))
		
		override def isChunked() = b.chunked
		
		override def isRepeatable() = b.repeatable
		
		override def isStreaming() = !b.repeatable
		
		override def writeTo(output: OutputStream) = b.writeTo(output).get
	}
}