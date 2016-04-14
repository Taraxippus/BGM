package com.taraxippus.bgm;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class StringUtils
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
	
	public static int fromUrlTime(String text)
	{
		try
		{
			int time = 0;

			int index = Math.max(text.indexOf('h'), text.indexOf('H'));
			if (index != -1)
			{
				time += Integer.parseInt(text.substring(0, index)) * 60 * 60;
				text = text.substring(0, index + 1);
			}
			
			index = Math.max(text.indexOf('m'), text.indexOf('M'));
			if (index != -1)
			{
				time += Integer.parseInt(text.substring(0, index)) * 60;
				text = text.substring(index + 1);
			}
			
			index = Math.max(text.indexOf('s'), text.indexOf('S'));
			if (index != -1)
			{
				time += Integer.parseInt(text.substring(0, index));
				text = text.substring(index + 1);
			}
			
			return time;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return 0;
		}
	}
	
	public static String toUrlTime(int time)
	{
		int hours = time / 60 / 60;
		int minutes = (time % (60 * 60)) / 60;
		int seconds = time % 60;

		return (hours > 0 ? hours + "h" : "") + (minutes > 0 ?  minutes + "m" : "") + (seconds > 0 ? seconds + "s" : "");
	}
	
	public static int fromTime(String text)
	{
		try
		{
			int time = 0;

			int index = text.lastIndexOf(':');

			if (index == -1)
			{
				time += Integer.parseInt(text);
			}
			else
			{
				time += Integer.parseInt(text.substring(index + 1));

				int index1 = text.lastIndexOf(':', index - 1);
				if (index1 != -1)
				{
					time += Integer.parseInt(text.substring(index1 + 1, index)) * 60;
					time += Integer.parseInt(text.substring(0, index1)) * 60;
				}
				else
					time += Integer.parseInt(text.substring(0, index)) * 60;

			}

			return time;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return 0;
		}
	}

	public static String toTime(int time)
	{
		int hours = time / 60 / 60;
		int minutes = (time % (60 * 60)) / 60;
		int seconds = time % 60;

		return (hours > 0 ? hours + ":" + String.format("%02d", minutes) + ":" : minutes + ":") + String.format("%02d", seconds);
	}
	
}
