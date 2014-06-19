package sftp;

import java.net.*;
import java.util.*;
import java.io.*;

public class Helper{
	//bytes
	static byte[] get_bytes(byte[] input,int start,int len){
		int size = (len>input.length-start) ? (input.length-start) : len;
		byte[] tmp = new byte[size];
		for(int i=start;i<start+len && i<input.length;i++)
			tmp[i-start] = input[i];
		return tmp;
	}
	static void set_bytes(byte[] dst,int start,byte[] src){
		for(int i=0;i<src.length;i++)
			dst[i+start] = src[i];	//if error, then exception
	}
	static String get_string(byte[] src){
		StringBuilder t = new StringBuilder();
		for(byte x:src)
			t.append((char)x);
		return t.toString();
	}
	//numbers
	static short combine_byte(byte l,byte h){
		int low = ((int)l)&0xff;
		int high = (h<<8)&0xffff;
		return (short)(low+high);
	}
	
}
