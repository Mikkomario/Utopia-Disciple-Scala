package utopia.disciple.apache

import scala.collection.JavaConverters._
import utopia.access.http.Method._

import scala.language.implicitConversions
import scala.language.postfixOps

import utopia.access.http.Method
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpPut
import org.apache.http.impl.client.HttpClients
import utopia.disciple.http.Request
import org.apache.http.client.methods.CloseableHttpResponse
import utopia.disciple.http.StreamedResponse
import utopia.access.http.Status
import utopia.access.http.OK
import utopia.access.http.Created
import utopia.access.http.Accepted
import utopia.access.http.NoContent
import utopia.access.http.NotModified
import utopia.access.http.BadRequest
import utopia.access.http.Unauthorized
import utopia.access.http.Forbidden
import utopia.access.http.NotFound
import utopia.access.http.MethodNotAllowed
import utopia.access.http.InternalServerError
import utopia.access.http.NotImplemented
import utopia.access.http.ServiceUnavailable
import utopia.access.http.Headers
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.io.InputStream
import utopia.disciple.http.BufferedResponse
import scala.concurrent.Promise
import utopia.disciple.http.BufferedResponse
import utopia.disciple.http.BufferedResponse
import utopia.flow.datastructure.immutable.Model
import utopia.flow.datastructure.immutable.Constant
import org.apache.http.message.BasicNameValuePair
import org.apache.http.Consts
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.HttpEntity
import org.apache.http.client.utils.URIBuilder
import utopia.disciple.http.Body
import org.apache.http.Header
import org.apache.http.message.BasicHeader
import java.io.OutputStream


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
            InternalServerError, NotImplemented, ServiceUnavailable);
    
    
    // OTHER METHODS    ----------------------
    
    // TODO: Add support for multipart body:
    // https://stackoverflow.com/questions/2304663/apache-httpclient-making-multipart-form-post
    
    /**
     * Performs a synchronous request over a HTTP connection, calling the provided function 
     * when a response is received
     * @param request the request that is sent to the server
     * @param consumeResponse the function that handles the server response (or the lack of it)
     */
    def makeRequest(request: Request, consumeResponse: Try[StreamedResponse] => Unit) = 
    {
        val client = HttpClients.createDefault()
        try
        {
            // Makes the base request (uri + params + body)
            val base = makeRequestBase(request.method, request.requestUri, request.params, 
                    request.body);
            
            // Adds the headers
            request.headers.fields.foreach {case (key, value) => base.addHeader(key, value)}
            
            // Performs the request and consumes any response
            val response = client.execute(base)
            try
            {
                consumeResponse(Success(wrapResponse(response)))
            }
            finally
            {
                response.close()
            }
        }
        catch
        {
            case e: Exception => consumeResponse(Failure(e))
        }
        finally
        {
            // Response and client are always closed
            client.close()
        }
    }
    
    /**
     * Performs an asynchronous request over a HTTP connection, calling the provided function 
     * when a response is received
     * @param request the request that is sent to the server
     * @param consumeResponse the function that handles the server response (or the lack of it)
     */
    def makeAsyncRequest(request: Request, consumeResponse: Try[StreamedResponse] => Unit)
            (implicit context: ExecutionContext) = Future(makeRequest(request, consumeResponse));
    
    /**
     * Performs a request and buffers / parses it to the program memory
     * @param request the request that is sent to the server
     * @param parseResponse the function that parses the response stream contents
     * @return A future that holds the request results
     */
    def getResponse[T](request: Request, parseResponse: InputStream => T)(implicit context: ExecutionContext) = 
    {
        val response = Promise[BufferedResponse[Option[T]]]()
        makeAsyncRequest(request, result => response.complete(result.map(_.buffered(parseResponse))))
        response.future
    }
    
    // Adds parameters and body to the request base. No headers are added at this point
	private def makeRequestBase(method: Method, baseUri: String, params: Model[Constant] = Model.empty, 
	        body: Option[HttpEntity] = None) = 
	{
	    if (method == Get || method == Delete)
	    {
	        // Adds the parameters to uri, no body is supported
	        val uri = makeUriWithParams(baseUri, params)
	        if (method == Get) new HttpGet(uri) else new HttpDelete(uri)
	    }
	    else if (body.isEmpty)
	    {
	        // If there is no body, adds the parameters as a body entity instead
	        val base = if (method == Post) new HttpPost(baseUri) else new HttpPut(baseUri)
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
	    val headers = new Headers(response.getAllHeaders.map(h => (h.getName(), h.getValue())).toMap)
	    
	    new StreamedResponse(status, headers, () => response.getEntity.getContent)
	}
	
	private def statusForCode(code: Int) = _introducedStatuses.find(
	        _.code == code).getOrElse(new Status("Other", code));
	
	
	// IMPLICIT CASTS    ------------------------
	
	private implicit def convertOption[T](option: Option[T])
	        (implicit f: T => HttpEntity): Option[HttpEntity] = option.map(f)
	
	private implicit class EntityBody(val b: Body) extends HttpEntity
	{
	    def consumeContent() = 
	    {
	        b.stream.foreach(input => 
	        {
    	        val bytes = new Array[Byte](1024) //1024 bytes - Buffer size
                Iterator
                .continually (input.read(bytes))
                .takeWhile (-1 !=)
	        })
	    }
	    
        def getContent() = b.stream.getOrElse(null)
        
        def getContentEncoding() = b.contentEncoding.map(
                new BasicHeader("Content-Encoding", _)).getOrElse(null);
        
        def getContentLength() = b.contentLength.getOrElse(-1)
        
        def getContentType() = new BasicHeader("Content-Type", 
                b.contentType.toString() + b.charset.map(_.name()).getOrElse(""))
        
        def isChunked() = b.chunked
        
        def isRepeatable() = b.repeatable
        
        def isStreaming() = !b.repeatable
        
        def writeTo(output: OutputStream) = b.writeTo(output).get
	}
}