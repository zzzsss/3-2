package sftp;

import java.lang.reflect.Array;
import java.net.*;
import java.util.*;
import java.io.*;

import static sftp.SftpDatagram.*;

public class ConnectionClient extends Connection{
	/* the respond answer */
	AnswerDatagram session_answer;
	Thread read_user_t;
	//init 
	public ConnectionClient(InetSocketAddress addr,DatagramSocket s){
		super(addr,s);
		//send a hello
		println_info("Tryin to init to the server...");
		state = WAIT;
		cur_session = new Session(this);
		file_to_write = null;
		session_driver = new QueryDatagram(HELLO,(short)0,(short)1,(short)0,new byte[0],new byte[0],this);
		cur_session.send(session_driver, null);
		state = WAIT;
		is_open = true;
	}
	
	//main fsm1 --- IDLE to WAIT
	synchronized public void deal_cmd(String input){
		OneCmd user_c = parse_cmd(input);
		file_to_write = null;
		byte[] bytes_to_send = null;
		switch(user_c.cmd){
		case HELLO:
			println_info("Reinit to the server...");
			session_num = 0;
			session_driver = new QueryDatagram(HELLO,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
			break;
		case BYE:
			println_info("Trying to disconnect...");
			session_driver = new QueryDatagram(BYE,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
			break;
		case CD:
			byte[] the_cd = user_c.param.getBytes();
			session_driver = new QueryDatagram(CD,session_num,(short)1,(short)the_cd.length,the_cd,new byte[0],this);
			break;
		case PWD:
			session_driver = new QueryDatagram(PWD,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
			break;
		case LS:
			session_driver = new QueryDatagram(LS,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
			break;
		case GET:
			if(user_c.param.length()==0){
				println_info("Get what?? Need file-name...");
				return;
			}
			byte[] the_get = user_c.param.getBytes();
			//just write to the top level
			file_to_write = new File((new File(user_c.param)).getName());
			session_driver = new QueryDatagram(GET,session_num,(short)1,(short)the_get.length,the_get,new byte[0],this);
			break;
		case PUT:
			if(user_c.param.length()==0){
				println_info("Put what?? Need file-name...");
				return;
			}
			byte[] the_put = user_c.param.getBytes();
			File to_put = new File(Helper.get_string(the_put));
			//file name
			byte[] to_put_name = to_put.getName().getBytes();
			if(!to_put.exists()){
				println_info("File "+to_put.getName()+" does not exist...");
				return;
			}
			else if(!to_put.isFile() || !to_put.canRead()){
				println_info("File "+to_put.getName()+" can't be transmitted...");
				return;
			}
			else if(to_put.length() > LARGEST_FILE){
				println_info("File "+to_put.getName()+" too large, sorry :( ...");
				return;
			}
			//to transfer it
			byte[] the_filebytes = new byte[(int)to_put.length()];
			FileInputStream trans_stream = null;
			try{
				trans_stream = new FileInputStream(to_put);
				trans_stream.read(the_filebytes);
				//for the header data
				bytes_to_send = the_filebytes;
				short size_of_first2 = (short)((the_filebytes.length > Session.BLOCK_SIZE) ? Session.BLOCK_SIZE : the_filebytes.length);
				short size_of_pack2 = (short)((the_filebytes.length + Session.BLOCK_SIZE - 1)/Session.BLOCK_SIZE);
				session_driver = new QueryDatagram(PUT,session_num,size_of_pack2,(short)to_put_name.length,to_put_name,Helper.get_bytes(the_filebytes, 0, size_of_first2),this);
			}catch(Exception e){
				println_info("File "+the_put+" IO exception ...");
				return;
			}finally{
				try{
					trans_stream.close();
				}catch(Exception e){}
			}
			break;
		case DEL:
			if(user_c.param.length()==0){
				println_info("Del what?? Need file-name...");
				return;
			}
			byte[] the_del = user_c.param.getBytes();
			session_driver = new QueryDatagram(DEL,session_num,(short)1,(short)the_del.length,the_del,new byte[0],this);
			break;
		case QUERY_END:
			println_info("Illegal command:"+user_c.param);
			return;
		default://empty line
			return;
		}
		//switch to WAIT
		state = WAIT;
		cur_session = new Session(this);
		cur_session.send(session_driver, bytes_to_send);
	}
	//main fsm2 --- IDLE/WAIT
	synchronized public void deal_datagram(SftpDatagram p){
		if(state==IDLE){
			if(p.type != ACK)
				(new AckDatagram(ACK_UPDATE,session_num,(short)0,"Finished session".getBytes(),this)).send_datagram();
		}
		else{
			if(p.type==ACK && p.code == ACK_ERR){
				println_info("Connection broken, exit!!");
				is_open = false;
			}
			if(p.session_id != session_num){
				if(p.type==ACK && p.code==ACK_UPDATE &&p.session_id>session_num){
					finish_session(p.session_id);
				}
				return;
			}
			switch(p.type){
			case ANSWER:
				AnswerDatagram ans = (AnswerDatagram)p;
				if(ans.code==ERR){
					println_info("Request error: "+Helper.get_string(ans.param));
					session_answer = ans;
					(new AckDatagram(ACK_YES,session_num,(short)1,new byte[0],this)).send_datagram();
					finish_session((short)(session_num+1));
					return;
				}
				else{
					cur_session.recv_head(ans.size_of_datagram, ans.data);
					session_answer = ans;
					(new AckDatagram(ACK_YES,session_num,(short)1,new byte[0],this)).send_datagram();
				}
				break;
			case DATA:
				DataDatagram dd = (DataDatagram)p;
				cur_session.recv_data(dd.id_of_datagram, dd.data);
				(new AckDatagram(ACK_YES,session_num,dd.id_of_datagram,new byte[0],this)).send_datagram();
				break;
			case ACK:
				AckDatagram ac = (AckDatagram)p;
				if(ac.code == ACK_YES)
					cur_session.getack(ac.id_of_datagram);
				else{}	//nope
				break;
			default:
				//nope, ignore
			}
			//check finish
			byte[] recv_byte = null;
			if(cur_session.getack_all() && (recv_byte=cur_session.recv_all())!=null){
				switch(session_driver.code){
				case HELLO:
					println_info("Initialization OK");
					break;
				case BYE:
					println_info("Bye-bye...");
					is_open = false;	//help to let the upper layer to know the finish
					break;
				case CD:
					break;
				case PWD:
					println_info("Current dir is: " + Helper.get_string(session_answer.param));
					break;
				case LS:
					println_info("Current dir's content:");
					println_info(Helper.get_string(recv_byte));
					break;
				case GET:
					if(file_to_write != null){
						FileOutputStream out_stream = null;
						try{
							out_stream = new FileOutputStream(file_to_write);
							out_stream.write(recv_byte);
						}catch(Exception e){
							println_info("Sorry, write file error: "+e.getMessage());
						}finally{
							try{
								out_stream.close();
							}catch(Exception e){}
						}
					}
					break;
				case PUT: case DEL:
					break;
				default:
					//not possible
					break;
				}
				finish_session((short)(session_num+1));
			}
		}
	}
	
	/* some common procedures */
	void finish_session(short num){
		cur_session.cancel();
		println_session_sum(cur_session,session_driver,session_answer);
		session_num = num;
		state = IDLE;
		session_driver = null;
		session_answer = null;
		cur_session = null;
		synchronized(this.read_user_t){
			this.read_user_t.notifyAll();
		}
	}
	/* user interface */
	static void println_info(String x){
		System.out.println(x);
	}
	static void println_session_sum(Session cur,QueryDatagram driv,AnswerDatagram ans){
		long cur_time = System.nanoTime();
		double session_during_ms = (cur_time - cur.start_time)/1000000.0;
		if(driv.code == GET){
			double session_speed_KBps = (ans.size_of_datagram*Session.BLOCK_SIZE*1000)/(1024*session_during_ms);
			System.out.printf("GET-finished: time is %.2f ms; speed is %.2f KB/s\n",session_during_ms,session_speed_KBps);
		}
		else if(driv.code == PUT){
			double session_speed_KBps = (driv.size_of_datagram*Session.BLOCK_SIZE*1000)/(1024*session_during_ms);
			System.out.printf("PUT-finished: time is %.2f ms; speed is %.2f KB/s\n",session_during_ms,session_speed_KBps);
		}
	}
	static class OneCmd{
		byte cmd;
		String param;
		public OneCmd(byte c,String p){
			cmd=c;
			param=p;
		}
	}
	static OneCmd parse_cmd(String input){
		//only the first two token
		int i=0;
		String cmd = "",param="";
		/* empty line */
		int size = input.length();
		for(i=0;i<size && Character.isWhitespace(input.charAt(i));i++);
		if(i==size)
			return new OneCmd((byte)(QUERY_END+1),"");
		/* the cmd */
		for(;i<size && Character.isAlphabetic(input.charAt(i));i++)
			cmd += input.charAt(i);
		Byte cmd_num = CMDS.get(cmd);
		//illegal cmd
		if(cmd_num==null)
			return new OneCmd(QUERY_END,cmd);
		/* next token */
		for(;i<size && Character.isWhitespace(input.charAt(i));i++);
		for(;i<size && !Character.isWhitespace(input.charAt(i));i++)
			param += input.charAt(i);
		return new OneCmd(cmd_num,param);
	}
}

