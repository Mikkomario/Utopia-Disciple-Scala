package utopia.disciple.http

import utopia.access.http.Status
import utopia.access.http.Headers

/**
* A buffered response is a response that has its body parsed and buffered into program memory
* @author Mikko Hilpinen
* @since 19.2.2018
**/
class BufferedResponse[T](val body: T, override val status: Status, override val headers: Headers, 
        /*override val cookies: Set[Cookie]*/) extends Response