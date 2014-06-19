package sftp;

import java.io.IOException;
import java.net.*;
import java.util.*;

import static sftp.Connection.*;
import static sftp.DataDatagram.*;

public class SftpServer {
	static public void main(String[] argv){
		Map<InetSocketAddress,ConnectionServer> client_map = new HashMap<InetSocketAddress,ConnectionServer>();
		/* open listen */
		DatagramSocket server_sock = null;
		try{
			server_sock = new DatagramSocket(SFTP_PORT);
		}catch(SocketException e){
			System.err.println(e.getMessage());
			System.exit(1);
		}
		/* receive */
		byte[] recv_buf = new byte[1024];
		DatagramPacket tmp = new DatagramPacket(recv_buf,recv_buf.length);
		while(true){
			try{
				server_sock.receive(tmp);
				byte[] real_receive = Helper.get_bytes(tmp.getData(), 0, tmp.getLength());
				SftpDatagram temp = SftpDatagram.analyse_datagram(real_receive);
				if(temp != null){
					InetSocketAddress the_from = (InetSocketAddress)tmp.getSocketAddress();
					ConnectionServer the_conn = client_map.get(the_from);
					//debug
					if(temp != null && _DEBUG)
						deubg_println("RECV-ONE:"+temp);
					//old one
					if(the_conn != null){
						the_conn.deal_datagram(temp);
						if(!the_conn.is_open && the_conn.session_num>0)//already finished one
							client_map.remove(the_from);
					}
					//new one
					if(the_conn==null){
						the_conn = new ConnectionServer(the_from,server_sock,(QueryDatagram)temp);
						if(!the_conn.to_kill)
							client_map.put(the_from, the_conn);
					}
				}
			}catch(IOException e){
				System.err.println(e.getMessage());
			}
		}
	}
}
