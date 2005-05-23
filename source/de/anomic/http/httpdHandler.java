// httpdHandler.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 03.01.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
   Documentation:
   the servlet interface
   an actual servlet for the AnomicHTTPD must provide a class that implements
   this interface. The resulting class is then placed in a folder that contains
   all servlets and is configured in the httpd.conf configuration file.
   servlet classes in that directory are then automatically selected as CGI
   extensions to the server.
   The core functionality of file serving is also implemented as servlet.
*/

package de.anomic.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Properties;

import de.anomic.server.serverSwitch;

public interface httpdHandler {

    void doGet(Properties conProp, httpHeader header, OutputStream response) throws IOException;
    /*
      The GET method means retrieve whatever information (in the form of an
      entity) is identified by the Request-URI. If the Request-URI refers
      to a data-producing process, it is the produced data which shall be
      returned as the entity in the response and not the source text of the
      process, unless that text happens to be the output of the process.
      
      The semantics of the GET method change to a "conditional GET" if the
      request message includes an If-Modified-Since, If-Unmodified-Since,
      If-Match, If-None-Match, or If-Range header field. A conditional GET
      method requests that the entity be transferred only under the
      circumstances described by the conditional header field(s). The
      conditional GET method is intended to reduce unnecessary network
      usage by allowing cached entities to be refreshed without requiring
      multiple requests or transferring data already held by the client.
      
      The semantics of the GET method change to a "partial GET" if the
      request message includes a Range header field. A partial GET requests
      that only part of the entity be transferred, as described in section
      14.35. The partial GET method is intended to reduce unnecessary
      network usage by allowing partially-retrieved entities to be
      completed without transferring data already held by the client.
    */

    void doHead(Properties conProp, httpHeader header, OutputStream response) throws IOException;
    /*
      The HEAD method is identical to GET except that the server MUST NOT
      return a message-body in the response. The metainformation contained
      in the HTTP headers in response to a HEAD request SHOULD be identical
      to the information sent in response to a GET request. This method can
      be used for obtaining metainformation about the entity implied by the
      request without transferring the entity-body itself. This method is
      often used for testing hypertext links for validity, accessibility,
      and recent modification.
      
      The response to a HEAD request MAY be cacheable in the sense that the
      information contained in the response MAY be used to update a
      previously cached entity from that resource. If the new field values
      indicate that the cached entity differs from the current entity (as
      would be indicated by a change in Content-Length, Content-MD5, ETag
      or Last-Modified), then the cache MUST treat the cache entry as
      stale.
    */

    void doPost(Properties conProp, httpHeader header, OutputStream response, PushbackInputStream body) throws IOException;
    /*
      The POST method is used to request that the origin server accept the
      entity enclosed in the request as a new subordinate of the resource
      identified by the Request-URI in the Request-Line. POST is designed
      to allow a uniform method to cover the following functions:
      
      - Annotation of existing resources;

      - Posting a message to a bulletin board, newsgroup, mailing list,
        or similar group of articles;

      - Providing a block of data, such as the result of submitting a
        form, to a data-handling process;

      - Extending a database through an append operation.

      The actual function performed by the POST method is determined by the
      server and is usually dependent on the Request-URI. The posted entity
      is subordinate to that URI in the same way that a file is subordinate
      to a directory containing it, a news article is subordinate to a
      newsgroup to which it is posted, or a record is subordinate to a
      database.
      
      The action performed by the POST method might not result in a
      resource that can be identified by a URI. In this case, either 200
      (OK) or 204 (No Content) is the appropriate response status,
      depending on whether or not the response includes an entity that
      describes the result.
      
      If a resource has been created on the origin server, the response
      SHOULD be 201 (Created) and contain an entity which describes the
      status of the request and refers to the new resource, and a Location
      header (see section 14.30).
      
      Responses to this method are not cacheable, unless the response
      includes appropriate Cache-Control or Expires header fields. However,
      the 303 (See Other) response can be used to direct the user agent to
      retrieve a cacheable resource.
      
      POST requests MUST obey the message transmission requirements set out
      in section 8.2.
    */

    void doConnect(Properties conProp, httpHeader requestHeader, InputStream clientIn, OutputStream clientOut) throws IOException;
    /* this is only needed for https proxies. http daemons should throw a
     * UnsupportedOperationException
     */
    
    //public long getLastModified(Properties conProp);
}
