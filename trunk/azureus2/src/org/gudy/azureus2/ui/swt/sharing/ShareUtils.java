/*
 * File    : ShareUtils.java
 * Created : 08-Jan-2004
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

package org.gudy.azureus2.ui.swt.sharing;

/**
 * @author parg
 *
 */

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

import com.aelitis.azureus.core.*;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.*;

import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;

public class 
ShareUtils 
{
	public static void
	shareFile(
		final AzureusCore	azureus_core,
		final Shell			shell )
	{
		new AEThread("shareFile")
		{
			public void
			run()
			{
				Display display = shell.getDisplay();
				final String[] path = { null };
				final AESemaphore	sem = new AESemaphore("ShareUtils:file");
        
					
				display.asyncExec(new Runnable() {
					public void run()
					{
						try{
							FileDialog dialog = new FileDialog(shell, SWT.SYSTEM_MODAL | SWT.OPEN);
							
							dialog.setFilterPath( TorrentOpener.getFilterPathData() );
													
							dialog.setText(MessageText.getString("MainWindow.dialog.share.sharefile"));
							
              path[0] = TorrentOpener.setFilterPathData( dialog.open() );

						}finally{
							
							sem.release();
						}
					}
				});
				
				sem.reserve();
				
				if ( path[0] != null ){
					
					shareFile( azureus_core, path[0] );
				}
			}
		}.start();
	}

	public static void
	shareDir(
		AzureusCore	azureus_core,
		Shell		shell )
	{
		shareDirSupport( azureus_core, shell, false, false );
	}
	
	public static void
	shareDirContents(
		AzureusCore	azureus_core,
		Shell		shell,
		boolean		recursive )
	{
		shareDirSupport( azureus_core, shell, true, recursive );
	}
	
	protected static void
	shareDirSupport(
		final AzureusCore	azureus_core,
		final Shell			shell,
		final boolean		contents,
		final boolean		recursive )
	{
		new AEThread("shareDirSupport")
		{
			public void
			run()
			{
				Display display = shell.getDisplay();
				final String[] path = { null };
				final AESemaphore	sem = new AESemaphore("ShareUtils:dir");
				
				display.asyncExec(new Runnable() {
					public void run()
					{
						try{
							DirectoryDialog dialog = new DirectoryDialog(shell, SWT.SYSTEM_MODAL);
							
							dialog.setFilterPath( TorrentOpener.getFilterPathData() );
							
							dialog.setText( 
										contents?
										MessageText.getString("MainWindow.dialog.share.sharedircontents") + 
												(recursive?"("+MessageText.getString("MainWindow.dialog.share.sharedircontents.recursive")+")":""):
										MessageText.getString("MainWindow.dialog.share.sharedir"));
							
							path[0] = TorrentOpener.setFilterPathData( dialog.open() );

						}finally{
							
							sem.release();
						}
					}
				});
				
				sem.reserve();
				
				if ( path[0] != null ){
					
					if ( contents ){
						
						shareDirContents( azureus_core, path[0], recursive );
						
					}else{
						
						shareDir( azureus_core, path[0] );
					}
				}
			}
		}.start();
	}
	
	public static void
	shareFile(
		final AzureusCore	azureus_core,
		final String		file_name )
	{
		new AEThread("shareFile")
		{
			public void
			run()
			{
				try{
					azureus_core.getPluginManager().getDefaultPluginInterface().getShareManager().addFile(new File(file_name));
					
				}catch( Throwable e ){
					
					Debug.printStackTrace( e );
				}
			}
		}.start();
	}

	public static void
	shareDir(
		final AzureusCore	azureus_core,
		final String		file_name )
	{
		new AEThread("shareDir")
		{
			public void
			run()
			{
				try{
					azureus_core.getPluginManager().getDefaultPluginInterface().getShareManager().addDir(new File(file_name));
					
				}catch( Throwable e ){
					
					Debug.printStackTrace( e );
				}
			}
		}.start();
	}
	
	public static void
	shareDirContents(
		final AzureusCore	azureus_core,
		final String		file_name,
		final boolean		recursive )
	{
		new AEThread("shareDirCntents")
		{
			public void
			run()
			{
				try{
					azureus_core.getPluginManager().getDefaultPluginInterface().getShareManager().addDirContents(new File(file_name), recursive);
			
				}catch( Throwable e ){
					
					Debug.printStackTrace( e );
				}
			}
		}.start();
	}
}
