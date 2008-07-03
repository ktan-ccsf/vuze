/**
 * 
 */
package com.aelitis.azureus.ui.swt.skin;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.ui.swt.Utils;

import com.aelitis.azureus.ui.swt.utils.ImageLoader;

/**
 * @author TuxPaper
 * @created Jun 12, 2006
 *
 */
public class SWTSkinObjectBasic
	implements SWTSkinObject
{
	protected static final int BORDER_ROUNDED = 1;

	protected static final int BORDER_ROUNDED_FILL = 2;

	protected Control control;

	protected String type;

	protected String sConfigID;

	protected SWTBGImagePainter painter;

	protected SWTSkinProperties properties;

	protected String sID;

	// XXX Might be wise to force this to SWTSkinObjectContainer
	protected SWTSkinObject parent;

	protected SWTSkin skin;

	protected String[] suffixes = null;

	protected ArrayList listeners = new ArrayList();

	protected AEMonitor listeners_mon = new AEMonitor(
			"SWTSkinObjectBasic::listener");

	private String sViewID;

	private boolean isVisible;

	protected Color bgColor;

	private int borderType;

	private boolean initialized = false;

	/**
	 * @param properties TODO
	 * 
	 */
	public SWTSkinObjectBasic(SWTSkin skin, SWTSkinProperties properties,
			Control control, String sID, String sConfigID, String type,
			SWTSkinObject parent) {
		this(skin, properties, sID, sConfigID, type, parent);
		setControl(control);
	}

	public SWTSkinObjectBasic(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, String type, SWTSkinObject parent) {
		this.skin = skin;
		this.properties = properties;
		this.sConfigID = sConfigID;
		this.sID = sID;
		this.type = type;
		this.parent = parent;
	}

	public void setControl(final Control control) {
		if (!Utils.isThisThreadSWT()) {
			Debug.out("Warning: setControl not called in SWT thread for " + this);
			Utils.execSWTThread(new AERunnable() {
				public void runSupport() {
					setControl(control);
				}
			});
			return;
		}

		setViewID(properties.getStringValue(sConfigID + ".view"));

		this.control = control;
		control.setData("ConfigID", sConfigID);
		control.setData("SkinObject", this);

		SWTSkinUtils.addMouseImageChangeListeners(control);
		switchSuffix("", 1, false);

		// setvisible is one time only
		if (!properties.getBooleanValue(sConfigID + ".visible", true)) {
			setVisible(false);
		}

		final Listener lShowHide = new Listener() {
			public void handleEvent(final Event event) {
				boolean toBeVisible = event.type == SWT.Show;
				if (event.widget == control) {
					setIsVisible(toBeVisible);
					return;
				}

				if (!toBeVisible || control.isVisible()) {
					setIsVisible(toBeVisible);
					return;
				}

				// container item.. check listCanvas.isVisible(), but only after
				// events have been processed, so that the visibility is propogated
				// to the listCanvas
				control.getDisplay().asyncExec(new AERunnable() {
					public void runSupport() {
						if (control == null || control.isDisposed()) {
							setIsVisible(false);
							return;
						}
						setIsVisible(control.isVisible());
					}
				});
			}
		};
		setIsVisible(control.isVisible());

		final ArrayList listenersToRemove = new ArrayList(2);
		Control walkUp = control;
		do {
			listenersToRemove.add(walkUp);
			walkUp.addListener(SWT.Show, lShowHide);
			walkUp.addListener(SWT.Hide, lShowHide);
			walkUp = walkUp.getParent();
		} while (walkUp != null);

		control.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				for (Iterator iter = listenersToRemove.iterator(); iter.hasNext();) {
					Control control = (Control) iter.next();
					if (control != null && !control.isDisposed()) {
						control.removeListener(SWT.Show, lShowHide);
						control.removeListener(SWT.Hide, lShowHide);
					}
				}
			}
		});
	}

	/**
	 * @param visible
	 *
	 * @since 3.0.4.3
	 */
	private void setIsVisible(boolean visible) {
		if (visible == isVisible) {
			return;
		}
		isVisible = visible;
		triggerListeners(visible ? SWTSkinObjectListener.EVENT_SHOW
				: SWTSkinObjectListener.EVENT_HIDE);
	}

	public Control getControl() {
		return control;
	}

	public String getType() {
		return type;
	}

	public String getConfigID() {
		return sConfigID;
	}

	public String getSkinObjectID() {
		return sID;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#getParent()
	public SWTSkinObject getParent() {
		return parent;
	}

	public void setBackground(String sConfigID, String sSuffix) {
		Image imageBG;
		Image imageBGLeft;
		Image imageBGRight;

		if (sConfigID == null) {
			return;
		}

		ImageLoader imageLoader = skin.getImageLoader(properties);
		String s = properties.getStringValue(sConfigID + sSuffix, (String) null);
		if (s != null && s.length() > 0) {
			Image[] images = imageLoader.getImages(sConfigID + sSuffix);
			if (images.length == 1 && ImageLoader.isRealImage(images[0])) {
				imageBG = images[0];
				imageBGLeft = imageLoader.getImage(sConfigID + sSuffix + "-left");
				imageBGRight = imageLoader.getImage(sConfigID + sSuffix + "-right");
			} else if (images.length == 3 && ImageLoader.isRealImage(images[2])) {
				imageBGLeft = images[0];
				imageBG = images[1];
				imageBGRight = images[2];
			} else if (images.length == 2 && ImageLoader.isRealImage(images[1])) {
				imageBGLeft = images[0];
				imageBG = images[1];
				imageBGRight = imageLoader.getImage(sConfigID + sSuffix + "-right");
			} else {
				return;
			}
		} else {
			if (s != null && painter != null) {
				painter.dispose();
				painter = null;
			}
			return;
		}

		if (painter == null) {
			//control.setBackgroundImage doesn't handle transparency!
			//control.setBackgroundImage(image);

			// Workaround: create our own image with shell's background
			// for "transparent" area.  Doesn't allow control's image to show
			// through.  To do that, we'd have to walk up the tree until we 
			// found a composite with an image
			//control.setBackgroundMode(SWT.INHERIT_NONE);
			//control.setBackgroundImage(imageBG);

			String sTileMode = properties.getStringValue(sConfigID + ".drawmode");
			int tileMode = SWTSkinUtils.getTileMode(sTileMode);
			painter = new SWTBGImagePainter(control, imageBGLeft, imageBGRight,
					imageBG, tileMode);
		} else {
			//System.out.println("setImage " + sConfigID + "  " + sSuffix);
			painter.setImage(imageBGLeft, imageBGRight, imageBG);
		}

		// XXX Is this needed?  It causes flicker and slows things down.
		//     Maybe a redraw instead (if anything at all)?
		//control.update();
	}

	// @see java.lang.Object#toString()
	public String toString() {
		String s = "SWTSkinObjectBasic {" + sID;

		if (!sID.equals(sConfigID)) {
			s += "/" + sConfigID;
		}

		if (sViewID != null) {
			s += "/v=" + sViewID;
		}

		s += ", " + type + "; parent="
				+ ((parent == null) ? null : parent.getSkinObjectID() + "}");

		return s;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#getSkin()
	public SWTSkin getSkin() {
		return skin;
	}

	// @see java.lang.Object#equals(java.lang.Object)
	public boolean equals(Object obj) {
		if (obj instanceof SWTSkinObject) {
			SWTSkinObject skinObject = (SWTSkinObject) obj;
			boolean bEquals = skinObject.getSkinObjectID().equals(sID);
			if (parent != null) {
				return bEquals && parent.equals(skinObject.getParent());
			}
			return bEquals;
		}

		return super.equals(obj);
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#setVisible(boolean)
	public void setVisible(final boolean visible) {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (control != null && !control.isDisposed()) {
					Object ld = control.getLayoutData();
					if (ld instanceof FormData) {
						FormData fd = (FormData) ld;
						if (!visible) {
							if (fd.width != 0 && fd.height != 0) {
								control.setData("oldSize", new Point(fd.width, fd.height));
							}
							fd.width = 0;
							fd.height = 0;
						} else {
							Object oldSize = control.getData("oldSize");
							Point oldSizePoint = (oldSize instanceof Point) ? (Point) oldSize
									: new Point(SWT.DEFAULT, SWT.DEFAULT);
							if (fd.width <= 0) {
								fd.width = oldSizePoint.x;
							}
							if (fd.height <= 0) {
								fd.height = oldSizePoint.y;
							}
						}
						control.setLayoutData(fd);
						control.getParent().layout(true);
						Utils.relayout(control);
					}
					control.setVisible(visible);
					setIsVisible(visible);
				}
			}
		});
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#setDefaultVisibility()
	public void setDefaultVisibility() {
		if (sConfigID == null) {
			return;
		}

		setVisible(getDefaultVisibility());
	}

	public boolean getDefaultVisibility() {
		return properties.getBooleanValue(sConfigID + ".visible", true);
	}

	public boolean isVisible() {
		if (control == null || control.isDisposed()) {
			return false;
		}
		return isVisible;
	}

	/**
	 * Switch the suffix using the default of <code>1</code> for level and <code>false</code> for walkUp 
	 */
	public String switchSuffix(String suffix) {
		return switchSuffix(suffix, 1, false);
	}

	public String switchSuffix(String suffix, int level, boolean walkUp) {
		if (walkUp) {
			SWTSkinObject parentSkinObject = parent;
			SWTSkinObject skinObject = this;

			// Move up the tree until propogation stops
			while ((parentSkinObject instanceof SWTSkinObjectContainer)
					&& ((SWTSkinObjectContainer) parentSkinObject).getPropogation()) {
				skinObject = parentSkinObject;
				parentSkinObject = parentSkinObject.getParent();
			}

			if (skinObject != this) {
				//System.out.println(sConfigID + suffix + "; walkup");

				skinObject.switchSuffix(suffix, level, false);
				return null;
			}
		}

		//System.out.println(SystemTime.getCurrentTime() + ": " + this + suffix + "; switchy");
		if (suffixes == null) {
			suffixes = new String[level];
		} else if (suffixes.length < level) {
			String[] newSuffixes = new String[level];
			System.arraycopy(suffixes, 0, newSuffixes, 0, suffixes.length);
			suffixes = newSuffixes;
		}
		suffixes[level - 1] = suffix;

		suffix = getSuffix();

		if (sConfigID == null || control == null || control.isDisposed()) {
			return suffix;
		}

		final String sSuffix = suffix;

		Utils.execSWTThread(new AERunnable() {

			public void runSupport() {
				if (control == null || control.isDisposed()) {
					return;
				}

				Color color = properties.getColor(sConfigID + ".color" + sSuffix);
				if (color != null) {
					bgColor = color;
					String colorStyle = properties.getStringValue(sConfigID
							+ ".color.style" + sSuffix);
					if (colorStyle != null) {
						if (colorStyle.equals("rounded")) {
							borderType = BORDER_ROUNDED;
						} else if (colorStyle.equals("rounded-fill")) {
							borderType = BORDER_ROUNDED_FILL;
						}
					} else {
						control.setBackground(bgColor);
					}
				}

				Color fg = properties.getColor(sConfigID + ".fgcolor" + sSuffix);
				if (fg != null) {
					control.setForeground(fg);
				}

				setBackground(sConfigID + ".background", sSuffix);

				String sCursor = properties.getStringValue(sConfigID + ".cursor");
				if (sCursor != null && sCursor.length() > 0) {
					if (sCursor.equalsIgnoreCase("hand")) {
						control.addListener(SWT.MouseEnter,
								skin.getHandCursorListener(control.getDisplay()));
						control.addListener(SWT.MouseExit,
								skin.getHandCursorListener(control.getDisplay()));
					}
				}

				String sTooltip = properties.getStringValue(sConfigID + ".tooltip"
						+ sSuffix);
				if (sTooltip != null) {
					setTooltipAndChildren(control, sTooltip);
				}

			}

		});
		return suffix;
	}

	public String getSuffix() {
		String suffix = "";
		for (int i = 0; i < suffixes.length; i++) {
			suffix += suffixes[i];
		}
		return suffix;
	}

	public void setTooltipAndChildren(Control c, String sToolTip) {
		c.setToolTipText(sToolTip);
		if (c instanceof Composite) {
			Control[] children = ((Composite) c).getChildren();
			for (int i = 0; i < children.length; i++) {
				Control control = children[i];
				setTooltipAndChildren(control, sToolTip);
			}
		}
	}

	/**
	 * @return the properties
	 */
	public SWTSkinProperties getProperties() {
		return properties;
	}

	public void setProperties(SWTSkinProperties skinProperties) {
		this.properties = skinProperties;
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#addListener(com.aelitis.azureus.ui.swt.skin.SWTSkinObjectListener)
	 */
	public void addListener(SWTSkinObjectListener listener) {
		listeners_mon.enter();
		try {
			listeners.add(listener);
		} finally {
			listeners_mon.exit();
		}

		if (isVisible && initialized) {
			listener.eventOccured(this, SWTSkinObjectListener.EVENT_SHOW, null);
		}
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#removeListener(com.aelitis.azureus.ui.swt.skin.SWTSkinObjectListener)
	 */
	public void removeListener(SWTSkinObjectListener listener) {
		listeners_mon.enter();
		try {
			listeners.remove(listener);
		} finally {
			listeners_mon.exit();
		}
	}

	public SWTSkinObjectListener[] getListeners() {
		return (SWTSkinObjectListener[]) listeners.toArray(new SWTSkinObjectListener[0]);
	}

	public void triggerListeners(int eventType) {
		triggerListeners(eventType, null);
	}

	public void triggerListeners(final int eventType, final Object params) {
		// delay show and hide events while not initialized
		if (eventType == SWTSkinObjectListener.EVENT_SHOW
				|| eventType == SWTSkinObjectListener.EVENT_HIDE) {
			if (!initialized) {
				return;
			}

			if (eventType == SWTSkinObjectListener.EVENT_SHOW && !isVisible) {
				return;
			} else if (eventType == SWTSkinObjectListener.EVENT_HIDE && isVisible) {
				return;
			}
		} else if (eventType == SWTSkinObjectListener.EVENT_CREATED) {
			initialized = true;
		}

		// process listeners added locally
		listeners_mon.enter();
		try {
			for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
				try {
					SWTSkinObjectListener l = (SWTSkinObjectListener) iterator.next();
					l.eventOccured(this, eventType, params);
				} catch (Exception e) {
					Debug.out("Skin Event " + SWTSkinObjectListener.NAMES[eventType]
							+ " caused an error for listener added locally", e);
				}
			}
		} finally {
			listeners_mon.exit();
		}

		// process listeners added to skin
		SWTSkinObjectListener[] listeners = skin.getSkinObjectListeners(sViewID);
		for (int i = 0; i < listeners.length; i++) {
			try {
				SWTSkinObjectListener l = listeners[i];
				l.eventOccured(this, eventType, params);
			} catch (Exception e) {
				Debug.out("Skin Event " + SWTSkinObjectListener.NAMES[eventType]
						+ " caused an error for listener added to skin", e);
			}
		}

		if (eventType == SWTSkinObjectListener.EVENT_CREATED) {
			triggerListeners(isVisible ? SWTSkinObjectListener.EVENT_SHOW
					: SWTSkinObjectListener.EVENT_HIDE);
		}
	}

	protected void setViewID(String viewID) {
		sViewID = viewID;
	}

	public String getViewID() {
		return sViewID;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#dispose()
	public void dispose() {
		if (control != null && !control.isDisposed()) {
			control.dispose();
		}
	}

	public boolean isDisposed() {
		return control == null || control.isDisposed();
	}

	public void setTooltipByID(final String id) {
		if (isDisposed()) {
			return;
		}
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				control.setToolTipText(MessageText.getString(id));
			}
		});
	}

	public void paintControl(GC gc) {
		if (bgColor != null) {
			gc.setBackground(bgColor);
		}

		if (borderType > 0) {
			try {
				gc.setAdvanced(true);
				gc.setAntialias(SWT.ON);
			} catch (Exception e) {

			}
			Rectangle bounds = control.getBounds();
			if (borderType == BORDER_ROUNDED_FILL) {
				gc.fillRoundRectangle(0, 0, bounds.width, bounds.height, 10, 8);
			} else {
				Color oldFG = gc.getForeground();
				gc.setForeground(bgColor);
				gc.drawRoundRectangle(0, 0, bounds.width - 1, bounds.height - 1, 10, 8);
				gc.setForeground(oldFG);
			}
		}
	}
}
