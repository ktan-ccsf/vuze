/*
 * Created on 1 Nov 2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */


package com.aelitis.azureus.core.networkmanager.admin.impl;


import java.net.InetAddress;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminException;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminProgressListener;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminProtocol;

public class 
NetworkAdminProtocolImpl 
	implements NetworkAdminProtocol
{
	private AzureusCore		core;
	private int				type;
	private int				port;
	
	protected 
	NetworkAdminProtocolImpl(
		AzureusCore	_core,
		int			_type )
	{
		core		= _core;
		type		= _type;
		port		= -1;
	}
	
	protected 
	NetworkAdminProtocolImpl(
		AzureusCore	_core,
		int			_type,
		int			_port )
	{
		core		= _core;
		type		= _type;
		port		= _port;
	}
	
	public int
	getType()
	{
		return( type );
	}
	
	public int 
	getPort()
	{
		return( port );
	}
	
	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address )
	
		throws NetworkAdminException
	{
		return( test( address, null ));
	}
	
	public InetAddress
	test(
		NetworkAdminNetworkInterfaceAddress	address,
		NetworkAdminProgressListener		listener )
	
		throws NetworkAdminException
	{
		InetAddress bind_ip = address==null?null:address.getAddress();
		
		NetworkAdminProtocolTester	tester;
		
		if ( type == PT_HTTP ){
			
			tester = new NetworkAdminHTTPTester( core, listener );
			
		}else if ( type == PT_TCP ){
			
			tester = new NetworkAdminTCPTester( core, listener );

		}else{
			
			tester = new NetworkAdminUDPTester( core, listener );
		}
		
		InetAddress	res;
		
		if ( port <= 0 ){
			
			res = tester.testOutbound( bind_ip, 0 );
			
		}else{
			
			res = tester.testInbound( bind_ip, port );
		}
		
		return( res );
	}
	
	public String
	getName()
	{
		String	res;
		
		if ( type == PT_HTTP ){
			
			res = "HTTP";
			
		}else if ( type == PT_TCP ){
			
			res = "TCP";

		}else{
			
			res = "UDP";
		}
		
		if ( port == -1 ){
			
			return( res + " outbound" );
			
		}else{
			
			return( res + " port " + port + " inbound" );
		}
	}
}
