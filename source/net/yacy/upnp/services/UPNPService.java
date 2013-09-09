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

import org.apache.commons.jxpath.*;
import org.apache.commons.jxpath.xml.*;

import java.util.*;
import java.io.IOException;
import java.net.*;

import net.yacy.upnp.JXPathParser;
import net.yacy.upnp.devices.*;

/**
 * Representation of an UPNP service
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */
public class UPNPService {
  
  protected String serviceType;
  protected String serviceId;
  private int specVersionMajor;
  private int specVersionMinor;
  protected URL SCPDURL;
  protected String SCPDURLData;
  protected URL controlURL;
  protected URL eventSubURL;
  protected UPNPDevice serviceOwnerDevice;

  protected Map<String, ServiceAction> UPNPServiceActions;
  protected Map<String, ServiceStateVariable> UPNPServiceStateVariables;
  private String USN;

  private boolean parsedSCPD = false;
  private DocumentContainer UPNPService;

  public UPNPService( JXPathContext serviceCtx, URL baseDeviceURL, UPNPDevice serviceOwnerDevice ) throws MalformedURLException {
    this.serviceOwnerDevice = serviceOwnerDevice;
    serviceType = (String)serviceCtx.getValue( "upnp:serviceType" );
    serviceId = (String)serviceCtx.getValue( "upnp:serviceId" );
    SCPDURL = UPNPRootDevice.getURL( (String)serviceCtx.getValue( "upnp:SCPDURL" ), baseDeviceURL );
    controlURL = UPNPRootDevice.getURL( (String)serviceCtx.getValue( "upnp:controlURL" ), baseDeviceURL );
    eventSubURL = UPNPRootDevice.getURL( (String)serviceCtx.getValue( "upnp:eventSubURL" ), baseDeviceURL );
    USN = serviceOwnerDevice.getUDN().concat( "::" ).concat( serviceType );
  }

  public String getServiceType() {
    return serviceType;
  }

  public String getServiceId() {
    return serviceId;
  }
  
  public String getUSN(){
    return USN;
  }

  public URL getSCPDURL() {
    return SCPDURL;
  }

  public URL getControlURL() {
    return controlURL;
  }

  public URL getEventSubURL() {
    return eventSubURL;
  }

  public int getSpecVersionMajor() {
    lazyInitiate();
    return specVersionMajor;
  }

  public int getSpecVersionMinor() {
    lazyInitiate();
    return specVersionMinor;
  }

  public UPNPDevice getServiceOwnerDevice() {
    return serviceOwnerDevice;
  }

  /**
   * Retreives a service action for its given name
   * @param actionName the service action name
   * @return a ServiceAction object or null if no matching action for this service has been found
   */
  public ServiceAction getUPNPServiceAction( String actionName ) {
    lazyInitiate();
    return UPNPServiceActions.get( actionName );
  }

  /**
   * Retreives a service state variable for its given name
   * @param stateVariableName the state variable name
   * @return a ServiceStateVariable object or null if no matching state variable has been found
   */
  public ServiceStateVariable getUPNPServiceStateVariable( String stateVariableName ) {
    lazyInitiate();
    return UPNPServiceStateVariables.get( stateVariableName );
  }

  public Iterator<String> getAvailableActionsName() {
    lazyInitiate();
    return UPNPServiceActions.keySet().iterator();
  }
  
  public int getAvailableActionsSize() {
	  lazyInitiate();
	  return UPNPServiceActions.keySet().size();
	}

  public Iterator<String> getAvailableStateVariableName() {
    lazyInitiate();
    return UPNPServiceStateVariables.keySet().iterator();
  }
  
  public int getAvailableStateVariableSize() {
	  lazyInitiate();
	  return UPNPServiceStateVariables.keySet().size();
	}

  private void parseSCPD() {
    try {
      DocumentContainer.registerXMLParser( DocumentContainer.MODEL_DOM, new JXPathParser() );
      UPNPService = new DocumentContainer( SCPDURL, DocumentContainer.MODEL_DOM );
      JXPathContext context = JXPathContext.newContext( this );
      context.registerNamespace("scpdns", "urn:schemas-upnp-org:service-1-0");
      Pointer rootPtr = context.getPointer( "UPNPService/scpdns:scpd" );
      JXPathContext rootCtx = context.getRelativeContext( rootPtr );
      rootCtx.registerNamespace("scpdns", "urn:schemas-upnp-org:service-1-0");
  
      specVersionMajor = Integer.parseInt( (String)rootCtx.getValue( "scpdns:specVersion/scpdns:major" ) );
      specVersionMinor = Integer.parseInt( (String)rootCtx.getValue( "scpdns:specVersion/scpdns:minor" ) );
  
      parseServiceStateVariables( rootCtx );
  
      Pointer actionsListPtr = rootCtx.getPointer( "scpdns:actionList" );
      JXPathContext actionsListCtx = context.getRelativeContext( actionsListPtr );
      actionsListCtx.registerNamespace("scpdns", "urn:schemas-upnp-org:service-1-0");
      Double arraySize = (Double)actionsListCtx.getValue( "count( scpdns:action )" );
      UPNPServiceActions = new HashMap<String, ServiceAction>();
      for ( int i = 1; i <= arraySize.intValue(); i++ ) {
        ServiceAction action = new ServiceAction();
        action.name = (String)actionsListCtx.getValue( "scpdns:action["+i+"]/scpdns:name" );
        action.parent = this;
        Pointer argumentListPtr = null;
        try {
          argumentListPtr = actionsListCtx.getPointer( "scpdns:action["+i+"]/scpdns:argumentList" );
        } catch (final  JXPathException ex ) {
          // there is no arguments list.
        }
        if ( argumentListPtr != null ) {
          JXPathContext argumentListCtx = actionsListCtx.getRelativeContext( argumentListPtr );
          argumentListCtx.registerNamespace("scpdns", "urn:schemas-upnp-org:service-1-0");
          Double arraySizeArgs = (Double)argumentListCtx.getValue( "count( scpdns:argument )" );
  
          List<ServiceActionArgument> orderedActionArguments = new ArrayList<ServiceActionArgument>();
          for ( int z = 1; z <= arraySizeArgs.intValue(); z++ ) {
            ServiceActionArgument arg = new ServiceActionArgument();
            arg.name = (String)argumentListCtx.getValue( "scpdns:argument["+z+"]/scpdns:name" );
            String direction = (String)argumentListCtx.getValue( "scpdns:argument["+z+"]/scpdns:direction" );
            arg.direction = direction.equals( ServiceActionArgument.DIRECTION_IN ) ? ServiceActionArgument.DIRECTION_IN : ServiceActionArgument.DIRECTION_OUT;
            String stateVarName = (String)argumentListCtx.getValue( "scpdns:argument["+z+"]/scpdns:relatedStateVariable" );
            ServiceStateVariable stateVar = UPNPServiceStateVariables.get( stateVarName );
            if ( stateVar == null ) {
              throw new IllegalArgumentException( "Unable to find any state variable named " + stateVarName + " for service " + getServiceId() + " action " + action.name + " argument " + arg.name );
            }
            arg.relatedStateVariable = stateVar;
            orderedActionArguments.add( arg );
          }
          
          if ( arraySizeArgs.intValue() > 0 ) {
            action.setActionArguments( orderedActionArguments );
          }
        }
  
        UPNPServiceActions.put( action.getName(), action );
      }
      parsedSCPD = true;
    } catch (final  Throwable t ) {
      throw new RuntimeException( "Error during lazy SCDP file parsing at " + SCPDURL, t );
    }
  }

  private void parseServiceStateVariables( JXPathContext rootContext ) {
    Pointer serviceStateTablePtr = rootContext.getPointer( "scpdns:serviceStateTable" );
    JXPathContext serviceStateTableCtx = rootContext.getRelativeContext( serviceStateTablePtr );
    serviceStateTableCtx.registerNamespace("scpdns", "urn:schemas-upnp-org:service-1-0");
    Double arraySize = (Double)serviceStateTableCtx.getValue( "count( scpdns:stateVariable )" );
    UPNPServiceStateVariables = new HashMap<String, ServiceStateVariable>();
    for ( int i = 1; i <= arraySize.intValue(); i++ ) {
      ServiceStateVariable srvStateVar = new ServiceStateVariable();
      String sendEventsLcl = null;
      try {
        sendEventsLcl = (String)serviceStateTableCtx.getValue( "scpdns:stateVariable["+i+"]/scpdns:@sendEvents" );
      } catch (final  JXPathException defEx ) {
        // sendEvents not provided defaulting according to specs to "yes"
        sendEventsLcl = "yes";
      }
      srvStateVar.parent = this;
      srvStateVar.sendEvents = sendEventsLcl.equalsIgnoreCase( "no" ) ? false : true;
      srvStateVar.name = (String)serviceStateTableCtx.getValue( "scpdns:stateVariable["+i+"]/scpdns:name" );
      srvStateVar.dataType = (String)serviceStateTableCtx.getValue( "scpdns:stateVariable["+i+"]/scpdns:dataType" );
      try {
        srvStateVar.defaultValue = (String)serviceStateTableCtx.getValue( "scpdns:stateVariable["+i+"]/scpdns:defaultValue" );
      } catch (final  JXPathException defEx ) {
        // can happend since default value is not
      }
      Pointer allowedValuesPtr = null;
      try {
        allowedValuesPtr = serviceStateTableCtx.getPointer( "scpdns:stateVariable["+i+"]/scpdns:allowedValueList" );
      } catch (final  JXPathException ex ) {
        // there is no allowed values list.
      }
      if ( allowedValuesPtr != null ) {
        JXPathContext allowedValuesCtx = serviceStateTableCtx.getRelativeContext( allowedValuesPtr );
        allowedValuesCtx.registerNamespace("scpdns", "urn:schemas-upnp-org:service-1-0");
        Double arraySizeAllowed = (Double)allowedValuesCtx.getValue( "count( scpdns:allowedValue )" );
        srvStateVar.allowedvalues = new HashSet<String>();
        for ( int z = 1; z <= arraySizeAllowed.intValue(); z++ ) {
          String allowedValue = (String)allowedValuesCtx.getValue( "scpdns:allowedValue["+z+"]" );
          srvStateVar.allowedvalues.add( allowedValue );
        }
      }

      Pointer allowedValueRangePtr = null;
      try {
        allowedValueRangePtr = serviceStateTableCtx.getPointer( "scpdns:stateVariable["+i+"]/scpdns:allowedValueRange" );
      } catch (final  JXPathException ex ) {
        // there is no allowed values list, can happen
      }
      if ( allowedValueRangePtr != null ) {

        srvStateVar.minimumRangeValue = (String)serviceStateTableCtx.getValue( "scpdns:stateVariable["+i+"]/scpdns:allowedValueRange/scpdns:minimum" );
        srvStateVar.maximumRangeValue = (String)serviceStateTableCtx.getValue( "scpdns:stateVariable["+i+"]/scpdns:allowedValueRange/scpdns:maximum" );
        try {
          srvStateVar.stepRangeValue = (String)serviceStateTableCtx.getValue( "scpdns:stateVariable["+i+"]/scpdns:allowedValueRange/scpdns:step" );
        } catch (final  JXPathException stepEx ) {
          // can happend since step is not mandatory
        }
      }
      UPNPServiceStateVariables.put( srvStateVar.getName(), srvStateVar );
    }

  }

  private void lazyInitiate() {
    if ( !parsedSCPD )
      synchronized( this ) {
        if ( !parsedSCPD )
          parseSCPD();
      }
  }

  /**
   * Used for JXPath parsing, do not use this method
   * @return a Container object for Xpath parsing capabilities
   */
  public Container getUPNPService() {
    return UPNPService;
  }
  
  public String getSCDPData() {
  	if ( SCPDURLData == null ) {
  	  try {
  	  
	  		java.io.InputStream in = SCPDURL.openConnection().getInputStream();
	  		int readen = 0;
	  		byte[] buff = new byte[512];
				StringBuilder strBuff = new StringBuilder();
	  		while( ( readen = in.read( buff ) ) != -1 ) {
					strBuff.append( new String( buff, 0, readen ) );
	  		}
	  		in.close();
				SCPDURLData = strBuff.toString();
  		} catch (final  IOException ioEx ) {
  			return null;	
  		}
  	}
  	return SCPDURLData;
  }
}
