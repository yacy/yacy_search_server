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

import java.util.*;
import java.io.*;
import java.net.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.upnp.services.UPNPService;

import org.apache.commons.logging.*;
import org.xml.sax.InputSource;

/**
 * This class can be used with the ServiceEventHandler interface
 * to recieve notifications about state variables changes on
 * a given UPNP service.
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class ServicesEventing implements Runnable {

  private final static Log log = LogFactory.getLog( ServicesEventing.class );

  private final static ServicesEventing singleton = new ServicesEventing();
  private boolean inService = false;
  
  private boolean daemon = true;
  private int daemonPort = 9999;
  
  private ServerSocket server = null;
  
  private List<Subscription> registered = new ArrayList<Subscription>();
  
  private ServicesEventing() {
  }

  public final static ServicesEventing getInstance() {
    return singleton;
  }
  
  /**
   * Set the listeniner thread as a daemon, default to true.
   * Only works when no more objects are registered.
   * @param daemon the new thread type.
   */
  public void setDaemon( boolean daemon ) {
    this.daemon = daemon;
  }
  
  /**
   * Sets the listener thread port, default to 9999.
   * Only works when no more objects are registered.
   * @param daemonPort the new listening port
   */
  public void setDaemonPort( int daemonPort ) {
    this.daemonPort = daemonPort;
  }
  
  /**
   * Register state variable events notification for a device service
   * @param service the service to register with
   * @param handler the registrant object
   * @param subscriptionDuration subscription time in seconds, -1 for infinite time
   * @return the subscription duration returned by the device, 0 for an infinite duration or -1 if no subscription done
   * @throws IOException if some IOException error happens during coms with the device
   */
  public int register( UPNPService service, ServiceEventHandler handler, int subscriptionDuration ) throws IOException  {
    ServiceEventSubscription sub = registerEvent( service, handler, subscriptionDuration );
    if ( sub != null ) {
      return sub.getDurationTime();
    }
    return -1;
  }

  /**
   * Register state variable events notification for a device service
   * @param service the service to register with
   * @param handler the registrant object
   * @param subscriptionDuration subscription time in seconds, -1 for infinite time
   * @return an ServiceEventSubscription object instance containing all the required info or null if no subscription done
   * @throws IOException if some IOException error happens during coms with the device
   */
  public ServiceEventSubscription registerEvent( UPNPService service, ServiceEventHandler handler, int subscriptionDuration ) throws IOException  {

    URL eventingLoc = service.getEventSubURL();
  
    if ( eventingLoc != null ) {
      
      if ( !inService ) startServicesEventingThread();
      String duration = Integer.toString( subscriptionDuration );
      if ( subscriptionDuration == -1 ) {
        duration = "infinite";
      }

      Subscription sub = lookupSubscriber( service, handler );
      if ( sub != null ) {
        // allready registered let's try to unregister it
        unRegister( service, handler );
      }
      
      StringBuilder packet = new StringBuilder( 64 );
      packet.append( "SUBSCRIBE " ).append( eventingLoc.getFile() ).append( " HTTP/1.1\r\n" );
      packet.append( "HOST: " ).append( eventingLoc.getHost() ).append( ":" ).append( eventingLoc.getPort() ).append( "\r\n" );
      packet.append( "CALLBACK: <http://" ).append( InetAddress.getLocalHost().getHostAddress() ).append( ":" ).append( daemonPort ).append( "" ).append( eventingLoc.getFile() ).append( ">\r\n" );
      packet.append( "NT: upnp:event\r\n" );
      packet.append( "Connection: close\r\n" ); 
      packet.append( "TIMEOUT: Second-" ).append( duration ).append( "\r\n\r\n" );

      Socket skt = new Socket( eventingLoc.getHost(), eventingLoc.getPort() );
      skt.setSoTimeout( 30000 ); // 30 secs timeout according to the specs
      if ( log.isDebugEnabled() ) log.debug( packet );
      OutputStream out = skt.getOutputStream();
      out.write( packet.toString().getBytes() );
      out.flush();

      InputStream in = skt.getInputStream();      
      StringBuilder data = new StringBuilder();
      int readen = 0;
      byte[] buffer = new byte[256];
      while ( ( readen = in.read( buffer ) ) != -1 ) {
        data.append( new String( buffer, 0, readen ) );
      }
      in.close();
      out.close();
      skt.close();
      if ( log.isDebugEnabled() ) log.debug( data.toString() );
      if ( data.toString().trim().length() > 0 ) {
        HttpResponse resp = new HttpResponse( data.toString() );
  
        if ( resp.getHeader().startsWith( "HTTP/1.1 200 OK" ) ) {
          String sid = resp.getHTTPHeaderField( "SID" );
          String actualTimeout = resp.getHTTPHeaderField( "TIMEOUT" );
          int durationTime = 0;
          // actualTimeout = Second-xxx or Second-infinite
          if ( !actualTimeout.equalsIgnoreCase( "Second-infinite" ) ) {
            durationTime = Integer.parseInt( actualTimeout.substring( 7 ) );
          }
          sub = new Subscription();
          sub.handler = handler;
          sub.sub = new ServiceEventSubscription( service.getServiceType(), service.getServiceId(),
                                                  service.getEventSubURL(), sid, skt.getInetAddress(),
                                                  durationTime );
          synchronized( registered ) {
            registered.add( sub );
          }
          return sub.sub;
        }
      }
    }
    return null;

  }
  
  private Subscription lookupSubscriber( UPNPService service, ServiceEventHandler handler ) {
    synchronized( registered ) {
      for ( Iterator<Subscription> i = registered.iterator(); i.hasNext(); ) {
        Subscription sub = i.next();
        
        if ( sub.handler == handler &&
             sub.sub.getServiceId().hashCode() == service.getServiceId().hashCode() &&
             sub.sub.getServiceType().hashCode() == service.getServiceType().hashCode() &&
             sub.sub.getServiceURL().equals( service.getEventSubURL() ) ) {
          return sub;
        }     
      }
    }
    return null;
  }
  
  private Subscription lookupSubscriber( String sid, InetAddress deviceIp ) {
    synchronized( registered ) {
      for ( Iterator<Subscription> i = registered.iterator(); i.hasNext(); ) {
        Subscription sub = i.next();
        
        if ( sub.sub.getSID().equals( sid ) && sub.sub.getDeviceIp().equals( deviceIp ) ) {
          return sub;
        }     
      }
    }
    return null;
  }
  
  private Subscription lookupSubscriber( String sid ) {
    synchronized( registered ) {
      for ( Iterator<Subscription> i = registered.iterator(); i.hasNext(); ) {
        Subscription sub = i.next();
      
        if ( sub.sub.getSID().equals( sid ) ) {
          return sub;
        }     
      }
    }
    return null;
  }
  
  /**
   * Unregisters events notifications from a service
   * @param service the service that need to be unregistered
   * @param handler the handler that registered for this service
   * @return true if unregistered false otherwise ( the given handler never registred for the given service )
   * @throws IOException if some IOException error happens during coms with the device
   */
  public boolean unRegister( UPNPService service, ServiceEventHandler handler ) throws IOException  {

    URL eventingLoc = service.getEventSubURL();
  
    if ( eventingLoc != null ) {
    
      Subscription sub = lookupSubscriber( service, handler );
      if ( sub != null ) {
        synchronized( registered ) {
          registered.remove( sub );
        }
        if (registered.isEmpty()) {
          stopServicesEventingThread(); 
        }
        
        StringBuilder packet = new StringBuilder( 64 );
        packet.append( "UNSUBSCRIBE  " ).append( eventingLoc.getFile() ).append( " HTTP/1.1\r\n" );
        packet.append( "HOST: " ).append( eventingLoc.getHost() ).append( ":" ).append( eventingLoc.getPort() ).append( "\r\n" );
        packet.append( "SID: " ).append( sub.sub.getSID() ).append( "\r\n\r\n" );
        Socket skt = new Socket( eventingLoc.getHost(), eventingLoc.getPort() );
        skt.setSoTimeout( 30000 ); // 30 secs timeout according to the specs
        if ( log.isDebugEnabled() ) log.debug( packet );
        OutputStream out = skt.getOutputStream();
        out.write( packet.toString().getBytes() );
        out.flush();
  
        InputStream in = skt.getInputStream();      
        StringBuilder data = new StringBuilder();
        int readen = 0;
        byte[] buffer = new byte[256];
        while ( ( readen = in.read( buffer ) ) != -1 ) {
          data.append( new String( buffer, 0, readen ) );
        }
        in.close();
        out.close();
        skt.close();
        if ( log.isDebugEnabled() ) log.debug( data.toString() );
        if ( data.toString().trim().length() > 0 ) {
          HttpResponse resp = new HttpResponse( data.toString() );
          if ( resp.getHeader().startsWith( "HTTP/1.1 200 OK" ) ) {
            return true; 
          }
        }
      }
    }
    return false;
  }
 

  private void startServicesEventingThread() {
    synchronized( singleton ) {
      if ( !inService ) {
        Thread deamon = new Thread( singleton, "ServicesEventing daemon" );
        deamon.setDaemon( daemon );
        inService = true;
        deamon.start();
      }
    }
  }
  
  private void stopServicesEventingThread() {
    synchronized( singleton ) {
      inService = false;
      try {
        server.close();
      } catch (final  IOException ex ) {
        // should not happen
      }
    }
  }

  @Override
public void run() {
    // only the deamon thread is allowed to call such method
    if ( !Thread.currentThread().getName().equals( "ServicesEventing daemon" ) ) return;
    try {
      server = new ServerSocket( daemonPort );
    } catch (final  IOException ex )  {
      log.error( "Error during daemon server socket on port " + daemonPort + " creation", ex );
      return;
    }
    while ( inService ) {
      try {
        Socket skt = server.accept();
        new Thread( new RequestProcessor( skt ) ).start();
      } catch (final  IOException ioEx ) {
        if ( inService ) {
          log.error( "IO Exception during UPNP messages listening thread", ioEx );
        }
      } 
    }
  }
  
  private class Subscription {
    private ServiceEventSubscription sub = null;
    private ServiceEventHandler handler = null;
  }
  
  private class RequestProcessor implements Runnable {
    
    private Socket client;
    
    private RequestProcessor( Socket client ) {
      this.client = client;
    }
    
    @Override
    public void run() {
      try {
        client.setSoTimeout( 30000 );
        InputStream in = client.getInputStream();
        OutputStream out = client.getOutputStream();

        int readen = 0;
        StringBuilder data = new StringBuilder();
        byte[] buffer = new byte[256];
        boolean EOF = false;
        while ( !EOF && ( readen = in.read( buffer ) ) != -1 ) {
          data.append( new String( buffer, 0, readen ) );
          // avoid a strange behaviour with some impls.. the -1 is never reached and a sockettimeout occurs
          // and a 0 byte is sent as the last byte
          if ( data.charAt( data.length()-1 ) == (char)0 ) {
            EOF = true;
          }
        }
       
        String packet = data.toString();
        if ( packet.trim().length() > 0 ) {
          
          if ( packet.indexOf( (char)0 ) != -1 ) packet = packet.replace( (char)0, ' ' );
          HttpResponse resp = new HttpResponse( packet );
          if ( resp.getHeader().startsWith( "NOTIFY" ) ) {

            String sid = resp.getHTTPHeaderField( "SID" );
            InetAddress deviceIp = client.getInetAddress();
            String postURL = resp.getHTTPHeaderField( "SID" );
            Subscription subscription = null;
            if ( sid != null && postURL != null ) {
              subscription = lookupSubscriber( sid, deviceIp );
              if ( subscription == null ) {
                // not found maybe that the IP is not the same
                subscription  = lookupSubscriber( sid );
              }
            }
            if ( subscription != null ) {
              // respond ok
              out.write( "HTTP/1.1 200 OK\r\n".getBytes() );
            } else {
              // unknown sid respond ko
              out.write( "HTTP/1.1 412 Precondition Failed\r\n".getBytes() );
            }
      
            out.flush();
            in.close();
            out.close();
            client.close();
      
            if ( subscription != null ) {
              // let's parse it
              SAXParserFactory saxParFact = SAXParserFactory.newInstance();
              saxParFact.setValidating( false );
              saxParFact.setNamespaceAware( true );
              SAXParser parser = saxParFact.newSAXParser();
              ServiceEventMessageParser msgParser = new ServiceEventMessageParser();
              StringReader stringReader = new StringReader( resp.getBody() );
              InputSource src = new InputSource( stringReader );
              parser.parse( src, msgParser );
        
              Map<String, String> changedStateVars = msgParser.getChangedStateVars();
              for ( Iterator<String> i = changedStateVars.keySet().iterator(); i.hasNext(); ) {
                String stateVarName = i.next();
                String stateVarNewVal = changedStateVars.get( stateVarName );
                subscription.handler.handleStateVariableEvent( stateVarName, stateVarNewVal );
              }
            }
          }
        }
      } catch (final  IOException ioEx ) {
        log.error( "IO Exception during client processing thread", ioEx );
      } catch( Exception ex ) {
        log.error( "Unexpected error during client processing thread", ex );
      }
    }
  }
}
