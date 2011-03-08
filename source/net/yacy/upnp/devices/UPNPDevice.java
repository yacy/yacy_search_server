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
package net.yacy.upnp.devices;

import net.yacy.upnp.services.*;

import java.util.*;
import java.net.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class represents an UPNP device, this device contains a set of services
 * that will be needed to access the device functionalities.
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class UPNPDevice {

  private final static Log log = LogFactory.getLog( UPNPDevice.class );

  protected String deviceType;
  protected String friendlyName;
  protected String manufacturer;
  protected URL manufacturerURL;
  protected URL presentationURL;
  protected String modelDescription;
  protected String modelName;
  protected String modelNumber;
  protected String modelURL;
  protected String serialNumber;
  protected String UDN;
  protected String USN;
  protected long UPC;

  protected ArrayList<DeviceIcon> deviceIcons;
  protected ArrayList<UPNPService> services;
  protected ArrayList<UPNPDevice> childDevices;
  
  protected UPNPDevice parent;
  
  public URL getManufacturerURL() {
    return manufacturerURL;
  }
  
  /**
   * Presentation URL
   * @return URL the presenation URL, or null if the device does not provide
   *         such information
   */
  public URL getPresentationURL() {
    return presentationURL;
  }

  public String getModelDescription() {
    return modelDescription;
  }

  public String getModelName() {
    return modelName;
  }

  public String getModelNumber() {
    return modelNumber;
  }

  public String getModelURL() {
    return modelURL;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public String getUDN() {
    return UDN;
  }
  
  public String getUSN(){
    return USN;
  }

  public long getUPC() {
    return UPC;
  }

  public String getDeviceType() {
    return deviceType;
  }

  public String getFriendlyName() {
    return friendlyName;
  }

  public String getManufacturer() {
    return manufacturer;
  }
  
  public boolean isRootDevice() {
    return this instanceof UPNPRootDevice;
  }

  /**
   * Access to the device icons definitions
   * @return a list containing DeviceIcon objects or null if no icons defined
   */
  public List<DeviceIcon> getDeviceIcons() {
    return deviceIcons;
  }

  /**
   * Generates a list of all the child ( not only top level, full childrens hierarchy included )
   * UPNPDevice objects for this device.
   * @return the generated list or null if no child devices bound
   */
  public ArrayList<UPNPDevice> getChildDevices() {
    if ( childDevices == null ) return null;
    ArrayList<UPNPDevice> rtrVal = new ArrayList<UPNPDevice>();
    for ( Iterator<UPNPDevice> itr = childDevices.iterator(); itr.hasNext(); ) {
      UPNPDevice device = itr.next();
      rtrVal.add( device );
      ArrayList<UPNPDevice> found = device.getChildDevices();
      if ( found != null ) {
        rtrVal.addAll( found );
      }
    }
    return rtrVal;

  }
  
  /**
   * Generates a list of all the child ( only top level )
   * UPNPDevice objects for this device.
   * @return the generated list or null if no child devices bound
   */
  public List<UPNPDevice> getTopLevelChildDevices() {
    if ( childDevices == null ) return null;
    List<UPNPDevice> rtrVal = new ArrayList<UPNPDevice>();
    for ( Iterator<UPNPDevice> itr = childDevices.iterator(); itr.hasNext(); ) {
      UPNPDevice device = itr.next();
      rtrVal.add( device );
    }
    return rtrVal;
  }
  
  /**
   * Return the parent UPNPDevice, null if the device is an UPNPRootDevice
   * @return the parent device instance
   */
  public UPNPDevice getDirectParent() {
    return parent;
  }
 
  /**
   * Looks for a child UPNP device definition file,
   * the whole devices tree will be searched, starting from the current
   * device node.
   * @param deviceURI the device URI to search
   * @return An UPNPDevice if anything matches or null
   */
  public UPNPDevice getChildDevice( String deviceURI ) {
    if ( log.isDebugEnabled() ) log.debug( "searching for device URI:" + deviceURI );
    if ( getDeviceType().equals( deviceURI ) ) return this;
    if ( childDevices == null ) return null;
    for ( Iterator<UPNPDevice> itr = childDevices.iterator(); itr.hasNext(); ) {
      UPNPDevice device = itr.next();
      UPNPDevice found = device.getChildDevice( deviceURI );
      if ( found != null ) {
        return found;
      }
    }
    return null;
  }

  /**
   * Looks for all UPNP device service definitions objects
   * @return A list of all device services
   */
  public ArrayList<UPNPService> getServices() {
    if ( services == null ) return null;
    ArrayList<UPNPService> rtrVal = new ArrayList<UPNPService>();
    rtrVal.addAll( services );
    return rtrVal;
  }

  /**
   * Looks for a UPNP device service definition object for the given service URI (Type)
   * @param serviceURI the URI of the service
   * @return A matching UPNPService object or null
   */
  public UPNPService getService( String serviceURI ) {
    if ( services == null ) return null;
    if ( log.isDebugEnabled() ) log.debug( "searching for service URI:" + serviceURI );
    for ( Iterator<UPNPService> itr = services.iterator(); itr.hasNext(); ) {
      UPNPService service = itr.next();
      if ( service.getServiceType().equals( serviceURI ) ) {
        return service;
      }
    }
    return null;
  }
  
  /**
   * Looks for a UPNP device service definition object for the given service ID
   * @param serviceURI the ID of the service
   * @return A matching UPNPService object or null
   */
  public UPNPService getServiceByID( String serviceID ) {
    if ( services == null ) return null;
    if ( log.isDebugEnabled() ) log.debug( "searching for service ID:" + serviceID );
    for ( Iterator<UPNPService> itr = services.iterator(); itr.hasNext(); ) {
      UPNPService service = itr.next();
      if ( service.getServiceId().equals( serviceID ) ) {
        return service;
      }
    }
    return null;
  }
  
  /**
   * Looks for the all the UPNP device service definition object for the current
   * UPNP device object. This method can be used to retreive multiple same kind
   * ( same service type ) of services with different services id on a device
   * @param serviceURI the URI of the service
   * @return A matching List of UPNPService objects or null
   */
  public List<UPNPService> getServices( String serviceURI ) {
    if ( services == null ) return null;
    List<UPNPService> rtrVal = new ArrayList<UPNPService>();
    if ( log.isDebugEnabled() ) log.debug( "searching for services URI:" + serviceURI );
    for ( Iterator<UPNPService> itr = services.iterator(); itr.hasNext(); ) {
      UPNPService service = itr.next();
      if ( service.getServiceType().equals( serviceURI ) ) {
        rtrVal.add( service );
      }
    }
    if ( rtrVal.isEmpty() ) {
      return null;
    }
    return rtrVal;
  }

  /**
   * The toString return the device type
   * @return the device type
   */
    @Override
  public String toString() {
    return getDeviceType();
  }

}
