/*
 * Created on 25 juin 2003
 *
 */
package org.gudy.azureus2.ui.swt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderAdapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;
import org.gudy.azureus2.core.BDecoder;
import org.gudy.azureus2.core.ConfigurationManager;
import org.gudy.azureus2.core.DownloadManager;
import org.gudy.azureus2.core.GlobalManager;
import org.gudy.azureus2.core.LocaleUtil;
import org.gudy.azureus2.core.MessageText;
import org.gudy.azureus2.ui.systray.SystemTray;

import snoozesoft.systray4j.SysTrayMenu;

/**
 * @author Olivier
 * 
 */
public class MainWindow implements IComponentListener {

  private static final String VERSION = "2.0.1.1"; //$NON-NLS-1$
  private String latestVersion = ""; //$NON-NLS-1$

  private static MainWindow window;
  
  private static boolean jarDownloaded = false;
  private static boolean updateJar = false;

  private GUIUpdater updater;

  private static int instanceCount = 0;

  private Display display;
  private Shell mainWindow;
  private Menu menuBar;

  protected static final Color[] blues = new Color[5];
  public static Color black;
  public static Color blue;
  protected static Color grey;
  public static Color red;
  public static Color white;
  protected static Color red_ConsoleView;
  protected static Color red_ManagerItem;

  private CTabFolder folder;
  private CLabel statusText;
  private CLabel statusDown;
  private CLabel statusUp;

  private GlobalManager globalManager;

  private Tab mytorrents;
  private Tab console;
  private Tab config;

  private MenuItem selectedLanguageItem;

  private TrayWindow tray;
  private SystemTray trayIcon;

  private HashMap downloadViews;
  private HashMap downloadBars;

  private StartServer startServer;

  private class GUIUpdater extends Thread {
    boolean finished = false;
    static final int waitTime = 250;
    IView view;

    public GUIUpdater() {
      super("GUI updater"); //$NON-NLS-1$
      setPriority(Thread.MIN_PRIORITY);
    }

    public void run() {
      while (!finished) {
        update();
        try {
          Thread.sleep(waitTime);
        }
        catch (Exception ignore) {
        }
      }
    }

    private void update() {
      if(display != null && !display.isDisposed())
      display.asyncExec(new Runnable() {
        public void run() {
          if (!mainWindow.isDisposed() && mainWindow.isVisible() && !mainWindow.getMinimized()) {
            try {
              view = Tab.getView(folder.getSelection());
            }
            catch (Exception e) {
              view = null;
            }
            if(view != null) {
              view.refresh();
              Tab.refresh();
            }
            statusDown.setText("D: " + globalManager.getDownloadSpeed()); //$NON-NLS-1$
            statusUp.setText("U: " + globalManager.getUploadSpeed()); //$NON-NLS-1$
          }
          if (trayIcon != null)
            trayIcon.refresh();
          synchronized (downloadBars) {
            Iterator iter = downloadBars.values().iterator();
            while (iter.hasNext()) {
              MinimizedWindow mw = (MinimizedWindow) iter.next();
              mw.refresh();
            }
          }
        }
      });
    }

    public void stopIt() {
      finished = true;
    }
  }

  private class VersionChecker extends Thread {
    /* (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    public void run() {
      ByteArrayOutputStream message = new ByteArrayOutputStream(); //$NON-NLS-1$

      int nbRead = 0;
      HttpURLConnection con = null; 
      InputStream is = null;
      try {
        URL reqUrl = new URL("http://azureus.sourceforge.net/version.php"); //$NON-NLS-1$
        con = (HttpURLConnection) reqUrl.openConnection();
        con.connect();
        is = con.getInputStream();
//        int length = con.getContentLength();
//        System.out.println(length);
        byte[] data = new byte[1024];
        while (nbRead >= 0) {
          nbRead = is.read(data);
          if (nbRead >= 0) {
            message.write(data, 0, nbRead);
          }
        }
        Map decoded = BDecoder.decode(message.toByteArray());
        latestVersion = new String((byte[])decoded.get("version")); //$NON-NLS-1$

        if (display == null || display.isDisposed())
          return;
        display.asyncExec(new Runnable() {
          public void run() {
            if (statusText.isDisposed())
              return;
            setStatusVersion();
            if(!VERSION.equals(latestVersion))
              showUpgradeWindow();
          }
        });
      } catch (Exception e) {
        if (display == null || display.isDisposed())
          return;
        display.asyncExec(new Runnable() {
          public void run() {
            if (statusText.isDisposed())
              return;
            latestVersion = MessageText.getString("MainWindow.status.unknown"); //$NON-NLS-1$
            setStatusVersion();
          }
        });
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (IOException e1) {
          }
          is = null;
        }
        if(con != null) {
          con.disconnect();
          con = null;
        }
      }
    }
  }

  public MainWindow(GlobalManager gm, StartServer server) {
    if (window != null)
      return;

    window = this;
    this.startServer = server;
    this.globalManager = gm;
    mytorrents = null;
    console = null;
    config = null;
    downloadViews = new HashMap();
    downloadBars = new HashMap();
    //The display
    display = new Display();
    ImageRepository.loadImages(display);

    if(instanceCount == 0) {
      blues[4] = new Color(display, new RGB(0, 128, 255));
      blues[3] = new Color(display, new RGB(64, 160, 255));
      blues[2] = new Color(display, new RGB(128, 192, 255));
      blues[1] = new Color(display, new RGB(192, 224, 255));
      blues[0] = new Color(display, new RGB(255, 255, 255));
      black = new Color(display, new RGB(0, 0, 0));
      blue = new Color(display, new RGB(128, 128, 255));
      grey = new Color(display, new RGB(170, 170, 170));
      red = new Color(display, new RGB(255, 0, 0));
      white = new Color(display, new RGB(255, 255, 255));
      red_ConsoleView = new Color(display, new RGB(255, 192, 192));
      red_ManagerItem = new Color(display, new RGB(255, 68, 68));
    }
    instanceCount++;

    //The Main Window    
    mainWindow = new Shell(display, SWT.RESIZE | SWT.BORDER | SWT.CLOSE | SWT.MAX | SWT.MIN);
    mainWindow.setText("Azureus"); //$NON-NLS-1$
    mainWindow.setImage(ImageRepository.getImage("azureus")); //$NON-NLS-1$
    //The Main Menu
    menuBar = new Menu(mainWindow, SWT.BAR);
    mainWindow.setMenuBar(menuBar);
    //The File Menu
    MenuItem fileItem = new MenuItem(menuBar, SWT.CASCADE);
    Messages.setLanguageText(fileItem, "MainWindow.menu.file"); //$NON-NLS-1$
    Menu fileMenu = new Menu(mainWindow, SWT.DROP_DOWN);
    fileItem.setMenu(fileMenu);
    MenuItem file_new = new MenuItem(fileMenu, SWT.CASCADE);
    Messages.setLanguageText(file_new, "MainWindow.menu.file.open"); //$NON-NLS-1$
    new MenuItem(fileMenu, SWT.SEPARATOR);
    MenuItem file_exit = new MenuItem(fileMenu, SWT.NULL);
    Messages.setLanguageText(file_exit, "MainWindow.menu.file.exit"); //$NON-NLS-1$

    Menu newMenu = new Menu(mainWindow, SWT.DROP_DOWN);
    file_new.setMenu(newMenu);
    MenuItem file_new_torrent = new MenuItem(newMenu, SWT.NULL);
    Messages.setLanguageText(file_new_torrent, "MainWindow.menu.file.open.torrent"); //$NON-NLS-1$
    file_new_torrent.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        FileDialog fDialog = new FileDialog(mainWindow, SWT.OPEN | SWT.MULTI);
        fDialog.setFilterExtensions(new String[] { "*.torrent" }); //$NON-NLS-1$
        fDialog.setFilterNames(new String[] { "*.torrent" }); //$NON-NLS-1$
        fDialog.setText(MessageText.getString("MainWindow.dialog.choose.file")); //$NON-NLS-1$
        String fileName = fDialog.open();
        if (fileName == null)
          return;
        openTorrents(fDialog.getFilterPath(), fDialog.getFileNames());
      }
    });
    // MenuItem file_new_url = new MenuItem(newMenu,SWT.NULL);
    //file_new_url.setText("URL");
    MenuItem file_new_folder = new MenuItem(newMenu, SWT.NULL);
    Messages.setLanguageText(file_new_folder, "MainWindow.menu.file.folder"); //$NON-NLS-1$
    file_new_folder.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        DirectoryDialog fDialog = new DirectoryDialog(mainWindow, SWT.NULL);
        fDialog.setText(MessageText.getString("MainWindow.dialog.choose.folder")); //$NON-NLS-1$
        String fileName = fDialog.open();
        if (fileName == null)
          return;
        openTorrentsFromDirectory(fileName);
      }
    });

    file_exit.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        dispose();
      }
    });

    //The View Menu
    MenuItem viewItem = new MenuItem(menuBar, SWT.CASCADE);
    Messages.setLanguageText(viewItem, "MainWindow.menu.view"); //$NON-NLS-1$
    Menu viewMenu = new Menu(mainWindow, SWT.DROP_DOWN);
    viewItem.setMenu(viewMenu);

    MenuItem view_torrents = new MenuItem(viewMenu, SWT.NULL);
    Messages.setLanguageText(view_torrents, "MainWindow.menu.view.mytorrents"); //$NON-NLS-1$
    view_torrents.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        if (mytorrents == null)
          mytorrents = new Tab(new MyTorrentsView(globalManager));
        else
          mytorrents.setFocus();
      }
    });

    MenuItem view_config = new MenuItem(viewMenu, SWT.NULL);
    Messages.setLanguageText(view_config, "MainWindow.menu.view.configuration"); //$NON-NLS-1$
    view_config.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        if (config == null)
          config = new Tab(new ConfigView());
        else
          config.setFocus();
      }
    });

    MenuItem view_console = new MenuItem(viewMenu, SWT.NULL);
    Messages.setLanguageText(view_console, "MainWindow.menu.view.console"); //$NON-NLS-1$
    view_console.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        if (console == null)
          console = new Tab(new ConsoleView());
        else
          console.setFocus();
      }
    });

    new MenuItem(viewMenu, SWT.SEPARATOR);

  	MenuItem view_closeDetails = new MenuItem(viewMenu, SWT.NULL);
    Messages.setLanguageText(view_closeDetails, "MainWindow.menu.closealldetails"); //$NON-NLS-1$
  	view_closeDetails.addListener(SWT.Selection, new Listener() {
  		public void handleEvent(Event e) {
        Tab.closeAllDetails();
 			}
  	});

    addCloseDownloadBarsToMenu(viewMenu);

    createLanguageMenu(menuBar);

    //The Help Menu
    MenuItem helpItem = new MenuItem(menuBar, SWT.CASCADE);
    Messages.setLanguageText(helpItem, "MainWindow.menu.help"); //$NON-NLS-1$
    Menu helpMenu = new Menu(mainWindow, SWT.DROP_DOWN);
    helpItem.setMenu(helpMenu);

    MenuItem help_about = new MenuItem(helpMenu, SWT.NULL);
    Messages.setLanguageText(help_about, "MainWindow.menu.help.about"); //$NON-NLS-1$
    help_about.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        showAboutWindow();
      }
    });

    createDropTarget(mainWindow);

    GridLayout mainLayout = new GridLayout();
    mainLayout.numColumns = 1;
    mainLayout.marginHeight = 0;
    mainLayout.marginWidth = 0;
    mainWindow.setLayout(mainLayout);
    //mainWindow.setBackground(white);

    GridData gridData;

    gridData = new GridData(GridData.FILL_BOTH);
    folder = new CTabFolder(mainWindow, SWT.NULL);
    Tab.setFolder(folder);

    folder.setSelectionBackground(new Color[] { white }, new int[0]);
    folder.setLayoutData(gridData);
    folder.addCTabFolderListener(new CTabFolderAdapter() {
      public void itemClosed(CTabFolderEvent event) {
        Tab.closed((CTabItem) event.item);
        event.doit = true;
      }
    });

    mytorrents = new Tab(new MyTorrentsView(globalManager));

    gridData = new GridData(GridData.FILL_HORIZONTAL);

    Composite statusBar = new Composite(mainWindow, SWT.SHADOW_IN);
    statusBar.setLayoutData(gridData);
    GridLayout layout_status = new GridLayout();
    layout_status.numColumns = 3;
    layout_status.horizontalSpacing = 1;
    layout_status.verticalSpacing = 0;
    layout_status.marginHeight = 0;
    layout_status.marginWidth = 0;
    statusBar.setLayout(layout_status);

    gridData = new GridData(GridData.FILL_HORIZONTAL);
    statusText = new CLabel(statusBar, SWT.SHADOW_IN);
    latestVersion = MessageText.getString("MainWindow.status.checking") + "..."; //$NON-NLS-1$ //$NON-NLS-2$
    setStatusVersion();
    statusText.setLayoutData(gridData);

    Thread versionChecker = new VersionChecker();
    versionChecker.start();

    gridData = new GridData();
    gridData.widthHint = 90;
    statusDown = new CLabel(statusBar, SWT.SHADOW_IN);
    statusDown.setText("D:"); //$NON-NLS-1$
    statusDown.setLayoutData(gridData);

    gridData = new GridData();
    gridData.widthHint = 90;
    statusUp = new CLabel(statusBar, SWT.SHADOW_IN);
    statusUp.setText("U:"); //$NON-NLS-1$
    statusUp.setLayoutData(gridData);

    globalManager.addListener(this);

    String windowRectangle = ConfigurationManager.getInstance().getStringParameter("window.rectangle", null); 
    if(null != windowRectangle) {
      int i = 0;
      int[] values = new int[4]; 
      StringTokenizer st = new StringTokenizer(windowRectangle, ",");
      try {
        while (st.hasMoreTokens() && i < 4) {
          values[i++] = Integer.valueOf(st.nextToken()).intValue();
        }
        if (i == 4) {
          mainWindow.setBounds(values[0], values[1], values[2], values[3]);
        }
      } catch (Exception e) {
      }
    }
    
    mainWindow.open();
    updater = new GUIUpdater();
    updater.start();

    boolean available = false;
    try {
      available = SysTrayMenu.isAvailable();
    }
    catch (NoClassDefFoundError e) {}

    if (available) {
      trayIcon = new SystemTray(this);
    }
    else
      tray = new TrayWindow(this);

    mainWindow.addListener(SWT.Close, new Listener() {
      public void handleEvent(Event event) {
        event.doit = false;
        mainWindow.setVisible(false);
        if (tray != null)
          tray.setVisible(true);
        synchronized (downloadBars) {
          Iterator iter = downloadBars.values().iterator();
          while (iter.hasNext()) {
            MinimizedWindow mw = (MinimizedWindow) iter.next();
            mw.setVisible(true);
          }
        }
      }
    });
  }

  private void createLanguageMenu(Menu menu) {
    MenuItem languageItem = new MenuItem(menu, SWT.CASCADE);
    Messages.setLanguageText(languageItem, "MainWindow.menu.language"); //$NON-NLS-1$
    Menu languageMenu = new Menu(mainWindow, SWT.DROP_DOWN);
    languageItem.setMenu(languageMenu);

    Locale[] locales = MessageText.getLocales();
    String savedLocaleString = ConfigurationManager.getInstance().getStringParameter("locale", Locale.getDefault().toString()); //$NON-NLS-1$
    Locale savedLocale = savedLocaleString.length() > 4 ? new Locale(savedLocaleString.substring(0, 2), savedLocaleString.substring(3, 5)) : Locale.getDefault(); 

    MenuItem[] items = new MenuItem[locales.length];
    
    for (int i = 0; i < locales.length; i++) {
//      System.out.println("found Locale: " + locales[i]);
      items[i] = new MenuItem(languageMenu, SWT.RADIO);
      createLanguageMenuitem(items[i], locales[i]);
    }

    Locale currentLocale = MessageText.getCurrentLocale();
    if(MessageText.changeLocale(savedLocale)) {
      for (int i = 0; i < items.length; i++) {
        if(currentLocale.equals(items[i].getData())) {
          items[i].setSelection(false);
          break;
        }      
      }
      for (int i = 0; i < items.length; i++) {
        if(savedLocale.equals(items[i].getData())) {
          items[i].setSelection(true);
          setSelectedLanguageItem(items[i]);
          break;
        }      
      }
    }
  }

  private void setStatusVersion() {
    if(statusText != null)
      statusText.setText("Azureus " + VERSION + " / " + MessageText.getString("MainWindow.status.latestversion") + " : " + latestVersion); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
  }

  private void createLanguageMenuitem(MenuItem language, final Locale locale) {
    language.setData(locale);
    language.setText(((Locale)language.getData()).getDisplayLanguage());
    language.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        if (isSelectedLanguageDifferent(e.widget)) {
          if (MessageText.changeLocale(locale)) {
            ConfigurationManager.getInstance().setParameter("locale", locale.toString()); //$NON-NLS-1$
            ConfigurationManager.getInstance().save();
            setSelectedLanguageItem((MenuItem) e.widget);
          } else {
            ((MenuItem) e.widget).setSelection(false);
            selectSelectedLanguageItem();
          }
        }
      }
    });
    language.setSelection(MessageText.isCurrentLocale(locale));
    if(language.getSelection())
      selectedLanguageItem = language;
  }
  
  private synchronized void setSelectedLanguageItem(MenuItem newLanguage) {
    selectedLanguageItem = newLanguage;
    Messages.updateLanguageForControl(mainWindow);
    updateMenuText(menuBar);
    if(statusText != null)
      statusText.update();
    if(folder != null)
      folder.update();
    if(trayIcon != null) {
      trayIcon.updateLanguage();
      trayIcon.refresh();
    }
    if(tray != null)
      tray.updateLanguage();

    Tab.updateLanguage();

    setStatusVersion();
  }

  private void showAboutWindow() {
    final Shell s = new Shell(mainWindow, SWT.CLOSE | SWT.PRIMARY_MODAL);
    s.setImage(ImageRepository.getImage("azureus")); //$NON-NLS-1$
    s.setText(MessageText.getString("MainWindow.about.title")); //$NON-NLS-1$
    GridData gridData;
    s.setLayout(new GridLayout(1, true));
    s.setLayoutData(gridData = new GridData());

    Label label = new Label(s, SWT.NONE);
    label.setImage(ImageRepository.loadImage(display, "org/gudy/azureus2/ui/splash/azureus.jpg", "azureus_splash"));
    label.setLayoutData(gridData = new GridData());

    Properties properties = new Properties();
    try {
      properties.load(ClassLoader.getSystemResourceAsStream("org/gudy/azureus2/ui/swt/about.properties"));
    } catch (Exception e1) {
      e1.printStackTrace();
    }

    Group gDevelopers = new Group(s, SWT.NULL);
    gDevelopers.setLayout(new GridLayout());
    Messages.setLanguageText(gDevelopers, "MainWindow.about.section.developers"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gDevelopers.setLayoutData(gridData);

    label = new Label(gDevelopers, SWT.LEFT);
    label.setText(properties.getProperty("developers")); //$NON-NLS-1$ //$NON-NLS-2$
    label.setLayoutData(gridData = new GridData());

    Group gTranslators = new Group(s, SWT.NULL);
    gTranslators.setLayout(new GridLayout());
    Messages.setLanguageText(gTranslators, "MainWindow.about.section.translators"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gTranslators.setLayoutData(gridData);

    label = new Label(gTranslators, SWT.LEFT);
    label.setText(properties.getProperty("translators")); //$NON-NLS-1$ //$NON-NLS-2$
    label.setLayoutData(gridData = new GridData());

    s.pack();
    Rectangle splashRect = s.getBounds();
    Rectangle displayRect = display.getBounds();
    int x = (displayRect.width - splashRect.width) / 2;
    int y = (displayRect.height - splashRect.height) / 2;
    s.setLocation(x, y);
    s.open();
    while (!s.isDisposed()) {
      try {
        if (!display.readAndDispatch())
          display.sleep();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private void showUpgradeWindow() {
    final Shell s = new Shell(mainWindow, SWT.CLOSE | SWT.PRIMARY_MODAL);
    s.setImage(ImageRepository.getImage("azureus")); //$NON-NLS-1$
    s.setText(MessageText.getString("MainWindow.upgrade.assistant")); //$NON-NLS-1$
    s.setSize(250, 300);
    s.setLayout(new GridLayout(3, true));
    GridData gridData;
    s.setLayoutData(gridData = new GridData());
//    gridData.horizontalIndent = 10;

    Group gInfo = new Group(s, SWT.NULL);
    gInfo.setLayout(new GridLayout());
    Messages.setLanguageText(gInfo, "MainWindow.upgrade.section.info"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gInfo.setLayoutData(gridData);
    gridData.horizontalSpan = 3;

    Label label = new Label(gInfo, SWT.CENTER);
    label.setText(MessageText.getString("MainWindow.upgrade.newerversion")+": " + latestVersion + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    FontData[] fontData = label.getFont().getFontData();
    for (int i = 0; i < fontData.length; i++) {
      fontData[i].setStyle(SWT.BOLD);
    }
    label.setFont(new Font(display, fontData));
    label.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    label = new Label(gInfo, SWT.LEFT);
    label.setText(MessageText.getString("MainWindow.upgrade.explanation")+".\n"); //$NON-NLS-1$ //$NON-NLS-2$
    label.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    label = new Label(s, SWT.LEFT);
    label.setText("\n"); //$NON-NLS-1$
    label.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    Group gManual = new Group(s, SWT.NULL);
    gManual.setLayout(new GridLayout());
    Messages.setLanguageText(gManual, "MainWindow.upgrade.section.manual"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gManual.setLayoutData(gridData);
    gridData.horizontalSpan = 3;
    
    label = new Label(gManual, SWT.NULL);
    label.setText(MessageText.getString("MainWindow.upgrade.explanation.manual")+":\n"); //$NON-NLS-1$ //$NON-NLS-2$
    label.setLayoutData(gridData = new GridData());
    
    final String downloadLink = "http://azureus.sourceforge.net/Azureus2.jar";  //$NON-NLS-1$
    final Label linklabel = new Label(gManual, SWT.NULL);
    linklabel.setText(downloadLink);
    linklabel.setCursor(new Cursor(display, SWT.CURSOR_HAND));
    linklabel.setForeground(blue);
    linklabel.setLayoutData(gridData = new GridData());
            
    linklabel.addMouseListener(new MouseAdapter() {
      public void mouseDoubleClick(MouseEvent arg0) {
        Program.launch(downloadLink);
      }
      public void mouseDown(MouseEvent arg0) {
        Program.launch(downloadLink);
      }
    });

    label = new Label(s, SWT.LEFT);
    label.setText("\n"); //$NON-NLS-1$
    label.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    Group gAutomatic = new Group(s, SWT.NULL);
    gAutomatic.setLayout(new GridLayout());
    Messages.setLanguageText(gAutomatic, "MainWindow.upgrade.section.automatic"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gAutomatic.setLayoutData(gridData);
    gridData.horizontalSpan = 3;

    final Label step1 = new Label(gAutomatic, SWT.LEFT);
    step1.setText("- "+MessageText.getString("MainWindow.upgrade.step1")); //$NON-NLS-1$ //$NON-NLS-2$
    step1.setForeground(blue);
    step1.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    final Label step2 = new Label(gAutomatic, SWT.LEFT);
    step2.setText("- "+MessageText.getString("MainWindow.upgrade.step2")+"\n\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    step2.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    final Label hint = new Label(gAutomatic, SWT.LEFT);
    hint.setText(MessageText.getString("MainWindow.upgrade.hint1")+"."); //$NON-NLS-1$ //$NON-NLS-2$
    hint.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    label = new Label(gAutomatic, SWT.LEFT);
    label.setText("\n"); //$NON-NLS-1$
    label.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    final ProgressBar progressBar = new ProgressBar (gAutomatic, SWT.SMOOTH);
    progressBar.setLayoutData(gridData = new GridData(GridData.FILL_HORIZONTAL));
    gridData.horizontalSpan = 3;
    progressBar.setToolTipText(MessageText.getString("MainWindow.upgrade.tooltip.progressbar")); //$NON-NLS-1$

    label = new Label(s, SWT.LEFT);
    label.setText("\n"); //$NON-NLS-1$
    label.setLayoutData(gridData = new GridData());
    gridData.horizontalSpan = 3;

    final Button next = new Button(s, SWT.PUSH);
    next.setText(" " + MessageText.getString("Button.next") + " > "); //$NON-NLS-1$ //$NON-NLS-3$ //$NON-NLS-2$
    
    gridData = new GridData();
    next.setLayoutData(gridData);

    final Button finish = new Button(s, SWT.PUSH);
    finish.setText(" "+MessageText.getString("Button.finish")+" "); //$NON-NLS-1$ //$NON-NLS-3$ //$NON-NLS-2$
    finish.setLayoutData(new GridData());

    final Button cancel = new Button(s, SWT.PUSH);
    cancel.setText(" "+MessageText.getString("Button.cancel")+" "); //$NON-NLS-1$ //$NON-NLS-3$ //$NON-NLS-2$
    cancel.setLayoutData(new GridData());

    SelectionAdapter update = new SelectionAdapter() {
      public void widgetSelected(SelectionEvent event) {
        downloadJar(progressBar);
        if (jarDownloaded) {
          if(event.widget == finish) {
            updateJar = true;
            s.dispose();
            dispose();
          } else {
            next.setEnabled(false);
            step1.setForeground(black);
            step2.setForeground(blue);
            s.setDefaultButton(finish);
            hint.setText(MessageText.getString("MainWindow.upgrade.hint2")+"."); //$NON-NLS-1$ //$NON-NLS-2$
            hint.setForeground(black);
            hint.pack();
            linklabel.setEnabled(false);
          }
        } else {
          if(event.widget == finish) {
            s.dispose();
          } else {
            hint.setText(MessageText.getString("MainWindow.upgrade.error.downloading.hint")+"!"); //$NON-NLS-1$ //$NON-NLS-2$
            hint.setForeground(red);
            hint.pack();
            next.setEnabled(false);
            finish.setEnabled(false);
          }
        }
      }
    };

    next.addSelectionListener(update);
    finish.addSelectionListener(update);
    
    cancel.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent event) {
        s.dispose();
      }
    });
    
    s.pack();

    Rectangle parent = mainWindow.getBounds();
    Rectangle child = s.getBounds();
    child.x = parent.x + (parent.width - child.width) / 2;
    child.y = parent.y + (parent.height - child.height) / 2;
    s.setBounds(child);

    s.open();
    s.setFocus();

    while (!s.isDisposed()) {
      if (!display.readAndDispatch())
      display.sleep();
    } //end while
}

  private void updateJar() {
    FileOutputStream out = null;
    InputStream in = null;
    try {
      String classPath = System.getProperty("java.class.path"); //$NON-NLS-1$
      String libraryPath = System.getProperty("java.library.path"); //$NON-NLS-1$
      String userPath = System.getProperty("user.dir"); //$NON-NLS-1$

      File updaterJar = GlobalManager.getApplicationFile("Updater.jar"); //$NON-NLS-1$
      if (!updaterJar.isFile()) {
        URL reqUrl = new URL("http://azureus.sourceforge.net/Updater.jar"); //$NON-NLS-1$
        HttpURLConnection con = (HttpURLConnection) reqUrl.openConnection();
        con.connect();
        in = con.getInputStream();
        out = new FileOutputStream(updaterJar);
        byte[] buffer = new byte[1024];
        int c;
        while ((c = in.read(buffer)) != -1)
          out.write(buffer, 0, c);
      }

      String exec = "java -classpath \"" + updaterJar.getAbsolutePath() + "\" org.gudy.azureus2.update.Updater \"" //$NON-NLS-1$ //$NON-NLS-2$
          + classPath + "\" \"" + libraryPath + "\" \"" + userPath + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
//      System.out.println("Azureus exec: " + exec);

      Runtime.getRuntime().exec(exec);
/*
      BufferedReader d = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String text;
      while((text = d.readLine()) != null && text.length() != 0)
        System.out.println(text);
//*/
    } catch (Exception e1) {
      e1.printStackTrace();
      updateJar = false;
    } finally {
      try {
        if (out != null)
          out.close();
      } catch (Exception e) {
      }
      try {
        if (in != null)
          in.close();
      } catch (Exception e) {
      }
    }
  }

  private void downloadJar(final ProgressBar progressBar) {
    if(jarDownloaded) {
      progressBar.setSelection(progressBar.getMaximum());
      return;
    }

    FileOutputStream fos = null;
    InputStream in = null;
    try {
      File originFile = GlobalManager.getApplicationFile("Azureus2.jar"); //$NON-NLS-1$
      File newFile = new File(originFile.getParentFile(), "Azureus2-new.jar"); //$NON-NLS-1$
      
      URL reqUrl = new URL("http://azureus.sourceforge.net/Azureus2.jar"); //$NON-NLS-1$
      HttpURLConnection con = (HttpURLConnection) reqUrl.openConnection();
      con.connect();
      in = con.getInputStream();
      fos = new FileOutputStream(newFile);

      progressBar.setMinimum(0);
      progressBar.setMaximum(100);

      final InputStream input = in; 
      final FileOutputStream output = fos;

      final long length = con.getContentLength();
      final byte[] buffer = new byte[32768];
      int c;
      long bytesDownloaded = 0L;
      while ((c = input.read(buffer)) != -1) {
        output.write(buffer, 0, c);
        bytesDownloaded += c;
        int progress = (int) Math.round(((double) bytesDownloaded / length) * 100);
        progressBar.setSelection(progress <= 100 ? progress : 100);
        progressBar.update();
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e1) {
        }
      }
      jarDownloaded = true;
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (fos != null)
          fos.close();
      } catch (Exception e) {
      }
      try {
        if (in != null)
          in.close();
      } catch (Exception e) {
      }
    }
  }

  public static void updateMenuText(Object menu) {
    if(menu == null)
      return;
    if (menu instanceof Menu) {
      MenuItem[] menus = ((Menu) menu).getItems();
      for (int i = 0; i < menus.length; i++) {
        updateMenuText(menus[i]);
      }
    } else if (menu instanceof MenuItem) {
      MenuItem item = (MenuItem) menu;
      if (item.getData() != null) {
        if (item.getData() instanceof String)
          item.setText(MessageText.getString((String) item.getData()));
        else
          item.setText(((Locale) item.getData()).getDisplayLanguage());
        updateMenuText(item.getMenu());  
      }
    }
  }

  private boolean isSelectedLanguageDifferent(Widget newLanguage) {
    return selectedLanguageItem != newLanguage;
  }
  
  private void selectSelectedLanguageItem() {
    selectedLanguageItem.setSelection(true);
  }
  
  protected void addCloseDownloadBarsToMenu(Menu menu) {
    MenuItem view_closeAll = new MenuItem(menu, SWT.NULL);
    Messages.setLanguageText(view_closeAll, "MainWindow.menu.closealldownloadbars"); //$NON-NLS-1$
    view_closeAll.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        closeDownloadBars();
      }
    });
  }

  public void closeDownloadBars() {
    if (display == null || display.isDisposed())
      return;
    display.asyncExec(new Runnable() {

      public void run() {
        synchronized (downloadBars) {
          Iterator iter = downloadBars.keySet().iterator();
          while (iter.hasNext()) {
            DownloadManager dm = (DownloadManager) iter.next();
            MinimizedWindow mw = (MinimizedWindow) downloadBars.get(dm);
            mw.close();
            iter.remove();
          }
        }
      }

    });
  }

  private void createDropTarget(final Control control) {
    DropTarget dropTarget = new DropTarget(control, DND.DROP_MOVE | DND.DROP_COPY);
    dropTarget.setTransfer(new Transfer[] { FileTransfer.getInstance()});
    dropTarget.addDropListener(new DropTargetAdapter() {
      public void drop(DropTargetEvent event) {
        final String[] sourceNames = (String[]) event.data;
        if (sourceNames == null)
          event.detail = DND.DROP_NONE;
        if (event.detail == DND.DROP_NONE)
          return;
        for (int i = 0;(i < sourceNames.length); i++) {
          final File source = new File(sourceNames[i]);
          if (source.isFile())
            openTorrent(source.getAbsolutePath());
          else if (source.isDirectory())
            openTorrentsFromDirectory(source.getAbsolutePath());
        }
      }
    });
  }

  public void waitForClose() {
    while (!mainWindow.isDisposed()) {
      try {
        if (!display.readAndDispatch())
          display.sleep();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    if (tray != null)
      tray.dispose();

    display.dispose();
  }

  public static void main(String args[]) {
    GlobalManager gm = new GlobalManager();
    MainWindow mw = new MainWindow(gm, null);
    mw.waitForClose();
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IComponentListener#objectAdded(java.lang.Object)
   */
  public void objectAdded(Object created) {
    if (!(created instanceof DownloadManager))
      return;
    if (((DownloadManager) created).getState() == DownloadManager.STATE_STOPPED)
      return;
    if (ConfigurationManager.getInstance().getBooleanParameter("Open Details", true)) //$NON-NLS-1$
      openManagerView((DownloadManager) created);
    if (ConfigurationManager.getInstance().getBooleanParameter("Open Bar", false)) { //$NON-NLS-1$
      synchronized (downloadBars) {
        MinimizedWindow mw = new MinimizedWindow((DownloadManager) created, mainWindow);
        downloadBars.put(created, mw);
      }
    }
  }

  public void openManagerView(DownloadManager downloadManager) {
    synchronized (downloadViews) {
      if (downloadViews.containsKey(downloadManager)) {
        Tab tab = (Tab) downloadViews.get(downloadManager);
        tab.setFocus();
      }
      else {
        Tab tab = new Tab(new ManagerView(downloadManager));
        downloadViews.put(downloadManager, tab);
      }
    }
  }

  public void removeManagerView(DownloadManager downloadManager) {
    synchronized (downloadViews) {
      downloadViews.remove(downloadManager);
    }
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IComponentListener#objectRemoved(java.lang.Object)
   */
  public void objectRemoved(Object removed) {
    if (!(removed instanceof DownloadManager))
      return;
    synchronized (downloadViews) {
      if (downloadViews.containsKey(removed)) {
        final Tab tab = (Tab) downloadViews.get(removed);
        if (display == null || display.isDisposed())
          return;
        display.asyncExec(new Runnable() {
          public void run() {
            tab.dispose();
          }
        });

      }
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
  }

  public void dispose() {
    if (this.trayIcon != null)
      SysTrayMenu.dispose();

    if (startServer != null)
      startServer.stopIt();
    updater.stopIt();
    globalManager.stopAll();

    Rectangle windowRectangle = mainWindow.getBounds();
    ConfigurationManager.getInstance().setParameter("window.rectangle", windowRectangle.x + "," + windowRectangle.y + "," + windowRectangle.width + "," + windowRectangle.height);
    ConfigurationManager.getInstance().save(); 

    mainWindow.dispose();

    if(instanceCount-- == 0) {
      for (int i = 0; i < blues.length; i++) {
        if (blues[i] != null && !blues[i].isDisposed())
          blues[i].dispose();
      }
      if(grey != null && !grey.isDisposed())
        grey.dispose();
      if(black != null && !black.isDisposed())
        black.dispose();
      if(blue != null && !blue.isDisposed())
        blue.dispose();
      if(red != null && !red.isDisposed())
        red.dispose();
      if(white != null && !white.isDisposed())
        white.dispose();
      if(red_ConsoleView != null && !red_ConsoleView.isDisposed())
        red_ConsoleView.dispose();
      if(red_ManagerItem != null && !red_ManagerItem.isDisposed())
        red_ManagerItem.dispose();
    }
    if(updateJar)
      updateJar();
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

  /**
   * @return
   */
  public static MainWindow getWindow() {
    return window;
  }

  public void openTorrent(final String fileName) {
    if (!fileName.endsWith(".torrent")) //$NON-NLS-1$
      return;
    display.asyncExec(new Runnable() {
      public void run() {
        String savePath = getSavePath(fileName);
        if (savePath == null)
          return;
        globalManager.addDownloadManager(fileName, savePath);
      }
    });
  }

  public String getSavePath(String fileName) {
    String savePath = ConfigurationManager.getInstance().getStringParameter("Default save path", ""); //$NON-NLS-1$ //$NON-NLS-2$
    if (savePath.length() == 0) {
      mainWindow.setActive();
      boolean singleFile = false;
      String singleFileName = ""; //$NON-NLS-1$
      FileInputStream fis = null;
      try {
        byte[] buf = new byte[1024];
        int nbRead;
        ByteArrayOutputStream metaInfo = new ByteArrayOutputStream();
        fis = new FileInputStream(fileName);
        while ((nbRead = fis.read(buf)) > 0)
          metaInfo.write(buf, 0, nbRead);

        Map map = BDecoder.decode(metaInfo.toByteArray());
        Map info = (Map) map.get("info"); //$NON-NLS-1$
        singleFileName = LocaleUtil.getCharsetString((byte[]) info.get("name")); //$NON-NLS-1$

        Object test = info.get("length"); //$NON-NLS-1$
        if (test != null) {        
          singleFile = true;          
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      } finally {
        try {
          if (fis != null)
            fis.close();
        } catch (Exception e) {
        }
      }
      if (singleFile) {
        FileDialog fDialog = new FileDialog(mainWindow, SWT.SYSTEM_MODAL);
        fDialog.setFilterPath(ConfigurationManager.getInstance().getStringParameter("Default Path", "")); //$NON-NLS-1$ //$NON-NLS-2$
        fDialog.setFileName(singleFileName);
        fDialog.setText(MessageText.getString("MainWindow.dialog.choose.savepath") + " (" + singleFileName + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        savePath = fDialog.open();

      }
      else {
        DirectoryDialog dDialog = new DirectoryDialog(mainWindow, SWT.SYSTEM_MODAL);
        dDialog.setFilterPath(ConfigurationManager.getInstance().getStringParameter("Default Path", "")); //$NON-NLS-1$ //$NON-NLS-2$
        dDialog.setText(MessageText.getString("MainWindow.dialog.choose.savepath") + " (" + singleFileName + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        savePath = dDialog.open();
      }
      if (savePath == null)
        return null;
      ConfigurationManager.getInstance().setParameter("Default Path", savePath); //$NON-NLS-1$
      ConfigurationManager.getInstance().save();
    }
    return savePath;
  }

  public void openTorrents(final String path, final String fileNames[]) {
    display.asyncExec(new Runnable() {
      public void run() {

        String separator = System.getProperty("file.separator"); //$NON-NLS-1$
        for (int i = 0; i < fileNames.length; i++) {
          if (!fileNames[i].endsWith(".torrent")) //$NON-NLS-1$
            continue;
          String savePath = getSavePath(path + separator + fileNames[i]);
          if (savePath == null)
            continue;
          globalManager.addDownloadManager(path + separator + fileNames[i], savePath);
        }
      }
    });
  }

  public void openTorrentsFromDirectory(String directoryName) {
    File f = new File(directoryName);
    if (!f.isDirectory())
      return;
    File[] files = f.listFiles(new FileFilter() {
      public boolean accept(File arg0) {
        if (arg0.getName().endsWith(".torrent")) //$NON-NLS-1$
          return true;
        return false;
      }
    });
    if (files.length == 0)
      return;
    DirectoryDialog dDialog = new DirectoryDialog(mainWindow, SWT.NULL);
    dDialog.setText(MessageText.getString("MainWindow.dialog.choose.savepath_forallfiles")); //$NON-NLS-1$
    String savePath = dDialog.open();
    if (savePath == null)
      return;
    for (int i = 0; i < files.length; i++)
      globalManager.addDownloadManager(files[i].getAbsolutePath(), savePath);
  }

  /**
   * @return
   */
  public HashMap getDownloadBars() {
    return downloadBars;
  }

  /**
   * @return
   */
  public Tab getConfig() {
    return config;
  }

  /**
   * @param tab
   */
  public void setConfig(Tab tab) {
    config = tab;
  }

}
