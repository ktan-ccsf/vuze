/*
 * Created on Apr 23, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.plugins.net.buddy;

import java.util.Map;

public class 
BuddyPluginBuddyMessage 
{
	private BuddyPluginBuddyMessageHandler		handler;
	private int									id;
	private int									subsystem;
	private int									timeout;
	private long								create_time;
	
	protected
	BuddyPluginBuddyMessage(
		BuddyPluginBuddyMessageHandler		_handler,
		int									_id,
		int									_subsystem,
		Map									_content,
		int									_timeout,
		long								_create_time )
	
		throws BuddyPluginException
	{
		handler		= _handler;
		id			= _id;
		subsystem	= _subsystem;
		timeout		= _timeout;
		create_time	= _create_time;
		
		if ( _content != null ){
			
			handler.writeContent( this, _content );
		}
	}
	
	public BuddyPluginBuddy
	getBuddy()
	{
		return( handler.getBuddy());
	}
	
	public int
	getID()
	{
		return( id );
	}
	
	protected int
	getSubsystem()
	{
		return( subsystem );
	}
	
	protected int
	getTimeout()
	{
		return( timeout );
	}
	
	protected long
	getCreateTime()
	{
		return( create_time );
	}
	
	protected Map
	getContent()
	
		throws BuddyPluginException
	{
		return( handler.readContent( this ));
	}
	
	public void
	delete()
	{
		handler.delete( this );
	}
}
