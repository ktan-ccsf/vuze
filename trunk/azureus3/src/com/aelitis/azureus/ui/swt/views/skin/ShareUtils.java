package com.aelitis.azureus.ui.swt.views.skin;

import org.gudy.azureus2.core3.download.DownloadManager;

public class ShareUtils
{

	private static ShareUtils instance;

	public static ShareUtils getInstance() {
		if (null == instance) {
			instance = new ShareUtils();
		}
		return instance;
	}

	public void shareTorrent(DownloadManager dm) {
		System.out.println("Sharing:");//KN: sysout
		System.out.println("\t: " + dm.getDisplayName());//KN: sysout
		
		

	}
}
