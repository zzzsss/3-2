package sftp;

import java.net.*;
import java.io.*;

abstract public class Connection {
	//debug
	static final boolean _DEBUG = false; 
	static final boolean _RETRANSMIT = true;
	//final values
	static final int IDLE=0,WAIT=1;
	static final int LARGEST_FILE=512*1024*16;	//8M is the largest
	static final int SFTP_PORT = 10000;
	/* the server states... */
	boolean is_open=false;
	int state=IDLE;
	short session_num=0;
	/* data --- in one session */
	Session cur_session;
	File file_to_write;
	QueryDatagram session_driver;
	/* connect */
	InetSocketAddress sock_addr;
	DatagramSocket sock;
	
	public Connection(InetSocketAddress addr,DatagramSocket s){
		sock_addr = addr;
		sock = s;
	}
	
	//p must be legal SftpDatagram
	abstract public void deal_datagram(SftpDatagram p);
	
	static void deubg_println(String x){
		System.err.println(x);
	}
}
