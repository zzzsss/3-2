package sftp;

import java.io.*;
import java.net.*;

import static sftp.SftpDatagram.*;

public class ConnectionServer extends Connection{
	//constants
	final String init_dir = ".";
	//dir state
	File root_dir;
	File cur_dir;
	//helper ones
	boolean to_kill = false;
	
	//init --- only accept HELLO
	public ConnectionServer(InetSocketAddress addr,DatagramSocket s,QueryDatagram d){
		super(addr,s);
		root_dir = new File(init_dir);
		cur_dir = root_dir;
		
		deal_datagram(d);
	}
	//main fsm
	public void deal_datagram(SftpDatagram p){
		//if not open, just send a ack_err
		if(!is_open && (p.type!=ACK) && !((p.type==QUERY) && (p.code==HELLO))){
			(new AckDatagram(ACK_ERR,(short)0,(short)0,"Not open".getBytes(),this)).send_datagram();
			to_kill = true;
			return;
		}
		if(state==IDLE){
			switch(p.type){
			case QUERY:
			{
				QueryDatagram q = (QueryDatagram)p;
				//check sid
				if(q.session_id != session_num && q.code != HELLO){
					(new AckDatagram(ACK_UPDATE,session_num,(short)0,"Wrong s-id".getBytes(),this)).send_datagram();
					return;
				}	
				cur_session = new Session(this);
				file_to_write = null;
				session_driver = q;
				AnswerDatagram reply = null;
				byte[] msg = null;
				switch(q.code){
				case HELLO:
					session_num = 0;
					reply = new AnswerDatagram(OK,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
					break;
				case BYE:
					reply = new AnswerDatagram(OK,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
					break;
				case CD:
				{
					String dir_name = Helper.get_string(q.param);
					File new_dir = null;
					//if absolute dir
					if(dir_name.length()==0)
						new_dir = cur_dir;
					else if(dir_name.charAt(0)=='/')
						new_dir = new File(root_dir.getPath()+'/'+dir_name); 
					else
						new_dir = new File(cur_dir.getPath()+'/'+dir_name);
					//check it
					if(!new_dir.exists() || !new_dir.isDirectory()){
						byte[] reply_param = "Not legal dir".getBytes();
						reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
					}
					else{
						cur_dir = new_dir;
						reply = new AnswerDatagram(OK,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
					}
					break;
				}
				case PWD:
				{
					try{
						String root_pwd = root_dir.getCanonicalPath();
						String cur_pwd = cur_dir.getCanonicalPath();
						String the_pwd = "Root:"+cur_pwd.substring(root_pwd.length());
						reply = new AnswerDatagram(OK,session_num,(short)1,(short)the_pwd.getBytes().length,the_pwd.getBytes(),new byte[0],this);
					}catch(Exception e){
						byte[] reply_param = "Pwd error".getBytes();
						reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
					}
					break;
				}
				case LS:
				{
					StringBuilder tmp = new StringBuilder();
					for(File x : cur_dir.listFiles())
						tmp.append(x.getName()+'\n');
					byte[] the_ls = tmp.toString().getBytes();
					msg = the_ls;	//need data
					//for the header data
					short size_of_first = (short)((the_ls.length > Session.BLOCK_SIZE) ? Session.BLOCK_SIZE : the_ls.length);
					short size_of_pack = (short)((the_ls.length + Session.BLOCK_SIZE - 1)/Session.BLOCK_SIZE);
					reply = new AnswerDatagram(OK,session_num,size_of_pack,(short)0,new byte[0],Helper.get_bytes(the_ls, 0, size_of_first),this);
					break;
				}
				case GET:
				{
					String file_name = Helper.get_string(q.param);
					File the_file = null;
					if(file_name.charAt(0)=='/')
						the_file = new File(root_dir,file_name);
					else
						the_file = new File(cur_dir,file_name);
					//check it
					if(!the_file.exists() || !the_file.isFile()){
						byte[] reply_param = "Not exist or not file".getBytes();
						reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
					}
					else if(!the_file.canRead()){
						byte[] reply_param = "File can't be read".getBytes();
						reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
					}
					else if(the_file.length() > LARGEST_FILE){
						byte[] reply_param = "File too large".getBytes();
						reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
					}
					else{
						byte[] reply_get = new byte[(int)the_file.length()];
						FileInputStream tmp_stream = null;
						try{
							tmp_stream = (new FileInputStream(the_file));
							tmp_stream.read(reply_get);
							msg = reply_get;
							//for the header data
							short size_of_first2 = (short)((reply_get.length > Session.BLOCK_SIZE) ? Session.BLOCK_SIZE : reply_get.length);
							short size_of_pack2 = (short)((reply_get.length + Session.BLOCK_SIZE - 1)/Session.BLOCK_SIZE);
							reply = new AnswerDatagram(OK,session_num,size_of_pack2,(short)0,new byte[0],Helper.get_bytes(reply_get, 0, size_of_first2),this);
						}catch(IOException e){
							byte[] reply_param = "File IO Exception".getBytes();
							reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
						}finally{
							try{
								tmp_stream.close();
							}catch(Exception e){}
						}
					}
					break;
				}
				case PUT:
				{
					String file_name = Helper.get_string(q.param);
					File the_file = null;
					if(file_name.charAt(0)=='/')
						the_file = new File(root_dir,file_name);
					else
						the_file = new File(cur_dir,file_name);
					//check
					if(the_file.exists() && !the_file.delete()){
						byte[] reply_param = "File Exist and can't delete".getBytes();
						reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
					}
					else{
						file_to_write = the_file;
						cur_session.recv_head(q.size_of_datagram, q.data);
						reply = new AnswerDatagram(OK,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
					}
					break;
				}
				case DEL:
				{
					String file_name = Helper.get_string(q.param);
					File the_file = null;
					if(file_name.charAt(0)=='/')
						the_file = new File(root_dir,file_name);
					else
						the_file = new File(cur_dir,file_name);
					if(the_file.delete()){
						reply = new AnswerDatagram(OK,session_num,(short)1,(short)0,new byte[0],new byte[0],this);
					}
					else{
						byte[] reply_param = "Delete fails".getBytes();
						reply = new AnswerDatagram(ERR,session_num,(short)1,(short)reply_param.length,reply_param,new byte[0],this);
					}
					break;
				}
				default:
					throw new RuntimeException("Bug");
				}
				state = WAIT;
				cur_session.send(reply,msg);
				break;
			}
			case DATA:
			{
				DataDatagram dd = (DataDatagram)p;
				if(dd.session_id < session_num)
					(new AckDatagram(ACK_UPDATE,session_num,(short)0,"Finished session".getBytes(),this)).send_datagram();
			}
			default:
				//ignore
				break;
			}
		}
		else if(state == WAIT){
			switch(p.type){
			//new query --- cancel current
			case QUERY:
				if(p.session_id > session_num){
					cur_session.cancel();
					session_num = p.session_id;
					state = IDLE;
					this.deal_datagram(p);
					return;
				}
				break;
			case ACK:
				AckDatagram q = (AckDatagram)p;
				if(q.code == ACK_UPDATE){
					cur_session.cancel();
					session_num = q.session_id;
					state = IDLE;
					return;
				}
				else if(q.code == ACK_YES && q.session_id==session_num){
					cur_session.getack(q.id_of_datagram);
				}
				break;
			case DATA:
				DataDatagram dd = (DataDatagram)p;
				if(dd.session_id == session_num){
					cur_session.recv_data(dd.id_of_datagram, dd.data);
					(new AckDatagram(ACK_YES,session_num,dd.id_of_datagram,new byte[0],this)).send_datagram();
				}
				break;
			default:
				//ignore
				break;
			}
			//check finshed yet??
			byte[] recv_byte = null;
			if(cur_session.getack_all() && (recv_byte=cur_session.recv_all())!=null){
				if(file_to_write != null){
					FileOutputStream out_stream = null;
					try{
						out_stream = new FileOutputStream(file_to_write);
						out_stream.write(recv_byte);
					}catch(Exception e){
					}finally{
						try{
							out_stream.close();
						}catch(Exception e){}
					}
				}
				if(session_driver.code == HELLO)
					is_open = true;
				else if(session_driver.code == BYE)
					is_open = false;
				session_driver = null;
				cur_session = null;
				session_num++;
				state = IDLE;
				return;
			}
		}
		else
			throw new RuntimeException("Bug");
	}
}
