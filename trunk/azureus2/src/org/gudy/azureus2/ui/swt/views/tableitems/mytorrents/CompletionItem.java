/*
 * File    : CompletionItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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
 
package org.gudy.azureus2.ui.swt.views.tableitems.mytorrents;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Point;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.pluginsimpl.local.ui.SWT.SWTManagerImpl;
import org.gudy.azureus2.ui.swt.views.table.utils.CoreTableColumn;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;

/** Torrent Completion Level Graphic Cell for My Torrents.
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class CompletionItem
       extends CoreTableColumn 
       implements TableCellAddedListener
{
  private static final int borderWidth = 1;

  /** Default Constructor */
  public CompletionItem() {
    super("completion", TableManager.TABLE_MYTORRENTS_INCOMPLETE);
    initializeAsGraphic(POSITION_INVISIBLE, 250);
  }

  public void cellAdded(TableCell cell) {
    new Cell(cell);
  }

  private class Cell
          implements TableCellRefreshListener, TableCellDisposeListener
  {
    int lastPercentDone = 0;
    int lastWidth = 0;
    
    public Cell(TableCell cell) {
      cell.addRefreshListener(this);
      cell.addDisposeListener(this);
    }

    public void dispose(TableCell cell) {
      Image img = cell.getGraphic();
      if (img != null && !img.isDisposed())
        img.dispose();
      cell.setGraphic(null);
    }
  
      
    
    public void refresh(TableCell cell) {    
      //Compute bounds ...
      Point ptNewSize = cell.getSize();
  
      if (ptNewSize.x == 0)
        return;
      
      int x1 = ptNewSize.x - borderWidth - 1;
      int y1 = ptNewSize.y - borderWidth - 1;
      if (x1 < 10 || y1 < 3) {
        return;
      }
  
      int percentDone = getPercentDone(cell);
      boolean bImageBufferValid = (lastPercentDone == percentDone) && 
                                  (lastWidth == ptNewSize.x) && 
                                  cell.isValid();
      if (bImageBufferValid) {
        return;
      }
  
      lastPercentDone = percentDone;
      lastWidth = ptNewSize.x;
  
      Image image = cell.getGraphic();
      GC gcImage;
      boolean bImageSizeChanged;
      Rectangle imageBounds;
      if (image == null) {
        bImageSizeChanged = true;
      } else {
        imageBounds = image.getBounds();
        bImageSizeChanged = imageBounds.width != ptNewSize.x ||
                            imageBounds.height != ptNewSize.y;
      }
      if (bImageSizeChanged) {
        image = new Image(SWTManagerImpl.getSingleton().getDisplay(),
                          ptNewSize.x, ptNewSize.y);
        imageBounds = image.getBounds();
        bImageBufferValid = false;
  
        // draw border
        gcImage = new GC(image);
        gcImage.setForeground(Colors.grey);
        gcImage.drawRectangle(0, 0, ptNewSize.x - 1, ptNewSize.y - 1);
      } else {
        gcImage = new GC(image);
      }
  
      int limit = (x1 * percentDone) / 1000;
      gcImage.setBackground(Colors.blues[Colors.BLUES_DARKEST]);
      gcImage.fillRectangle(1,1,limit,y1);
      if (limit < x1) {
        gcImage.setBackground(Colors.blues[Colors.BLUES_LIGHTEST]);
        gcImage.fillRectangle(limit+1, 1, x1-limit, y1);
      }
  
      gcImage.dispose();
        
      if (!bImageBufferValid) {
        cell.setGraphic(image);
      }
      cell.setSortValue(percentDone);
    }
  
    public int getPercentDone(TableCell cell) {
      DownloadManager dm = (DownloadManager)cell.getDataSource();
      if (dm == null) {
        return 0;
      }
      return dm.getStats().getDownloadCompleted(true);
    }
  }
}
