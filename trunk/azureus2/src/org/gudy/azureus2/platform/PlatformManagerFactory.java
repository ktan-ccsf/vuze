/*
 * Created on 18-Apr-2004
 * Created by Paul Gardner
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
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
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package org.gudy.azureus2.platform;

import org.gudy.azureus2.platform.win32.PlatformManagerImpl;

/**
 * @author parg
 *
 */
public class 
PlatformManagerFactory 
{
	protected static boolean				init_tried;
	protected static PlatformManager		platform_manager;
	
	public static synchronized PlatformManager
	getPlatformManager()
	
		throws PlatformManagerException
	{
		if ( platform_manager == null && !init_tried ){
		
			init_tried	= true;
						    
			if ( getPlatformType() == PlatformManager.PT_WINDOWS ){
				
				platform_manager = PlatformManagerImpl.getSingleton();
			}
		}
		
		return( platform_manager );
	}
	
	public static int
	getPlatformType()
	{
		String OS = System.getProperty("os.name").toLowerCase();
	    
		if ( OS.indexOf("windows") >= 0 ){
			
			return( PlatformManager.PT_WINDOWS );
		}else{
			
			return( PlatformManager.PT_OTHER );
		}
	}
}
