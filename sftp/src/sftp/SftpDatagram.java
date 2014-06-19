package sftp;

import java.io.*;
import java.util.*;
import java.net.*;

/**	The datagram format of the simple-ftp
 * @author zzs
 */
public abstract class SftpDatagram {
	//top-level types
	static final byte QUERY=0,ANSWER=1,ACK=2,DATA=3,ALIVE=4;
	//codes
	static final byte HELLO=0,BYE=1,CD=2,PWD=3,LS=4,GET=5,PUT=6,DEL=7,QUERY_END=8;
	static final byte OK=0,ERR=1,ANSWER_END=2;
	static final byte ACK_YES=0,ACK_UPDATE=1,ACK_ERR=2,ACK_END=3;
	static final byte NOTHING=0,DATA_END=1;
	static final byte TEST=0,REPLY=1,ALIVE_END=2;
	//others
	static final int INIT_TIMEOUT=1000,LIMIT_TIMIEOUT=64000;
	//user interfaces
	static final String[] CMDS_NAMES = new String[]{"init","bye","cd","pwd","ls","get","put","del"};
	static final HashMap<String,Byte> CMDS;
	static{
		CMDS = new HashMap<String,Byte>();
		for(byte i=0;i<(byte)CMDS_NAMES.length;i++)
			CMDS.put(CMDS_NAMES[i], new Byte(i));
	}
	
	//the common fields
	byte type;
	byte code;
	short session_id;
	
	//connection
	Connection the_connection;	//null for input datagrams
	
	//sending and retransmit --- they are seperated...
	boolean need_retrans;	//no use
	Timer retrans_timer;
	int timeout=INIT_TIMEOUT;
	public void set_timer(){
		if(Connection._RETRANSMIT){
			retrans_timer = new Timer();
			retrans_timer.schedule(new LocalTimerTask(), timeout);
		}
	}
	public void cancel_timer(){
		if(Connection._RETRANSMIT){
			retrans_timer.cancel();
			retrans_timer = null;
		}
	}
	class LocalTimerTask extends TimerTask{
		public void run(){
			//send datagram
			send_datagram();
			if(timeout < LIMIT_TIMIEOUT)
				timeout = timeout*2;
			retrans_timer.cancel();
			set_timer();
		}
	}
	public void send_datagram(){
		byte[] data = generate_datagram();
		try{
			if(Connection._DEBUG)
				Connection.deubg_println("SEND-ONE:"+this);
			the_connection.sock.send(new DatagramPacket(data,data.length,the_connection.sock_addr));
		}catch(Exception e){
		}
	}
	abstract byte[] generate_datagram();
	abstract public String toString();
	
	//get datagram from byte[]
	public static SftpDatagram analyse_datagram(byte[] input){
		//byte filed
		try{
			byte in_type = input[0];
			switch(in_type){
			case QUERY:	case ANSWER:
				return QueryAnswerDatagram.analyse(input);
			case ACK: case DATA: case ALIVE:
				return OtherDatagram.analyse(input);
			default:
				throw new Err("Unknown Type");
			}
		}
		catch(Err e){	//invalid input byte[] datagram
			return null;
		}
	}
}

abstract class QueryAnswerDatagram extends SftpDatagram{
	//commmon fields
	short size_of_datagram;
	short len_of_param;
	byte[] param;
	byte[] data;
	protected QueryAnswerDatagram(byte t,byte c,short s,short s2,short l,byte[] p,byte[] d,Connection con){
		type = t;	code = c;	session_id = s;
		size_of_datagram = s2;	len_of_param = l;
		param = p;	data = d;
		the_connection = con;
	}
	byte[] generate_datagram(){
		int param_size = param.length;
		int data_size = data.length;
		int send_size = 1+1+2+2+2+param_size+data_size;
		byte[] send = new byte[send_size];
		send[0] = type;
		send[1] = code;
		send[2] = (byte)(session_id & 0xff);
		send[3] = (byte)((session_id>>8) & 0xff);
		send[4] = (byte)(size_of_datagram & 0xff);
		send[5] = (byte)((size_of_datagram>>8) & 0xff);
		send[6] = (byte)(len_of_param & 0xff);
		send[7] = (byte)((len_of_param>>8) & 0xff);
		for(int i=0;i<param_size;i++)
			send[i+8] = param[i];
		for(int i=0;i<data_size;i++)
			send[i+8+param_size] = data[i];
		return send;
	}
	static QueryAnswerDatagram analyse(byte[] input){
		byte in_type = input[0];
		byte in_code = input[1];
		short in_sid = Helper.combine_byte(input[2], input[3]);
		short in_size_datagram = Helper.combine_byte(input[4], input[5]);
		short in_len_param = Helper.combine_byte(input[6], input[7]);
		byte[] in_param = Helper.get_bytes(input, 8, in_len_param);
		byte[] in_data = Helper.get_bytes(input, 8+in_len_param, input.length-(8+in_len_param));
		if((in_type==QUERY && in_code>=QUERY_END)||(in_type==ANSWER && in_code>=ANSWER_END))
			throw new Err("Unknown Code");
		if(in_type==QUERY)
			return new QueryDatagram(in_code,in_sid,in_size_datagram,in_len_param,in_param,in_data,null);
		else if(in_type==ANSWER)
			return new AnswerDatagram(in_code,in_sid,in_size_datagram,in_len_param,in_param,in_data,null);
		else
			throw new RuntimeException("Bug");
	}
}

abstract class OtherDatagram extends SftpDatagram{
	//commmon fields
	short id_of_datagram;
	byte[] data;
	protected OtherDatagram(byte t,byte c,short s,short id,byte[] d,Connection con){
		type = t;	code = c;	session_id = s;
		id_of_datagram = id;	data = d;
		the_connection = con;
	}
	byte[] generate_datagram(){
		int data_size = data.length;
		int send_size = 1+1+2+2+data_size;
		byte[] send = new byte[send_size];
		send[0] = type;
		send[1] = code;
		send[2] = (byte)(session_id & 0xff);
		send[3] = (byte)((session_id>>8) & 0xff);
		send[4] = (byte)(id_of_datagram & 0xff);
		send[5] = (byte)((id_of_datagram>>8) & 0xff);
		for(int i=0;i<data_size;i++)
			send[i+6] = data[i];
		return send;
	}
	static OtherDatagram analyse(byte[] input){
		byte in_type = input[0];
		byte in_code = input[1];
		short in_sid = Helper.combine_byte(input[2], input[3]);
		short in_datagram_id = Helper.combine_byte(input[4], input[5]);
		byte[] in_data = Helper.get_bytes(input, 6, input.length-6);
		switch(in_type){
		case ACK:
			if(in_code >= ACK_END)
				throw new Err("Unknown Code");
			return new AckDatagram(in_code,in_sid,in_datagram_id,in_data,null);
		case DATA:
			if(in_code >= DATA_END)
				throw new Err("Unknown Code");
			return new DataDatagram(in_sid,in_datagram_id,in_data,null);
		case ALIVE:
			if(in_code >= ALIVE_END)
				throw new Err("Unknown Code");
			return new AliveDatagram(in_code,null);
		default:
			throw new RuntimeException("Bug");
		}
	}
}

class QueryDatagram extends QueryAnswerDatagram{
	public QueryDatagram(byte c,short s,short s2,short l,byte[] p,byte[] d,Connection con){
		super(QUERY,c,s,s2,l,p,d,con);
	}
	public String toString(){
		return "QUERY:"+CMDS_NAMES[(int)code]+":<sid>"+session_id+":<pack_num>"+size_of_datagram+":<param>"+Helper.get_string(param);
	}
}

class AnswerDatagram extends QueryAnswerDatagram{
	public AnswerDatagram(byte c,short s,short s2,short l,byte[] p,byte[] d,Connection con){
		super(ANSWER,c,s,s2,l,p,d,con);
	}
	public String toString(){
		return "ANSWER:"+code+":<sid>"+session_id+":<pack_num>"+size_of_datagram+":<param>"+Helper.get_string(param);
	}
}

class AckDatagram extends OtherDatagram{
	public AckDatagram(byte c,short s,short l,byte[] d,Connection con){
		super(ACK,c,s,l,d,con);
	}
	public String toString(){
		return "ACK:<code>"+code+":<sid>"+session_id+":<pack-id>"+id_of_datagram;
	}
}

class DataDatagram extends OtherDatagram{
	public DataDatagram(short s,short l,byte[] d,Connection con){
		super(DATA,NOTHING,s,l,d,con);
	}
	public String toString(){
		return "DATA:<sid>"+session_id+":<pack-id>"+id_of_datagram;
	}
}

class AliveDatagram extends OtherDatagram{
	public AliveDatagram(byte c,Connection con){
		super(ALIVE,c,(short)0,(short)0,new byte[0],con);
	}
	public String toString(){
		return "Not implement";
	}
}

//datagram error
class Err extends RuntimeException{
	Err(String str){
		super(str);
	}
}

