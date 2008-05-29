/*
 * Created on Apr 1, 2008
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

package org.gudy.azureus2.platform.macosx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.gudy.azureus2.core3.util.FileUtil;

public class 
PListEditor 
{	
	private String plistFile;
	
	
	public 
	PListEditor(
		String plistFile )
	
		throws IOException
	{
		this.plistFile = plistFile;
		
		File	file  = new File( plistFile );
		
		if ( !file.exists()){
			
			throw( new IOException( "plist file '" + file + "' doesn't exist" ));
		}
		
		if ( !file.canWrite()){
			
			throw( new IOException( "plist file '" + file + "' is read only" ));
		}
	}
	
	public void 
	setFileTypeExtensions(
		String[] extensions )
	
		throws IOException
	{
		StringBuffer value = new StringBuffer();
		StringBuffer find = new StringBuffer();
		find.append("(?s).*?<key>CFBundleDocumentTypes</key>\\s*<array>.*?<key>CFBundleTypeExtensions</key>\\s*<array>");
		for(int i = 0 ; i < extensions.length ; i++) {
			value.append("\n\t\t\t\t<string>");
			value.append(extensions[i]);
			value.append("</string>");
			
			find.append(".*?");
			find.append(extensions[i]);
		}
		value.append("\n\t\t\t");
		
		find.append(".*?</array>.*");
		String match = "(?s)(<key>CFBundleDocumentTypes</key>\\s*<array>.*?<key>CFBundleTypeExtensions</key>\\s*<array>)(.*?)(</array>)";
		
		setValue(find.toString(), match, value.toString());
	}
	
	public void 
	setSimpleStringValue(
		String key,
		String value)
	
		throws IOException
	{
		String find = "(?s).*?<key>" + key + "</key>\\s*" + "<string>" + value + "</string>.*";
		String match = "(?s)(<key>" + key + "</key>\\s*" + "<string>)(.*?)(</string>)";
		setValue(find, match, value);
	}
	
	private boolean 
	isValuePresent(
		String match )
	
		throws IOException
	{
		String fileContent = getFileContent();
		
		//System.out.println("Searching for:\n" + match);
		return fileContent.matches(match);
	}
	
	/**
	 * 
	 * @param find the regex expression to find if the value is already present
	 * @param match the regex expression that will match for the replace, it needs to capture 3 groups, the 2nd one being replaced by value
	 * @param value the value that replaces the 2nd match group
	 */
	private void 
	setValue(
		String find,
		String match,
		String value)
	
		throws IOException
	{
		String fileContent = getFileContent();
		
		if( !isValuePresent(find)) {
			//System.out.println("Changing " +plistFile);
			fileContent = fileContent.replaceFirst(match, "$1"+value + "$3");
			setFileContent(fileContent);
			touchFile();
		}
	}
	
	private String 
	getFileContent()
		throws IOException
	{
		return FileUtil.readFileAsString(new File(plistFile), 64*1024, "UTF-8" );
	}
	
	private void 
	setFileContent(
		String fileContent )
	
		throws IOException
	{
		File	file		= new File( plistFile );
		
		File	backup_file = new File( plistFile + ".bak" );
		
		if ( file.exists()){
			
			if ( !FileUtil.copyFile( file, backup_file )){
				
				throw( new IOException( "Failed to backup plist file prior to modification" ));
			}
		}
		
		boolean	ok = false;
		
		try{
			FileOutputStream fos = null;
			
			try{
				
				fos = new FileOutputStream(plistFile);
				
				fos.write(fileContent.getBytes("UTF-8"));
	
			} finally {
				
				if( fos != null ){
					
					fos.close();
					
					ok = true;
				}
			}
		}finally{
			if ( ok ){
				
				backup_file.delete();
				
			}else{
				
				if ( backup_file.exists()){
					
					File	bork_file = new File( plistFile + ".bad" );

					file.renameTo( bork_file );
					
					file.delete();
					
					backup_file.renameTo( file );
				}
			}
		}
	}
	
	private void 
	touchFile()
	{
		String command[] = new String[] { "touch", plistFile };
		
		try{
			Runtime.getRuntime().exec(command);
			
		} catch(Exception e) {
			
			e.printStackTrace();
		}
	}
	
	public static void main(String args[]) {
		try{
			PListEditor editor = new PListEditor("/Applications/Azureus.app/Contents/Info.plist");
			editor.setFileTypeExtensions(new String[] {"torrent","tor","vuze"});
			editor.setSimpleStringValue("CFBundleName", "Vuze");
			editor.setSimpleStringValue("CFBundleTypeName", "Vuze Download");
			editor.setSimpleStringValue("CFBundleGetInfoString","Vuze");
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}

}
