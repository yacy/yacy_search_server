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

import net.yacy.upnp.services.*;

/**
 * Factory to create UPNP messages to access and communicate with
 * a given UPNPDevice service capabilities
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class UPNPMessageFactory {

  private UPNPService service;

  /**
   * Private constructor since this is a factory.
   * @param service the UPNPService that will be used to generate messages by thid factory
   */
  private UPNPMessageFactory( UPNPService service ) {
    this.service = service;
  }

  /**
   * Generate a new factory instance for a given device service definition object
   * @param service the UPNP service definition object for messages creation
   * @return a new message factory
   */
  public static UPNPMessageFactory getNewInstance( UPNPService service ) {
    return new UPNPMessageFactory( service );
  }

  /**
   * Creation of a new ActionMessage to communicate with the UPNP device
   * @param serviceActionName the name of a service action, this name is case sensitive
   *                          and matches exactly the name provided by the UPNP device in the XML definition file
   * @return a ActionMessage object or null if the action is unknown for this service messages factory
   */
  public ActionMessage getMessage( String serviceActionName ) {
    ServiceAction serviceAction = service.getUPNPServiceAction( serviceActionName );
    if ( serviceAction != null ) {
      return new ActionMessage( service, serviceAction );
    }
    return null;
  }

  /**
   * Creation of a new StateVariableMessage to communicate with the UPNP device, for a service state variable query
   * @param serviceStateVariable the name of a service state variable, this name is case sensitive
   *                          and matches exactly the name provided by the UPNP device in the XML definition file
   * @return a StateVariableMessage object or null if the state variable is unknown for this service mesages factory
   */
  public StateVariableMessage getStateVariableMessage( String serviceStateVariable ) {
    ServiceStateVariable stateVar = service.getUPNPServiceStateVariable( serviceStateVariable );
    if ( stateVar != null ) {
      return new StateVariableMessage( service, stateVar );
    }
    return null;
  }

}
