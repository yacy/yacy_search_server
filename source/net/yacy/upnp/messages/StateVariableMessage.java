/*
 * ============================================================================
 *                 The Apache Software License, Version 1.1
 * ============================================================================
 *
 * Copyright (C) 2002 The Apache Software Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modifica-
 * tion, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must  retain the above copyright  notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following  acknowledgment: "This product includes software
 *    developed by SuperBonBon Industries (http://www.sbbi.net/)."
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "UPNPLib" and "SuperBonBon Industries" must not be
 *    used to endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    info@sbbi.net.
 *
 * 5. Products  derived from this software may not be called 
 *    "SuperBonBon Industries", nor may "SBBI" appear in their name, 
 *    without prior written permission of SuperBonBon Industries.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED. IN NO EVENT SHALL THE
 * APACHE SOFTWARE FOUNDATION OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT,INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLU-
 * DING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software  consists of voluntary contributions made by many individuals
 * on behalf of SuperBonBon Industries. For more information on 
 * SuperBonBon Industries, please see <http://www.sbbi.net/>.
 */
package net.yacy.upnp.messages;

import org.xml.sax.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.yacy.upnp.services.*;

/**
 * This class is used to create state variable messages to
 * comminicate with the device
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class StateVariableMessage {
  
  private final static Log log = LogFactory.getLog( StateVariableMessage.class );
  
  private UPNPService service;
  private ServiceStateVariable serviceStateVar;
  
  protected StateVariableMessage( UPNPService service, ServiceStateVariable serviceStateVar ) {
    this.service = service;
    this.serviceStateVar = serviceStateVar;
  }
  
  /**
   * Executes the state variable query and retuns the UPNP device response, according to the UPNP specs,
   * this method could take up to 30 secs to process ( time allowed for a device to respond to a request )
   * @return a state variable response object containing the variable value
   * @throws IOException if some IOException occurs during message send and reception process
   * @throws UPNPResponseException if an UPNP error message is returned from the server
   *         or if some parsing exception occurs ( detailErrorCode = 899, detailErrorDescription = SAXException message )
   */
  public StateVariableResponse service() throws IOException, UPNPResponseException {
    StateVariableResponse rtrVal = null;
    UPNPResponseException upnpEx = null;
    IOException ioEx = null;
    StringBuilder body = new StringBuilder( 256 );
    
    body.append( "<?xml version=\"1.0\"?>\r\n" );
    body.append( "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"" );
    body.append( " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" );
    body.append( "<s:Body>" );
    body.append( "<u:QueryStateVariable xmlns:u=\"urn:schemas-upnp-org:control-1-0\">" );
    body.append( "<u:varName>" ).append( serviceStateVar.getName() ).append( "</u:varName>" );
    body.append( "</u:QueryStateVariable>" );
    body.append( "</s:Body>" );
    body.append( "</s:Envelope>" );
    
    if ( log.isDebugEnabled() ) log.debug( "POST prepared for URL " + service.getControlURL() );
    URL url = new URL( service.getControlURL().toString() );
    HttpURLConnection conn = (HttpURLConnection)url.openConnection();
    conn.setDoInput( true );
    conn.setDoOutput( true );
    conn.setUseCaches( false );
    conn.setRequestMethod( "POST" );
    HttpURLConnection.setFollowRedirects( false );
    //conn.setConnectTimeout( 30000 );
    conn.setRequestProperty( "HOST", url.getHost() + ":" + url.getPort() ); 
    conn.setRequestProperty( "SOAPACTION", "\"urn:schemas-upnp-org:control-1-0#QueryStateVariable\"" );
    conn.setRequestProperty( "CONTENT-TYPE", "text/xml; charset=\"utf-8\"" );
    conn.setRequestProperty( "CONTENT-LENGTH", Integer.toString( body.length() ) );
    OutputStream out = conn.getOutputStream();
    out.write( body.toString().getBytes() );
    out.flush();
    conn.connect();
    InputStream input = null;
    
    if ( log.isDebugEnabled() ) log.debug( "executing query :\n" + body );
    try {
      input = conn.getInputStream();
    } catch (final  IOException ex ) {
      // java can throw an exception if he error code is 500 or 404 or something else than 200
      // but the device sends 500 error message with content that is required
      // this content is accessible with the getErrorStream
      input = conn.getErrorStream();
    }
    
    if ( input != null ) {
      int response = conn.getResponseCode();
      String responseBody = getResponseBody( input );
      if ( log.isDebugEnabled() ) log.debug( "received response :\n" + responseBody );
      SAXParserFactory saxParFact = SAXParserFactory.newInstance();
      saxParFact.setValidating( false );
      saxParFact.setNamespaceAware( true );
      StateVariableResponseParser msgParser = new StateVariableResponseParser( serviceStateVar );
      StringReader stringReader = new StringReader( responseBody );
      InputSource src = new InputSource( stringReader );
      try {
        SAXParser parser = saxParFact.newSAXParser();
        parser.parse( src, msgParser );
      } catch (final  ParserConfigurationException confEx ) {
        // should never happen
        // we throw a runtimeException to notify the env problem
        throw new RuntimeException( "ParserConfigurationException during SAX parser creation, please check your env settings:" + confEx.getMessage() );
      } catch (final  SAXException saxEx ) {
        // kind of tricky but better than nothing..
        upnpEx = new UPNPResponseException( 899, saxEx.getMessage() );
      } finally {
        try {
          input.close();
        } catch (final  IOException ex ) {
          // ignoring
        }
      }
      if ( upnpEx == null ) {
        if ( response == HttpURLConnection.HTTP_OK ) {
          rtrVal = msgParser.getStateVariableResponse();
        } else if ( response == HttpURLConnection.HTTP_INTERNAL_ERROR ) {
          upnpEx = msgParser.getUPNPResponseException();
        } else {
          ioEx = new IOException( "Unexpected server HTTP response:" + response );
        }
      }
    }
    try {
      out.close();
    } catch (final  IOException ex ) {
      // ignore
    }
    conn.disconnect();
    if ( upnpEx != null ) {
      throw upnpEx;
    }
    if ( rtrVal == null && ioEx == null ) {
      ioEx = new IOException( "Unable to receive a response from the UPNP device" );
    }
    if ( ioEx != null ) {
      throw ioEx;
    }
    return rtrVal;
  }
  
  private String getResponseBody( InputStream in ) throws IOException {
    byte[] buffer = new byte[256];
    int readen = 0;
    StringBuilder content = new StringBuilder( 256 );
    while ( ( readen = in.read( buffer ) ) != -1 ) {
      content.append( new String( buffer, 0 , readen ) );
    }
    // some devices add \0 chars at XML message end
    // which causes XML parsing errors...
    int len = content.length();
    while ( content.charAt( len-1 ) == '\0' ) {
      len--;
      content.setLength( len );
    }
    return content.toString().trim();
  }
}
