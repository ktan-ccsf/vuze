/*
 * Created on 03-Aug-2004
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

package com.aelitis.azureus.core.diskmanager.cache.impl;

/**
 * @author parg
 *
 */

import java.io.File;
import java.util.*;

import org.gudy.azureus2.core3.util.DirectByteBuffer;
import org.gudy.azureus2.core3.config.*;
import org.gudy.azureus2.core3.logging.*;

import com.aelitis.azureus.core.diskmanager.cache.*;
import com.aelitis.azureus.core.diskmanager.file.*;

public class 
CacheFileManagerImpl 
	implements CacheFileManager
{
	protected boolean	cache_enabled;
	protected long		cache_size;
	protected long		cache_minimum_free_size;
	protected long		cache_space_free;
		
	protected FMFileManager		file_manager;

		// access order
	
	protected LinkedHashMap		cache_entries = new LinkedHashMap(1024, 0.75f, true );
	
	protected long				cache_bytes_written;
	protected long				cache_bytes_read;
	protected long				file_bytes_written;
	protected long				file_bytes_read;
	
	public
	CacheFileManagerImpl()
	{
		file_manager	= FMFileManagerFactory.getSingleton();
		
		cache_enabled	= COConfigurationManager.getBooleanParameter( "diskmanager.perf.cache.enable" );
		
		cache_size		= 1024*1024*COConfigurationManager.getIntParameter( "diskmanager.perf.cache.size" );
		
		cache_minimum_free_size	= cache_size/4;
		
		cache_space_free		= cache_size;
		
		LGLogger.log( "DiskCache: enabled = " + cache_enabled + ", size = " + cache_size + " MB" );
	}
	
	public CacheFile
	createFile(
		final CacheFileOwner	owner,
		File					file )
	
		throws CacheFileManagerException
	{
		try{
			FMFile	fm_file	= 
				file_manager.createFile(
					new FMFileOwner()
					{
						public String
						getName()
						{
							return( owner.getCacheFileOwnerName());
						}
					}, file );
				
			return( new CacheFileImpl( this, fm_file ));
			
		}catch( FMFileManagerException e ){
			
			rethrow( e );
			
			return( null );
		}
	}
	
	protected boolean
	isCacheEnabled()
	{
		return( cache_enabled );
	}
	
	protected CacheEntry
	allocateCacheSpace(
		CacheFileImpl		file,
		DirectByteBuffer	buffer,
		long				file_position,
		int					length )
	
		throws CacheFileManagerException
	{
		boolean	ok 	= false;
		boolean	log	= false;
		
		while( !ok ){
			
			synchronized( this ){
			
				if ( length < cache_space_free || cache_space_free == cache_size ){
				
					ok	= true;
				}
			}
			
			if ( !ok ){
				
				log	= true;
				
				CacheEntry	oldest = (CacheEntry)cache_entries.keySet().iterator().next();
				
				long	old_cw	= cache_bytes_written;
				
				oldest.getFile().flushCache( true, cache_minimum_free_size );
				
				LGLogger.log( "DiskCache: cache full, flushed " + ( cache_bytes_written - old_cw ) + " from " + oldest.getFile().getName());
			}
		}
		
		synchronized( this ){
			
			cache_space_free	-= length;
			
			// System.out.println( "Total cache space = " + cache_space_free );
			
			CacheEntry	entry = new CacheEntry( file, buffer, file_position, length );
			
			cache_entries.put( entry, entry );
			
			if ( log ){
				
				LGLogger.log( 
						"DiskCache: cr=" + cache_bytes_read + ",cw=" + cache_bytes_written+
						",fr=" + file_bytes_read + ",fw=" + file_bytes_written ); 
			}
			
			return( entry );
		}
	}
	
	protected void
	cacheEntryUsed(
		CacheEntry		entry )
	{
		synchronized( this ){
		
			cache_entries.get( entry );
		}
	}
	
	protected void
	releaseCacheSpace(
		CacheEntry		entry )
	{
		entry.getBuffer().returnToPool();
		
		synchronized( this ){

			cache_space_free	+= entry.getLength();
			
			cache_entries.remove( entry );

			// System.out.println( "Total cache space = " + cache_space_free );
		}
	}
	
	protected synchronized void
	cacheBytesWritten(
		int		num )
	{
		cache_bytes_written	+= num;
	}
	
	protected synchronized void
	cacheBytesRead(
		int		num )
	{
		cache_bytes_read	+= num;
	}
	
	protected synchronized void
	fileBytesWritten(
		int		num )
	{
		file_bytes_written	+= num;
	}
	
	protected synchronized void
	fileBytesRead(
		int		num )
	{
		file_bytes_read	+= num;
	}
	
	protected void
	rethrow(
		FMFileManagerException e )
	
		throws CacheFileManagerException
	{
		Throwable 	cause = e.getCause();
		
		if ( cause != null ){
			
			throw( new CacheFileManagerException( e.getMessage(), cause ));
		}
		
		throw( new CacheFileManagerException( e.getMessage(), e ));
	}
}
