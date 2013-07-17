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
package net.yacy.upnp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class can be used to listen for UPNP devices responses when a search message is sent by a control point
 * ( using the net.yacy.upnp.Discovery.sendSearchMessage() method )
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class DiscoveryListener implements Runnable {
  
  private final static Log log = LogFactory.getLog( DiscoveryListener.class );
  
  private static boolean MATCH_IP = true;
  
  static {
    String prop = System.getProperty( "net.yacy.upnp.ddos.matchip" );
    if ( prop != null && prop.equals( "false" ) ) MATCH_IP = false;
  }
  
  private static final int DEFAULT_TIMEOUT = 250;
 
  private final Map<String, Set<DiscoveryResultsHandler>> registeredHandlers = new HashMap<String, Set<DiscoveryResultsHandler>>();
  
  private final Object REGISTRATION_PROCESS = new Object();
  
  private final static DiscoveryListener singleton = new DiscoveryListener();
  
  private boolean inService = false;
  private boolean daemon = true;
  
  private java.net.MulticastSocket skt;
  private DatagramPacket input;
  
  private DiscoveryListener() {
  }
  
  public final static DiscoveryListener getInstance() {
    return singleton;
  }
  
  /**
   * Sets the listener as a daemon thread
   * @param daemon daemon thread
   */
  public void setDaemon( boolean daemon ) {
    this.daemon = daemon;
  }
  
  /**
   * Registers an SSDP response message handler
   * @param resultsHandler the SSDP response message handler
   * @param searchTarget the search target
   * @throws IOException if some errors occurs during SSDP search response messages listener thread startup
   */
  public void registerResultsHandler( DiscoveryResultsHandler resultsHandler, String searchTarget ) throws IOException {
    synchronized( this.REGISTRATION_PROCESS ) {
      if ( !this.inService ) startDevicesListenerThread();
      Set<DiscoveryResultsHandler> handlers = this.registeredHandlers.get( searchTarget );
      if ( handlers == null ) {
        handlers = new HashSet<DiscoveryResultsHandler>();
        this.registeredHandlers.put( searchTarget, handlers );
      }
      handlers.add( resultsHandler );
    }
  }

  /**
   * Unregisters an SSDP response message handler
   * @param resultsHandler the SSDP response message handler
   * @param searchTarget the search target
   */
  public void unRegisterResultsHandler( DiscoveryResultsHandler resultsHandler, String searchTarget ) {
    synchronized( this.REGISTRATION_PROCESS ) {
      Set<DiscoveryResultsHandler> handlers = this.registeredHandlers.get( searchTarget );
      if ( handlers != null ) {
        handlers.remove( resultsHandler );
        if (handlers.isEmpty()) {
          this.registeredHandlers.remove( searchTarget );
        }
      }
      if (this.registeredHandlers.isEmpty()) {
        stopDevicesListenerThread();
      }
    }
  }
  
  private void startDevicesListenerThread() throws IOException {
    synchronized( singleton ) {
      if ( !this.inService ) {
        
        this.startMultiCastSocket();
        Thread deamon = new Thread( this, "DiscoveryListener daemon" );
        deamon.setDaemon( this.daemon );
        deamon.start();
        while ( !this.inService ) {
          // wait for the thread to be started let's wait a few ms
          try {
            Thread.sleep( 2 );
          } catch( InterruptedException ex ) {
            // don t care
          }
        }
      }
    }
  }
  
  private void stopDevicesListenerThread() {
    synchronized( singleton ) {
      this.inService = false;
    }
  }
  
  private void startMultiCastSocket() throws IOException {
    int bindPort = Discovery.DEFAULT_SSDP_SEARCH_PORT;
    String port = System.getProperty( "net.yacy.upnp.Discovery.bindPort" );
    if ( port != null ) {
      bindPort = Integer.parseInt( port );
    }

    this.skt = new java.net.MulticastSocket( null );
    this.skt.bind( new InetSocketAddress( InetAddress.getByName( "0.0.0.0" ), bindPort ) );
    this.skt.setTimeToLive( Discovery.DEFAULT_TTL );
    this.skt.setSoTimeout( DEFAULT_TIMEOUT );
    this.skt.joinGroup( InetAddress.getByName( Discovery.SSDP_IP ) );

    byte[] buf = new byte[2048];
    this.input = new DatagramPacket( buf, buf.length );

  }

  @Override
public void run() {
    if ( !Thread.currentThread().getName().equals( "DiscoveryListener daemon" ) ) {
      throw new RuntimeException( "No right to call this method" );
    }
    this.inService = true;
    while ( this.inService ) {
      try {
        listenBroadCast();
      } catch (final  SocketTimeoutException ex ) {
        // ignoring
      } catch (final  IOException ioEx ) {
        log.error( "IO Exception during UPNP DiscoveryListener messages listening thread" );
      } catch( Exception ex ) {
        log.error( "Fatal Error during UPNP DiscoveryListener messages listening thread, thread will exit" );
        this.inService = false;
      }
    }
    
    try {
      this.skt.leaveGroup( InetAddress.getByName( Discovery.SSDP_IP ) );
      this.skt.close();
    } catch (final  Exception ex ) {
      // ignoring
    }
  }

  private void listenBroadCast() throws IOException {
    
    this.skt.receive( this.input );
    InetAddress from = this.input.getAddress();
    String received = new String( this.input.getData(), this.input.getOffset(), this.input.getLength() );
    HttpResponse msg = null;
    try {
      msg = new HttpResponse( received );
    } catch (final IllegalArgumentException ex ) {
      // crappy http sent
      if ( log.isDebugEnabled() ) log.debug( "Skipping uncompliant HTTP message " + received );
      return;
    }
    String header = msg.getHeader();
    if ( header != null && header.startsWith( "HTTP/1.1 200 OK" ) && msg.getHTTPHeaderField( "st" ) != null ) {
      // probably a search repsonse !
      String deviceDescrLoc = msg.getHTTPHeaderField( "location" );
      if( deviceDescrLoc == null || deviceDescrLoc.trim().isEmpty() ) {
        if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'location' field" );
        return;
      }
      URL loc = new URL( deviceDescrLoc );
      if ( MATCH_IP ) {
        InetAddress locHost = InetAddress.getByName( loc.getHost() );
        if ( !from.equals( locHost ) ) {
         log.warn( "Discovery message sender IP " + from +
                   " does not match device description IP " + locHost + 
                   " skipping device, set the net.yacy.upnp.ddos.matchip system property" +
                   " to false to avoid this check" );
         return;
        }
      }
      if ( log.isDebugEnabled() ) log.debug( "Processing " + deviceDescrLoc + " device description location" );
      String st = msg.getHTTPHeaderField( "st" );
      if( st == null || st.trim().isEmpty() ) {
        if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'st' field" );
        return;
      }
      String usn = msg.getHTTPHeaderField( "usn" );
      if( usn == null || usn.trim().isEmpty() ) {
        if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'usn' field" );
        return;
      }
      String maxAge = msg.getHTTPFieldElement( "Cache-Control", "max-age" );
      if( maxAge == null || maxAge.trim().isEmpty() ) {
        if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'max-age' field" );
        return;
      }
      String server = msg.getHTTPHeaderField( "server" );
      if( server == null || server.trim().isEmpty() ) {
        if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'server' field" );
        return;
      }

      String udn = usn;
      int index = udn.indexOf( "::" );
      if ( index != -1 ) udn = udn.substring( 0, index );
      synchronized( this.REGISTRATION_PROCESS ) {
        Set<DiscoveryResultsHandler> handlers = this.registeredHandlers.get( st );
        if ( handlers != null ) {
          for ( DiscoveryResultsHandler handler : handlers ) {
            handler.discoveredDevice( usn, udn, st, maxAge, loc, server );
          }
        }
      }
    } else {
      if ( log.isDebugEnabled() ) log.debug( "Skipping uncompliant HTTP message " + received );
    }
  }
}
