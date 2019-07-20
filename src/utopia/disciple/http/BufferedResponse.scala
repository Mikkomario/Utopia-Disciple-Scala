package utopia.disciple.http

import utopia.access.http.Status
import utopia.access.http.Headers

/**
* A buffered response is a response that has its body parsed and buffered into program memory
* @author Mikko Hilpinen
* @since 19.2.2018
  * @param body Parsed response body
  * @param status Response status
  * @param headers Response headers
**/
class BufferedResponse[+A](val body: A, override val status: Status, override val headers: Headers,
        /*override val cookies: Set[Cookie]*/) extends Response