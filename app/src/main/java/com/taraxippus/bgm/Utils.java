package com.taraxippus.bgm;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class Utils
{
	public static String unescapeJava(String str) 
	{
		if (str == null)
			return null;
		
		try
		{
			StringWriter writer = new StringWriter(str.length());
			unescapeJava(writer, str);
			return writer.toString();
		} 
		catch (IOException ioe) 
		{
			throw new RuntimeException(ioe);
		}
	}
		
	public static void unescapeJava(Writer out, String str) throws IOException 
	{
		if (out == null)
			throw new IllegalArgumentException("The Writer must not be null");
		
		if (str == null)
			return;
			
		int sz = str.length();
		StringBuilder unicode = new StringBuilder(4);
		boolean hadSlash = false;
		boolean inUnicode = false;
		char ch;
		int value;
		for (int i = 0; i < sz; i++) 
		{
			ch = str.charAt(i);
			if (inUnicode)
			{
				unicode.append(ch);
				if (unicode.length() == 4)
				{
					try 
					{
						value = Integer.parseInt(unicode.toString(), 16);
						out.write((char) value);
						unicode.setLength(0);
						inUnicode = false;
						hadSlash = false;
					}
					catch (NumberFormatException nfe)
					{
						throw new RuntimeException("Unable to parse unicode value: " + unicode, nfe);
					}
				}
				continue;
			}
			
			if (hadSlash)
			{
				hadSlash = false;
				switch (ch)
				{
					case '\\':
						out.write('\\');
						break;
					case '\'':
						out.write('\'');
						break;
					case '\"':
						out.write('"');
						break;
					case 'r':
						out.write('\r');
						break;
					case 'f':
						out.write('\f');
						break;
					case 't':
						out.write('\t');
						break;
					case 'n':
						out.write('\n');
						break;
					case 'b':
						out.write('\b');
						break;
					case 'u':
						inUnicode = true;
						break;
					default :
						out.write(ch);
						
					break;
				}
				continue;
			} 
			else if (ch == '\\')
			{
				hadSlash = true;
				continue;
			}
			out.write(ch);
		}
		if (hadSlash)
		{      
			out.write('\\');
		}
	}
}
