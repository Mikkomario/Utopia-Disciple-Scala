package utopia.disciple.http

import utopia.access.http.Status
import utopia.access.http.Headers

/**
* Responses are sent by a server. Responses have a specific status and may contain a body 
 * section. Context and content type determine, how the response body is parsed.
* @author Mikko Hilpinen
* @since 19.2.2018
**/
trait Response
{
	// ABSTRACT    ------------------
    
    /**
     * The HTTP status of this response
     */
    def status: Status
    
    /**
     * The HTTP headers of this response
     */
    def headers: Headers
    
    /*
     * The cookies associated with this response
     */
    // def cookies: Set[Cookie]
    
    
    // COMPUTED PROPERTIES    ---------------
    
    /**
     * The content type of the response
     */
    def contentType = headers.contentType
    
    /**
     * The length of the response body
     */
    def contentLength = headers.contentLength
    
    /**
     * Whether the response is empty (no body)
     */
    def isEmpty = contentLength == 0 && !headers.isChunked
}