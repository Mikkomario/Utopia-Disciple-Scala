package utopia.disciple.http

import utopia.access.http.Status
import utopia.access.http.Headers
import java.io.InputStream
import utopia.access.http.Cookie

/**
 * Streamed Responses are Responses that have a limited lifespan and can be consumed once only
 * @author Mikko Hilpinen
 * @since 30.11.2017
 */
class StreamedResponse(override val status: Status, override val headers: Headers, 
        /*override val cookies: Set[Cookie], */private val openStream: () => InputStream) extends Response
{
    // ATTRIBUTES    ------------------------
    
    private var closed = false
    
    
    // OTHER METHODS    ----------------------
    
    /**
     * Consumes this response by reading the response body
     * @param reader the function that is used for parsing the response body
     * @throws IllegalStateException if the response has already been consumed
     */
    @throws[IllegalStateException]("If this Response has already been consumed")
    def consume[T](reader: InputStream => T) = 
    {
        if (closed)
            throw new IllegalStateException("Response is already consumed")
        else
        {
            closed = true
            val stream = openStream()
            try
            {
                reader(stream)
            }
            finally
            {
                stream.close()
            }
        }
    }
    
    /**
     * Consumes this response by reading the response body, but only if the response contains a body
     * @param reader the function that is used for parsing the response body
     * @throws IllegalStateException if the response has already been consumed
     */
    @throws[IllegalStateException]("If this Response has already been consumed")
    def consumeIfDefined[T](reader: InputStream => T) = if (isEmpty) None else Some(consume(reader));
    
    /**
     * Buffers this response into program memory, parsing the response contents as well
     * @param parser the function that is used for parsing the response contents
     * @throws IllegalStateException if the response has already been consumed
     */
    @throws[IllegalStateException]("If this Response has already been consumed")
    def buffered[T](parser: InputStream => T) = new BufferedResponse(consumeIfDefined(parser), 
            status, headers/*, cookies*/)
}