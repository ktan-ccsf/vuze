/*
 * Created on 14-Jun-2004
 * Created by Paul Gardner
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package org.gudy.azureus2.pluginsimpl.upnp;

/**
 * @author parg
 *
 */

import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.logging.*;
import org.gudy.azureus2.plugins.ui.*;
import org.gudy.azureus2.plugins.ui.model.*;

import org.gudy.azureus2.core3.upnp.*;

public class 
UPnPPlugin
	implements Plugin
{
	protected PluginInterface		plugin_interface;
	protected LoggerChannel 		log;
	
	public void
	initialize(
		PluginInterface	_plugin_interface )
	{
		plugin_interface	= _plugin_interface;
		
		log = plugin_interface.getLogger().getChannel("UPnP");

		UIManager	ui_manager = plugin_interface.getUIManager();
		
		final BasicPluginViewModel model = 
			ui_manager.createBasicPluginViewModel( 
					"UPnP");
		
		//boolean enabled = plugin_interface.getPluginconfig().getPluginBooleanParameter( "enable.update", true );

		//model.getStatus().setText( enabled?"Running":"Optional checks disabled" );
		model.getActivity().setVisible( false );
		model.getProgress().setVisible( false );
		
		log.addListener(
			new LoggerChannelListener()
			{
				public void
				messageLogged(
					int		type,
					String	message )
				{
					model.getLogArea().appendText( message+"\n");
				}
				
				public void
				messageLogged(
					String		str,
					Throwable	error )
				{
					model.getLogArea().appendText( error.toString()+"\n");
				}
			});
		
		try{
			UPnP	upnp = UPnPFactory.getSingleton();
				
			upnp.addRootDeviceListener(
				new UPnPRootDeviceListener()
				{
					public void
					rootDeviceFound(
						UPnPDevice		device )
					{
						try{
							processDevice( device );
							
						}catch( Throwable e ){
							
							log.log( "Root device processing fails", e );
						}
					}
				});
			
			upnp.addLogListener(
				new UPnPLogListener()
				{
					public void
					log(
						String	str )
					{
						log.log( str );
					}
				});
			
			Thread.sleep(2000);
			
		}catch( Throwable e ){
			
			log.log( e );
		}
	}
	
	protected void
	processDevice(
		UPnPDevice	device )
	
		throws UPnPException
	{
		if ( device.getDeviceType().equalsIgnoreCase("urn:schemas-upnp-org:device:WANConnectionDevice:1")){
			
			log.log( "Found WANConnectionDevice" );
			
			processServices( device.getServices());
			
		}else{
			
			UPnPDevice[]	kids = device.getSubDevices();
			
			for (int i=0;i<kids.length;i++){
				
				processDevice( kids[i] );
			}
		}
	}
	
	protected void
	processServices(
		UPnPService[] services )
	
		throws UPnPException
	{
		for (int i=0;i<services.length;i++){
			
			UPnPService	s = services[i];
			
			String	service_type = s.getServiceType();
			
			if ( 	service_type.equalsIgnoreCase( "urn:schemas-upnp-org:service:WANIPConnection:1") || 
					service_type.equalsIgnoreCase( "urn:schemas-upnp-org:service:WANPPPConnection:1")){
				
				log.log( "    Found " + ( service_type.indexOf("PPP") == -1? "WANIPConnection":"WANPPPConnection" ));
	
				UPnPAction[]	actions = s.getActions();
			
				for (int j=0;j<actions.length;j++){
				
					log.log( "      " + actions[j].getName());
				}
			}
		}
	}
}
