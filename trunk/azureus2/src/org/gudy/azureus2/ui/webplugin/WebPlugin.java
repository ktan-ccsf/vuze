/*
 * File    : WebPlugin.java
 * Created : 23-Jan-2004
 * By      : parg
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

package org.gudy.azureus2.ui.webplugin;

/**
 * @author parg
 *
 */

import java.io.*;
import java.util.*;
//import java.net.*;

import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.logging.*;
import org.gudy.azureus2.plugins.tracker.*;
import org.gudy.azureus2.plugins.tracker.web.*;


import HTML.Template;

public class 
WebPlugin
	implements Plugin, TrackerWebPageGenerator
{
	public static final String DEFAULT_PORT		= "8089";
	public static final String DEFAULT_PROTOCOL	= "HTTP";
	
	protected static final String	NL			= "\r\n";
	
	protected static final String[]		welcome_pages = {"index.html", "index.htm", "index.php", "index.tmpl" };
	protected static File[]				welcome_files;
	
	protected PluginInterface		plugin_interface;
	protected LoggerChannel			log;
	protected Tracker				tracker;
	
	protected String				file_root;
	
	public void 
	initialize(
		PluginInterface _plugin_interface )
	{	
		plugin_interface	= _plugin_interface;
		
		log = plugin_interface.getLogger().getChannel("SeedingRules");
		
		
		tracker = plugin_interface.getTracker();
		
		file_root = FileUtil.getApplicationPath() + "web";

		welcome_files = new File[welcome_pages.length];
		
		for (int i=0;i<welcome_pages.length;i++){
			
			welcome_files[i] = new File( file_root + File.separator + welcome_pages[i] );
		}
		
		Properties	props = plugin_interface.getPluginProperties();
					
		int port	= Integer.parseInt( props.getProperty( "port", DEFAULT_PORT ));

		String	protocol_str = props.getProperty( "protocol", DEFAULT_PROTOCOL );
		
		int	protocol = protocol_str.equalsIgnoreCase( "HTTP")?
							Tracker.PR_HTTP:Tracker.PR_HTTPS;
	
		log.log( LoggerChannel.LT_INFORMATION, "WebPlugin Initialisation: port = " + port + ", protocol = " + protocol_str  );
		
		try{
			TrackerWebContext	context = tracker.createWebContext( port, protocol );
		
			context.addPageGenerator( this );
			
		}catch( TrackerException e ){
			
			log.log( "Plugin Initialisation Fails", e );
		}
	}
	
	public boolean
	generate(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )
	
	throws IOException
	{
		OutputStream os = response.getOutputStream();
		
		PrintWriter	pw = new PrintWriter(new OutputStreamWriter(os));
		
		pw.println( "Under Construction!!!!");
		
		pw.flush();
		
		return( true );
	}
}
