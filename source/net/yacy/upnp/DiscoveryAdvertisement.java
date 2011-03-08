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

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.commons.logging.*;

/**
 * SSDP messages listener Thread, notify registered objects implementing the interface DiscoveryEventHandler</br>
 * when a device joins the networks or leaves it.<br/>
 * The listener thread is set to only accept matching device description and broadcast message sender IP
 * to avoid a security flaw with the protocol. If you are not happy with such behaviour
 * you can set the net.yacy.upnp.ddos.matchip system property to false to avoid this check.
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class DiscoveryAdvertisement implements Runnable {
  
  private final static Log log = LogFactory.getLog( DiscoveryAdvertisement.class );
  
  private static boolean MATCH_IP = true;
  
  static {
    String prop = System.getProperty( "net.yacy.upnp.ddos.matchip" );
    if ( prop != null && prop.equals( "false" ) ) MATCH_IP = false;
  }
  
  private static final int DEFAULT_TIMEOUT = 250;
  
  public final static int EVENT_SSDP_ALIVE = 0;
  public final static int EVENT_SSDP_BYE_BYE = 1;

  private final static String NTS_SSDP_ALIVE = "ssdp:alive";
  private final static String NTS_SSDP_BYE_BYE = "ssdp:byebye";
  private final static String NT_ALL_EVENTS = "DiscoveryAdvertisement:nt:allevents";
  
  private Map<String, Set<DiscoveryEventHandler>> byeByeRegistered = new HashMap<String, Set<DiscoveryEventHandler>>();
  private Map<String, Set<DiscoveryEventHandler>> aliveRegistered = new HashMap<String, Set<DiscoveryEventHandler>>();
  private Map<String, InetAddress> USNPerIP = new HashMap<String, InetAddress>();
  
  private final Object REGISTRATION_PROCESS = new Object();
  
  private final static DiscoveryAdvertisement singleton = new DiscoveryAdvertisement();
  private boolean inService = false;
  private boolean daemon = true;
  
  private java.net.MulticastSocket skt;
  private DatagramPacket input;
  
  private DiscoveryAdvertisement() {
  }
  
  public final static DiscoveryAdvertisement getInstance() {
    return singleton;
  }
  
  public void setDaemon( boolean daemon ) {
    this.daemon = daemon;
  }
  
  /**
   * Registers an event category sent by UPNP devices
   * @param notificationEvent the event type, either DiscoveryAdvertisement.EVENT_SSDP_ALIVE
   *        or DiscoveryAdvertisement.EVENT_SSDP_BYE_BYE
   * @param nt the type of device advertisement, upnp:rootdevice will return you all advertisement in relation with nt upnp:rootdevice
   *           a null value specify that all nt type are wanted
   * @param eventHandler the events handler, this objet will receive notifications..
   * @throws IOException if an error ocurs when the SSDP events listeners threads starts
   */
  public void registerEvent( int notificationEvent, String nt, DiscoveryEventHandler eventHandler ) throws IOException {
    synchronized( REGISTRATION_PROCESS ) {
      if ( !inService ) startDevicesListenerThread();
      if ( nt == null ) nt = NT_ALL_EVENTS;
      if ( notificationEvent == EVENT_SSDP_ALIVE ) {
        Set<DiscoveryEventHandler> handlers = aliveRegistered.get( nt );
        if ( handlers == null ) {
          handlers = new HashSet<DiscoveryEventHandler>();
          aliveRegistered.put( nt, handlers );
        }
        handlers.add( eventHandler );
      } else if ( notificationEvent == EVENT_SSDP_BYE_BYE ) {
        Set<DiscoveryEventHandler> handlers = byeByeRegistered.get( nt );
        if ( handlers == null ) {
          handlers = new HashSet<DiscoveryEventHandler>();
          byeByeRegistered.put( nt, handlers );
        }
        handlers.add( eventHandler );
      } else {
        throw new IllegalArgumentException( "Unknown notificationEvent type" );	
      }
    }
  }
  
  /**
   * Unregisters an event category sent by UPNP devices
   * @param notificationEvent the event type, either DiscoveryAdvertisement.EVENT_SSDP_ALIVE
   *        or DiscoveryAdvertisement.EVENT_SSDP_BYE_BYE
   * @param nt the type of device advertisement, upnp:rootdevice will unregister all advertisement in relation with nt upnp:rootdevice
   *           a null value specify that all nt type are unregistered
   * @param eventHandler the events handler that needs to be unregistred.
   */
  public void unRegisterEvent( int notificationEvent, String nt, DiscoveryEventHandler eventHandler ) {
    synchronized( REGISTRATION_PROCESS ) {
      if ( nt == null ) nt = NT_ALL_EVENTS;
      if ( notificationEvent == EVENT_SSDP_ALIVE ) {
        Set<DiscoveryEventHandler> handlers = aliveRegistered.get( nt );
        if ( handlers != null ) {
          handlers.remove( eventHandler );
          if (handlers.isEmpty()) {
            aliveRegistered.remove( nt );
          }
        }
      } else if ( notificationEvent == EVENT_SSDP_BYE_BYE ) {
        Set<DiscoveryEventHandler> handlers = byeByeRegistered.get( nt );
        if ( handlers != null ) {
          handlers.remove( eventHandler );
          if (handlers.isEmpty()) {
            byeByeRegistered.remove( nt );
          }
        }
      } else {
        throw new IllegalArgumentException( "Unknown notificationEvent type" );	
      }
      if (aliveRegistered.isEmpty() && byeByeRegistered.isEmpty()) {
        stopDevicesListenerThread();
      }
    }
  }
  
  private void startDevicesListenerThread() throws IOException {
    synchronized( singleton ) {
      if ( !inService ) {
        this.startMultiCastSocket();
        Thread deamon = new Thread( this, "DiscoveryAdvertisement daemon" );
        deamon.setDaemon( daemon );
        deamon.start();
        // wait for the thread to be started
        while( !inService ) {
          // let's wait a few ms
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
      inService = false;
    }
  }
  
  private void startMultiCastSocket() throws IOException {

    skt = new java.net.MulticastSocket( null );
    skt.bind( new InetSocketAddress( InetAddress.getByName( "0.0.0.0" ), Discovery.SSDP_PORT ) );
    skt.setTimeToLive( Discovery.DEFAULT_TTL );
    skt.setSoTimeout( DEFAULT_TIMEOUT );
    skt.joinGroup( InetAddress.getByName( Discovery.SSDP_IP ) );
    
    byte[] buf = new byte[2048];
    input = new DatagramPacket( buf, buf.length );

  }

  public void run() {
    if ( !Thread.currentThread().getName().equals( "DiscoveryAdvertisement daemon" ) ) {
      throw new RuntimeException( "No right to call this method" );
    }
    inService = true;
    while ( inService ) {
      try {
        listenBroadCast();
      } catch ( SocketTimeoutException ex ) {
        // ignoring
      } catch ( IOException ioEx ) {
        log.error( "IO Exception during UPNP DiscoveryAdvertisement messages listening thread", ioEx );
      } catch( Exception ex ) {
        log.error( "Fatal Error during UPNP DiscoveryAdvertisement messages listening thread, thread will exit", ex );
        inService = false;
        aliveRegistered.clear();
        byeByeRegistered.clear();
        USNPerIP.clear();
      }
    }
    
    try {
      skt.leaveGroup( InetAddress.getByName( Discovery.SSDP_IP ) );
      skt.close();
    } catch ( Exception ex ) {
      // ignoring
    }
  }

  private void listenBroadCast() throws IOException {
    
     skt.receive( input );
     InetAddress from = input.getAddress();
     String received = new String( input.getData(), input.getOffset(), input.getLength() );
     HttpResponse msg = null;
     try {
       msg = new HttpResponse( received );
     } catch (IllegalArgumentException ex ) {
       // crappy http sent
       if ( log.isDebugEnabled() ) log.debug( "Skipping uncompliant HTTP message " + received );
       return;
     }
     String header = msg.getHeader();
     if ( header != null && header.startsWith( "NOTIFY" ) ) {
       if ( log.isDebugEnabled() ) log.debug( received );
       String ntsField = msg.getHTTPHeaderField( "nts" );
       if( ntsField == null || ntsField.trim().length() == 0 ) {
         if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'ntsField' field" );
         return;
       }
       if ( ntsField.equals( NTS_SSDP_ALIVE ) ) {
         String deviceDescrLoc = msg.getHTTPHeaderField( "location" );
         if( deviceDescrLoc == null || deviceDescrLoc.trim().length() == 0 ) {
           if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'location' field" );
           return;
         }
         URL loc = new URL( deviceDescrLoc );
         if ( MATCH_IP ) {
           InetAddress locHost = InetAddress.getByName( loc.getHost() );
           if ( !from.equals( locHost ) ) {
             log.warn( "Discovery message sender IP " + from +
                       " does not match device description IP " + locHost + 
                       " skipping message, set the net.yacy.upnp.ddos.matchip system property" +
                       " to false to avoid this check" );
             return;
           }
         }
         
         String nt = msg.getHTTPHeaderField( "nt" );
         if( nt == null || nt.trim().length() == 0 ) {
           if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'nt' field" );
           return;
         }
         String maxAge = msg.getHTTPFieldElement( "Cache-Control", "max-age" );
         if( maxAge == null || maxAge.trim().length() == 0 ) {
           if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'max-age' field" );
           return;
         }
         String usn = msg.getHTTPHeaderField( "usn" );
         if( usn == null || usn.trim().length() == 0 ) {
           if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'usn' field" );
           return;
         }

         USNPerIP.put( usn, from );
         String udn = usn;
         int index = udn.indexOf( "::" );
         if ( index != -1 ) udn = udn.substring( 0, index );
         synchronized( REGISTRATION_PROCESS ) {
           Set<DiscoveryEventHandler> handlers = aliveRegistered.get( NT_ALL_EVENTS );
           if ( handlers != null ) {
             for ( Iterator<DiscoveryEventHandler> i = handlers.iterator(); i.hasNext(); ) {
               DiscoveryEventHandler eventHandler = i.next();
               eventHandler.eventSSDPAlive( usn, udn, nt, maxAge, loc );
             }
           }
           handlers = aliveRegistered.get( nt );
           if ( handlers != null ) {
             for ( Iterator<DiscoveryEventHandler> i = handlers.iterator(); i.hasNext(); ) {
               DiscoveryEventHandler eventHandler = i.next();
               eventHandler.eventSSDPAlive( usn, udn, nt, maxAge, loc );
             }
           }
         }
       } else if ( ntsField.equals( NTS_SSDP_BYE_BYE ) ) {
         String usn = msg.getHTTPHeaderField( "usn" );
         if( usn == null || usn.trim().length() == 0 ) {
           if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'usn' field" );
           return;
         }
         String nt = msg.getHTTPHeaderField( "nt" );
         if( nt == null || nt.trim().length() == 0 ) {
           if ( log.isDebugEnabled() ) log.debug( "Skipping SSDP message, missing HTTP header 'nt' field" );
           return;
         }

         InetAddress originalAliveSenderIp = USNPerIP.get( usn );
         if ( originalAliveSenderIp != null ) {
           // we check that the sender ip of message for the usn
           // match the sender ip of the alive message for wich the usn
           // has been received
           if ( !originalAliveSenderIp.equals( from ) ) {
             // someone else is trying to say that the usn is leaving
             // since IP do not match we skip the message
             return;
           }
         }
         
         String udn = usn;
         int index = udn.indexOf( "::" );
         if ( index != -1 ) udn = udn.substring( 0, index );
         synchronized( REGISTRATION_PROCESS ) {
           Set<DiscoveryEventHandler> handlers = byeByeRegistered.get( NT_ALL_EVENTS );
           if ( handlers != null ) {
             for ( Iterator<DiscoveryEventHandler> i = handlers.iterator(); i.hasNext(); ) {
               DiscoveryEventHandler eventHandler = i.next();
               eventHandler.eventSSDPByeBye( usn, udn, nt );
             }
           }
           handlers = byeByeRegistered.get( nt );
           if ( handlers != null ) {
             for ( Iterator<DiscoveryEventHandler> i = handlers.iterator(); i.hasNext(); ) {
               DiscoveryEventHandler eventHandler = i.next();
               eventHandler.eventSSDPByeBye( usn, udn, nt );
             }
           }
         }
       } else {
         log.warn( "Unvalid NTS field value (" + ntsField + ") received in NOTIFY message :" + received );
       }
     }
   }
}
