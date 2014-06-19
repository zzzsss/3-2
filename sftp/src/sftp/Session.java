package sftp;

import java.io.*;
import java.util.*;

/* datas in one seesion
 * 	---	but only provide data controls(real control goes to <C/S>Connection)
 * 	---	this maybe just another layer between UDP and FTP
 * 	--- to support large file mode(all use files to transfer) (current means ls use files)
 */

public class Session {
	static final int BLOCK_SIZE=512,WINDOW_SIZE=10;
	/* blocks counting from 0 --- 0 means QUERY/ANSWER
	 * the following ones means data...
	 */
	/* control the send-reply-ack */
	Connection the_connection;
	long start_time;
	public Session(Connection c){
		the_connection = c;
		start_time = System.nanoTime();
	}
	//send
	byte[] data_send;
	int not_ack_yet;	/* the number of sending ones but not ack */
	int covered_datagrams;	/* current datagrams send */
	int total_datagrams;
	Map<Integer,SftpDatagram> sendings = new LinkedHashMap<Integer,SftpDatagram>();
	//recv
	TreeMap<Integer,byte[]> recvings = new TreeMap<Integer,byte[]>();
	int recved_data_num;
	int total_data_num=-1;	//if -1,no data ; if 0, then not known yet
	
	/* internal api */
	/* sendings */
	//put one datagram and send data
	void put_and_set_timer(SftpDatagram d){
		sendings.put(new Integer(covered_datagrams), d);
		not_ack_yet++;	//must assure not over SEND_WINDOW
		d.set_timer();
	}
	//check whether window_open and 
	void check_and_send(){
		while(not_ack_yet <= WINDOW_SIZE){
			if(covered_datagrams >= total_datagrams)
				return;
			//send one data more
			covered_datagrams++;
			byte[] data_to_send = Helper.get_bytes(data_send,(covered_datagrams-1)*BLOCK_SIZE,BLOCK_SIZE);
			DataDatagram send = new DataDatagram((short)the_connection.session_num,
					(short)(covered_datagrams),data_to_send,the_connection);
			send.send_datagram();
			put_and_set_timer(send);
		}
	}
	
	/* api */
	/* for sending */
	//send one --- all files (assure that the file is ok)
	void send(SftpDatagram head,byte[] data){
		data_send = data;
		//first send the first one
		total_datagrams = 1;
		covered_datagrams++;
		head.send_datagram();
		put_and_set_timer(head);
		//sending
		if(data_send != null){
			long size = data_send.length;
			if(size != 0)
				total_datagrams = (int)((size+BLOCK_SIZE-1) / BLOCK_SIZE);
		}
		//check sendings(if not small mode)
		check_and_send();
	}
	//ack one piece --- return true if not acked
	void getack(int datagram_id){
		SftpDatagram the_datagram = sendings.get(new Integer(datagram_id));
		//indeed ack
		if(the_datagram != null){
			the_datagram.cancel_timer();
			sendings.remove(new Integer(datagram_id));
			not_ack_yet--;
			check_and_send();
		}
	}
	boolean getack_all(){
		return (covered_datagrams>=total_datagrams) && not_ack_yet==0;
	}
	//cancel current session
	void cancel(){
		for(SftpDatagram the_datagram:sendings.values()){
			the_datagram.cancel_timer();
		}
		covered_datagrams = total_datagrams;
		not_ack_yet = 0;
	}
	/* for receiving */
	void recv_head(int total_datas,byte[] datas){
		if(!recvings.containsKey(new Integer(0))){
			total_data_num = total_datas;
			recved_data_num++;
			recvings.put(new Integer(1),datas);
			//also ack the sending one
			getack(1);
		}
	}
	void recv_data(int datagram_id,byte[] datas){
		if(!recvings.containsKey(new Integer(datagram_id))){
			if(total_data_num==-1)	//means need data
				total_data_num=0;
			recvings.put(new Integer(datagram_id), datas);
			recved_data_num++;
		}
	}
	byte[] recv_all(){
		if(total_data_num >0 && recved_data_num == total_data_num){
			byte[] tmp = new byte[(total_data_num-1)*BLOCK_SIZE+recvings.lastEntry().getValue().length];
			for(Map.Entry<Integer,byte[]> e: recvings.entrySet()){
				if(e.getKey() > total_data_num)
					break;
				Helper.set_bytes(tmp, (e.getKey()-1)*BLOCK_SIZE, e.getValue());
			}
			return tmp;
		}
		else if(total_data_num == -1)
			return new byte[0];
		else
			return null;
	}
}
