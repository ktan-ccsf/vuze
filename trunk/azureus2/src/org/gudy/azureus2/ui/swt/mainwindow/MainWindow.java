 /*
 * Created on Jun 25, 2003
 * Modified Apr 13, 2004 by Alon Rohter
 * Modified Apr 17, 2004 by Olivier Chalouhi (OSX system menu)
 * Copyright (C) 2003, 2004, 2005, 2006 Aelitis, All Rights Reserved.
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
package org.gudy.azureus2.ui.swt.mainwindow;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreException;
import com.aelitis.azureus.core.AzureusCoreListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerListener;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerListener;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.security.SESecurityManager;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.PluginEvent;
import org.gudy.azureus2.plugins.PluginView;
import org.gudy.azureus2.ui.swt.*;
import org.gudy.azureus2.ui.swt.associations.AssociationChecker;
import org.gudy.azureus2.ui.swt.components.ColorUtils;
import org.gudy.azureus2.ui.swt.components.shell.ShellManager;
import org.gudy.azureus2.ui.swt.config.wizard.ConfigureWizard;
import org.gudy.azureus2.ui.swt.donations.DonationWindow2;
import org.gudy.azureus2.ui.swt.maketorrent.NewTorrentWizard;
import org.gudy.azureus2.ui.swt.plugins.UISWTPluginView;
import org.gudy.azureus2.ui.swt.plugins.UISWTView;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEventListener;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTInstanceImpl;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewImpl;
import org.gudy.azureus2.ui.swt.sharing.progress.ProgressWindow;
import org.gudy.azureus2.ui.swt.update.UpdateWindow;
import org.gudy.azureus2.ui.swt.views.*;
import org.gudy.azureus2.ui.swt.views.stats.StatsView;
import org.gudy.azureus2.ui.swt.welcome.WelcomeWindow;
import org.gudy.azureus2.ui.swt.wizard.WizardListener;
import org.gudy.azureus2.ui.systray.SystemTraySWT;

import java.util.*;

/**
 * @author Olivier
 * Runnable : so that GUI initialization is done via asyncExec(this)
 * STProgressListener : To make it visible once initialization is done
 */
public class 
MainWindow
	extends AERunnable
	implements 	GlobalManagerListener, DownloadManagerListener, 
				ParameterListener, IconBarEnabler, AzureusCoreListener,
				AEDiagnosticsEvidenceGenerator
{
	private static final LogIDs LOGID = LogIDs.GUI;
  
  private static MainWindow window;

  private Initializer initializer;  
  private GUIUpdater updater;

  private AzureusCore			azureus_core;
  
  private GlobalManager       	globalManager;

  //NICO handle swt on macosx
  public static boolean isAlreadyDead = false;
  public static boolean isDisposeFromListener = false;  

  private Display display;
  private Shell mainWindow;
  
  private MainMenu mainMenu;
  
  private IconBar iconBar;
  
  private boolean useCustomTab;
  private Composite folder;
      
  
  /** 
   * Handles initializing and refreshing the status bar (w/help of GUIUpdater)
   */
  private MainStatusBar mainStatusBar;
  
  private TrayWindow downloadBasket;

  private SystemTraySWT systemTraySWT;
  
  private HashMap downloadViews;
  private AEMonitor	downloadViews_mon			= new AEMonitor( "MainWindow:dlviews" );

  HashMap 	downloadBars;
  AEMonitor	downloadBars_mon			= new AEMonitor( "MainWindow:dlbars" );

     
  private Tab 	mytorrents;
  private Tab 	my_tracker_tab;
  private Tab 	my_shares_tab;
  private Tab 	stats_tab;
  private Tab 	console;
  
  private Tab 			config;
  private ConfigView	config_view;
  
  protected AEMonitor	this_mon			= new AEMonitor( "MainWindow" );

  private UISWTInstanceImpl uiSWTInstanceImpl;

  private ArrayList events;

  public
  MainWindow(
  	AzureusCore		_azureus_core,
	Initializer 	_initializer,
	ArrayList events) 
  { 
  	try{
  		if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "MainWindow start"));
	    
  		AEDiagnostics.addEvidenceGenerator( this );
		
	    azureus_core	= _azureus_core;
	    
	    globalManager = azureus_core.getGlobalManager();
	    
	    initializer = _initializer;
	    
	    display = SWTThread.getInstance().getDisplay();
	    
	    window = this;
	    
	    initializer.addListener(this);
	    
	    this.events = events;
	    
	    display.syncExec(this);
	    
  	}catch( AzureusCoreException e ){
  		
  		Debug.printStackTrace( e );
  	}
  }
  
  public void runSupport() {
    try{
       
    useCustomTab = COConfigurationManager.getBooleanParameter("useCustomTab");
    

    COConfigurationManager.addParameterListener( "config.style.useSIUnits", this );
  
    mytorrents = null;
    my_tracker_tab	= null;
    console = null;
    config = null;
    config_view = null;
    downloadViews = new HashMap();
    downloadBars = new HashMap();
    
    //The Main Window
    mainWindow = new Shell(display, SWT.RESIZE | SWT.BORDER | SWT.CLOSE | SWT.MAX | SWT.MIN);
    mainWindow.setText("Azureus"); //$NON-NLS-1$
    Utils.setShellIcon(mainWindow);

    // register window
    ShellManager.sharedManager().addWindow(mainWindow);

    mainMenu = new MainMenu(this);

    try {
    	Utils.createTorrentDropTarget(mainWindow, true);
    } catch (Throwable e) {
    	Logger.log(new LogEvent(LOGID, "Drag and Drop not available", e));
    }
    

    FormLayout mainLayout = new FormLayout(); 
    FormData formData;
    
    mainLayout.marginHeight = 0;
    mainLayout.marginWidth = 0;
    try {
      mainLayout.spacing = 0;
    } catch (NoSuchFieldError e) { /* Pre SWT 3.0 */ }
    mainWindow.setLayout(mainLayout);

    Label separator = new Label(mainWindow,SWT.SEPARATOR | SWT.HORIZONTAL);
    formData = new FormData();
    formData.top = new FormAttachment(0, 0); // 2 params for Pre SWT 3.0
    formData.left = new FormAttachment(0, 0); // 2 params for Pre SWT 3.0
    formData.right = new FormAttachment(100, 0); // 2 params for Pre SWT 3.0
    separator.setLayoutData(formData);
    
    Control attachToTopOf = separator;

    try {
	    this.iconBar = new IconBar(mainWindow);
	    this.iconBar.setCurrentEnabler(this);
	    
	    formData = new FormData();
	    formData.top = new FormAttachment(attachToTopOf);
	    formData.left = new FormAttachment(0, 0); // 2 params for Pre SWT 3.0
	    formData.right = new FormAttachment(100, 0); // 2 params for Pre SWT 3.0
	    this.iconBar.setLayoutData(formData);

	    separator = new Label(mainWindow,SWT.SEPARATOR | SWT.HORIZONTAL);
	    
	    attachToTopOf = iconBar.getCoolBar();
    } catch (Exception e) {
    	Logger.log(new LogEvent(LOGID, "Creating Icon Bar", e));
    }

    formData = new FormData();
    formData.top = new FormAttachment(attachToTopOf);
    formData.left = new FormAttachment(0, 0);  // 2 params for Pre SWT 3.0
    formData.right = new FormAttachment(100, 0);  // 2 params for Pre SWT 3.0
    separator.setLayoutData(formData);
        
    if(!useCustomTab) {
      folder = new TabFolder(mainWindow, SWT.V_SCROLL);
    } else {
      folder = new CTabFolder(mainWindow, SWT.CLOSE | SWT.FLAT);
      final Color bg = ColorUtils.getShade(folder.getBackground(), (Constants.isOSX) ? -25 : -6);
      final Color fg = ColorUtils.getShade(folder.getForeground(), (Constants.isOSX) ? 25 : 6);
      folder.setBackground(bg);
      folder.setForeground(fg);
      ((CTabFolder)folder).setBorderVisible(false);
      folder.addDisposeListener(new DisposeListener() {
          public void widgetDisposed(DisposeEvent event) {
              bg.dispose();
              fg.dispose();
          }
      });
    }    
    
    Tab.setFolder(folder);
    
    folder.getDisplay().addFilter(SWT.KeyDown, new Listener() {
				public void handleEvent(Event event) {
					// Another window has control, skip filter
					Control focus_control = display.getFocusControl();
					if (focus_control != null && focus_control.getShell() != mainWindow)
						return;

					int key = event.character;
					if ((event.stateMask & SWT.MOD1) != 0 && event.character <= 26
							&& event.character > 0)
						key += 'a' - 1;

					// ESC or CTRL+F4 closes current Tab
					if (key == SWT.ESC
							|| (event.keyCode == SWT.F4 && event.stateMask == SWT.CTRL)) {
						Tab.closeCurrent();
						event.doit = false;
					} else if (event.keyCode == SWT.F6
							|| (event.character == SWT.TAB && (event.stateMask & SWT.CTRL) != 0)) {
						// F6 or Ctrl-Tab selects next Tab
						// On Windows the tab key will not reach this filter, as it is
						// processed by the traversal TRAVERSE_TAB_NEXT.  It's unknown
						// what other OSes do, so the code is here in case we get TAB
						if ((event.stateMask & SWT.SHIFT) == 0) {
							event.doit = false;
							Tab.selectNextTab(true);
							// Shift+F6 or Ctrl+Shift+Tab selects previous Tab
						} else if (event.stateMask == SWT.SHIFT) {
							Tab.selectNextTab(false);
							event.doit = false;
						}
					} else if (key == 'l' && (event.stateMask & SWT.MOD1) != 0) {
						// Ctrl-L: Open URL
						OpenTorrentWindow.invokeURLPopup(mainWindow, globalManager);
						event.doit = false;
					}
				}
			});

    SelectionAdapter selectionAdapter = new SelectionAdapter() {
      public void widgetSelected(final SelectionEvent event) {
        if(display != null && ! display.isDisposed())
        	Utils.execSWTThread(new AERunnable() {
	          public void runSupport() {
              if(useCustomTab) {
                CTabItem item = (CTabItem) event.item;
                if(item != null && ! item.isDisposed() && ! folder.isDisposed()) {
                  try {
                  ((CTabFolder)folder).setSelection(item);
                  Control control = item.getControl();
                  if (control != null) {
                    control.setVisible(true);
                    control.setFocus();
                  }
                  } catch(Throwable e) {
                  	Debug.printStackTrace( e );
                    //Do nothing
                  }
                }
              }
              if (iconBar != null)
									iconBar.setCurrentEnabler(MainWindow.this);
	          }
          });       
      }
    };
    
    if(!useCustomTab) {
      ((TabFolder)folder).addSelectionListener(selectionAdapter);
    } else {
      try {
        ((CTabFolder)folder).setMinimumCharacters( 75 );
      } catch (Exception e) {
      	Logger.log(new LogEvent(LOGID, "Can't set MIN_TAB_WIDTH", e));
      }      
      //try {
      ///  TabFolder2ListenerAdder.add((CTabFolder)folder);
      //} catch (NoClassDefFoundError e) {
        ((CTabFolder)folder).addCTabFolderListener(new CTabFolderAdapter() {          
          public void itemClosed(CTabFolderEvent event) {
            Tab.closed((CTabItem) event.item);
            event.doit = true;
            ((CTabItem) event.item).dispose();
          }
        });
      //}

      ((CTabFolder)folder).addSelectionListener(selectionAdapter);

      try {
        ((CTabFolder)folder).setSelectionBackground(
                new Color[] {display.getSystemColor(SWT.COLOR_LIST_BACKGROUND), 
                             display.getSystemColor(SWT.COLOR_LIST_BACKGROUND), 
                             display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND) },
                new int[] {10, 90}, true);
      } catch (NoSuchMethodError e) {
        /** < SWT 3.0M8 **/
        ((CTabFolder)folder).setSelectionBackground(new Color[] {display.getSystemColor(SWT.COLOR_LIST_BACKGROUND) },
                                                    new int[0]);
      }
      ((CTabFolder)folder).setSelectionForeground(display.getSystemColor(SWT.COLOR_LIST_FOREGROUND));

      try {
        /* Pre 3.0M8 doesn't have Simple-mode (it's always simple mode)
           in 3.0M9, it was called setSimpleTab(boolean)
           in 3.0RC1, it's called setSimple(boolean)
           Prepare for the future, and use setSimple()
         */
        ((CTabFolder)folder).setSimple(!COConfigurationManager.getBooleanParameter("GUI_SWT_bFancyTab"));
      } catch (NoSuchMethodError e) { 
        /** < SWT 3.0RC1 **/ 
      }
    }

    if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Initializing GUI complete"));
   
    globalManager.addListener(this);

    Utils.linkShellMetricsToConfig(mainWindow, "window");
    
    //NICO catch the dispose event from file/quit on osx
    mainWindow.addDisposeListener(new DisposeListener() {
    	public void widgetDisposed(DisposeEvent event) {
    		if (!isAlreadyDead) {
    			isDisposeFromListener = true;
    			if (mainWindow != null) {
    				mainWindow.removeDisposeListener(this);
    				dispose(false,false);
    			}
    			isAlreadyDead = true;
    		}
    	}      
    });
        
    mainWindow.addShellListener(new ShellAdapter() {
      public void 
	  shellClosed(ShellEvent event) 
      {
        if (	systemTraySWT != null &&
        		COConfigurationManager.getBooleanParameter("Enable System Tray") && 
        		COConfigurationManager.getBooleanParameter("Close To Tray", true)){
        	
          minimizeToTray(event);
        }
        else {
          event.doit = dispose(false,false);
        }
      }

      public void 
	  shellIconified(ShellEvent event) 
      {
        if ( 	systemTraySWT != null &&
        		COConfigurationManager.getBooleanParameter("Enable System Tray") &&
        		COConfigurationManager.getBooleanParameter("Minimize To Tray", false)) {
        	
          minimizeToTray(event);
        }
      }
      
    });
    
    mainWindow.addListener(SWT.Deiconify, new Listener() {
      public void handleEvent(Event e) {
        if (Constants.isOSX && COConfigurationManager.getBooleanParameter("Password enabled", false)) {
          e.doit = false;
        		mainWindow.setVisible(false);
        		PasswordWindow.showPasswordWindow(display);
        }
      }
    });
    
			mainStatusBar = new MainStatusBar();
			Composite statusBar = mainStatusBar.initStatusBar(this);
			formData = new FormData();
			formData.top = new FormAttachment(separator);
			formData.bottom = new FormAttachment(statusBar);
			formData.left = new FormAttachment(0, 0); // 2 params for Pre SWT 3.0
			formData.right = new FormAttachment(100, 0); // 2 params for Pre SWT 3.0
			folder.setLayoutData(formData);
			
			azureus_core.getPluginManager().firePluginEvent(
					PluginEvent.PEV_CONFIGURATION_WIZARD_STARTS);

			if (!COConfigurationManager
					.getBooleanParameter("Wizard Completed", false)) {
				ConfigureWizard wizard = new ConfigureWizard(getAzureusCore(), display);

				wizard.addListener(new WizardListener() {
					public void closed() {
						azureus_core.getPluginManager().firePluginEvent(
								PluginEvent.PEV_CONFIGURATION_WIZARD_COMPLETES);
					}
				});
			} else {

				azureus_core.getPluginManager().firePluginEvent(
						PluginEvent.PEV_CONFIGURATION_WIZARD_COMPLETES);
			}

			// attach the UI to plugins
			// Must be done before initializing views, since plugins may register
			// table columns and other objects
			uiSWTInstanceImpl = new UISWTInstanceImpl(azureus_core);

			showMyTorrents();

			//  share progress window

			new ProgressWindow();

			if (azureus_core.getTrackerHost().getTorrents().length > 0) {

				showMyTracker();
			}

			if (COConfigurationManager.getBooleanParameter("Open Console", false)) {
				showConsole();
			}
			events = null;

			if (COConfigurationManager.getBooleanParameter("Open Config", false)) {
				showConfig();
			}

			if (COConfigurationManager.getBooleanParameter("Open Stats On Start",
					false)) {
				showStats();
			}

			COConfigurationManager.addParameterListener("GUI_SWT_bFancyTab", this);

			updater = new GUIUpdater(this);
			updater.start();

		} catch (Throwable e) {
			System.out.println("Initialize Error");
			Debug.printStackTrace(e);
		}
  }
  
	private void showMainWindow() {
		// No tray access on OSX yet
		boolean bEnableTray = COConfigurationManager
				.getBooleanParameter("Enable System Tray")
				&& (!Constants.isOSX || SWT.getVersion() > 3230);
		boolean bPassworded = COConfigurationManager.getBooleanParameter(
				"Password enabled", false);
		boolean bStartMinimize = bEnableTray
				&& (bPassworded || COConfigurationManager.getBooleanParameter(
						"Start Minimized", false));

		if (!bStartMinimize) {
	    mainWindow.layout();
			mainWindow.open();
			if (!Constants.isOSX) {
				mainWindow.forceActive();
			}
		} else if (Constants.isOSX) {
			mainWindow.setMinimized(true);
			mainWindow.setVisible(true);
		}

		if (bEnableTray) {

			try {
				systemTraySWT = new SystemTraySWT(this);

			} catch (Throwable e) {

				Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
						"Upgrade to SWT3.0M8 or later for system tray support."));
			}

			if (bStartMinimize) {
				minimizeToTray(null);
			}
			//Only show the password if not started minimized
			//Correct bug #878227
			else {
				if (bPassworded) {
					minimizeToTray(null);
					PasswordWindow.showPasswordWindow(display);
				}
			}
		}

		COConfigurationManager.addAndFireParameterListener("Show Download Basket",
				this);

		checkForWhatsNewWindow();

		// check file associations   
		AssociationChecker.checkAssociations();
		DonationWindow2.checkForDonationPopup();
	}


  public void showMyTracker() {
  	if (my_tracker_tab == null) {
  		my_tracker_tab = new Tab(new MyTrackerView(azureus_core));
  	} else {
  		my_tracker_tab.setFocus();
  		refreshIconBar();
  	}
  }
  
  public void 
  showMyShares() 
  {
  	if (my_shares_tab == null) {
  		my_shares_tab = new Tab(new MySharesView(azureus_core));
  	} else {
  		my_shares_tab.setFocus();
  		refreshIconBar();
  	}
  }
  
  public void showMyTorrents() {
    if (mytorrents == null) {
    	MyTorrentsSuperView view = new MyTorrentsSuperView(azureus_core);
      mytorrents = new Tab(view);
    } else {
      mytorrents.setFocus();
    }
    refreshIconBar();
  }
	
  private void minimizeToTray(ShellEvent event) {
    //Added this test so that we can call this method with null parameter.
    if (event != null)
      event.doit = false;
    if(Constants.isOSX) {
      mainWindow.setMinimized(true);
    } else {  
      mainWindow.setVisible(false);
    }
    if (downloadBasket != null)
      downloadBasket.setVisible(true);
    try{
    	downloadBars_mon.enter();
      Iterator iter = downloadBars.values().iterator();
      while (iter.hasNext()) {
        MinimizedWindow mw = (MinimizedWindow) iter.next();
        mw.setVisible(true);
      }
    }finally{
    	downloadBars_mon.exit();
    }
  }
  
  private void
  updateComponents()
  {
  	if (mainStatusBar != null)
  		mainStatusBar.refreshStatusText();

  	if (folder != null) {
  		if(useCustomTab) {
  			((CTabFolder)folder).update();
  		} else {
  			((TabFolder)folder).update();
  		}
  	}
  }

  public void closeDownloadBars() {
    Utils.execSWTThread(new AERunnable() {

      public void runSupport() {
        if (display == null || display.isDisposed())
          return;

        try{
        	downloadBars_mon.enter();
        
          Iterator iter = downloadBars.keySet().iterator();
          while (iter.hasNext()) {
            DownloadManager dm = (DownloadManager) iter.next();
            MinimizedWindow mw = (MinimizedWindow) downloadBars.get(dm);
            mw.close();
            iter.remove();
          }
        }finally{
        	
        	downloadBars_mon.exit();
        }
      }

    });
  }

  public void
  destroyRequest()
  {
	  Logger.log(new LogEvent(LOGID, "MainWindow::destroyRequest"));

	  if ( COConfigurationManager.getBooleanParameter("Password enabled", false )){
		  
	  	Logger.log(new LogEvent(LOGID, "    denied - password is enabled"));

		  return;
	  }
	  
	  Utils.execSWTThread(
			new Runnable()
			{
				public void
				run()
				{
					dispose( false, false );
				}
			});
  }

	// globalmanagerlistener
	
  public void
  destroyed()
  {
  }
  
  public void
  destroyInitiated()
  {
  }				
  
  public void seedingStatusChanged( boolean seeding_only_mode ){
  }       
  
  public void 
  downloadManagerAdded(
  	final DownloadManager created) 
  {
    
    DonationWindow2.checkForDonationPopup();
      
    created.addListener(this);
  }

  public void openManagerView(DownloadManager downloadManager) {
    try{
    	downloadViews_mon.enter();
    
      if (downloadViews.containsKey(downloadManager)) {
        Tab tab = (Tab) downloadViews.get(downloadManager);
        tab.setFocus();
        refreshIconBar();
      }
      else {
        Tab tab = new Tab(new ManagerView(azureus_core, downloadManager));
        downloadViews.put(downloadManager, tab);
      }
    }finally{
    	
    	downloadViews_mon.exit();
    }
  }

  public void removeManagerView(DownloadManager downloadManager) {
    try{
    	downloadViews_mon.enter();
      
    	downloadViews.remove(downloadManager);
    }finally{
    	
    	downloadViews_mon.exit();
    }
  }

   public void downloadManagerRemoved(DownloadManager removed) {
    try{
    	downloadViews_mon.enter();
    
      if (downloadViews.containsKey(removed)) {
        final Tab tab = (Tab) downloadViews.get(removed);
        Utils.execSWTThread(new AERunnable(){
          public void runSupport() {
            if (display == null || display.isDisposed())
              return;

            tab.dispose();
          }
        });

      }
    }finally{
    	
    	downloadViews_mon.exit();
    }
  }

  public Display getDisplay() {
    return this.display;
  }

  public Shell getShell() {
    return mainWindow;
  }

  public void setVisible(boolean visible) {
    mainWindow.setVisible(visible);
    if (visible) {
      if (downloadBasket != null)
        downloadBasket.setVisible(false);
      /*
      if (trayIcon != null)
        trayIcon.showIcon();
      */
      mainWindow.forceActive();
      mainWindow.setMinimized(false);
    }
  }

  public boolean isVisible() {
    return mainWindow.isVisible();
  }

  public boolean 
  dispose(
  	boolean	for_restart,
	boolean	close_already_in_progress ) 
  {
    if(COConfigurationManager.getBooleanParameter("confirmationOnExit", false) && !getExitConfirmation(for_restart))
      return false;
    
    if(systemTraySWT != null) {
      systemTraySWT.dispose();
    }
    
    // close all tabs
    Tab.closeAllTabs();

    isAlreadyDead = true; //NICO try to never die twice...
    /*
    if (this.trayIcon != null)
      SysTrayMenu.dispose();
    */

    if(updater != null){
    	
      updater.stopIt();
    }
    
    initializer.stopIt( for_restart, close_already_in_progress );

    //NICO swt disposes the mainWindow all by itself (thanks... ;-( ) on macosx
    if(!mainWindow.isDisposed() && !isDisposeFromListener) {
    	mainWindow.dispose();
    }
      
    
    COConfigurationManager.removeParameterListener( "config.style.useSIUnits", this );
    COConfigurationManager.removeParameterListener( "Show Download Basket", this );
    COConfigurationManager.removeParameterListener( "GUI_SWT_bFancyTab", this );
    
    
    	// problem with closing down web start as AWT threads don't close properly
	if ( SystemProperties.isJavaWebStartInstance()){    	
 	
		Thread close = new AEThread( "JWS Force Terminate")
			{
				public void
				runSupport()
				{
					try{
						Thread.sleep(2500);
						
					}catch( Throwable e ){
						
						Debug.printStackTrace( e );
					}
					
					SESecurityManager.exitVM(1);
				}
			};
			
		close.setDaemon(true);
		
		close.start();
    	
    }
    
    return true;
  }

  /**
   * @return true, if the user choosed OK in the exit dialog
   *
   * @author Rene Leonhardt
   */
  private boolean 
  getExitConfirmation(
  	boolean	for_restart) {
    MessageBox mb = new MessageBox(mainWindow, SWT.ICON_WARNING | SWT.YES | SWT.NO);
    
    mb.setText(MessageText.getString(
    		for_restart?"MainWindow.dialog.restartconfirmation.title":"MainWindow.dialog.exitconfirmation.title"));
    
    mb.setMessage(MessageText.getString(
    		for_restart?"MainWindow.dialog.restartconfirmation.text":"MainWindow.dialog.exitconfirmation.text"));
    if(mb.open() == SWT.YES)
      return true;
    return false;
  }

  public GlobalManager getGlobalManager() {
    return globalManager;
  }

  /**
	 * @return
	 */
  public Tab getConsole() {
    return console;
  }

  /**
	 * @return
	 */
  public Tab getMytorrents() {
	return mytorrents;
  }
  
  public Tab getMyTracker() {
	return my_tracker_tab;
  }

  /**
	 * @param tab
	 */
  public void setConsole(Tab tab) {
    console = tab;
  }

  /**
	 * @param tab
	 */
  public void setMytorrents(Tab tab) {
	mytorrents = tab;
  }
  
  public void setMyTracker(Tab tab) {
  	my_tracker_tab = tab;
  }
  
  public void setMyShares(Tab tab) {
  	my_shares_tab = tab;
  }
  
  /**
	 * @return
	 */
  public static MainWindow getWindow() {
    return window;
  }

  /**
	 * @return
	 */
  public HashMap getDownloadBars() {
    return downloadBars;
  }

  /**
	 * @param tab
	 */
  public void clearConfig() {
    config 		= null;
    config_view	= null;
  }

  /**
   * @param tab
   */
  public void clearStats() {
    stats_tab = null;
  }

  /**
	 * @return
	 */
  public TrayWindow getTray() {
    return downloadBasket;
  }



  /**
   * @return Returns the useCustomTab.
   */
  public boolean isUseCustomTab() {
    return useCustomTab;
  }    
  
  
  
  
  Map pluginTabs = new HashMap();
  

  
  public void 
  openPluginView(
	PluginView view) 
  {
	  openPluginView( view, view.getPluginViewName());
  }
  
  public void 
  openPluginView(
	UISWTPluginView view) 
  {
	  openPluginView( view, view.getPluginViewName());
  }
  
  public void openPluginView(String sParentID, String sViewID, UISWTViewEventListener l,
			Object dataSource, boolean bSetFocus) {
  	
  	UISWTViewImpl view = null;
  	try {
  		view = new UISWTViewImpl(sParentID, sViewID, l);
  	} catch (Exception e) {
  		Tab tab = (Tab) pluginTabs.get(sViewID);
  		if (tab != null) {
  			tab.setFocus();
  		}
			return;
  	}
		view.dataSourceChanged(dataSource);

		Tab tab = new Tab(view, bSetFocus);

 		pluginTabs.put(sViewID, tab);
	}
  
  /**
   * Close all plugin views with the specified ID
   * 
   * @param sViewID
   */
  public void closePluginViews(String sViewID) {
  	Item[] items;

		if (folder instanceof CTabFolder)
			items = ((CTabFolder) folder).getItems();
		else if (folder instanceof TabFolder)
			items = ((TabFolder) folder).getItems();
		else
			return;

		for (int i = 0; i < items.length; i++) {
			IView view = Tab.getView(items[i]);
			if (view instanceof UISWTViewImpl) {
				String sID = ((UISWTViewImpl) view).getViewID();
				if (sID != null && sID.equals(sViewID)) {
					try {
						closePluginView(view);
					} catch (Exception e) {
						Debug.printStackTrace(e);
					}
				}
			}
		} // for
  }
  
  /**
   * Get all open Plugin Views
   * 
   * @return open plugin views
   */
  public UISWTView[] getPluginViews() {
  	Item[] items;

		if (folder instanceof CTabFolder)
			items = ((CTabFolder) folder).getItems();
		else if (folder instanceof TabFolder)
			items = ((TabFolder) folder).getItems();
		else
			return new UISWTView[0];

		ArrayList views = new ArrayList();
		
		for (int i = 0; i < items.length; i++) {
			IView view = Tab.getView(items[i]);
			if (view instanceof UISWTViewImpl) {
				views.add(view);
			}
		} // for
		
		return (UISWTView[])views.toArray(new UISWTView[0]);
  }

  protected void 
  openPluginView(
	AbstractIView 	view,
	String			name )
  {
    Tab tab = (Tab) pluginTabs.get(name);
    if(tab != null) {
      tab.setFocus();
    } else {
      tab = new Tab(view);
      pluginTabs.put(name,tab);         
    }
  }
  
  public void 
  closePluginView( 
	IView	view) 
  {
	  Item	tab = Tab.getTab( view );
	  
	  if ( tab != null ){
		  
		  Tab.closed( tab );
	  }
  }
  
  public void removeActivePluginView( String view_name ) {
    pluginTabs.remove(view_name);
  }
  
 


  
  public void parameterChanged(String parameterName) {
    if( parameterName.equals( "Show Download Basket" ) ) {
      if (COConfigurationManager.getBooleanParameter("Show Download Basket")) {
        if(downloadBasket == null) {
          downloadBasket = new TrayWindow(this);
          downloadBasket.setVisible(true);
        }
      } else if(downloadBasket != null) {
        downloadBasket.setVisible(false);
        downloadBasket = null;
      }
    }
    
    if( parameterName.equals( "GUI_SWT_bFancyTab" ) && 
        folder instanceof CTabFolder && 
        folder != null && !folder.isDisposed()) {
      try {
        ((CTabFolder)folder).setSimple(!COConfigurationManager.getBooleanParameter("GUI_SWT_bFancyTab"));
      } catch (NoSuchMethodError e) { 
        /** < SWT 3.0RC1 **/ 
      }
    }
    
    if( parameterName.equals( "config.style.useSIUnits" ) ) {
      updateComponents();
    }
  }
  
 


  public boolean isEnabled(String itemKey) {
    if(itemKey.equals("open"))
      return true;
    if(itemKey.equals("new"))
      return true;
    IView currentView = getCurrentView();
    if(currentView != null)
      return currentView.isEnabled(itemKey);
    return false;
  }

  public boolean isSelected(String itemKey) {   
    return false;
  }

  public void itemActivated(String itemKey) {   
    if(itemKey.equals("open")) {        
     TorrentOpener.openTorrentWindow();
     return;
    }
    if(itemKey.equals("new")) {
      new NewTorrentWizard(getAzureusCore(),display);
      return;
    }
    IView currentView = getCurrentView();
    if(currentView != null)
      currentView.itemActivated(itemKey);    
  }
  
  IView getCurrentView() {
	  try {
	    if(!useCustomTab) {
	      TabItem[] selection = ((TabFolder)folder).getSelection();
				if(selection.length > 0)  {
				  return Tab.getView(selection[0]);
				}
			  return null;
	    }
      return Tab.getView(((CTabFolder)folder).getSelection());
	  }
	  catch (Exception e) {
	    return null;
	  }
  }

	public void refreshIconBar() {
		if (iconBar != null)
			iconBar.setCurrentEnabler(this);
	}

  public void close() {
      getShell().close();
  }

  public void closeViewOrWindow() {
      if(getCurrentView() != null)
        Tab.closeCurrent();
      else
          close();
  }

  public ConfigView showConfig() {
    if (config == null){
      config_view = new ConfigView( azureus_core );
      config = new Tab(config_view);
    }else{
      config.setFocus();
    }
    return config_view;
  }
  

  public boolean showConfig(String id) {
    if (config == null){
      config_view = new ConfigView( azureus_core );
      config = new Tab(config_view);
    }else{
      config.setFocus();
    }
    return config_view.selectSection(id);
  }
  

  
  public void showConsole() {
    if (console == null)
      console = new Tab(new LoggerView(events));
    else
      console.setFocus();
  }
  
  public void showStats() {
    if (stats_tab == null)
      stats_tab = new Tab(new StatsView(globalManager,azureus_core));
    else
      stats_tab.setFocus();
  }

  public void showStatsDHT() {
    if (stats_tab == null)
      stats_tab = new Tab(new StatsView(globalManager,azureus_core));
    else
      stats_tab.setFocus();
		((StatsView) stats_tab.getView()).showDHT();
  }
  
  public void showStatsTransfers() {
    if (stats_tab == null)
      stats_tab = new Tab(new StatsView(globalManager,azureus_core));
    else
      stats_tab.setFocus();
		((StatsView) stats_tab.getView()).showTransfers();
  }

  public void setSelectedLanguageItem() 
  {
  	try{
  		this_mon.enter();
  	
	    Messages.updateLanguageForControl(mainWindow.getShell());
	    
	    if ( systemTraySWT != null ){
	    	Messages.updateLanguageForControl(systemTraySWT.getMenu());
	    }
	    
	  	if (mainStatusBar != null) {
	  		mainStatusBar.refreshStatusText();
  		}

	    
	    if (folder != null) {
	      if(useCustomTab) {
	        ((CTabFolder)folder).update();
	      } else {
	        ((TabFolder)folder).update();
	      }
	    }
	
	    if (downloadBasket != null){
	      downloadBasket.updateLanguage();
	    }
	    
	    Tab.updateLanguage();
	  
	  	if (mainStatusBar != null) {
	  		mainStatusBar.updateStatusText();
  		}
  	}finally{
  		
  		this_mon.exit();
  	}
  }
  
  public MainMenu getMenu() {
    return mainMenu;
  }
  
  /*
   * STProgressListener implementation, used for startup.
   */
  // AzureusCoreListener
  public void reportCurrentTask(String task) {}
  
  /**
   * A percent > 100 means the end of the startup process
   */
  // AzureusCoreListener
  public void reportPercent(int percent) {
    if(percent > 100) {
      Utils.execSWTThread(new AERunnable(){
        public void runSupport() {
          if(display == null || display.isDisposed())
            return;
          showMainWindow();
        }
      });
    }
  }
  

  
    
  /**
   * MUST be called by the SWT Thread
   * @param updateWindow the updateWindow or null if no update is available
   */
  public void setUpdateNeeded(UpdateWindow updateWindow) {
    if (mainStatusBar != null) {
    	mainStatusBar.setUpdateNeeded(updateWindow);
    }
  }
  
  //DownloadManagerListener implementation

  public void completionChanged(DownloadManager manager, boolean bCompleted) {
    // Do Nothing
  }
  
  public void downloadComplete(DownloadManager manager) {
    // Do Nothing

  }

  public void positionChanged(DownloadManager download, int oldPosition,
      int newPosition) {
    // Do Nothing

  }

  public void stateChanged(final DownloadManager manager, int state) {
    // if state == STARTED, then open the details window (according to config)
    if(state == DownloadManager.STATE_DOWNLOADING || state == DownloadManager.STATE_SEEDING) {
        if(display != null && !display.isDisposed()) {
        	Utils.execSWTThread(new AERunnable() {
            public void runSupport() {
            	if (display == null || display.isDisposed())
            		return;

              if (COConfigurationManager.getBooleanParameter("Open Details",false)) {
                openManagerView(manager);
              }
              
              if (COConfigurationManager.getBooleanParameter("Open Bar", false)) {
                try{
                	downloadBars_mon.enter();
                
                	if(downloadBars.get(manager) == null) {
                	  MinimizedWindow mw = new MinimizedWindow(manager, mainWindow);
                	
                	  downloadBars.put(manager, mw);
                	}
                }finally{
                	
                	downloadBars_mon.exit();
                }
              }
            }
          });
        }
    }
  }
  
  public AzureusCore
  getAzureusCore()
  {
  	return( azureus_core );
  }
  
  
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "SWT UI" );
		
		try{
			writer.indent();
		
			writer.println( "SWT Version:" + SWT.getVersion() + "/" + SWT.getPlatform());
			
			writer.println( "MyTorrents" );
		
			try{
				writer.indent();

				Tab	t = mytorrents;
				
				if ( t != null ){
					
					t.generateDiagnostics( writer );
				}
			}finally{
				
				writer.exdent();
			}
			
			writer.println( "MyTracker" );
			
			try{
				writer.indent();

				Tab	t = my_tracker_tab;
				
				if ( t != null ){
					
					t.generateDiagnostics( writer );
				}
			}finally{
				
				writer.exdent();
			}
			
			writer.println( "MyShares" );
			
			try{
				writer.indent();

				Tab	t = my_shares_tab;
				
				if ( t != null ){
					
					t.generateDiagnostics( writer );
				}
			}finally{
				
				writer.exdent();
			}
		}finally{
			
			writer.exdent();
		}
	}
  
  private void checkForWhatsNewWindow() {
    try {
      int version = WelcomeWindow.WELCOME_VERSION;
      int latestDisplayed = COConfigurationManager.getIntParameter("welcome.version.lastshown",0);
      if(latestDisplayed < version) {
        new WelcomeWindow();
        COConfigurationManager.setParameter("welcome.version.lastshown",version);
        COConfigurationManager.save();
      }      
    } catch(Exception e) {
      //DOo Nothing
    }    
  }
  
  public UISWTInstanceImpl getUISWTInstanceImpl() {
  	return uiSWTInstanceImpl;
  }

	/**
	 * @param string
	 */
	public void setStatusText(String string) {
		// TODO Auto-generated method stub
		if (mainStatusBar != null)
			mainStatusBar.setStatusText(string);
	}

	public SystemTraySWT getSystemTraySWT() {
		return systemTraySWT;
	}

	public MainStatusBar getMainStatusBar() {
		return mainStatusBar;
	}
}
