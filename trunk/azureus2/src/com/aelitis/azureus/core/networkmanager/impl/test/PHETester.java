/*
 * Created on 17-Jan-2006
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
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.core.networkmanager.impl.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.networkmanager.VirtualChannelSelector;
import com.aelitis.azureus.core.networkmanager.VirtualServerChannelSelector;
import com.aelitis.azureus.core.networkmanager.VirtualChannelSelector.VirtualSelectorListener;
import com.aelitis.azureus.core.networkmanager.impl.TCPProtocolDecoder;
import com.aelitis.azureus.core.networkmanager.impl.TCPProtocolDecoderInitial;
import com.aelitis.azureus.core.networkmanager.impl.TCPProtocolDecoderAdapter;
import com.aelitis.azureus.core.networkmanager.impl.TCPTransportHelperFilter;

public class 
PHETester 
{
	private final VirtualChannelSelector connect_selector = new VirtualChannelSelector( VirtualChannelSelector.OP_CONNECT, true );
	 
	public
	PHETester()
	{
		VirtualServerChannelSelector
			accept_server = new VirtualServerChannelSelector( 
					new InetSocketAddress( 8765 ), 
					0, 
					new VirtualServerChannelSelector.SelectListener() 
					{
						public void 
						newConnectionAccepted( 
							SocketChannel channel ) 
						{      
							incoming( channel );
						}
					});
		
		accept_server.start();
	
		new Thread()
		{
			public void
			run()
			{
				while( true ){
					try{
						connect_selector.select( 100 );
					}
					catch( Throwable t ) {
					  Debug.out( "connnectSelectLoop() EXCEPTION: ", t );
					}
				}
			}
		}.start();
		
		outgoings();
	}
	
	protected void
	incoming(
		SocketChannel	channel )
	{
		try{
			final TCPProtocolDecoderInitial	decoder = 
				new TCPProtocolDecoderInitial( 
						channel, 
						false,
						new TCPProtocolDecoderAdapter()
						{
							public void
							decodeComplete(
								TCPProtocolDecoder	decoder )
							{
								System.out.println( "incoming decode complete: " +  decoder.getFilter());
																
								readStream( "incoming", decoder.getFilter() );
								
								writeStream( "ten fat monkies", decoder.getFilter() );
							}
							
							public void
							decodeFailed(
								TCPProtocolDecoder	decoder,
								Throwable			cause )
							{
								System.out.println( "incoming decode failed: " + Debug.getNestedExceptionMessage(cause));
							}
						});
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	
	protected void
	outgoings()
	{
		while( true ){
			
			outgoing();
			
			try{
				Thread.sleep(10000000);
				
			}catch( Throwable e ){
	
			}
		}
	}
	
	protected void
	outgoing()
	{
		try{			
			final SocketChannel	channel = SocketChannel.open();
			
			channel.configureBlocking( false );
		
			if ( channel.connect( new InetSocketAddress("localhost", 8765 ))){
							
				outgoing( channel );
				
			}else{
				
				connect_selector.register(
					channel,
					new VirtualSelectorListener()
					{
						public boolean 
						selectSuccess(
							VirtualChannelSelector selector, SocketChannel sc, Object attachment)
						{
							try{
								if ( channel.finishConnect()){
									
									outgoing( channel );
									
									return( true );
								}else{
									
									throw( new IOException( "finishConnect failed" ));
								}
							}catch( Throwable e ){
								
								e.printStackTrace();
								
								return( false );
							}
						}
	
						public void 
						selectFailure(
								VirtualChannelSelector selector, SocketChannel sc, Object attachment, Throwable msg)
						{
							msg.printStackTrace();
						}
						 					
					},
					null );
			}
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	
	protected void
	outgoing(
		SocketChannel	channel )
	{
		try{
			final TCPProtocolDecoderInitial decoder =
				new TCPProtocolDecoderInitial( 
					channel,
					true,
					new TCPProtocolDecoderAdapter()
					{
						public void
						decodeComplete(
							TCPProtocolDecoder	decoder )
						{
							System.out.println( "outgoing decode complete: " +  decoder.getFilter());
														
							readStream( "incoming", decoder.getFilter() );
							
							writeStream( "two jolly porkers", decoder.getFilter() );
						}
						
						public void
						decodeFailed(
							TCPProtocolDecoder	decoder,
							Throwable			cause )
						{
							System.out.println( "outgoing decode failed: " + Debug.getNestedExceptionMessage(cause));

						}
					});
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	
	protected void
	readStream(
		final String					str,
		final TCPTransportHelperFilter	filter )
	{
		try{
			NetworkManager.getSingleton().getReadSelector().register(
				filter.getSocketChannel(),
				new VirtualSelectorListener()
				{
					public boolean 
					selectSuccess(
						VirtualChannelSelector selector, SocketChannel sc, Object attachment)
					{
						ByteBuffer	buffer = ByteBuffer.allocate(1024);
						
						try{
							long	len = filter.read( new ByteBuffer[]{ buffer }, 0, 1 );
						
							byte[]	data = new byte[buffer.position()];
							
							buffer.flip();
							
							buffer.get( data );
							
							System.out.println( str + ": " + new String(data));
							
							return( len > 0 );
							
						}catch( Throwable e ){
							
							e.printStackTrace();
							
							return( false );
						}
					}

					public void 
					selectFailure(
							VirtualChannelSelector selector, SocketChannel sc, Object attachment, Throwable msg)
					{
						msg.printStackTrace();
					}
				},
				null );
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	
	protected void
	writeStream(
		String						str,
		TCPTransportHelperFilter	filter )
	{
		try{
			filter.write( new ByteBuffer[]{ ByteBuffer.wrap( str.getBytes())}, 0, 1 );
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	
	public static void
	main(
		String[]	args )
	{
		AEDiagnostics.startup();
		
		COConfigurationManager.initialiseFromMap( new HashMap());
		
		new PHETester();
		
		try{
			Thread.sleep(10000000);
			
		}catch( Throwable e ){
			
		}
	}
}
