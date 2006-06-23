/*
 * Created on May 8, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004, 2005, 2006 Aelitis, All Rights Reserved.
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

package com.aelitis.azureus.core.networkmanager.impl.tcp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.*;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelperFilter;
import com.aelitis.azureus.core.networkmanager.impl.TransportCryptoManager;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelper;
import com.aelitis.azureus.core.networkmanager.impl.TransportImpl;



/**
 * Represents a peer TCP transport connection (eg. a network socket).
 */
public class TCPTransportImpl extends TransportImpl implements Transport {
	private static final LogIDs LOGID = LogIDs.NET;
  
  protected ProtocolEndpointTCP		protocol_endpoint;


  
  private ConnectDisconnectManager.ConnectListener connect_request_key = null;
  private String description = "<disconnected>";
  private final boolean is_inbound_connection;
  
  private int transport_mode = TRANSPORT_MODE_NORMAL;

  public volatile boolean has_been_closed = false;
    
  private boolean 	connect_with_crypto;
  private byte[]	shared_secret;
  private int		fallback_count;
  private final boolean fallback_allowed;

  
  /**
   * Constructor for disconnected (outbound) transport.
   */
  public 
  TCPTransportImpl( 
	ProtocolEndpointTCP endpoint, 
	boolean use_crypto, 
	boolean allow_fallback, 
	byte[] _shared_secret ) 
  {
	protocol_endpoint = endpoint;  
    is_inbound_connection = false;
    connect_with_crypto = use_crypto;
    shared_secret		= _shared_secret;
    fallback_allowed  = allow_fallback;
  }
  
  
  /**
   * Constructor for connected (inbound) transport.
   * @param channel connection
   * @param already_read bytes from the channel
   */
  
  public 
  TCPTransportImpl( 
	ProtocolEndpointTCP 	endpoint, 
	TransportHelperFilter	filter )
  {
	protocol_endpoint = endpoint;
   
	setFilter( filter );
        
    is_inbound_connection = true;
    connect_with_crypto = false;  //inbound connections will automatically be using crypto if necessary
    fallback_allowed = false;
    description = ( is_inbound_connection ? "R" : "L" ) + ": " + getSocketChannel().socket().getInetAddress().getHostAddress() + ": " + getSocketChannel().socket().getPort();
 
  }
  
  /**
   * Get the socket channel used by the transport.
   * @return the socket channel
   */
  public SocketChannel getSocketChannel() {  return ((TCPTransportHelper)getFilter().getHelper()).getSocketChannel();  }
  
  public TransportEndpoint
  getTransportEndpoint()
  {
	  return( new TransportEndpointTCP( protocol_endpoint, getSocketChannel()));
  }
  
  public int
  getMssSize()
  {
	  return( TCPNetworkManager.getTcpMssSize());
  }
  
  /**
   * Get a textual description for this transport.
   * @return description
   */
  public String getDescription() {  return description;  }
  
 
 
  /**
   * Request the transport connection be established.
   * NOTE: Will automatically connect via configured proxy if necessary.
   * @param address remote peer address to connect to
   * @param listener establishment failure/success listener
   */
  public void connectOutbound( final ConnectListener listener ) {
    if( has_been_closed )  return;
    
    if( getFilter() != null ) {  //already connected
      Debug.out( "socket_channel != null" );
      listener.connectSuccess( this );
      return;
    }
    
    final boolean use_proxy = COConfigurationManager.getBooleanParameter( "Proxy.Data.Enable" );
    final TCPTransportImpl transport_instance = this;    
    
    final InetSocketAddress	address = protocol_endpoint.getAddress();
    
    ConnectDisconnectManager.ConnectListener connect_listener = new ConnectDisconnectManager.ConnectListener() {
      public void connectAttemptStarted() {
        listener.connectAttemptStarted();
      }
      
      public void connectSuccess( final SocketChannel channel ) {
      	if( channel == null ) {
      		String msg = "connectSuccess:: given channel == null";
      		Debug.out( msg );
      		listener.connectFailure( new Exception( msg ) );
      		return;
      	}
      	
        if( has_been_closed ) {  //closed between select ops
          TCPNetworkManager.getSingleton().getConnectDisconnectManager().closeConnection( channel );  //just close it
          return;
        }
        
        connect_request_key = null;
        description = ( is_inbound_connection ? "R" : "L" ) + ": " + channel.socket().getInetAddress().getHostAddress() + ": " + channel.socket().getPort();

        if( use_proxy ) {  //proxy server connection established, login
        	Logger.log(new LogEvent(LOGID,"Socket connection established to proxy server [" +description+ "], login initiated..."));
          
        		// set up a transparent filter for socks negotiation
        	
          setFilter( TCPTransportHelperFilterFactory.createTransparentFilter( channel ));
      		
          new ProxyLoginHandler( transport_instance, address, new ProxyLoginHandler.ProxyListener() {
            public void connectSuccess() {
            	Logger.log(new LogEvent(LOGID, "Proxy [" +description+ "] login successful." ));
              handleCrypto( address, channel, listener );
            }
            
            public void connectFailure( Throwable failure_msg ) {
            	TCPNetworkManager.getSingleton().getConnectDisconnectManager().closeConnection( channel );
              listener.connectFailure( failure_msg );
            }
          });
        }
        else {  //direct connection established, notify
        	handleCrypto( address, channel, listener );
        }
      }

      public void connectFailure( Throwable failure_msg ) {
        connect_request_key = null;
        listener.connectFailure( failure_msg );
      }
    };
    
    connect_request_key = connect_listener;
    
    InetSocketAddress to_connect = use_proxy ? ProxyLoginHandler.SOCKS_SERVER_ADDRESS : address;
    
    TCPNetworkManager.getSingleton().getConnectDisconnectManager().requestNewConnection( to_connect, connect_listener );
  }
  
    
  
  
  protected void handleCrypto( final InetSocketAddress address, final SocketChannel channel, final ConnectListener listener ) {  	
  	if( connect_with_crypto ) {
    	//attempt encrypted transport
  		TransportHelper	helper = new TCPTransportHelper( channel );
    	TransportCryptoManager.getSingleton().manageCrypto( helper, shared_secret, false, new TransportCryptoManager.HandshakeListener() {
    		public void handshakeSuccess( TransportHelperFilter _filter ) {    			
    			//System.out.println( description+ " | crypto handshake success [" +_filter.getName()+ "]" );     			
    			setFilter( _filter ); 
    			if ( Logger.isEnabled()){
    		      Logger.log(new LogEvent(LOGID, "Outgoing TCP stream to " + channel.socket().getRemoteSocketAddress() + " established, type = " + _filter.getName()));
    			}
    			
    			connectedOutbound();
          
    			listener.connectSuccess( TCPTransportImpl.this );
    		}

    		public void handshakeFailure( Throwable failure_msg ) {        	
        	if( fallback_allowed && NetworkManager.OUTGOING_HANDSHAKE_FALLBACK_ALLOWED ) {        		
        		if( Logger.isEnabled() ) Logger.log(new LogEvent(LOGID, description+ " | crypto handshake failure [" +failure_msg.getMessage()+ "], attempting non-crypto fallback." ));
        		connect_with_crypto = false;
        		fallback_count++;
        		TCPNetworkManager.getSingleton().getConnectDisconnectManager().closeConnection( channel );  //just close it
        		close();
        		has_been_closed = false;
        		connectOutbound( listener );
        	}
        	else {
        		TCPNetworkManager.getSingleton().getConnectDisconnectManager().closeConnection( channel );
        		listener.connectFailure( failure_msg );
        	}
        }
    		
    		public int
    		getMaximumPlainHeaderLength()
    		{
    			throw( new RuntimeException());	// this is outgoing
    		}
		
    		public int
    		matchPlainHeader(
    				ByteBuffer			buffer )
    		{
    			throw( new RuntimeException());	// this is outgoing
    		}
    	});
  	}
  	else {  //no crypto
  		//if( fallback_count > 0 ) {
  		//	System.out.println( channel.socket()+ " | non-crypto fallback successful!" );
  		//}
  		setFilter( TCPTransportHelperFilterFactory.createTransparentFilter( channel ));
  		
		if ( Logger.isEnabled()){
		  Logger.log(new LogEvent(LOGID, "Outgoing TCP stream to " + channel.socket().getRemoteSocketAddress() + " established, type = " + getFilter().getName() + ", fallback = " + (fallback_count==0?"no":"yes" )));
		}
		
		connectedOutbound();
		
    	listener.connectSuccess( this );
  	}
  }
  
  
  

  private void setTransportBuffersSize( int size_in_bytes ) {
  	if( getFilter() == null ) {
  		Debug.out( "socket_channel == null" );
  		return;
  	}
  	
    try{
    	SocketChannel	channel = getSocketChannel();
    	
    	channel.socket().setSendBufferSize( size_in_bytes );
    	channel.socket().setReceiveBufferSize( size_in_bytes );
      
      int snd_real = channel.socket().getSendBufferSize();
      int rcv_real = channel.socket().getReceiveBufferSize();
      
      Logger.log(new LogEvent(LOGID, "Setting new transport [" + description
					+ "] buffer sizes: SND=" + size_in_bytes + " [" + snd_real
					+ "] , RCV=" + size_in_bytes + " [" + rcv_real + "]"));
    }
    catch( Throwable t ) {
      Debug.out( t );
    }
  }
  
  
  /**
   * Set the transport to the given speed mode.
   * @param mode to change to
   */
  public void setTransportMode( int mode ) {
    if( mode == transport_mode )  return;  //already in mode
    
    switch( mode ) {
      case TRANSPORT_MODE_NORMAL:
        setTransportBuffersSize( 8 * 1024 );
        break;
        
      case TRANSPORT_MODE_FAST:
        setTransportBuffersSize( 64 * 1024 );
        break;
        
      case TRANSPORT_MODE_TURBO:
        setTransportBuffersSize( 512 * 1024 );
        break;
        
      default:
        Debug.out( "invalid transport mode given: " +mode );
    }
    
    transport_mode = mode;
  }
  
 
  /**
   * Get the transport's speed mode.
   * @return current mode
   */
  public int getTransportMode() {  return transport_mode;  }
    
  /**
   * Close the transport connection.
   */
  public void close() {
    has_been_closed = true;
    
    if( connect_request_key != null ) {
    	TCPNetworkManager.getSingleton().getConnectDisconnectManager().cancelRequest( connect_request_key );
    }
    
    readyForRead( false );
    readyForWrite( false );

	TransportHelperFilter	filter = getFilter();

    if( filter != null ){
 
      filter.getHelper().close();
      
      setFilter( null );
    }
  }
     
  
}
