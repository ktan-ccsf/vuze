/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.core.metasearch;

import java.util.Map;
import java.io.IOException;

import com.aelitis.azureus.core.metasearch.impl.EngineFactoryImpl;

public class 
EngineFactory 
{
	public static Engine
	importFromBEncodedMap(
		Map		map )
	
		throws IOException
	{
		return( EngineFactoryImpl.importFromBEncodedMap( map ));
	}
	
	public static Engine
	importFromJSONString(
		int			type,
		long		id,
		long		last_updated,
		String		name,
		String		content )
	
		throws IOException
	{
		return( EngineFactoryImpl.importFromJSONString( type, id, last_updated, name, content ));
	}
}
