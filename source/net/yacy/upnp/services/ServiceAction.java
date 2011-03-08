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
package net.yacy.upnp.services;

import java.util.*;

/**
 * An object to represent a service action proposed by an UPNP service
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */
public class ServiceAction {
  
  protected String name;
  protected UPNPService parent;
  private List<ServiceActionArgument> orderedActionArguments;
  private List<ServiceActionArgument> orderedInputActionArguments;
  private List<ServiceActionArgument> orderedOutputActionArguments;
  private List<String> orderedInputActionArgumentsNames;
  private List<String> orderedOutputActionArgumentsNames;
  
  protected ServiceAction() {
  }
  
  public UPNPService getParent() {
    return parent;
  }
  
  /**
   * The action in and out arguments ServiceActionArgument objects list
   * @return the list with ServiceActionArgument objects or null if the action has no params
   */
  public List<ServiceActionArgument> getActionArguments() {
    return orderedActionArguments;
  }
  
  /**
   * Look for an ServiceActionArgument for a given name
   * @param argumentName the argument name
   * @return the argument or null if not found or not available
   */
  public ServiceActionArgument getActionArgument( String argumentName ) {
    if ( orderedActionArguments == null ) return null;
    for ( Iterator<ServiceActionArgument> i = orderedActionArguments.iterator(); i.hasNext(); ) {
      ServiceActionArgument arg = i.next();
      if ( arg.getName().equals( argumentName ) ) return arg;
    }
    return null;
  }
  
  protected void setActionArguments( List<ServiceActionArgument> orderedActionArguments ) {
    this.orderedActionArguments = orderedActionArguments;
    orderedInputActionArguments = getListForActionArgument( orderedActionArguments, ServiceActionArgument.DIRECTION_IN );
    orderedOutputActionArguments = getListForActionArgument( orderedActionArguments, ServiceActionArgument.DIRECTION_OUT );
    orderedInputActionArgumentsNames = getListForActionArgumentNames( orderedActionArguments, ServiceActionArgument.DIRECTION_IN );
    orderedOutputActionArgumentsNames = getListForActionArgumentNames( orderedActionArguments, ServiceActionArgument.DIRECTION_OUT );
  }
  
  /**
   * Return a list containing input ( when a response is sent ) arguments objects
   * @return a list containing input arguments ServiceActionArgument objects or null when nothing
   * is needed for such operation
   */
  public List<ServiceActionArgument> getInputActionArguments() {
    return orderedInputActionArguments;
  }
  
  /**
   * Look for an input ServiceActionArgument for a given name
   * @param argumentName the input argument name
   * @return the argument or null if not found or not available
   */
  public ServiceActionArgument getInputActionArgument( String argumentName ) {
    if ( orderedInputActionArguments == null ) return null;
    for ( Iterator<ServiceActionArgument> i = orderedInputActionArguments.iterator(); i.hasNext(); ) {
      ServiceActionArgument arg = i.next();
      if ( arg.getName().equals( argumentName ) ) return arg;
    }
    return null;
  }
  
  
  /**
   * Return a list containing output ( when a response is received ) arguments objects
   * @return a list containing output arguments ServiceActionArgument objects or null when nothing
   * returned for such operation
   */
  public List<ServiceActionArgument> getOutputActionArguments() {
    return orderedOutputActionArguments;
  }
  
  /**
   * Look for an output ServiceActionArgument for a given name
   * @param argumentName the input argument name
   * @return the argument or null if not found or not available
   */
  public ServiceActionArgument getOutputActionArgument( String argumentName ) {
    if ( orderedOutputActionArguments == null ) return null;
    for ( Iterator<ServiceActionArgument> i = orderedOutputActionArguments.iterator(); i.hasNext(); ) {
      ServiceActionArgument arg = i.next();
      if ( arg.getName().equals( argumentName ) ) return arg;
    }
    return null;
  }
  
  /**
   * Return a list containing input ( when a response is sent ) arguments names
   * @return a list containing input arguments names as Strings or null when nothing
   * is needed for such operation
   */
  public List<String> getInputActionArgumentsNames() {
    return orderedInputActionArgumentsNames;
  }
  
  /**
   * Return a list containing output ( when a response is received ) arguments names
   * @return a list containing output arguments names as Strings or null when nothing
   * returned for such operation
   */
  public List<String> getOutputActionArgumentsNames() {
    return orderedOutputActionArgumentsNames;
  }
  
  /**
   * The action name
   * @return The action name
   */
  public String getName() {
    return name;
  }
  
  private List<ServiceActionArgument> getListForActionArgument( List<ServiceActionArgument> args, String direction ) {
    if ( args == null ) return null;
    List<ServiceActionArgument> rtrVal = new ArrayList<ServiceActionArgument>();
    for ( Iterator<ServiceActionArgument> itr = args.iterator(); itr.hasNext(); ) {
      ServiceActionArgument actArg = itr.next();		
      if ( actArg.getDirection() == direction ) {
        rtrVal.add( actArg );
      }
    }
    if ( rtrVal.isEmpty() ) rtrVal = null;
    return rtrVal;
  }
  
  private List<String> getListForActionArgumentNames( List<ServiceActionArgument> args, String direction ) {
    if ( args == null ) return null;
    List<String> rtrVal = new ArrayList<String>();
    for ( Iterator<ServiceActionArgument> itr = args.iterator(); itr.hasNext(); ) {
      ServiceActionArgument actArg = itr.next();		
      if ( actArg.getDirection() == direction ) {
        rtrVal.add( actArg.getName() );
      }
    }
    if ( rtrVal.isEmpty() ) rtrVal = null;
    return rtrVal;
  }
}
