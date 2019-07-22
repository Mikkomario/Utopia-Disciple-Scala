package utopia.disciple.http

import utopia.flow.datastructure.template
import utopia.access.http.Method
import utopia.access.http.Method._
import utopia.flow.datastructure.immutable.Model
import utopia.flow.datastructure.immutable.Constant
import utopia.access.http.Headers
import utopia.flow.datastructure.immutable.Value

// See https://hc.apache.org/httpcomponents-client-ga/

/**
 * Requests are used for requesting certain data and for performing certain actions on server side 
 * over a http connection
 * @author Mikko Hilpinen
 * @since 26.11.2017
  * @param requestUri Targeted uri / url
  * @param method Http method used (default = GET)
  * @param params Parameters included in request (model format, default = empty)
  * @param headers Headers sent with the request (default = current date headers)
  * @param body Body included in request (default = None)
  * @param supportsBodyParameters Whether parameters could be moved to request body when body is omitted (default = true).
  *                               Use false if you wish to force parameters to uri parameters)
 */
class Request(val requestUri: String, val method: Method = Get, val params: Model[Constant] = Model.empty,
              val headers: Headers = Headers.currentDateHeaders, val body: Option[Body] = None,
              val supportsBodyParameters: Boolean = true)
{
    // IMPLEMENTED  --------------------
    
    override def toString = s"$method $requestUri Parameters: $params, Body: $body, Headers: $headers"
    
    
    // OPERATORS    --------------------
    
    /**
     * Adds a new parameter to this request
     */
    def +(parameter: Constant) = new Request(requestUri, method, params + parameter, headers, body)
    
    /**
     * Adds a new parameter to this request
     */
    def +(parameter: (String, Value)): Request = this + Constant(parameter._1, parameter._2)
    
    /**
     * Adds multiple new parameters to this request
     */
    def ++(params: template.Model[Constant]) = new Request(requestUri, method, this.params ++ params, headers, body)
    
    
    // OTHER METHODS    ----------------
    
    /**
     * Modifies the headers of this request
     */
    def withModifiedHeaders(mod: Headers => Headers) = new Request(requestUri, method, params, mod(headers), body)
}