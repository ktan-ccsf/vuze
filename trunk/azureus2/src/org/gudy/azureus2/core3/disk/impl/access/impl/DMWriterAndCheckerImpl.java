/*
 * Created on 31-Jul-2004
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

package org.gudy.azureus2.core3.disk.impl.access.impl;

import java.util.LinkedList;
import java.util.List;
import java.nio.ByteBuffer;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.disk.DiskManager;
import org.gudy.azureus2.core3.disk.impl.DiskManagerFileInfoImpl;
import org.gudy.azureus2.core3.disk.impl.DiskManagerHelper;
import org.gudy.azureus2.core3.disk.impl.PieceList;
import org.gudy.azureus2.core3.disk.impl.PieceMapEntry;
import org.gudy.azureus2.core3.disk.impl.access.*;
import org.gudy.azureus2.core3.logging.LGLogger;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.PEPiece;
import org.gudy.azureus2.core3.util.*;


import com.aelitis.azureus.core.diskmanager.cache.*;

/**
 * @author parg
 *
 */

public class 
DMWriterAndCheckerImpl 
	implements DMWriterAndChecker
{
	protected static final int	QUEUE_REPORT_CHUNK	= 32;
	
	private static int			global_write_queue_block_sem_size;
	private static AESemaphore	global_write_queue_block_sem;
	private static int			global_write_queue_block_sem_next_report_size;
	
	private static int			global_check_queue_block_sem_size;
	private static AESemaphore	global_check_queue_block_sem;
	private static int			global_check_queue_block_sem_next_report_size;
	
	static{
		int	write_limit_blocks = COConfigurationManager.getIntParameter("DiskManager Write Queue Block Limit", 0);

		global_write_queue_block_sem_size	= write_limit_blocks==0?1024:write_limit_blocks;
		
		global_write_queue_block_sem_next_report_size	= global_write_queue_block_sem_size - QUEUE_REPORT_CHUNK;
		
		global_write_queue_block_sem = new AESemaphore("writeQ", global_write_queue_block_sem_size);
		
		/*
		if ( write_limit_blocks == 0 ){
			
			global_write_queue_block_sem.releaseForever();
		}
		*/
		
		int	check_limit_pieces = COConfigurationManager.getIntParameter("DiskManager Check Queue Piece Limit", 0);

		global_check_queue_block_sem_size	= check_limit_pieces==0?512:check_limit_pieces;
		
		global_check_queue_block_sem_next_report_size	= global_check_queue_block_sem_size - QUEUE_REPORT_CHUNK;
		
		global_check_queue_block_sem = new AESemaphore("checkQ", global_check_queue_block_sem_size);
		
		/*
		if ( check_limit_pieces == 0 ){
			
			global_check_queue_block_sem.releaseForever();
		}
		*/
		
		// System.out.println( "global writes = " + write_limit_blocks + ", global checks = " + check_limit_pieces );
	}

	private DiskManagerHelper		disk_manager;
	private DMReader				reader;
	
	private DiskWriteThread writeThread;
	private List 			writeQueue;
	private List 			checkQueue;
	private AESemaphore		writeCheckQueueSem;
	private	AEMonitor		writeCheckQueueLock_mon	= new AEMonitor( "DMW&C:WCQ");
		
	protected ConcurrentHasherRequest	current_hash_request;
	
	protected Md5Hasher md5;			

	protected boolean	bOverallContinue		= true;
	
	protected int		pieceLength;
	protected int		lastPieceLength;
	protected long		totalLength;
	
	protected int		nbPieces;
	
	protected AEMonitor	this_mon	= new AEMonitor( "DMW&C" );
	
	public
	DMWriterAndCheckerImpl(
		DiskManagerHelper	_disk_manager,
		DMReader			_reader )
	{
		disk_manager	= _disk_manager;
		reader			= _reader;
		
		pieceLength		= disk_manager.getPieceLength();
		lastPieceLength	= disk_manager.getLastPieceLength();
		totalLength		= disk_manager.getTotalLength();
		
		nbPieces		= disk_manager.getNumberOfPieces();
		
		md5 	= new Md5Hasher();
	}
	
	public void
	start()
	{
		bOverallContinue	= true;
		
		writeQueue			= new LinkedList();
		checkQueue			= new LinkedList();
		writeCheckQueueSem	= new AESemaphore("writeCheckQ");
   				
		writeThread = new DiskWriteThread();
		writeThread.start();
	}
	
	public void
	stop()
	{
		try{
			this_mon.enter();
			
			bOverallContinue	= false;
			
			if ( current_hash_request != null ){
				
				current_hash_request.cancel();
			}
		}finally{
			
			this_mon.exit();
		}
		
		if (writeThread != null){
			
			writeThread.stopIt();
		}
	}
	
	public boolean 
	isChecking() 
	{
	   return (checkQueue.size() != 0);
	}

	public boolean 
	zeroFile( 
		DiskManagerFileInfoImpl file, 
		long 					length ) 
	{
		CacheFile	cache_file = file.getCacheFile();
		
		long written = 0;
		
		try{
			if( length == 0 ){ //create a zero-length file if it is listed in the torrent
				
				cache_file.setLength( 0 );
				
			}else{
					
		        DirectByteBuffer	buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_DM_ZERO,pieceLength);
		    
		        try{
			        buffer.limit(DirectByteBuffer.SS_DW, pieceLength);
			        
					for (int i = 0; i < buffer.limit(DirectByteBuffer.SS_DW); i++){
						
						buffer.put(DirectByteBuffer.SS_DW, (byte)0);
					}
					
					buffer.position(DirectByteBuffer.SS_DW, 0);

					while (written < length && bOverallContinue){
						
						int	write_size = buffer.capacity(DirectByteBuffer.SS_DW);
						
						if ((length - written) < write_size ){
            	
							write_size = (int)(length - written);
						}
            
						buffer.limit(DirectByteBuffer.SS_DW, write_size);
             
						cache_file.write( buffer, written );
            
						buffer.position(DirectByteBuffer.SS_DW, 0);
            
						written += write_size;
            
						disk_manager.setAllocated( disk_manager.getAllocated() + write_size );
            
						disk_manager.setPercentDone((int) ((disk_manager.getAllocated() * 1000) / totalLength));
					}
		        }finally{
		        	
		        	buffer.returnToPool();
		        }
				
			}
			
			if (!bOverallContinue){
						
				cache_file.close();
				   
				return false;
			}
		} catch (Exception e) {  Debug.printStackTrace( e );  }
			
		return true;
	}
	  

	public void 
	aSyncCheckPiece(
		int pieceNumber ) 
	{  	
			// recursion here will deadlock the write thread
		
		if ( Thread.currentThread() != writeThread ){			

			global_check_queue_block_sem.reserve();
		}
	   		
		if ( global_check_queue_block_sem.getValue() < global_check_queue_block_sem_next_report_size ){
			
	    	// Debug.out( "Disk Manager check queue size exceeds " + ( global_check_queue_block_sem_size - global_check_queue_block_sem_next_report_size ));

			global_check_queue_block_sem_next_report_size -= QUEUE_REPORT_CHUNK;
		}
		
		// System.out.println( "check queue size = " + ( global_check_queue_block_sem_size - global_check_queue_block_sem.getValue()));
		
	  	try{
	  		writeCheckQueueLock_mon.enter();
	 		
	   		checkQueue.add(new QueueElement(pieceNumber, 0, null, null));
	   		
	    }finally{
	    	
	    	writeCheckQueueLock_mon.exit();
	    }
	   		
	   	writeCheckQueueSem.release();
	}  
	  
	public boolean 
	checkPiece(
		int 				pieceNumber )
	{
		try{
			this_mon.enter();
		
	        DirectByteBuffer	buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_DM_CHECK,pieceLength);
	                
			try{
			    if( COConfigurationManager.getBooleanParameter( "diskmanager.friendly.hashchecking" ) ){
			    	
			    	try{  Thread.sleep( 100 );  }catch(Exception e) { Debug.printStackTrace( e ); }
			    }
			       
			    boolean[]	pieceDone	= disk_manager.getPiecesDone();
			    
			    if ( bOverallContinue == false ){
			    	
			    	return false;
			    }
	
			    buffer.position(DirectByteBuffer.SS_DW, 0);
	
				int length = pieceNumber < nbPieces - 1 ? pieceLength : lastPieceLength;
	
				buffer.limit(DirectByteBuffer.SS_DW, length);
	
					//get the piece list
				
				PieceList pieceList = disk_manager.getPieceList(pieceNumber);
	
					//for each piece
				
				for (int i = 0; i < pieceList.size(); i++) {
					
						//get the piece and the file
					
					PieceMapEntry tempPiece = pieceList.get(i);
		            
	
					try {
		                    
							   //if the file is large enough
						
						if ( tempPiece.getFile().getCacheFile().getLength() >= tempPiece.getOffset()){
							
							tempPiece.getFile().getCacheFile().read(buffer, tempPiece.getOffset());
							
						}else{
								   //too small, can't be a complete piece
							
							buffer.clear(DirectByteBuffer.SS_DW);
							
							pieceDone[pieceNumber] = false;
							
							return false;
						}
					}catch (Exception e){
						
						Debug.printStackTrace( e );
					}
				}
	
				try {
		      
					if (bOverallContinue == false) return false;
		      
		      		buffer.position(DirectByteBuffer.SS_DW, 0);
	
					// byte[] testHash = hasher.calculateHash(buffer.getBuffer());
		      		
		      			// running torrents have highest priority. 
		      			// for checking torrents, smaller torrents have higher priority to get them
		      			// checked and running ASAP
		      		
		      		long	hash_priority	= disk_manager.getState() == DiskManager.CHECKING?disk_manager.getTotalLength():0;
		      		
		      		try{
		      			this_mon.enter();
		      			
		    		    if ( !bOverallContinue ){
		    		    	
		    		    	return false;
		    		    }
	
		    		    current_hash_request = ConcurrentHasher.getSingleton().addRequest(buffer.getBuffer(DirectByteBuffer.SS_DW),hash_priority);
		      		}finally{
		      			
		      			this_mon.exit();
		      		}
					
					byte[] testHash = current_hash_request.getResult();
					
					current_hash_request	= null;
					
					if ( testHash == null ){
					
							// cancelled
						
						return( false );
					}
					
					byte[]	required_hash = disk_manager.getPieceHash(pieceNumber);
					
					int i = 0;
					
					for (i = 0; i < 20; i++){
						
						if (testHash[i] != required_hash[i]){
							
							break;
						}
					}
					
					if (i >= 20){
						
							//mark the piece as done..
						
						if (!pieceDone[pieceNumber]) {
							
							pieceDone[pieceNumber] = true;
							
							disk_manager.setRemaining( disk_manager.getRemaining() - length );
							
							disk_manager.computeFilesDone(pieceNumber);
						}
						
						return true;
					}
					
					if( pieceDone[pieceNumber]){
						
						pieceDone[pieceNumber] = false;
						
						disk_manager.setRemaining( disk_manager.getRemaining() + length );
					}
					
				} catch (Exception e) {
					
					Debug.printStackTrace( e );
				}
				
				return false;
					
			}finally{
				
				buffer.returnToPool();
			}
		}finally{
			
			this_mon.exit();
		}
	}
		
		
	private byte[] 
	computeMd5Hash(
		DirectByteBuffer buffer) 
	{ 	
	    md5.reset();
	    
	    int position = buffer.position(DirectByteBuffer.SS_DW);
	    
	    md5.update(buffer.getBuffer(DirectByteBuffer.SS_DW));
	    
	    buffer.position(DirectByteBuffer.SS_DW, position);
	    
	    ByteBuffer md5Result	= ByteBuffer.allocate(16);
	    
	    md5Result.position(0);
	    
	    md5.finalDigest( md5Result );
	    
	    byte[] result = new byte[16];
	    
	    md5Result.position(0);
	    
	    for(int i = 0 ; i < result.length ; i++) {
	    	
	      result[i] = md5Result.get();
	    }   
	    
	    return result;    
	  }
	  
	  private void MD5CheckPiece(int pieceNumber,boolean correct) {
	    PEPiece piece = disk_manager.getPeerManager().getPieces()[pieceNumber];
	    if(piece == null) {
	      return;
	    }
	    PEPeer[] writers = piece.getWriters();
	    int offset = 0;
	    for(int i = 0 ; i < writers.length ; i++) {
	      int length = piece.getBlockSize(i);
	      PEPeer peer = writers[i];
	      if(peer != null) {
	        DirectByteBuffer buffer = reader.readBlock(pieceNumber,offset,length);
	        byte[] hash = computeMd5Hash(buffer);
	        buffer.returnToPool();
	        buffer = null;
	        piece.addWrite(i,peer,hash,correct);        
	      }
	      offset += length;
	    }        
	  }
	  
	 
	/**
	 * @param e
	 * @return FALSE if the write failed for some reason. Error will have been reported
	 * and queue element set back to initial state to allow a re-write attempt later
	 */
	
	private boolean 
	dumpBlockToDisk(
		QueueElement queue_entry ) 
	{
		int pieceNumber 	= queue_entry.getPieceNumber();
		int offset		 	= queue_entry.getOffset();
		DirectByteBuffer buffer 	= queue_entry.getData();
		int	initial_buffer_position = buffer.position(DirectByteBuffer.SS_DW);

		PieceMapEntry current_piece = null;
		
		try{
			int previousFilesLength = 0;
			int currentFile = 0;
			PieceList pieceList = disk_manager.getPieceList(pieceNumber);
			current_piece = pieceList.get(currentFile);
			long fileOffset = current_piece.getOffset();
			while ((previousFilesLength + current_piece.getLength()) < offset) {
				previousFilesLength += current_piece.getLength();
				currentFile++;
				fileOffset = 0;
				current_piece = pieceList.get(currentFile);
			}
	
			boolean	buffer_handed_over	= false;
			
			//Now tempPiece points to the first file that contains data for this block
			while (buffer.hasRemaining(DirectByteBuffer.SS_DW)) {
				current_piece = pieceList.get(currentFile);
	
				if (current_piece.getFile().getAccessMode() == DiskManagerFileInfo.READ){
		
					LGLogger.log(0, 0, LGLogger.INFORMATION, "Changing " + current_piece.getFile().getName() + " to read/write");
						
					current_piece.getFile().setAccessMode( DiskManagerFileInfo.WRITE );
				}
				
				int realLimit = buffer.limit(DirectByteBuffer.SS_DW);
					
				long limit = buffer.position(DirectByteBuffer.SS_DW) + ((current_piece.getFile().getLength() - current_piece.getOffset()) - (offset - previousFilesLength));
	       
				if (limit < realLimit){
					
					buffer.limit(DirectByteBuffer.SS_DW, (int)limit);
				}
	
					// surely we always have remaining here?
				
				if ( buffer.hasRemaining(DirectByteBuffer.SS_DW) ){

					long	pos = fileOffset + (offset - previousFilesLength);
					
					if ( limit < realLimit ){
						
						current_piece.getFile().getCacheFile().write( buffer, pos );
						
					}else{
						
						current_piece.getFile().getCacheFile().writeAndHandoverBuffer( buffer, pos );
						
						buffer_handed_over	= true;
						
						break;
					}
				}
					
				buffer.limit(DirectByteBuffer.SS_DW, realLimit);
				
				currentFile++;
				fileOffset = 0;
				previousFilesLength = offset;
			}
			
			if ( !buffer_handed_over ){
			
					// the last write for a block should always be handed over, hence we
					// shouln't get here....
				
				Debug.out( "buffer not handed over to file cache!" );
				
				buffer.returnToPool();
			}
			
			return( true );
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
			String file_name = current_piece==null?"<unknown>":current_piece.getFile().getName();
						
			disk_manager.setErrorMessage( Debug.getNestedExceptionMessage(e) + " when processing file '" + file_name + "'" );
			
			LGLogger.logAlert( LGLogger.AT_ERROR, disk_manager.getErrorMessage() );
			
			buffer.position(DirectByteBuffer.SS_DW, initial_buffer_position);
			
			return( false );
		}
	}
  
	
	public void 
	writeBlock(
		int pieceNumber, 
		int offset, 
		DirectByteBuffer data,
		PEPeer sender) 
	{		
			// recursive entry here will deadlock the writeThread
		
		if ( Thread.currentThread() != writeThread ){
			
			global_write_queue_block_sem.reserve();
		}
		
		if ( global_write_queue_block_sem.getValue() < global_write_queue_block_sem_next_report_size ){
			
			LGLogger.log( "Disk Manager write queue size exceeds " + ( global_write_queue_block_sem_size - global_write_queue_block_sem_next_report_size ));

			global_write_queue_block_sem_next_report_size -= QUEUE_REPORT_CHUNK;
		}
		
		// System.out.println( "write queue size = " + ( global_write_queue_block_sem_size - global_write_queue_block_sem.getValue()));

		// System.out.println( "reserved global write slot (buffer = " + data.limit() + ")" );
		
		try{
			writeCheckQueueLock_mon.enter();
			
			writeQueue.add(new QueueElement(pieceNumber, offset, data,sender));
		}finally{
			
			writeCheckQueueLock_mon.exit();
		}
		
		writeCheckQueueSem.release();
	}

  
	public boolean checkBlock(int pieceNumber, int offset, DirectByteBuffer data) {
		if (pieceNumber < 0) {
      LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK1: pieceNumber="+pieceNumber+" < 0");
			return false;
    }
		if (pieceNumber >= this.nbPieces) {
      LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK1: pieceNumber="+pieceNumber+" >= this.nbPieces="+this.nbPieces);
			return false;
    }
		int length = this.pieceLength;
		if (pieceNumber == nbPieces - 1) {
			length = this.lastPieceLength;
    }
		if (offset < 0) {
      LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK1: offset="+offset+" < 0");
			return false;
    }
		if (offset > length) {
      LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK1: offset="+offset+" > length="+length);
			return false;
    }
		int size = data.remaining(DirectByteBuffer.SS_DW);
		if (offset + size > length) {
      LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK1: offset="+offset+" + size="+size+" > length="+length);
			return false;
    }
		return true;
	}
  

	public boolean checkBlock(int pieceNumber, int offset, int length) {
		if (length > 65536) {
		  LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK2: length="+length+" > 65536");
		  return false;
		}
		if (pieceNumber < 0) {
		  LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK2: pieceNumber="+pieceNumber+" < 0");
		  return false;
      }
		if (pieceNumber >= this.nbPieces) {
		  LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK2: pieceNumber="+pieceNumber+" >= this.nbPieces="+this.nbPieces);
		  return false;
      }
		int pLength = this.pieceLength;
		if (pieceNumber == this.nbPieces - 1)
			pLength = this.lastPieceLength;
		if (offset < 0) {
		  LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK2: offset="+offset+" < 0");
		  return false;
		}
		if (offset > pLength) {
		  LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK2: offset="+offset+" > pLength="+pLength);
		  return false;
		}
		if (offset + length > pLength) {
		  LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK2: offset="+offset+" + length="+length+" > pLength="+pLength);
		  return false;
		}
		if(!disk_manager.getPiecesDone()[pieceNumber]) {
		  LGLogger.log(0, 0, LGLogger.ERROR, "CHECKBLOCK2: pieceNumber="+pieceNumber+" not done");
		  return false;
		}
		return true;
	}



	public class 
	DiskWriteThread 
		extends AEThread 
	{
		private boolean bWriteContinue = true;

		public DiskWriteThread() 
		{
			super("Disk Writer & Checker");
			
			setDaemon(true);
		}

		public void runSupport() 
		{
			while (bWriteContinue){
				
				try{
					int	entry_count = writeCheckQueueSem.reserveSet( 64 );
					
					for (int i=0;i<entry_count;i++){
						
						QueueElement	elt;
						boolean			elt_is_write;
						
						try{
							writeCheckQueueLock_mon.enter();
							
							if ( !bWriteContinue){
															
								break;
							}
							
							if ( writeQueue.size() > checkQueue.size()){
								
								elt	= (QueueElement)writeQueue.remove(0);
								
								// System.out.println( "releasing global write slot" );
	
								global_write_queue_block_sem.release();
								
								elt_is_write	= true;
								
							}else{
								
								elt	= (QueueElement)checkQueue.remove(0);
								
								global_check_queue_block_sem.release();
														
								elt_is_write	= false;
							}
						}finally{
							
							writeCheckQueueLock_mon.exit();
						}
		
						if ( elt_is_write ){
							
								//Do not allow to write in a piece marked as done.
							
							int pieceNumber = elt.getPieceNumber();
							
							if(!disk_manager.getPiecesDone()[pieceNumber]){
								
							  if ( dumpBlockToDisk(elt)){
							  
							  	disk_manager.getPeerManager().blockWritten(elt.getPieceNumber(), elt.getOffset(),elt.getSender());
							  	
							  }else{
							  	
							  		// could try and recover if, say, disk full. however, not really
							  		// worth the effort as user intervention is no doubt required to
							  		// fix the problem 
							  	
								elt.data.returnToPool();
										
								elt.data = null;
								  
								stopIt();
								
								disk_manager.setState( DiskManager.FAULTY );
								
							  }
							  
							}else{
		  
								elt.data.returnToPool();
								
							  elt.data = null;
							}
							
						}else{
							
						  boolean correct = checkPiece(elt.getPieceNumber());

						  if(!correct){
						  	
						    MD5CheckPiece(elt.getPieceNumber(),false);
						    
						    LGLogger.log(0, 0, LGLogger.ERROR, "Piece " + elt.getPieceNumber() + " failed hash check.");
						    
						  }else{
						  	
						    LGLogger.log(0, 0, LGLogger.INFORMATION, "Piece " + elt.getPieceNumber() + " passed hash check.");
						    
						    if( disk_manager.getPeerManager().needsMD5CheckOnCompletion(elt.getPieceNumber())){
						    	
						      MD5CheckPiece(elt.getPieceNumber(),true);
						    }
						  }
		
						  disk_manager.getPeerManager().asyncPieceChecked(elt.getPieceNumber(), correct);
					  }
					}
				}catch( Throwable e ){
					
					Debug.printStackTrace( e );
					
					Debug.out( "DiskWriteThread: error occurred during processing: " + e.toString());
				}
        
			}
		}

		public void stopIt(){
			
			try{
				writeCheckQueueLock_mon.enter();
				
				bWriteContinue = false;
			}finally{
				
				writeCheckQueueLock_mon.exit();
			}
			
			writeCheckQueueSem.releaseForever();
			
			while (writeQueue.size() != 0){
				
				// System.out.println( "releasing global write slot (tidy up)" );

				global_write_queue_block_sem.release();
				
				QueueElement elt = (QueueElement)writeQueue.remove(0);
				
				elt.data.returnToPool();
				
				elt.data = null;
			}
			
			while (checkQueue.size() != 0){
				
				// System.out.println( "releasing global write slot (tidy up)" );

				global_check_queue_block_sem.release();
				
				QueueElement elt = (QueueElement)checkQueue.remove(0);
			}
		}
	}
	public class QueueElement {
		private int pieceNumber;
		private int offset;
		private DirectByteBuffer data;
    private PEPeer sender; 

		public 
		QueueElement(
			int 				_pieceNumber, 
			int 				_offset, 
			DirectByteBuffer	_data, 
			PEPeer 				_sender) 
		{
			pieceNumber = _pieceNumber;
			offset = _offset;
			data = _data;
			sender = _sender;
		}  

		public int getPieceNumber() {
			return pieceNumber;
		}

		public int getOffset() {
			return offset;
		}

		public DirectByteBuffer getData() {
			return data;
		}
    
	    public PEPeer getSender() {
	      return sender;
		}
	}


}
