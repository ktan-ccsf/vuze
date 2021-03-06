/*
 * Created on 2 juil. 2003
 *
 * Copyright (C) 2004, 2005, 2006 Aelitis SAS, All rights Reserved
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
 *
 * AELITIS, SAS au capital de 46,603.30 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package org.gudy.azureus2.ui.swt.views;

import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerTPSListener;
import org.gudy.azureus2.core3.util.AERunnable;

import org.gudy.azureus2.ui.swt.TorrentUtil;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewSWTImpl;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewTab;
import org.gudy.azureus2.ui.swt.views.tableitems.tracker.*;
import org.gudy.azureus2.ui.swt.views.utils.ManagerUtils;

import com.aelitis.azureus.core.tracker.TrackerPeerSource;
import com.aelitis.azureus.ui.common.table.TableColumnCore;
import com.aelitis.azureus.ui.common.table.TableDataSourceChangedListener;
import com.aelitis.azureus.ui.common.table.TableLifeCycleListener;

import org.gudy.azureus2.plugins.ui.tables.TableManager;



public class TrackerView 
	extends TableViewTab<TrackerPeerSource>
	implements TableLifeCycleListener, TableDataSourceChangedListener, DownloadManagerTPSListener
{
	private final static TableColumnCore[] basicItems = {
		new TypeItem(),
		new NameItem(),
		new StatusItem(),
		new PeersItem(),
		new SeedsItem(),
		new LeechersItem(),
		new UpdateInItem(),
		new IntervalItem(),
	};

	DownloadManager manager;
  
	private TableViewSWTImpl<TrackerPeerSource> tv;

	private ScrapeInfoView scrapeInfoView;
 
	/**
	 * Initialize
	 *
	 */
	public TrackerView() {
		super("TrackerView");
	}

	public TableViewSWT<TrackerPeerSource>
	initYourTableView() 
	{
		tv = new TableViewSWTImpl<TrackerPeerSource>(
				TrackerPeerSource.class,
				TableManager.TABLE_TORRENT_TRACKERS, 
				getPropertiesPrefix(), 
				basicItems,
				basicItems[0].getName(), 
				SWT.SINGLE | SWT.FULL_SELECTION | SWT.VIRTUAL );

		tv.addLifeCycleListener(this);
		tv.addTableDataSourceChangedListener(this, true);
		tv.setEnableTabViews(true);
		scrapeInfoView = new ScrapeInfoView(manager);
		tv.setCoreTabViews(new IView[] {
			scrapeInfoView
		});
		return tv;
	}

	public void 
	trackerPeerSourcesChanged() 
	{
		Utils.execSWTThread(
			new AERunnable() 
			{
				public void
				runSupport()
				{
					if ( manager == null || tv.isDisposed()){
						
						return;
					}
					
					tv.removeAllTableRows();
					
					addExistingDatasources();
				}
			});
	}
	
	public void 
	tableDataSourceChanged(
		Object newDataSource ) 
	{
	  	if ( manager != null ){
	  		
	  		manager.removeTPSListener( this );
		}
	
		if ( newDataSource == null ){
			
			manager = null;
			
		}else if ( newDataSource instanceof Object[] ){
		
			manager = (DownloadManager)((Object[])newDataSource)[0];
			
		}else{
			
			manager = (DownloadManager)newDataSource;
		}
		
	  	if ( manager != null && !tv.isDisposed()){
	    	
  			manager.addTPSListener( this );
	  		
	    	addExistingDatasources();
	    }
	  if (scrapeInfoView != null) {
	  	scrapeInfoView.setDownlaodManager(manager);
	  }
	}
	
	public void 
	tableViewInitialized() 
	{
		if ( manager != null ){

			addExistingDatasources();
		}
    }

	public void 
	tableViewDestroyed() 
	{
		if ( manager != null ){
			
			manager.removeTPSListener( this );
		}
	}

	private void 
	addExistingDatasources() 
	{
		if ( manager == null || tv.isDisposed()){
			
			return;
		}

		List<TrackerPeerSource> tps = manager.getTrackerPeerSources();
		
		tv.addDataSources( tps.toArray( (new TrackerPeerSource[tps.size()])));
		
		tv.processDataSourceQueue();
	}
	
	public boolean toolBarItemActivated(String itemKey) {

		if ( super.toolBarItemActivated(itemKey)){
			return( true );
		}
		
		if (itemKey.equals("run")) {
			ManagerUtils.run(manager);
			return true;
		}
		
		if (itemKey.equals("start")) {
			ManagerUtils.queue(manager, getComposite().getShell());
			return true;
		}
		
		if (itemKey.equals("stop")) {
			ManagerUtils.stop(manager, getComposite().getShell());
			return true;
		}
		
		if (itemKey.equals("remove")) {
			TorrentUtil.removeDownloads(new DownloadManager[] {
				manager
			}, null);
			return true;
		}
		return false;
	}
	
	public void refreshToolBar(Map<String, Boolean> list) {
		list.put("run", true);
		list.put("start", ManagerUtils.isStartable(manager));
		list.put("stop", ManagerUtils.isStopable(manager));
		list.put("remove", true);
		
		super.refreshToolBar(list);
	}
}
