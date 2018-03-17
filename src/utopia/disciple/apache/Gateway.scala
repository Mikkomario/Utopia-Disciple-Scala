package utopia.disciple.apache

import scala.collection.JavaConverters._

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
            val base = makeRequestBase(request.method, request.requestUri)
            // makeParametersEntity(request.params).foreach(base.setEntity)
            
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
    
	private def makeRequestBase(method: Method, uri: String) = 
	{
	    // TODO: Put and post must be handled separately (HttpEntityEnclosingRequestBase, add entity)
	    method match 
	    {
	        case Method.Get => new HttpGet(uri);
	        case Method.Post => new HttpPost(uri)
	        case Method.Put => new HttpPut(uri)
	        case Method.Delete => new HttpDelete(uri)
	    }
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
	        _.code == code).getOrElse(new Status("Other", code))
}