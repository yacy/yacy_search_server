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
 * SSDP messages listener Thread, notify registered objects implementing the interface
 * DiscoveryEventHandler</br> when a device joins the networks or leaves it.<br/>
 * The listener thread is set to only accept matching device description and broadcast message sender IP to
 * avoid a security flaw with the protocol. If you are not happy with such behaviour you can set the
 * net.yacy.upnp.ddos.matchip system property to false to avoid this check.
 *
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class DiscoveryAdvertisement implements Runnable
{

    private final static Log log = LogFactory.getLog(DiscoveryAdvertisement.class);

    private static boolean MATCH_IP = true;

    static {
        String prop = System.getProperty("net.yacy.upnp.ddos.matchip");
        if ( prop != null && prop.equals("false") ) {
            MATCH_IP = false;
        }
    }

    private static final int DEFAULT_TIMEOUT = 250;

    public final static int EVENT_SSDP_ALIVE = 0;
    public final static int EVENT_SSDP_BYE_BYE = 1;

    private final static String NTS_SSDP_ALIVE = "ssdp:alive";
    private final static String NTS_SSDP_BYE_BYE = "ssdp:byebye";
    private final static String NT_ALL_EVENTS = "DiscoveryAdvertisement:nt:allevents";

    private final Map<String, Set<DiscoveryEventHandler>> byeByeRegistered =
        new HashMap<String, Set<DiscoveryEventHandler>>();
    private final Map<String, Set<DiscoveryEventHandler>> aliveRegistered =
        new HashMap<String, Set<DiscoveryEventHandler>>();
    private final Map<String, InetAddress> USNPerIP = new HashMap<String, InetAddress>();

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

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    /**
     * Registers an event category sent by UPNP devices
     *
     * @param notificationEvent the event type, either DiscoveryAdvertisement.EVENT_SSDP_ALIVE or
     *        DiscoveryAdvertisement.EVENT_SSDP_BYE_BYE
     * @param nt the type of device advertisement, upnp:rootdevice will return you all advertisement in
     *        relation with nt upnp:rootdevice a null value specify that all nt type are wanted
     * @param eventHandler the events handler, this objet will receive notifications..
     * @throws IOException if an error ocurs when the SSDP events listeners threads starts
     */
    public void registerEvent(int notificationEvent, String nt, DiscoveryEventHandler eventHandler)
        throws IOException {
        synchronized ( this.REGISTRATION_PROCESS ) {
            if ( !this.inService ) {
                startDevicesListenerThread();
            }
            if ( nt == null ) {
                nt = NT_ALL_EVENTS;
            }
            if ( notificationEvent == EVENT_SSDP_ALIVE ) {
                Set<DiscoveryEventHandler> handlers = this.aliveRegistered.get(nt);
                if ( handlers == null ) {
                    handlers = new HashSet<DiscoveryEventHandler>();
                    this.aliveRegistered.put(nt, handlers);
                }
                handlers.add(eventHandler);
            } else if ( notificationEvent == EVENT_SSDP_BYE_BYE ) {
                Set<DiscoveryEventHandler> handlers = this.byeByeRegistered.get(nt);
                if ( handlers == null ) {
                    handlers = new HashSet<DiscoveryEventHandler>();
                    this.byeByeRegistered.put(nt, handlers);
                }
                handlers.add(eventHandler);
            } else {
                throw new IllegalArgumentException("Unknown notificationEvent type");
            }
        }
    }

    /**
     * Unregisters an event category sent by UPNP devices
     *
     * @param notificationEvent the event type, either DiscoveryAdvertisement.EVENT_SSDP_ALIVE or
     *        DiscoveryAdvertisement.EVENT_SSDP_BYE_BYE
     * @param nt the type of device advertisement, upnp:rootdevice will unregister all advertisement in
     *        relation with nt upnp:rootdevice a null value specify that all nt type are unregistered
     * @param eventHandler the events handler that needs to be unregistred.
     */
    public void unRegisterEvent(int notificationEvent, String nt, DiscoveryEventHandler eventHandler) {
        synchronized ( this.REGISTRATION_PROCESS ) {
            if ( nt == null ) {
                nt = NT_ALL_EVENTS;
            }
            if ( notificationEvent == EVENT_SSDP_ALIVE ) {
                Set<DiscoveryEventHandler> handlers = this.aliveRegistered.get(nt);
                if ( handlers != null ) {
                    handlers.remove(eventHandler);
                    if ( handlers.isEmpty() ) {
                        this.aliveRegistered.remove(nt);
                    }
                }
            } else if ( notificationEvent == EVENT_SSDP_BYE_BYE ) {
                Set<DiscoveryEventHandler> handlers = this.byeByeRegistered.get(nt);
                if ( handlers != null ) {
                    handlers.remove(eventHandler);
                    if ( handlers.isEmpty() ) {
                        this.byeByeRegistered.remove(nt);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown notificationEvent type");
            }
            if ( this.aliveRegistered.isEmpty() && this.byeByeRegistered.isEmpty() ) {
                stopDevicesListenerThread();
            }
        }
    }

    private void startDevicesListenerThread() throws IOException {
        synchronized ( singleton ) {
            if ( !this.inService ) {
                this.startMultiCastSocket();
                Thread deamon = new Thread(this, "DiscoveryAdvertisement daemon");
                deamon.setDaemon(this.daemon);
                deamon.start();
                // wait for the thread to be started
                while ( !this.inService ) {
                    // let's wait a few ms
                    try {
                        Thread.sleep(2);
                    } catch (final  InterruptedException ex ) {
                        // don t care
                    }
                }
            }
        }
    }

    private void stopDevicesListenerThread() {
        synchronized ( singleton ) {
            this.inService = false;
        }
    }

    private void startMultiCastSocket() throws IOException {

        this.skt = new java.net.MulticastSocket(null);
        this.skt.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), Discovery.SSDP_PORT));
        this.skt.setTimeToLive(Discovery.DEFAULT_TTL);
        this.skt.setSoTimeout(DEFAULT_TIMEOUT);
        this.skt.joinGroup(InetAddress.getByName(Discovery.SSDP_IP));

        byte[] buf = new byte[2048];
        this.input = new DatagramPacket(buf, buf.length);

    }

    @Override
    public void run() {
        if ( !Thread.currentThread().getName().equals("DiscoveryAdvertisement daemon") ) {
            throw new RuntimeException("No right to call this method");
        }
        this.inService = true;
        while ( this.inService ) {
            try {
                listenBroadCast();
            } catch (final  SocketTimeoutException ex ) {
                // ignoring
            } catch (final  IOException ioEx ) {
                // fail silently
                //log.warn("IO Exception during UPNP DiscoveryAdvertisement messages listening thread");
            } catch (final  Exception ex ) {
                // fail silently
                //log.warn("Fatal Error during UPNP DiscoveryAdvertisement messages listening thread, thread will exit");
                this.inService = false;
                this.aliveRegistered.clear();
                this.byeByeRegistered.clear();
                this.USNPerIP.clear();
            }
        }

        try {
            this.skt.leaveGroup(InetAddress.getByName(Discovery.SSDP_IP));
            this.skt.close();
        } catch (final  Exception ex ) {
            // ignoring
        }
    }

    private void listenBroadCast() throws IOException {

        this.skt.receive(this.input);
        InetAddress from = this.input.getAddress();
        String received = new String(this.input.getData(), this.input.getOffset(), this.input.getLength());
        HttpResponse msg = null;
        try {
            msg = new HttpResponse(received);
        } catch (final  IllegalArgumentException ex ) {
            // crappy http sent
            if ( log.isDebugEnabled() ) {
                log.debug("Skipping uncompliant HTTP message " + received);
            }
            return;
        }
        String header = msg.getHeader();
        if ( header != null && header.startsWith("NOTIFY") ) {
            if ( log.isDebugEnabled() ) {
                log.debug(received);
            }
            String ntsField = msg.getHTTPHeaderField("nts");
            if ( ntsField == null || ntsField.trim().isEmpty() ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Skipping SSDP message, missing HTTP header 'ntsField' field");
                }
                return;
            }
            if ( ntsField.equals(NTS_SSDP_ALIVE) ) {
                String deviceDescrLoc = msg.getHTTPHeaderField("location");
                if ( deviceDescrLoc == null || deviceDescrLoc.trim().isEmpty() ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Skipping SSDP message, missing HTTP header 'location' field");
                    }
                    return;
                }
                URL loc = new URL(deviceDescrLoc);
                if ( MATCH_IP ) {
                    InetAddress locHost = InetAddress.getByName(loc.getHost());
                    if ( !from.equals(locHost) ) {
                        log.warn("Discovery message sender IP "
                            + from
                            + " does not match device description IP "
                            + locHost
                            + " skipping message, set the net.yacy.upnp.ddos.matchip system property"
                            + " to false to avoid this check");
                        return;
                    }
                }

                String nt = msg.getHTTPHeaderField("nt");
                if ( nt == null || nt.trim().isEmpty() ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Skipping SSDP message, missing HTTP header 'nt' field");
                    }
                    return;
                }
                String maxAge = msg.getHTTPFieldElement("Cache-Control", "max-age");
                if ( maxAge == null || maxAge.trim().isEmpty() ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Skipping SSDP message, missing HTTP header 'max-age' field");
                    }
                    return;
                }
                String usn = msg.getHTTPHeaderField("usn");
                if ( usn == null || usn.trim().isEmpty() ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Skipping SSDP message, missing HTTP header 'usn' field");
                    }
                    return;
                }

                this.USNPerIP.put(usn, from);
                String udn = usn;
                int index = udn.indexOf("::");
                if ( index != -1 ) {
                    udn = udn.substring(0, index);
                }
                synchronized ( this.REGISTRATION_PROCESS ) {
                    Set<DiscoveryEventHandler> handlers = this.aliveRegistered.get(NT_ALL_EVENTS);
                    if ( handlers != null ) {
                        for ( DiscoveryEventHandler eventHandler : handlers ) {
                            eventHandler.eventSSDPAlive(usn, udn, nt, maxAge, loc);
                        }
                    }
                    handlers = this.aliveRegistered.get(nt);
                    if ( handlers != null ) {
                        for ( DiscoveryEventHandler eventHandler : handlers ) {
                            eventHandler.eventSSDPAlive(usn, udn, nt, maxAge, loc);
                        }
                    }
                }
            } else if ( ntsField.equals(NTS_SSDP_BYE_BYE) ) {
                String usn = msg.getHTTPHeaderField("usn");
                if ( usn == null || usn.trim().isEmpty() ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Skipping SSDP message, missing HTTP header 'usn' field");
                    }
                    return;
                }
                String nt = msg.getHTTPHeaderField("nt");
                if ( nt == null || nt.trim().isEmpty() ) {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Skipping SSDP message, missing HTTP header 'nt' field");
                    }
                    return;
                }

                InetAddress originalAliveSenderIp = this.USNPerIP.get(usn);
                if ( originalAliveSenderIp != null ) {
                    // we check that the sender ip of message for the usn
                    // match the sender ip of the alive message for wich the usn
                    // has been received
                    if ( !originalAliveSenderIp.equals(from) ) {
                        // someone else is trying to say that the usn is leaving
                        // since IP do not match we skip the message
                        return;
                    }
                }

                String udn = usn;
                int index = udn.indexOf("::");
                if ( index != -1 ) {
                    udn = udn.substring(0, index);
                }
                synchronized ( this.REGISTRATION_PROCESS ) {
                    Set<DiscoveryEventHandler> handlers = this.byeByeRegistered.get(NT_ALL_EVENTS);
                    if ( handlers != null ) {
                        for ( DiscoveryEventHandler eventHandler : handlers ) {
                            eventHandler.eventSSDPByeBye(usn, udn, nt);
                        }
                    }
                    handlers = this.byeByeRegistered.get(nt);
                    if ( handlers != null ) {
                        for ( DiscoveryEventHandler eventHandler : handlers ) {
                            eventHandler.eventSSDPByeBye(usn, udn, nt);
                        }
                    }
                }
            } else {
                log
                    .warn("Unvalid NTS field value ("
                        + ntsField
                        + ") received in NOTIFY message :"
                        + received);
            }
        }
    }
}
