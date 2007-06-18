/*
 * File    : TRTrackerBTAnnouncerImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg 
 * 
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.gudy.azureus2.core3.tracker.client.impl.bt;

import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.net.ssl.*;

import java.util.zip.*;

import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.config.*;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.security.*;
import org.gudy.azureus2.core3.tracker.client.*;
import org.gudy.azureus2.core3.tracker.client.impl.*;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.core3.internat.*;
//import org.gudy.azureus2.core3.peer.util.*;
import org.gudy.azureus2.core3.peer.*;

import org.gudy.azureus2.core3.tracker.protocol.*;
import org.gudy.azureus2.core3.tracker.protocol.udp.*;
import org.gudy.azureus2.core3.tracker.util.TRTrackerUtils;

import org.gudy.azureus2.plugins.clientid.*;
import org.gudy.azureus2.plugins.download.*;
import org.gudy.azureus2.pluginsimpl.local.clientid.ClientIDManagerImpl;

import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPosition;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionManager;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.networkmanager.impl.udp.UDPNetworkManager;
import com.aelitis.net.udp.uc.PRUDPPacket;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerException;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerFactory;
import com.aelitis.net.udp.uc.PRUDPPacketRequest;


/**
 * 
 * This class handles communication with the tracker
 * 
 * @author Olivier
 *
 */
public class 
TRTrackerBTAnnouncerImpl
	extends TRTrackerAnnouncerImpl
{
	
		
	private static final int OVERRIDE_PERIOD			= 10*1000;
	 
	protected static Timer	tracker_timer = new Timer( "Tracker Timer", 32);
	
	public static String 	UDP_REALM = "UDP Tracker";
	
    static{
	  	PRUDPTrackerCodecs.registerCodecs();
	}
	
	private static AEMonitor 	class_mon 			= new AEMonitor( "TRTrackerBTAnnouncer:class" );
	private static Map			tracker_report_map	= new HashMap();
	
    
	private TOTorrent				torrent;
	
	private TimerEvent				current_timer_event;
	private TimerEventPerformer		timer_event_action;
	
	protected int				tracker_state 			= TS_INITIALISED;
	private String				tracker_status_str		= "";
	private TRTrackerAnnouncerResponseImpl	last_response			= null;
	private long				last_update_time_secs;
	private long				current_time_to_wait_secs;
	private boolean				manual_control;
  
	private long min_interval = 0;
  
	private int  failure_added_time = 0;
    private long failure_time_last_updated = 0;
	
	private boolean			stopped;
	private boolean			stopped_for_queue;
	private boolean			completed;
	private boolean			complete_reported	= false;
	
	private boolean			update_in_progress	= false;
	
	private long			rd_last_override = 0;
	private int				rd_override_percentage	= 100;

	private long			min_interval_override	= 0;
	
  	private List trackerUrlLists;
     
  	private URL lastUsedUrl;
  	private URL	lastAZTrackerCheckedURL;
    
  	private HashWrapper			torrent_hash;
  	
	private String	last_tracker_message;		// per torrent memory
	
	private String info_hash = "info_hash=";
	private byte[] tracker_peer_id;
	private String tracker_peer_id_str = "&peer_id=";
	
	private byte[] data_peer_id;
	
	private String 					key_id			= "";
	private static final int	   	key_id_length	= 8;
	private int						key_udp;
	
  
	private String tracker_id = "";
  
  
 
	private String 		ip_override;
	private String[]	peer_networks;
		
	private TRTrackerAnnouncerDataProvider 	announce_data_provider;
	
	protected AEMonitor this_mon 	= new AEMonitor( "TRTrackerBTAnnouncer" );

	private boolean	az_tracker;
	private boolean	destroyed;
		


	static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	public static String
	createKeyID()
	{
		String	key_id = "";
		
		for (int i = 0; i < key_id_length; i++) {
			int pos = (int) ( Math.random() * chars.length());
		    key_id +=  chars.charAt(pos);
		}
		
		return( key_id );
	}
	
  public 
  TRTrackerBTAnnouncerImpl(
   	TOTorrent		_torrent,
	String[]		_peer_networks,
	boolean			_manual ) 
  	
  	throws TRTrackerAnnouncerException
  {
	super( _torrent );
	 
	torrent			= _torrent;
  	peer_networks	= _peer_networks;
  	manual_control	= _manual;
  	
		//Get the Tracker url
		
	constructTrackerUrlLists( true );
       
		//Create our unique peerId
	
	try{
	    tracker_peer_id = ClientIDManagerImpl.getSingleton().generatePeerID( torrent, true );
	
	    if ( COConfigurationManager.getBooleanParameter("Tracker Separate Peer IDs")){
	    	
	    	data_peer_id = ClientIDManagerImpl.getSingleton().generatePeerID( torrent, false );
	    	
	    }else{
	    	
	    	data_peer_id	= tracker_peer_id;
	    }
	}catch( ClientIDException e ){

		 throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: Peer ID generation fails", e ));
	}

    key_id	= createKeyID();
    
	key_udp	= (int)(Math.random() *  0xFFFFFFFFL );
	
	try {
	
		torrent_hash = _torrent.getHashWrapper();
				
		this.info_hash += URLEncoder.encode(new String(torrent_hash.getBytes(), Constants.BYTE_ENCODING), Constants.BYTE_ENCODING).replaceAll("\\+", "%20");
	  
		this.tracker_peer_id_str += URLEncoder.encode(new String(tracker_peer_id, Constants.BYTE_ENCODING), Constants.BYTE_ENCODING).replaceAll("\\+", "%20");
	  
	}catch (UnsupportedEncodingException e){

		Logger.log(new LogEvent(torrent, LOGID, "URL encode fails", e));
	  
	  throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: URL encode fails"));
	  
	}catch( TOTorrentException e ){
	
		Logger.log(new LogEvent(torrent, LOGID, "Torrent hash retrieval fails", e));
		
		throw( new TRTrackerAnnouncerException( "TRTrackerAnnouncer: URL encode fails"));	
	}
	   
	timer_event_action =  
		new TimerEventPerformer()
		{
			public void
			perform(
				TimerEvent	this_event )
			{
				if ( manual_control ){
					
					requestUpdateSupport();
					
					return;
				}
				
				long	secs_to_wait = getErrorRetryInterval();
							
				try{
															
					secs_to_wait = requestUpdateSupport();

					if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID,
								"Next tracker announce (unadjusted) will be in " + secs_to_wait
										+ "s"));
								
				}finally{
						
					current_time_to_wait_secs	= secs_to_wait;
							
					if ( tracker_state == TS_STOPPED ){
						
						// System.out.println( "\tperform: stopped so no more events");
						
					}else{
						
					
						try{
							this_mon.enter();
						
								// it is possible that the current event was being processed
								// when another thread cancelled it and created a further timer
								// event. if this is the case we don't want to go ahead and
								// create another timer as one already exists 
								
							if ( this_event.isCancelled()){
								
								// System.out.println( "\tperform: cancelled so no new event");
								
							}else{
								
								
								secs_to_wait = getAdjustedSecsToWait();

								if (Logger.isEnabled())
									Logger.log(new LogEvent(torrent, LOGID,
											"Next tracker announce (adjusted) will be in "
													+ secs_to_wait + "s"));
								
								long target_time = SystemTime.getCurrentTime() + (secs_to_wait*1000);
								
								if ( current_timer_event != null && !current_timer_event.isCancelled()){
									
									if ( 	current_timer_event != this_event &&
											current_timer_event.getWhen() < target_time ){
									
											// existing event is earlier then current target, use it
												
										return;
									}
									
									current_timer_event.cancel();
								}
								
								if ( !destroyed ){
									
									current_timer_event = 
										tracker_timer.addEvent( target_time, this );
								}
							}
						}finally{
							
							this_mon.exit();
						}
					}
				}
			}
		};
    
		if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID,
					"Tracker Announcer Created using url : " + trackerURLListToString()));
  }
  	
	public void
	cloneFrom(
		TRTrackerAnnouncer	_other )
	{
		if ( _other instanceof TRTrackerBTAnnouncerImpl ){
			
			TRTrackerBTAnnouncerImpl	other = (TRTrackerBTAnnouncerImpl)_other;
			
			data_peer_id			= other.data_peer_id;
			tracker_peer_id			= other.tracker_peer_id;
			tracker_peer_id_str		= other.tracker_peer_id_str;
			tracker_id				= other.tracker_id;
			key_id					= other.key_id;
			key_udp					= other.key_udp;
	
			announce_data_provider	= other.announce_data_provider;
		}else{
			
			Debug.out( "Incompatible type" );
		}
	}
	
	protected long
	getAdjustedSecsToWait()
	{

	  long		secs_to_wait = current_time_to_wait_secs;
													
	  if( last_response != null && last_response.getStatus() != TRTrackerAnnouncerResponse.ST_ONLINE ) {
      
	  	if( last_response.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ) {
	  		
	  		//the tracker has explicitly reported an error (torrent is unauthorized for example),
	  		//so there's no need to keep trying to re-announce as if it were actually offline

	  		//there's no "min interval" returned, so start the re-announce backoff timings at 15min
	  		if( failure_added_time < 900 )  failure_added_time = 900;
  			secs_to_wait = getErrorRetryInterval();
  			
    		if (Logger.isEnabled()) {
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: tracker reported error, " + 
							"adjusting to error retry interval"));
    		}
	  	}
	  	else {	//tracker is OFFLINE
	  		secs_to_wait = getErrorRetryInterval();
    		if (Logger.isEnabled()) {
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: tracker seems to be offline, " + 
							"adjusting to error retry interval"));
    		}
	  	}
							
	  }
    else{
        
      if( rd_override_percentage == 0 ) {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: override, perc = 0"));
      	return REFRESH_MINIMUM_SECS;
      }

      if (rd_override_percentage != 100) {
      	secs_to_wait = (secs_to_wait * rd_override_percentage) /100;
    		if (Logger.isEnabled()) {
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: override, perc = " + rd_override_percentage));
    		}
      }
									
      if ( secs_to_wait < REFRESH_MINIMUM_SECS ){
	  			
        secs_to_wait = REFRESH_MINIMUM_SECS;
      }
      
      //use 'min interval' for calculation
      if( min_interval != 0 && secs_to_wait < min_interval ) {
        float percentage = (float)min_interval / current_time_to_wait_secs;  //percentage of original interval
        
        //long orig_override = secs_to_wait;
        
        int added_secs = (int)((min_interval - secs_to_wait) * percentage);  //increase by x percentage of difference
        secs_to_wait += added_secs;
        
    		if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID,
							"MIN INTERVAL CALC: min_interval=" + min_interval + ", interval="
									+ current_time_to_wait_secs + ", orig=" +  current_time_to_wait_secs
									+ ", new=" + secs_to_wait + ", added=" + added_secs
									+ ", perc=" + percentage));
      }
      
    }
  		
	  return( secs_to_wait );
	}
  
	
	public int
  	getStatus()
  	{
  		return( tracker_state );
  	}
	
  	public String
  	getStatusString()
  	{
  		return( tracker_status_str );
  	}
  	
	public void
	setRefreshDelayOverrides(
		int		percentage )
	{
		if ( percentage > 100 ){
			
			percentage = 100;
			
		}else if ( percentage < 0 ){
			
			percentage	= 0;
		}
		
		long	now = SystemTime.getCurrentTime();
		//only start overriding once the tracker announce update has been called
		boolean override_allowed = rd_last_override > 0 && now - rd_last_override > OVERRIDE_PERIOD;
    
		if( now < rd_last_override )  override_allowed = true;  //time went backwards
    
		if ( override_allowed && rd_override_percentage != percentage ){
		
			try{
				this_mon.enter();

				rd_last_override	= now;
				
				rd_override_percentage	= percentage;
				
				if ( current_timer_event != null && !current_timer_event.isCancelled()){
					
					long	start 	= current_timer_event.getCreatedTime();
					long	expiry	= current_timer_event.getWhen();
					
					long	secs_to_wait = getAdjustedSecsToWait();
								
					long target_time = start + (secs_to_wait*1000);

					if ( target_time != expiry ){
						
						current_timer_event.cancel();
						
						if ( !destroyed ){ 
							
							if (Logger.isEnabled())
								Logger.log(new LogEvent(torrent, LOGID,
										"Changed next tracker announce to " + secs_to_wait
												+ "s via " + Debug.getStackTrace(true, false, 0, 3)));
							
							current_timer_event = 
								tracker_timer.addEvent( 
									start,
									target_time,
									timer_event_action );	
						}
					}			
				}
			}finally{
				
				this_mon.exit();
			}
		}
	}
	
	public int
	getTimeUntilNextUpdate()
	{
		try{
			this_mon.enter();
		
			if ( current_timer_event == null ){
				
				return( getErrorRetryInterval() );
			}
					
			int rem = (int)((current_timer_event.getWhen() - SystemTime.getCurrentTime())/1000);
					
			return( rem );
			
		}finally{
			
			this_mon.exit();
		}
	}

	public int
	getLastUpdateTime()
	{
		return( (int)last_update_time_secs );
	}

	public void
	update(
		boolean		force )
	{
		long now = SystemTime.getCurrentTime() / 1000;
        
        if ( now < last_update_time_secs )  force = true;  //time went backwards

        long	effective_min = min_interval_override>0?min_interval_override:REFRESH_MINIMUM_SECS;
        
		if ( manual_control || force || ( now - last_update_time_secs >= effective_min )){
			
		  requestUpdate();
		}
	}
	
    
	public void
	complete(
		boolean	already_reported )
	{
		complete_reported	= (complete_reported || already_reported );
		
		completed			= true;
		
		requestUpdate();
	}
	
	public void
	stop(
		boolean	for_queue )
	{
		stopped				= true;
        stopped_for_queue	= for_queue;
        
		requestUpdate();
	}
	
	protected void
	requestUpdate()
	{
		try{
			this_mon.enter();
			
			if ( current_timer_event != null ){
				
				current_timer_event.cancel();
			}
      
			rd_last_override = SystemTime.getCurrentTime();  //"pause" overrides for 10s
      
			if ( !destroyed ){
				
				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID,
							"Forcing tracker announce now via "
									+ Debug.getStackTrace(true, false, 0, 3)));

				current_timer_event = 
					tracker_timer.addEvent( 
						SystemTime.getCurrentTime(),
						timer_event_action );
			}
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected long
	requestUpdateSupport()
	{
    
		boolean	clear_progress = true;
		
		try{
			try{
				this_mon.enter();

					// can't continue if the data provider hasn't been set yet...
				
				if ( update_in_progress || announce_data_provider == null ){
					
					clear_progress = false;
					
					return( getErrorRetryInterval() );
				}
				
				update_in_progress = true;
				
			}finally{
				
				this_mon.exit();
			}
	
			last_update_time_secs	= SystemTime.getCurrentTime()/1000;
			
			tracker_status_str = MessageText.getString("PeerManager.status.checking") + "..."; //$NON-NLS-1$ //$NON-NLS-2$      
		
			TRTrackerAnnouncerResponseImpl	response = null;
			
			if ( stopped ){
				
					// if manual control then we assume that a stop request is required, even if we
					// are in an init state. needed for explicit stop based on URL
				
				if ( tracker_state == TS_INITIALISED && !manual_control ){
					
						// never started
					
					tracker_state = TS_STOPPED;
					
				}else if ( tracker_state != TS_STOPPED ){
			
					response = stopSupport();
					
					if ( response.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){
												
						tracker_state = TS_STOPPED;
						
					}else{
						
							// just have one go at sending a stop event as we don't want to sit here
							// forever trying to send stop to a stuffed tracker
							
						tracker_state = TS_STOPPED;
					}
				}	
			}else if ( tracker_state == TS_INITIALISED ){
							
					// always go through the "start" phase, even if we're already complete
					// as some trackers insist on the initial "start"
				
				response = startSupport();
					
				if ( response.getStatus() == TRTrackerAnnouncerResponse.ST_ONLINE ){
						
					tracker_state = TS_DOWNLOADING;
				}
			}else if ( completed ){
				
				if ( !complete_reported ){
					
					response = completeSupport();
					
						// treat the "complete" as processed if the tracker replies either OK or an explicit
						// error. In particular, if the tracker has returned an error to control seed limits
						// we don't want to treat this as an error and report completed again next time around
						// as this causes the tracker to double count stats
					
					if ( response.getStatus() != TRTrackerAnnouncerResponse.ST_OFFLINE ){
						
						complete_reported	= true;
				
						tracker_state = TS_COMPLETED;
					}
				}else{
					tracker_state = TS_COMPLETED;
					
					response = updateSupport();
				}
				
			}else{
				
				response = updateSupport();
			}
						
			if ( response != null ){

				int	rs = response.getStatus();
				
				if ( rs == TRTrackerAnnouncerResponse.ST_OFFLINE ){
      
					tracker_status_str = MessageText.getString("PeerManager.status.offline"); 
      		      
				}else if ( rs == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){

					tracker_status_str = MessageText.getString("PeerManager.status.error"); 
	      		      
						// move state back to initialised to next time around a "started"
						// event it resent. Required for trackers like 123torrents.com that
						// will fail peers that don't start with a "started" event after a 
						// tracker restart
					
					tracker_state	= TS_INITIALISED;
					
				}else{
	    	       	        	
					tracker_status_str = MessageText.getString("PeerManager.status.ok"); //set the status      //$NON-NLS-1$
				}

				String	reason = response.getAdditionalInfo();
    		
				if ( reason != null ){
					tracker_status_str += " (" + reason + ")";		
				}
				
				last_response = response;
				
				listeners.dispatch( LDT_TRACKER_RESPONSE, response );
				
				return( response.getTimeToWait());
			}
				
			tracker_status_str = "";
			
			return( getErrorRetryInterval() );
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
			return( getErrorRetryInterval() );
			
		}finally{
			
			try{
				this_mon.enter();
			
				if ( clear_progress ){
					
					update_in_progress = false;
				}
			}finally{
				
				this_mon.exit();
			}
		}
	}
	
	protected TRTrackerAnnouncerResponseImpl startSupport() {
		if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "a start Request"));

    return (update("started"));
  }

  protected TRTrackerAnnouncerResponseImpl completeSupport() {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "a completed Request"));

		return (update("completed"));
  }

  protected TRTrackerAnnouncerResponseImpl stopSupport() {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "a stopped Request"));

    return (update("stopped"));
  }

  protected TRTrackerAnnouncerResponseImpl updateSupport() {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer is sending "
					+ "an update Request"));

    return update("");
  }
  
  	private TRTrackerAnnouncerResponseImpl
	update(
		String evt )
  	{
  		// this method filters out any responses incompatible with the network selection
  		
  		TRTrackerAnnouncerResponseImpl	resp = update2( evt );
  		
  		TRTrackerAnnouncerResponsePeer[]	peers = resp.getPeers();
  		
  		if ( peers != null ){
	  		List	p = new ArrayList();
	  		
	  		for (int i=0;i<peers.length;i++){
	  			
	  			TRTrackerAnnouncerResponsePeer	peer = peers[i];
	  			
	  			if ( peer_networks == null ){
	  				
	  				p.add( peer );
	  				
	  			}else{
	  				
		  			String	peer_address = peer.getAddress();
		  			
		  			String	peer_network = AENetworkClassifier.categoriseAddress( peer_address );
		  			
		  			boolean	added = false;
		  			
		  			for (int j=0;j<peer_networks.length;j++){
		  				
		  				if ( peer_networks[j] == peer_network ){
		  					
		  					p.add( peer );
		  					
		  					added = true;
		  					
		  					break;
		  				}
		  			}
	  			
		  			if (!added && Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_WARNING,
									"Tracker Announcer dropped peer '" + peer_address
											+ "' as incompatible " + "with network selection"));
	  			}
	  		}
	  		
	  		peers = new TRTrackerAnnouncerResponsePeer[ p.size()];
	  		
	  		p.toArray( peers );
	  		
	  		resp.setPeers( peers );
  		}
  		
  		return( resp );
  	}
  	
  private TRTrackerAnnouncerResponseImpl 
  update2(String evt) 
  {
  	TRTrackerAnnouncerResponseImpl	last_failure_resp = null;
	
  	String	skip_host = null;
  	
  outer:
  	
	for (int i = 0 ; i < trackerUrlLists.size() ; i++) {
	  	
		List urls = (List) trackerUrlLists.get(i);
		
		for (int j = 0 ; j < urls.size() ; j++) {
			
		  final URL original_url = (URL)urls.get(j);
		  
		  if ( skip_host != null && skip_host.equals( original_url.getHost())){
			  
			if (Logger.isEnabled())
					Logger.log(
						new LogEvent(
							torrent, 
							LOGID, 
							LogEvent.LT_WARNING,
							"Tracker Announcer is ignoring '" + original_url + "' as already received overloaded response from this host" ));

			continue;
		  }
		  
		  lastUsedUrl = original_url;
		   
		  if ( lastUsedUrl != lastAZTrackerCheckedURL ){
			  
			  az_tracker = TRTrackerUtils.isAZTracker( lastUsedUrl  );
		  }
		  
		  URL	request_url = null;
		  
		  try{
		  
		  	request_url = constructUrl(evt,original_url);
			  			
		  	URL[]	tracker_url = { original_url };
		  	
		  	byte[]	result_bytes = updateOld( tracker_url, request_url);
		  	
		  	lastUsedUrl = tracker_url[0];	// url may have redirected, use this value as it will be correct
		  	
			TRTrackerAnnouncerResponseImpl resp = decodeTrackerResponse( lastUsedUrl, result_bytes );
			  
			int	resp_status = resp.getStatus();
			
		    if ( resp_status == TRTrackerAnnouncerResponse.ST_ONLINE ){
					
		    	try{
		    			// tracker looks ok, make any redirection permanent
		    		
		    		if ( !original_url.toString().equals(lastUsedUrl.toString())){
		    	
		    			if (Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID,
									"announce url permanently redirected: old = " + original_url + ", new = " + lastUsedUrl ));
						
					
						TorrentUtils.replaceAnnounceURL( torrent, original_url, lastUsedUrl );
						
					}
		    	}catch( Throwable e ){
		    		
		    		Debug.printStackTrace(e);
		    	}

	            urls.remove(j);
	            	
	            urls.add(0, lastUsedUrl );	
	            	
	            trackerUrlLists.remove(i);
	            	
	            trackerUrlLists.add(0,urls);            
	            
	            informURLChange( original_url, lastUsedUrl, false );
	            	
	            	//and return the result
	            		
	            return( resp );
	            
			 }else  if ( resp_status == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){
				
			 	last_failure_resp = resp;	

			 	String	reason = resp.getAdditionalInfo();
			 	
			 		// avoid re-hitting a host with multiple ports if reporting overloaded. This is
			 		// particularly "interesting" when reporting the "completed" event and we get a
			 		// "overloaded" response - when we hit another port we record the event twice
			 		// as the tracker has discarded this peer and therefore doesn't know to ignore the
			 		// second "completed" event...
			 	
			 	if ( reason != null && 
			 			( 	reason.indexOf( "too many seeds" ) != -1 ||
			 				reason.indexOf( "too many peers" ) != -1 )){
			 				
			 		skip_host	= original_url.getHost();
			 	}
			 				
			 }else{
			  			  	
				 last_failure_resp = resp;
			 }
			 
		  }catch( MalformedURLException e ){
		  	
		  	Debug.printStackTrace( e );
		  	
		  	last_failure_resp = 
		  		new TRTrackerAnnouncerResponseImpl( 
		  				original_url,
		  				torrent_hash,
		  				TRTrackerAnnouncerResponse.ST_OFFLINE, 
						getErrorRetryInterval(), 
						"malformed URL '" + (request_url==null?"<null>":request_url.toString()) + "'" );
		  	
		  }catch( Exception e ){
		  			  	
		  	last_failure_resp = 
		  		new TRTrackerAnnouncerResponseImpl(
		  				original_url,
		  				torrent_hash,
		  				TRTrackerAnnouncerResponse.ST_OFFLINE, 
						getErrorRetryInterval(), 
						e.getMessage()==null?e.toString():e.getMessage());
		  }
		
	  	  if ( destroyed ){
	  		
	  		break outer;
	  	  }
		}
	  } 
	   
		// things no good here
	
		if ( last_failure_resp == null ){
			
		  	last_failure_resp = 
		  		new TRTrackerAnnouncerResponseImpl( 
		  				null,
		  				torrent_hash,
		  				TRTrackerAnnouncerResponse.ST_OFFLINE, 
						getErrorRetryInterval(), 
						"Reason Unknown" );
		
		}
     
		// use 4* the num_want as no doubt a fair few connections will fail and
		// we want to get a decent reconnect rate
	
	  int	num_want = calculateNumWant() * 4;


      TRTrackerAnnouncerResponsePeer[]	cached_peers = getPeersFromCache(num_want);
      
      if ( cached_peers.length > 0 ){

      	// System.out.println( "cached peers used:" + cached_peers.length );
      	
      	last_failure_resp.setPeers( cached_peers );
      }
      
      return( last_failure_resp );
  }

 	private byte[] 
 	updateOld(
 		URL[]		tracker_url,
 		URL 		reqUrl )
  
  		throws Exception
	{
 		
   		// set context in case authentication dialog is required
    	
    	TorrentUtils.setTLSTorrentHash( torrent_hash );
    	
 			// loop to possibly retry update on SSL certificate install
 		
 		for (int i=0;i<2;i++){	
 		
	  		String	failure_reason = null;
	  	
			try{  
				String	protocol = reqUrl.getProtocol();
				
				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID,
							"Tracker Announcer is Requesting: " + reqUrl));
		  
		  		ByteArrayOutputStream message = new ByteArrayOutputStream();
		  				
		  		if ( protocol.equalsIgnoreCase("udp")){
		  			
		  			failure_reason = announceUDP( reqUrl, message );
		  			
		  		}else{
		  			
		  			failure_reason = announceHTTP( tracker_url, reqUrl, message );
		  			
		  		}
					// if we've got some kind of response then return it
				
				if ( message.size() > 0 ){
			
					return( message.toByteArray());
					
				}
					
				if ( failure_reason == null ){
				
					failure_reason = "No data received from tracker";
				}
				
	
			}catch( SSLException e ){
				
				// e.printStackTrace();
								
					// try and install certificate regardless of error (as this changed in JDK1.5
					// and broke this...)
				
				if ( i == 0 ){//&& e.getMessage().indexOf("No trusted certificate found") != -1 ){
					
					if ( SESecurityManager.installServerCertificates( reqUrl ) != null ){
						
							// certificate has been installed
						
						continue;	// retry with new certificate
						
					}
						
					failure_reason = exceptionToString( e );
					
				}else{
					
					failure_reason = exceptionToString( e );
					
				}
			}catch (Exception e){
		  
		  		// e.printStackTrace();
		  
		  		failure_reason = exceptionToString( e );
			}
				
			if ( failure_reason.indexOf("401" ) != -1 ){
					
				failure_reason = "Tracker authentication failed";
			}
		
			if (Logger.isEnabled())
				Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
						"Exception while processing the Tracker Request : "
								+ failure_reason));
			
			throw( new Exception( failure_reason));
 		}
 		
 			// should never get here as second iteration of loop will always cause an exit
 		
 		throw( new Exception( "Internal Error: should never get here" ));
  	}
  
 	
 	protected String
 	announceHTTP(
 		URL[]					tracker_url,	// overwritten if redirected
 		URL						reqUrl,
 		ByteArrayOutputStream	message )
 	
 		throws IOException
 	{ 		
 		TRTrackerUtils.checkForBlacklistedURLs( reqUrl );
 		
 		reqUrl = TRTrackerUtils.adjustURLForHosting( reqUrl );
 		
 		reqUrl = AddressUtils.adjustURL( reqUrl );
 		
 		String	failure_reason = null;
 		
 		HttpURLConnection con;
 		
 		Properties	http_properties = new Properties();
 		
 		http_properties.put( ClientIDGenerator.PR_URL, reqUrl );
 		
 		try{
 			ClientIDManagerImpl.getSingleton().generateHTTPProperties( http_properties );
 			
 		}catch( ClientIDException e ){
 			
 			throw( new IOException( e.getMessage()));
 		}
		
 		reqUrl = (URL)http_properties.get( ClientIDGenerator.PR_URL );
 		
 		if ( reqUrl.getProtocol().equalsIgnoreCase("https")){
 			
 			// see ConfigurationChecker for SSL client defaults
 			
 			HttpsURLConnection ssl_con = (HttpsURLConnection)reqUrl.openConnection();
 			
 			// allow for certs that contain IP addresses rather than dns names
 			
 			ssl_con.setHostnameVerifier(
 					new HostnameVerifier()
 					{
 						public boolean
 						verify(
 								String		host,
								SSLSession	session )
 						{
 							return( true );
 						}
 					});
 			
 			
 			con = ssl_con;
 			
 		}else{
 			
 			con = (HttpURLConnection) reqUrl.openConnection();
 		}
 		
 		
 		String	user_agent = (String)http_properties.get( ClientIDGenerator.PR_USER_AGENT );
 		
 		if ( user_agent != null ){
 			
 			con.setRequestProperty("User-Agent", user_agent );
 		}
 		
 		con.setRequestProperty("Connection", "close" );
 		
 		// some trackers support gzip encoding of replies
 		
 		con.addRequestProperty("Accept-Encoding","gzip");
 		
 		try{
 			
 			con.connect();
 			
 			InputStream is = null;
 			
 			try{
 				
 				is = con.getInputStream();
 				
 				String	resulting_url_str = con.getURL().toString();
 				
 				if ( !reqUrl.toString().equals( resulting_url_str )){
 					
 						// some kind of redirect has occurred. Unfortunately we can't get at the underlying
 						// redirection reason (temp, perm etc) so we support the use of an explicit indicator
 						// in the resulting url
 					
 					String	marker = "permredirect=1";
 					
 					int	pos = resulting_url_str.indexOf( marker );
 				
 					if ( pos != -1 ){
 						
 						pos = pos-1;	// include the '&' or '?'
 						
 						try{
 							URL	redirect_url = 
 								new URL( resulting_url_str.substring(0,pos));
 							
  							tracker_url[0]	= redirect_url;
						
 						}catch( Throwable e ){
 							Debug.printStackTrace(e);
 						}
 					}
 				}
 				
 				String encoding = con.getHeaderField( "content-encoding");
 				
 				boolean	gzip = encoding != null && encoding.equalsIgnoreCase("gzip");
 				
 				// System.out.println( "encoding = " + encoding );
 				
 				if ( gzip ){
 					
 					is = new GZIPInputStream( is );
 				}
 				
 					// there are some trackers out there that don't set content length correctly
 					// so we can't reliably use it :(
 				
 				int content_length = -1; //con.getContentLength();
 				
 				//      System.out.println(length);
 				
 				byte[] data = new byte[1024];
 				
 				int	num_read = 0;
 				
 				// some trackers don't return content-length
 				
 				while ( content_length <= 0 || num_read < content_length ){
 					
 					try{
 						int	len = is.read(data);
 						
 						if ( len > 0 ){
 							
 							message.write(data, 0, len);
 							
 							num_read += len;
 							
 							if ( num_read > 128*1024 ){
 								
 									// someone's sending us junk, bail out
 								
 								message.reset();
 								
 								throw( new Exception( "Tracker response invalid (too large)" ));
 								
 							}
 						}else if ( len == 0 ){
 							
 							Thread.sleep(20);
 							
 						}else{
 							
 							break;
 						}
 						
 					}catch (Exception e){
 						
 						if (Logger.isEnabled()) {
							Logger.log(new LogEvent(torrent, LOGID,
									"Exception while Requesting Tracker", e));
							Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
									"Message Received was : " + message));
						}
 						
 						failure_reason = exceptionToString( e );
 						
 						break;
 					}
 				}
 				
 				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID, "Tracker Announcer ["
							+ lastUsedUrl + "] has received : " + message));
 				
 				
 			}catch (Exception e){
 				
 				// e.printStackTrace();
 				
 				failure_reason = exceptionToString( e );
 				
 			}finally{
 				
 				if (is != null) {
 					
 					try {
 						is.close();
 						
 					}catch (Exception e) {
 					}
 					
 					is = null;
 				}
 			}
 		}finally{
 			con.disconnect();
 		}
 		
 		return( failure_reason );
 	}
 	
 	protected String
 	announceUDP(
 		URL						reqUrl,
		ByteArrayOutputStream	message )
 	
 		throws IOException
 	{
 		reqUrl = TRTrackerUtils.adjustURLForHosting( reqUrl );

 		String	failure_reason = null;
		
 		PasswordAuthentication	auth = null;	
 		
 		try{
 			if ( reqUrl.getQuery().toLowerCase().indexOf("auth") != -1 ){
 				
 				 auth = SESecurityManager.getPasswordAuthentication( UDP_REALM, reqUrl );
 			}
 						
 			PRUDPPacketHandler handler = PRUDPPacketHandlerFactory.getHandler( UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber());
 			
 			InetSocketAddress destination = new InetSocketAddress(reqUrl.getHost(),reqUrl.getPort()==-1?80:reqUrl.getPort());
 			
 			for (int retry_loop=0;retry_loop<PRUDPPacketTracker.DEFAULT_RETRY_COUNT;retry_loop++){
 				
 				try{
 			
		 			PRUDPPacket connect_request = new PRUDPPacketRequestConnect();
		 			
		 			PRUDPPacket reply = handler.sendAndReceive( auth, connect_request, destination );
		 			
		 			if ( reply.getAction() == PRUDPPacketTracker.ACT_REPLY_CONNECT ){
		 			
		 				PRUDPPacketReplyConnect connect_reply = (PRUDPPacketReplyConnect)reply;
		 				
		 				long	my_connection = connect_reply.getConnectionId();
		 			
		 				PRUDPPacketRequest request;
		 				
		 				if ( PRUDPPacketTracker.VERSION == 1 ){
		 					
		 					PRUDPPacketRequestAnnounce announce_request = new PRUDPPacketRequestAnnounce( my_connection );
		 		
		 					request = announce_request;
		 					
			 					// bit of a hack this...
			 				
			 				String	url_str = reqUrl.toString();
			 				
			 				int		p_pos = url_str.indexOf("?");
			 				
			 				url_str	= url_str.substring(p_pos+1);
			 				
			 				String event_str = getURLParam( url_str, "event" );
			 				
			 				int	event = PRUDPPacketRequestAnnounce.EV_UPDATE;
			 				
			 				if ( event_str != null ){
			 					
			 					if ( event_str.equals( "started" )){
			 						
			 						event = PRUDPPacketRequestAnnounce.EV_STARTED;
			 						
			 					}else if ( event_str.equals( "stopped" )){
			 						
			 						event = PRUDPPacketRequestAnnounce.EV_STOPPED;
			 						
			 					}else if ( event_str.equals( "completed" )){
			 						
			 						event = PRUDPPacketRequestAnnounce.EV_COMPLETED;
			 					}
			 				}
			 				
			 				String	ip_str = getURLParam( url_str, "ip" );
			 				
			 				int	ip = 0;
			 				
			 				if ( ip_str != null ){
			 					
			 					ip = PRHelpers.addressToInt( ip_str);
			 				}
			 				
			 				announce_request.setDetails(
			 					torrent_hash.getBytes(),
			 					tracker_peer_id,
								getLongURLParam( url_str, "downloaded" ), 
								event,
								ip,
								(int)getLongURLParam( url_str, "numwant" ), 
								getLongURLParam( url_str, "left" ), 
								(short)getLongURLParam( url_str, "port" ),
								getLongURLParam( url_str, "uploaded" ));
		 				
		 				}else{
		 					PRUDPPacketRequestAnnounce2 announce_request = new PRUDPPacketRequestAnnounce2( my_connection );
		 					
		 					request = announce_request;
		 					
		 					// bit of a hack this...
			 				
			 				String	url_str = reqUrl.toString();
			 				
			 				int		p_pos = url_str.indexOf("?");
			 				
			 				url_str	= url_str.substring(p_pos+1);
			 				
			 				String event_str = getURLParam( url_str, "event" );
			 				
			 				int	event = PRUDPPacketRequestAnnounce.EV_UPDATE;
			 				
			 				if ( event_str != null ){
			 					
			 					if ( event_str.equals( "started" )){
			 						
			 						event = PRUDPPacketRequestAnnounce.EV_STARTED;
			 						
			 					}else if ( event_str.equals( "stopped" )){
			 						
			 						event = PRUDPPacketRequestAnnounce.EV_STOPPED;
			 						
			 					}else if ( event_str.equals( "completed" )){
			 						
			 						event = PRUDPPacketRequestAnnounce.EV_COMPLETED;
			 					}
			 				}
			 				
			 				String	ip_str = getURLParam( url_str, "ip" );
			 				
			 				int	ip = 0;
			 				
			 				if ( ip_str != null ){
			 					
			 					ip = PRHelpers.addressToInt( ip_str);
			 				}
			 				
			 				announce_request.setDetails(
			 					torrent_hash.getBytes(),
			 					tracker_peer_id,
								getLongURLParam( url_str, "downloaded" ), 
								event,
								ip,
								key_udp,
								(int)getLongURLParam( url_str, "numwant" ), 
								getLongURLParam( url_str, "left" ), 
								(short)getLongURLParam( url_str, "port" ),
								getLongURLParam( url_str, "uploaded" ));	
		 				}
		 				
		 				reply = handler.sendAndReceive( auth, request, destination );
		 			
		 				if ( reply.getAction() == PRUDPPacketTracker.ACT_REPLY_ANNOUNCE ){
		 					
		 					if ( auth != null ){
		 						
		 						SESecurityManager.setPasswordAuthenticationOutcome( UDP_REALM, reqUrl, true );
		 					}
		 					
		 					if ( PRUDPPacketTracker.VERSION == 1 ){
			 					PRUDPPacketReplyAnnounce	announce_reply = (PRUDPPacketReplyAnnounce)reply;
			 					
			 					Map	map = new HashMap();
			 					
			 					map.put( "interval", new Long( announce_reply.getInterval()));
			 					
			 					int[]	addresses 	= announce_reply.getAddresses();
			 					short[]	ports		= announce_reply.getPorts();
			 					
			 					List	peers = new ArrayList();
			 					
			 					map.put( "peers", peers );
			 					
			 					for (int i=0;i<addresses.length;i++){
			 						
			 						Map	peer = new HashMap();
			 						
			 						peers.add( peer );
			 						
			 						peer.put( "ip", PRHelpers.intToAddress(addresses[i]).getBytes());
			 						peer.put( "port", new Long( ports[i]));
			 					}
			 					
			 					byte[] data = BEncoder.encode( map );
			 					
			 					message.write( data );
			 					
			 					return( null );
			 					
		 					}
		 					
			 					PRUDPPacketReplyAnnounce2	announce_reply = (PRUDPPacketReplyAnnounce2)reply;
			 					
			 					Map	map = new HashMap();
			 					
			 					map.put( "interval", new Long( announce_reply.getInterval()));
			 					
			 					int[]	addresses 	= announce_reply.getAddresses();
			 					short[]	ports		= announce_reply.getPorts();
			 					
			 					map.put( "complete", new Long(announce_reply.getSeeders()));
			 					map.put( "incomplete", new Long(announce_reply.getLeechers()));
			 					
			 					List	peers = new ArrayList();
			 					
			 					map.put( "peers", peers );
			 					
			 					for (int i=0;i<addresses.length;i++){
			 						
			 						Map	peer = new HashMap();
			 						
			 						peers.add( peer );
			 						
			 						peer.put( "ip", PRHelpers.intToAddress(addresses[i]).getBytes());
			 						peer.put( "port", new Long( ports[i]));
			 					}
			 					
			 					byte[] data = BEncoder.encode( map );
			 					
			 					message.write( data );
			 					
			 					return( null );
		 					
		 				}
		 			
		 					failure_reason = ((PRUDPPacketReplyError)reply).getMessage();
		 				
		 			}else{
		 				
		 				failure_reason = ((PRUDPPacketReplyError)reply).getMessage();
		 			}
		 		}catch( PRUDPPacketHandlerException e ){
		 			
		 			if ( e.getMessage() == null || e.getMessage().indexOf("timed out") == -1 ){
		 				
		 				throw( e );
		 			}
		 		}
 			}
 			
 		}catch( Throwable e ){
 		
 			failure_reason = exceptionToString(e);
 		}
 		
		if ( auth != null ){
					
			SESecurityManager.setPasswordAuthenticationOutcome( UDP_REALM, reqUrl, false );
		}

 		return( failure_reason );
 	}
 	
 	protected long
 	getLongURLParam(
 		String		url,
		String		param )
 	{
 		String	val = getURLParam( url, param );
 		
 		if( val == null ){
 			
 			return(0);
 		}
 		
 		return( Long.parseLong( val ));
 	}
 	
 	protected String
 	getURLParam(
 		String		url,
		String		param )
 	{
 		int	p1 = url.indexOf( param + "=" );
 		
 		if ( p1 == -1 ){
 			
 			return( null );
 		}
 		
 		int	p2 = url.indexOf( "&", p1 );
 		
 		if ( p2 == -1 ){
 			
 			return( url.substring(p1+param.length()+1));
 		}
 		
 		return( url.substring(p1+param.length()+1,p2));
 	}
 	
  protected String
  exceptionToString(
  	Throwable 	e )
  {
  	String class_name = e.getClass().getName();
  	
  	int	pos = class_name.lastIndexOf( '.' );
  	
  	if ( pos != -1 ){
  		
  		class_name = class_name.substring(pos+1);
  	}
  	
  	String str = class_name + ":" + e.getMessage();
  	
  	if ( str.indexOf( "timed out") != -1 ){
  		
  		str  = "timeout";
  	}
  	
  	return( str );
  }
  
  public URL 
  constructUrl(
  	String 	evt,
	URL		_url)
  
  	throws Exception
  {
  	String	url = _url.toString();
  	
  	StringBuffer request = new StringBuffer(url);
  	
  		// if url already has a query component then just append our parameters on the end
  	if ( url.indexOf('?') != -1 ){
  		request.append('&');
  	}else{
  		request.append('?');
  	}
  	
  		// the client-id stuff RELIES on info_hash being the FIRST parameter added by
  		// us to the URL, so don't change it!
  	
  	request.append(info_hash);
  	request.append(tracker_peer_id_str);
  	
	String	port_details = 
		announce_data_provider.getCryptoLevel()==NetworkManager.CRYPTO_OVERRIDE_REQUIRED?
				TRTrackerUtils.getPortsForURLFullCrypto():
				TRTrackerUtils.getPortsForURL();

  	request.append(port_details);
  	request.append("&uploaded=").append(announce_data_provider.getTotalSent());
  	request.append("&downloaded=").append(announce_data_provider.getTotalReceived());
  	request.append("&left=").append(announce_data_provider.getRemaining());
	
    
    //TrackerID extension
    if( tracker_id.length() > 0 ) {
      request.append( "&trackerid=").append(tracker_id );
    }
    
    
    if (evt.length() != 0) {
    	request.append("&event=").append(evt);
    }
    
    boolean	stopped = evt.equals("stopped");
    
    if ( stopped ){
    	
    	request.append("&numwant=0");
    	
    	if ( stopped_for_queue ){
    		
    		request.append( "&azq=1" );
    	}
    }else {
    	
      //calculate how many peers we should ask for
    	
      int numwant = calculateNumWant();


      request.append("&numwant=").append(numwant);
      
      //no_peer_id has been made obsolete by 'compact'
    }
    
    	// actually, leave this in, ask PARG why!
    
    request.append("&no_peer_id=1");
      
    	// latest space saving measure, a compact return type where peers are returned
    	// as 6 byte entries in a single byte[] (4 bytes ip, 2 byte port)
    	// leave this as always supplied, ask PARG why
    
    request.append( "&compact=1" );
	
    	// any explicit override takes precedence over any implicit override added 
    	// when hosting torrents
    
    String explicit_ips = COConfigurationManager.getStringParameter( "Override Ip", "" );
    
    String 	ip					= null;
    String	tracker_network	= AENetworkClassifier.categoriseAddress( _url.getHost()); 
    
    	// make sure this tracker network is enabled
    
    boolean	network_ok			= false;
    boolean	normal_network_ok	= false;
    
    if ( peer_networks == null ){
    	
    	network_ok			= true;
    	normal_network_ok	= true;
    	
    }else{
	    for (int i=0;i<peer_networks.length;i++){
	    
	    	if ( peer_networks[i] == AENetworkClassifier.AT_PUBLIC ){
	    		
	    		normal_network_ok = true;
	    	}
	    	
	    	if ( peer_networks[i] == tracker_network ){
	    		
	    		network_ok	= true;
	    	}
	    }
    }
    
    if ( !network_ok ){
    	
    	throw( new Exception( "Network not enabled for url '" + _url + "'" ));
    }
    
    String	normal_explicit = null;
    
   	if ( explicit_ips.length() > 0 ){
    		
   			// gotta select an appropriate override based on network type
	  			
		StringTokenizer	tok = new StringTokenizer( explicit_ips, ";" );
				
		while( tok.hasMoreTokens()){
			
			String	this_address = tok.nextToken().trim();
			
			if ( this_address.length() > 0 ){
				
				String	cat = AENetworkClassifier.categoriseAddress( this_address );
				
				if ( cat == AENetworkClassifier.AT_PUBLIC ){
					
					normal_explicit	= this_address;
				}
				
				if ( tracker_network == cat ){
					
					ip = this_address;
					
					break;
				}
			}
		}	
   	}
    
   	if ( ip == null ){
   		
   			// if we have a normal explicit override and this is enabled then use it 
   		
   		if ( normal_network_ok && normal_explicit != null ){
   			
   			ip = normal_explicit;
   			
   		}else{
   			
	   		if ( ip_override != null ){
	   			
	   			ip = ip_override;
	   		}
   		}
   	}
    
    if ( ip != null ){
     	   	
    	if ( tracker_network == AENetworkClassifier.AT_PUBLIC ){
    	
    		try{
    			ip = PRHelpers.DNSToIPAddress( ip );
    		
    		}catch( UnknownHostException e){
    		    			
    			if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
								"IP Override host resolution of '" + ip
										+ "' fails, using unresolved address"));
    		}
    	}
    	    	
    	request.append("&ip=").append(ip);
    }
	
    if ( COConfigurationManager.getBooleanParameter("Tracker Key Enable Client", true )){
      	
      	request.append( "&key=").append(key_id);
    }
    
	String	ext = announce_data_provider.getExtensions();
	
	if ( ext != null ){
		
			// sanitise it
		
		while ( ext.startsWith("&")){
			
			ext = ext.substring( 1 );
		}
		
		request.append( "&" );
		
		request.append( ext );
	}
	
	request.append( "&azver=" + TRTrackerAnnouncer.AZ_TRACKER_VERSION_CURRENT );
	
	if ( az_tracker && !stopped ){
		
		int up = announce_data_provider.getUploadSpeedKBSec( evt.equals( "started" ));
		
		if ( up > 0 ){
			
			request.append( "&azup=" + up );
		}
		
	    String	as = COConfigurationManager.getStringParameter( "ASN AS", "" );

	    if ( as.length() > 0 ){
	    	
	    	request.append( "&azas=" + URLEncoder.encode( as, "UTF8" ));
	    }
	    
		DHTNetworkPosition	best_position = DHTNetworkPositionManager.getBestLocalPosition();
		
		if ( best_position != null ){
			
			try{
				byte[]	bytes = DHTNetworkPositionManager.serialisePosition( best_position );
				
				request.append( "&aznp=" + Base32.encode( bytes ));
								
			}catch( Throwable e ){
				
				Debug.printStackTrace( e );
			}
		}
	}
	
    return new URL( request.toString());
  }

  protected int
  calculateNumWant()
  {
    int MAX_PEERS = 100;
    
    int maxAllowed = announce_data_provider.getMaxNewConnectionsAllowed();
    
    if ( maxAllowed < 0 || maxAllowed > MAX_PEERS ) {
      maxAllowed = MAX_PEERS;
    }

    return maxAllowed;
  }
  
  public byte[] 
  getPeerId() 
  {
  	return( data_peer_id );
  }

 	public void 
 	setAnnounceDataProvider(
 		TRTrackerAnnouncerDataProvider _provider) 
 	{
		try{
			this_mon.enter();

			announce_data_provider = _provider;
			
		}finally{
			
			this_mon.exit();
		}
 	}
	
	public TOTorrent
	getTorrent()
	{
		return( torrent );
	}
	
	public URL 
	getTrackerUrl() 
	{
		return( lastUsedUrl );
	} 
  
	public void 
	setTrackerUrl(
		URL new_url ) 
	{
		try{
			new_url = new URL( new_url.toString().replaceAll(" ", ""));
			
			List list = new ArrayList(1);
	  	
			list.add( new_url );
	  	
			trackerUrlLists.clear();
	  	
			trackerUrlLists.add( list );
		
			informURLChange( lastUsedUrl, new_url, true );   
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
		}
	}
  
	public void
	resetTrackerUrl(
		boolean		shuffle )
	{
		String	old_list = trackerURLListToString();
		
		constructTrackerUrlLists(shuffle);
 	
		if ( trackerUrlLists.size() == 0 ){
		
			return;
		}
	
		if ( !old_list.equals(trackerURLListToString())){
			
			URL	first_url = (URL)((List)trackerUrlLists.get(0)).get(0);
			
			informURLChange( lastUsedUrl, first_url, true );
		}
	}
	
	public void
	refreshListeners()
	{
		informURLRefresh();
	}

	public void
	setIPOverride(
		String		override )
	{
		ip_override = override;
	}
	
	public void
	clearIPOverride()
	{
		ip_override = null;
	}
	
	private void 
	constructTrackerUrlLists(
		boolean	shuffle )
	{
		try{
			trackerUrlLists = new ArrayList(1);
	  
				//This entry is present on multi-tracker torrents
	  
			TOTorrentAnnounceURLSet[]	announce_sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
	       
			if ( announce_sets.length == 0 ){
	  	
					//If not present, we use the default specification
					
				URL url = torrent.getAnnounceURL();
				       
					//We then contruct a list of one element, containing this url, and put this list
					//into the list of lists of urls.
					
				List list = new ArrayList();
				
				list.add(url);
				
				trackerUrlLists.add(list);
			}else{
	  			
					//Ok we have a multi-tracker torrent
				
				for(int i = 0 ; i < announce_sets.length ; i++){
					
				  	//Each list contains a list of urls
				  
					URL[]	urls = announce_sets[i].getAnnounceURLs();
					
				 	List random_urls = new ArrayList();
				 	
				 	for(int j = 0 ; j < urls.length; j++){
				  		
						//System.out.println(urls.get(j).getClass());
						      
						URL url = urls[j];
						            		
							//Shuffle
							
						int pos = shuffle?(int)(Math.random() *  (random_urls.size()+1)):j;
						
						random_urls.add(pos,url);
				  	}
				  			  	         
				  		//Add this list to the list
				  		
				 	trackerUrlLists.add(random_urls);
				}
			}      
		}catch(Exception e){
			
			Debug.printStackTrace( e );
		}
	}
  
	protected String
	trackerURLListToString()
	{
		String trackerUrlListString = "[";
		
		for (int i=0;i<trackerUrlLists.size();i++){

			List	group = (List)trackerUrlLists.get(i);
			
			trackerUrlListString	+= (i==0?"":",") + "[";
			
			for (int j=0;j<group.size();j++){
				
				URL	u = (URL)group.get(j);
				
				trackerUrlListString	+= (j==0?"":",") + u.toString();
			}
			
			trackerUrlListString	+= "]";
		}
		
		trackerUrlListString += "]";
		
		return( trackerUrlListString );
	}
	
  	protected TRTrackerAnnouncerResponseImpl
  	decodeTrackerResponse(
  		URL			url,
  		byte[]		data )
  	{
  		String	failure_reason;
  		
  		if ( data == null ){
  			
  			failure_reason = "no response";
  			
  		}else{
  		
	 		try{
					   //parse the metadata
				
	 			try{
	 				Map metaData = BDecoder.decode(data); //$NON-NLS-1$
						
	 					// handle any user warnings in the response
					
	 				try{
	 					byte[]	b_warning_message = (byte[])metaData.get( "warning message" );
	 				
	 					if ( 	b_warning_message != null && 
								COConfigurationManager.getBooleanParameter( "Tracker Client Show Warnings" )){

	 						String	warning_message = new String(b_warning_message);
	 						
								// don't report the same message twice per torrent
							
							if ( !warning_message.equals( last_tracker_message )){
								
								last_tracker_message	= warning_message;
							
								boolean	log_it = false;
								
									// only report a given message once per tracker
								
								try{
									class_mon.enter();
								
									String last_warning_message = (String)tracker_report_map.get( url.getHost());
									
									if ( 	last_warning_message == null ||
											!warning_message.equals( last_warning_message )){
		 							
										log_it	= true;
										
										tracker_report_map.put( url.getHost(), warning_message );
									}
								}finally{
									
									class_mon.exit();
								}
								
								if ( log_it ){
		 							Logger.logTextResource(new LogAlert(torrent, 
		 									LogAlert.UNREPEATABLE, LogAlert.AT_WARNING,
											"TrackerClient.announce.warningmessage"), new String[] {
											announce_data_provider.getName(), warning_message });
		 						}
							}
	 					}
	 				}catch( Throwable e ){
	 					
	 					Debug.printStackTrace( e );
	 				}
	 				
					long	time_to_wait;
										
					try {
						time_to_wait = ((Long) metaData.get("interval")).longValue();

						Long raw_min_interval = (Long) metaData.get("min interval");

						if (Logger.isEnabled()) {
							Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_INFORMATION,
									"Received from announce: 'interval' = " + time_to_wait
											+ "; 'min interval' = " + raw_min_interval));
						}

						// guard against crazy return values
						if (time_to_wait < 0 || time_to_wait > 0xffffffffL) {
							time_to_wait = 0xffffffffL;
						}

						if (raw_min_interval != null) {
							min_interval = raw_min_interval.longValue();

							// ignore useless values
							// Note: Many trackers set min_interval and interval the same.
							if (min_interval < 1) {
								if (Logger.isEnabled()) {
									Logger.log(new LogEvent(
											torrent,
											LOGID,
											LogEvent.LT_INFORMATION,
											"Tracker being silly and "
													+ "returning a 'min interval' of less than 1 second ("
													+ min_interval + ")"));
								}
								min_interval = 0;
							} else if (min_interval > time_to_wait) {
								if (Logger.isEnabled()) {
									Logger.log(new LogEvent(
											torrent,
											LOGID,
											LogEvent.LT_INFORMATION,
											"Tracker being silly and "
													+ "returning a 'min interval' ("
													+ min_interval
													+ ") greater than recommended announce 'interval'"
													+ " (" + time_to_wait + ")"));
								}
								min_interval = 0;
							}
						}

						// roll back 10 seconds to make sure we announce before the tracker
						// times us out.  This is done after min_interval in order not to 
						// mess up the "ignore useless values"
						if (time_to_wait > 30)
							time_to_wait -= 10;

					} catch (Exception e) {
				   	
    				     byte[]	failure_reason_bytes = (byte[]) metaData.get("failure reason");
    						
    				     if ( failure_reason_bytes == null ){
    							
    				    	 if (Logger.isEnabled())
    								Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_WARNING,
    										"Problems with Tracker, will retry in "
    												+ getErrorRetryInterval() + "ms"));
    											   			
    				       return( new TRTrackerAnnouncerResponseImpl( url, torrent_hash, TRTrackerAnnouncerResponse.ST_OFFLINE, getErrorRetryInterval(), "Unknown cause" ));
    	
    				     }
    				     	
    			     		// explicit failure from the tracker
    			     	
    			       failure_reason = new String( failure_reason_bytes, Constants.DEFAULT_ENCODING);
                            				
    			       return( new TRTrackerAnnouncerResponseImpl( url, torrent_hash, TRTrackerAnnouncerResponse.ST_REPORTED_ERROR, getErrorRetryInterval(), failure_reason ));
				     
				   }
				   
				   	//System.out.println("Response from Announce: " + new String(data));
				   
				   Long incomplete_l 	= (Long)metaData.get("incomplete");
				   Long complete_l 		= (Long)metaData.get("complete");
				   
				   if ( incomplete_l != null || complete_l != null  ){
				   
				  	 if (Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID,
									"ANNOUNCE SCRAPE1: seeds=" + complete_l + " peers="
											+ incomplete_l));
				   }
           
           
			           //TrackerID extension, used by phpbt trackers.
			           //We reply with '&trackerid=1234' when we receive
			           //'10:tracker id4:1234e' on announce reply.
			           //NOTE: we receive as 'tracker id' but reply as 'trackerid'
			           byte[] trackerid = (byte[])metaData.get( "tracker id" );
			           if( trackerid != null ) {
			             tracker_id = new String( trackerid );
			           }
			           
				    byte[]	crypto_flags = (byte[])metaData.get( "crypto_flags" );
           						
						//build the list of peers
					List valid_meta_peers = new ArrayList();
						
				    Object	meta_peers_peek = metaData.get( "peers" );
				    
				    
			    	Long	az_compact_l 	= (Long)metaData.get( "azcompact" );
			    	long	az_compact		= az_compact_l==null?0:az_compact_l.longValue();

					boolean	this_is_az_tracker = az_compact == 2;
					
					if ( az_tracker != this_is_az_tracker || lastUsedUrl != lastAZTrackerCheckedURL ){
							
						lastAZTrackerCheckedURL = lastUsedUrl;
						
						az_tracker	= this_is_az_tracker;
						
						TRTrackerUtils.setAZTracker( url, az_tracker );
					}

			    	if ( az_compact == 2 ){
			    		
	
			    			// latest return to dictionary based data 
			    		
						List meta_peers = (List)meta_peers_peek;

						int peers_length = meta_peers.size();

						if ( peers_length > 1 ){
								// calculate average rtt to use for those with no rtt
		 					 
							long	total_rtt 	= 0;
							int		rtt_count	= 0;
							
							for ( int i = 0; i < peers_length; i++ ){
							 	
								Map peer = (Map) meta_peers.get(i);
								
					    		Long	l_rtt = (Long)peer.get( "r" );
	
					    		if ( l_rtt != null ){
					    			
					    			long rtt = l_rtt.longValue();
					    			
					    			if ( rtt <= 0 ){
					    				
					    					// invalid, remove
					    				
					    				peer.remove( "r" );
					    				
					    			}else{
					    				
					    				total_rtt 	+= rtt;
					    			}
					    			
					    			rtt_count++; 
					    		}
							}
							
							final int average_rtt = (int)( rtt_count==0?0:(total_rtt/rtt_count));
							
								// sort into smallest rtt order with biased at front
							
							Collections.sort(
									meta_peers,
									new Comparator()
									{
										public int 
										compare(
											Object	o1, 
											Object	o2 )
										{
											Map	map1 = (Map)o1;
											Map map2 = (Map)o2;
											
								    		Long	l_rtt1 = (Long)map1.get( "r" );
								    		Long	l_rtt2 = (Long)map2.get( "r" );
	
								    		boolean	biased_1 = map1.containsKey( "b" );
								    		boolean	biased_2 = map2.containsKey( "b" );
								    		
								    		if ( biased_1 == biased_2 ){
								    			
								    			int	rtt1 = l_rtt1==null?average_rtt:l_rtt1.intValue();
								    			int	rtt2 = l_rtt2==null?average_rtt:l_rtt2.intValue();
								    			
								    			return( rtt1 - rtt2 );
								    			
								    		}else if ( biased_1 ){
								    			
								    			return( -1 );
								    		}else{
								    			
								    			return( +1 );
								    		}
										}
									});
							
								// interleave non-biased peers with good rtt
							
							int	biased_pos		= peers_length;
							int	non_biased_pos	= peers_length;
							
							for ( int i = 0; i < peers_length; i++ ){
							 	
								Map peer = (Map) meta_peers.get(i);
			
								if ( peer.containsKey( "b" )){
									
									if ( i == 0 ){
										
										biased_pos	= i;
									}
								}else{
									
									non_biased_pos = i;
									
									break;
								}
							}
								
							List	new_peers = new ArrayList(peers_length);
							
							int		non_biased_start = non_biased_pos;
							
							boolean	last_biased	= true;
								
							while( biased_pos < non_biased_start || non_biased_pos < peers_length ){
								
								if ( biased_pos < non_biased_start ){
									
									if ( non_biased_pos < peers_length ){
										
										Map biased 		= (Map) meta_peers.get(biased_pos);
										Map non_biased 	= (Map) meta_peers.get(non_biased_pos);

										boolean	use_biased;
										
										if ( !last_biased ){
											
											use_biased = true;
											
										}else{
											
									    	Long	l_rtt_biased 		= (Long)biased.get( "r" );
								    		Long	l_rtt_non_biased 	= (Long)non_biased.get( "r" );
		
							    			int	biased_rtt 		= l_rtt_biased==null?average_rtt:l_rtt_biased.intValue();
							    			int	non_biased_rtt 	= l_rtt_non_biased==null?average_rtt:l_rtt_non_biased.intValue();
		
							    			use_biased = non_biased_rtt >= biased_rtt;
										}
										
										if ( use_biased ){
											
											new_peers.add( biased );
											
											biased_pos++;
											
										}else{
											
											new_peers.add( non_biased );
											
											non_biased_pos++;
										}
										
										last_biased = use_biased;
									}else{
										
										new_peers.add(  meta_peers.get( biased_pos++ ));
									}
								}else{
									
									new_peers.add( meta_peers.get( non_biased_pos++ ));
								}
							}
							
							meta_peers = new_peers;
						}
						
						for ( int i = 0; i < peers_length; i++ ){
						 	
							Map peer = (Map) meta_peers.get(i);
							   	
							try{
								byte[]		ip_bytes = (byte[])peer.get("i");
	
					    		int	ip1 = 0xff & ip_bytes[0];
					    		int	ip2 = 0xff & ip_bytes[1];
					    		int	ip3 = 0xff & ip_bytes[2];
					    		int	ip4 = 0xff & ip_bytes[3];
	
					    		String	ip 			= ip1 + "." + ip2 + "." + ip3 + "." + ip4;
					    		
					    		byte[]	tcp_bytes	= (byte[])peer.get("t");
					    		
					    		int		tcp_port 	= ((tcp_bytes[0]&0xff) << 8 ) + (tcp_bytes[1]&0xff );
	                
					    		byte[]	peer_peer_id = getAnonymousPeerId( ip, tcp_port );
	
					    		int		udp_port 	= 0;
					    		
					    		byte[]	udp_bytes = (byte[])peer.get("u");
								
					    		if ( udp_bytes != null ){
					    			
					    			if ( udp_bytes.length == 0 ){
					    				
					    				udp_port = tcp_port;
					    				
					    			}else{
					    				
					    				udp_port	= ((udp_bytes[0]&0xff) << 8 ) + (udp_bytes[1]&0xff );
					    			}
					    		}
					    		
					    		int	http_port = 0;
					    		
					    		byte[]	http_bytes = (byte[])peer.get("h");

					    		if ( http_bytes != null ){
					    			
					    			http_port	= ((http_bytes[0]&0xff) << 8 ) + (http_bytes[1]&0xff );
					    		}
					    		
					    		short	protocol = DownloadAnnounceResultPeer.PROTOCOL_NORMAL;
					    		
					    		byte[]	protocol_bytes = (byte[])peer.get("c");
					    		
					    		if ( protocol_bytes != null ){
					    			
					    			protocol = (protocol_bytes[0]&0x01)==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
					    		}
					    		
					    		Long	l_azver = (Long)peer.get("v" );
					    		
					    		byte	az_ver = l_azver==null?TRTrackerAnnouncer.AZ_TRACKER_VERSION_1:l_azver.byteValue();
					    								    		
					    		Long	l_up_speed = (Long)peer.get( "s" );
					    		
								TRTrackerAnnouncerResponsePeerImpl new_peer = 
									new TRTrackerAnnouncerResponsePeerImpl( 
										PEPeerSource.PS_BT_TRACKER, 
										peer_peer_id, 
										ip, 
										tcp_port,
										udp_port,
										http_port,
										protocol,
										az_ver,
										l_up_speed==null?0:l_up_speed.shortValue());
										
								if (Logger.isEnabled()){
									
						    		String	extra = "";
						    		
						    		Long	l_rtt = (Long)peer.get( "r" );
						    				
						    		if ( l_rtt != null ){
						    			
						    			extra = ",rtt=" + l_rtt;
						    		}
						    		
						    		boolean	biased = peer.containsKey( "b" );

						    		if ( biased ){
						    			
						    			extra += ",biased";
						    		}
						    		
									Logger.log(new LogEvent(torrent, LOGID,
											"AZ2-COMPACT PEER: " + new_peer.getString() + extra));
								}
								
								valid_meta_peers.add( new_peer );
								
							}catch( Throwable e ){
								
				    			if (Logger.isEnabled())
				    				Logger.log(
				    					new LogEvent(
				    						torrent, LOGID, LogEvent.LT_ERROR,
				    						"Invalid az2 peer received: " + peer ));
							}
						}
						
			    	}else if ( meta_peers_peek instanceof List ){
				    	
				    		// old style non-compact
			    		
						List meta_peers = (List)meta_peers_peek;
						 					 
							//for every peer
						
						int peers_length = meta_peers.size();
							
						if ( crypto_flags != null && peers_length != crypto_flags.length ){
							
							crypto_flags = null;
							
                           	if (Logger.isEnabled())
                        		Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
                        				"Invalid crypto_flags returned: length mismatch" ));
						}
						
						for (int i = 0; i < peers_length; i++) {
							 	
							Map peer = (Map) meta_peers.get(i);
							   						
							Object s_peerid	= peer.get("peer id"); 
							Object s_ip		= peer.get("ip"); 
							Object s_port	= peer.get("port"); 
												
								// Assert that all ip and port are available
							
							if ( s_ip != null && s_port != null ){
					
									//get the peer ip address
								
								String ip = new String((byte[]) s_ip, Constants.DEFAULT_ENCODING); 
								
									//get the peer port number
								
								int peer_port = ((Long) s_port).intValue();
                                // try to repair invalid peer ports; worst that can happen is we
                                // still can't make outgoing connections that we already can't make
                                if (peer_port >65535)
                                    peer_port -=65536;
                                if (peer_port <0)
                                    peer_port +=65536;
				                
                                if (peer_port < 0 || peer_port > 65535) {
                                	if (Logger.isEnabled())
                                		Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
                                				"Invalid peer port given: " + ip + ": " + peer_port));
                                	continue;
                                }
								
								byte[] peer_peer_id;
								
								// extension - if peer id is missing then the tracker isn't sending
								// peer ids to save on bandwidth. However, we need something "unique" to 
								// work on internally so make an ID up from the ip and port
								
								if ( s_peerid == null ){
	                
									// Debug.out(ip + ": tracker did not give peerID in reply");

									peer_peer_id = getAnonymousPeerId( ip, peer_port );
									
									// System.out.println("generated peer id" + new String(peerId) + "/" + ByteFormatter.nicePrint( peerId, true ));
								}else{
								
									peer_peer_id = (byte[])s_peerid ; 
								}
								
								short 	protocol;
								
								if ( crypto_flags == null ){
									
									protocol 	= DownloadAnnounceResultPeer.PROTOCOL_NORMAL;
									
								}else{
									
									protocol = crypto_flags[i]==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
								}
								
								int		udp_port	= 0;
								int		http_port	= 0;
								
								TRTrackerAnnouncerResponsePeerImpl new_peer = 
									new TRTrackerAnnouncerResponsePeerImpl( 
										PEPeerSource.PS_BT_TRACKER, 
										peer_peer_id, 
										ip, 
										peer_port,
										udp_port,
										http_port,
										protocol,
										TRTrackerAnnouncer.AZ_TRACKER_VERSION_1,
										(short)0 );
										
								if (Logger.isEnabled())
									Logger.log(new LogEvent(torrent, LOGID,
											"NON-COMPACT PEER: " + new_peer.getString()));

								valid_meta_peers.add( new_peer );

								
							} 
						}
				    }else if ( meta_peers_peek instanceof byte[] ){
				    	
				    		// byte[] for compact returns
				    	
				    
				    	byte[]	meta_peers = (byte[])meta_peers_peek;
				    					    	
				    	int	entry_size = az_compact==1?9:6;
				    	
				    	if ( crypto_flags != null && meta_peers.length/entry_size != crypto_flags.length ){
							
							crypto_flags = null;
							
                           	if (Logger.isEnabled())
                        		Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
                        				"Invalid crypto_flags returned: length mismatch" ));
						}

				    	int	peer_number = 0;
				    	
				    	for (int i=0;i<meta_peers.length;i+=entry_size){
				    		
				    		peer_number++;
				    		
				    		int	ip1 = 0xFF & meta_peers[i];
				    		int	ip2 = 0xFF & meta_peers[i+1];
				    		int	ip3 = 0xFF & meta_peers[i+2];
				    		int	ip4 = 0xFF & meta_peers[i+3];
				    		int	po1 = 0xFF & meta_peers[i+4];
				    		int	po2 = 0xFF & meta_peers[i+5];
				    		
				    		String	ip 			= "" + ip1 + "." + ip2 + "." + ip3 + "." + ip4;
				    		int		tcp_port 	= po1*256+po2;
				    		
				    		if (tcp_port < 0 || tcp_port > 65535) {
				    			if (Logger.isEnabled())
				    				Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
				    						"Invalid compact peer port given: " + ip + ": "
				    						+ tcp_port));
				    			continue;
				    		}
                
				    		byte[]	peer_peer_id = getAnonymousPeerId( ip, tcp_port );
							
				    		short 	protocol;
				    		int		udp_port;
				    		
				    		if ( az_compact == 1 ){
				    			
					    		int	upo1 = 0xFF & meta_peers[i+6];
					    		int	upo2 = 0xFF & meta_peers[i+7];
					    		
					    		udp_port 	= upo1*256+upo2;

					    		byte	flags = meta_peers[i+8];
					    		
					    		protocol = (flags&0x01)==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
					    		
				    		}else{
				    			
				    			
								if ( crypto_flags == null ){
									
					    			protocol 	= DownloadAnnounceResultPeer.PROTOCOL_NORMAL;

								}else{
									
									protocol = crypto_flags[peer_number-1]==0?DownloadAnnounceResultPeer.PROTOCOL_NORMAL:DownloadAnnounceResultPeer.PROTOCOL_CRYPT;
								}
								
				    			udp_port	= 0;
				    		}		    		

				    		int	http_port = 0;
				    		
				    		TRTrackerAnnouncerResponsePeerImpl peer = 
				    			new TRTrackerAnnouncerResponsePeerImpl( 
			    					PEPeerSource.PS_BT_TRACKER, 
			    					peer_peer_id, 
			    					ip, 
			    					tcp_port,
			    					udp_port,
			    					http_port,
			    					protocol,
			    					TRTrackerAnnouncer.AZ_TRACKER_VERSION_1,
			    					(short)0 );
			    			
				    		if (Logger.isEnabled())
								Logger.log(new LogEvent(torrent, LOGID, "COMPACT PEER: " + peer.getString()));

				    		valid_meta_peers.add( peer );
				    	}
				    }else if ( meta_peers_peek instanceof Map ){

				    		// some trackers incorrectly return an empty Map when no peers available
				    	
				    	if (((Map)meta_peers_peek).size() != 0 ){
				    	
							throw( new IOException( "peers missing from response" ));
				    	}
				    }else{
						
						throw( new IOException( "peers missing from response" ));
				    }
				    
				    
					TRTrackerAnnouncerResponsePeerImpl[] peers=new TRTrackerAnnouncerResponsePeerImpl[valid_meta_peers.size()];
					
					valid_meta_peers.toArray(peers);
					
					addToTrackerCache( peers);
					
					TRTrackerAnnouncerResponseImpl resp = new TRTrackerAnnouncerResponseImpl( url, torrent_hash, TRTrackerAnnouncerResponse.ST_ONLINE, time_to_wait, peers );
          
						//reset failure retry interval on successful connect
					
					failure_added_time = 0;
					
					Map extensions = (Map)metaData.get( "extensions" );
					
					resp.setExtensions(extensions);
					
					if ( extensions != null ){
						
						if ( complete_l == null) {
							complete_l = (Long)extensions.get("complete");
						}
						
						if ( incomplete_l == null) {
							incomplete_l = (Long)extensions.get("incomplete");
						}
					
						if (Logger.isEnabled())
							Logger.log(new LogEvent(torrent, LOGID,
									"ANNOUNCE SCRAPE2: seeds=" + complete_l + " peers="
											+ incomplete_l));
			            
						Object	override = extensions.get( "min interval override" );
						
						if ( override != null && override instanceof Long ){
							
								// this is to allow specific torrents to be refreshed more quickly
								// if the tracker permits. Parg
							
							min_interval_override = ((Long)override).longValue();
						}
					}

					if (complete_l != null || incomplete_l != null) {

						int complete = complete_l == null ? 0 : complete_l.intValue();

						int incomplete = incomplete_l == null ? 0 : incomplete_l.intValue();

						if (complete < 0 || incomplete < 0) {
							resp.setFailurReason(MessageText.getString(
									"Tracker.announce.ignorePeerSeed",
									new String[] { (complete < 0
											? MessageText.getString("MyTorrentsView.seeds") + " == "
													+ complete + ". " : "")
											+ (incomplete < 0
													? MessageText.getString("MyTorrentsView.peers")
															+ " == " + incomplete + ". " : "") }));
						} else {

							resp.setScrapeResult( complete, incomplete );

							TRTrackerScraper scraper = TRTrackerScraperFactory.getSingleton();

							if (scraper != null) {
								TRTrackerScraperResponse scrapeResponse = scraper.scrape(this);
								if (scrapeResponse != null) {
									long lNextScrapeTime = scrapeResponse.getNextScrapeStartTime();
									long lNewNextScrapeTime = TRTrackerScraperResponseImpl.calcScrapeIntervalSecs(
											0, complete) * 1000;

									// make it look as if the scrape has just run. Important
									// as seeding rules may make calculations on when the 
									// scrape value were set

									scrapeResponse.setScrapeStartTime(SystemTime.getCurrentTime());

									if (lNextScrapeTime < lNewNextScrapeTime)
										scrapeResponse.setNextScrapeStartTime(lNewNextScrapeTime);

									scrapeResponse.setSeedsPeers(complete, incomplete);
								}
							}
						}
					}

					return (resp);  

				}catch( IOException e ){
					
						// decode could fail if the tracker's returned, say, an HTTP response
						// indicating server overload
	 				 				
	 				String	trace_data;
	 				
	 				if ( data.length <= 150 ){
	 					
	 					trace_data = new String(data);
	 					
	 				}else{
	 					
	 					trace_data = new String(data,0,150) + "...";
	 				}
	 				
	 				if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID, LogEvent.LT_ERROR,
								"TRTrackerAnnouncer::invalid reply: " + trace_data));
	 				
	 				failure_reason = "invalid reply: " + trace_data;
	 			}	 				
	 		}catch( Throwable e ){
				
	 			Debug.printStackTrace( e );
				
				failure_reason = "error: " + e.getMessage();
			}
  		}

		return( new TRTrackerAnnouncerResponseImpl( url, torrent_hash, TRTrackerAnnouncerResponse.ST_OFFLINE, getErrorRetryInterval(), failure_reason ));
  	}
  	
	protected void
	informURLChange(
		URL		old_url,
		URL		new_url,
		boolean	explicit  )
	{
		listeners.dispatch(	LDT_URL_CHANGED,
							new Object[]{old_url, new_url, new Boolean(explicit)});
	}
	
	protected void
	informURLRefresh()
	{
		listeners.dispatch( LDT_URL_REFRESH, null );		
	}
	
	public TRTrackerAnnouncerResponse
	getLastResponse()
	{
		if( last_response == null ){
			
			return new TRTrackerAnnouncerResponseImpl( null, torrent_hash, TRTrackerAnnouncerResponse.ST_OFFLINE, TRTrackerAnnouncer.REFRESH_MINIMUM_SECS, "Initialising" );
		}
		
		return( last_response );
	}
	
	public boolean
	isManual()
	{
		return( manual_control );
	}
 
	public void
	destroy()
	{       
		destroyed	= true;
		
		TRTrackerAnnouncerFactoryImpl.destroy( this );
		
		try{
			this_mon.enter();
			
			if ( current_timer_event != null ){
				
					// cancel any events that are a way off being triggered. note that
					// we don't want to cancel all events as the "stopped" event that
					// is scheduled on stop of a download may still be lurking
				
				if ( current_timer_event.getWhen() - SystemTime.getCurrentTime() > 10*1000 ){
					
					if (Logger.isEnabled())
						Logger.log(new LogEvent(torrent, LOGID,
								"Canceling announce trigger"));

					current_timer_event.cancel();
				}
			}
		}finally{
			
			this_mon.exit();
		}
	}
  
  
  /**
   * Retrieve the retry interval to use on announce errors.
   */
  protected int getErrorRetryInterval() {
    
    long currentTime = SystemTime.getCurrentTime() /1000;
        
    long diff = currentTime - failure_time_last_updated;
    
    //use previously calculated interval if it's not time to update
    if( diff < failure_added_time && !(diff < 0) ) {
      return failure_added_time;
    }

    //update previous change time
    failure_time_last_updated = currentTime;
    
    if( failure_added_time == 0 ) { //start
      failure_added_time = 10;
    }
    else if( failure_added_time < 30 ) {
      //three 10-sec retries
      failure_added_time += 10;
    }
    else if( failure_added_time < 60 ) {
      //two 15-sec retries
      failure_added_time += 15;
    }
    else if( failure_added_time < 120 ) {
      //two 30-sec retries
      failure_added_time += 30;
    }
    else if( failure_added_time < 600 ) {
      //eight 60-sec retries
      failure_added_time += 60;
    }
    else {
      //2-3min random retry 
      failure_added_time += 120 + new Random().nextInt( 60 );
    }

    boolean is_seed = (announce_data_provider == null) ? false : announce_data_provider.getRemaining() == 0;
    
    if( is_seed ) failure_added_time = failure_added_time * 2; //no need to retry as often
    
    //make sure we're not waiting longer than 30min
    if( !is_seed && failure_added_time > 1800) {
      failure_added_time = 1800;
    }
    else if ( is_seed && failure_added_time > 3600) { //or 60min if seed
      failure_added_time = 3600;
    }

    return failure_added_time;
  }
  
 
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
			// this is how the results from "external" announces get into the system
			// really should refactor so that "normal" and "external" mechanisms are
			// just instances of the same generic approach
		
		TRTrackerAnnouncerResponseImpl 	response;
		String							status;
		
		if ( result.getResponseType() == DownloadAnnounceResult.RT_ERROR ){
			
			status = MessageText.getString("PeerManager.status.error"); 
		      
			String	reason = result.getError();
	
			if ( reason != null ){
		
				status += " (" + reason + ")";		
			}
			
	  		response = new TRTrackerAnnouncerResponseImpl(
				  				result.getURL(),
				  				torrent_hash,
				  				TRTrackerAnnouncerResponse.ST_OFFLINE, 
								result.getTimeToWait(), 
								reason );
		}else{
			DownloadAnnounceResultPeer[]	ext_peers = result.getPeers();
			
			TRTrackerAnnouncerResponsePeerImpl[] peers = new TRTrackerAnnouncerResponsePeerImpl[ext_peers.length];
				
			for (int i=0;i<ext_peers.length;i++){
				
				DownloadAnnounceResultPeer	ext_peer = ext_peers[i];
				
				int		http_port	= 0;
				byte	az_version 	= TRTrackerAnnouncer.AZ_TRACKER_VERSION_1;
				
				peers[i] = new TRTrackerAnnouncerResponsePeerImpl( 
								ext_peer.getSource(),
								ext_peer.getPeerID(),
								ext_peer.getAddress(), 
								ext_peer.getPort(),
								ext_peer.getUDPPort(),
								http_port,
								ext_peer.getProtocol(),
								az_version,
								(short)0 );
				
				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID, "EXTERNAL PEER: " + peers[i].getString())); 

			}
			
			addToTrackerCache( peers);
		
			status = MessageText.getString("PeerManager.status.ok");

			response = new TRTrackerAnnouncerResponseImpl( result.getURL(), torrent_hash, TRTrackerAnnouncerResponse.ST_ONLINE, result.getTimeToWait(), peers );
		}
		
			// only make the user aware of the status if the underlying announce is
			// failing
		
		if ( 	last_response == null ||
				last_response.getStatus() != TRTrackerAnnouncerResponse.ST_ONLINE ){
			
			tracker_status_str	= status + " (" + result.getURL() + ")";
		}
		
		listeners.dispatch( LDT_TRACKER_RESPONSE, response );
	}
	
	public void 
	generateEvidence(
		IndentWriter writer )
	{
		writer.println( "BT announce:" );
		
		try{
			writer.indent();
			
			writer.println( "state: " + tracker_state + ", in_progress=" + update_in_progress );
			
			writer.println( "current: " + (lastUsedUrl==null?"null":lastUsedUrl.toString()));
			
			writer.println( "last: " + (last_response==null?"null":last_response.getString()));
			
			writer.println( "last_update_secs: " + last_update_time_secs );
			
			writer.println( "secs_to_wait: " + current_time_to_wait_secs  + (manual_control?" - manual":""));
			
			writer.println( "min_interval: " + min_interval );
			
			writer.println( "min_interval_override: " + min_interval_override );
			
			writer.println( "rd: last_override=" + rd_last_override + ",percentage=" + rd_override_percentage );
			
			writer.println( "event: " + ( current_timer_event==null?"null":current_timer_event.getString()));
			
			writer.println( "stopped: " + stopped + ", for_q=" + stopped_for_queue );
			
			writer.println( "complete: " + completed + ", reported=" + complete_reported );
			
		}finally{
			writer.exdent();
		}
	}
}