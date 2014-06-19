package sftp;

import java.net.*;
import java.io.*;

import static sftp.Connection.*;

public class SftpClient {
	static String DEFAULT_TARGE = "127.0.0.1";
	static ConnectionClient conn;
	static public void main(String[] argv){
		/* target */
		String target = null;
		if(argv.length < 1){
			System.err.println("Using default target: "+DEFAULT_TARGE);
			target = DEFAULT_TARGE;
		}
		else if(argv.length == 1){
			target = argv[0];
		}
		else{
			System.err.println("Bad format");
			System.exit(1);
		}
		/* address */
		InetAddress addr = null;
		try{
			addr = InetAddress.getByName(target);
		}catch(UnknownHostException e){
			System.err.println(e.getMessage());
			System.exit(1);
		}
		InetSocketAddress sock_addr = new InetSocketAddress(addr,SFTP_PORT);
		DatagramSocket local_sock = null;
		/* Connection */
		try{
			local_sock = new DatagramSocket();
		}catch(SocketException e){
			System.err.println(e.getMessage());
			System.exit(1);
		}
		conn = new ConnectionClient(sock_addr,local_sock);
		conn.read_user_t = new Thread(new user_running());
		conn.read_user_t.start();
		
		/* listen on socket */
		byte[] recv_buf = new byte[1024];
		DatagramPacket tmp = new DatagramPacket(recv_buf,recv_buf.length);
		/* main loop */
		while(conn.is_open){
			try{
				local_sock.receive(tmp);
				byte[] real_receive = Helper.get_bytes(tmp.getData(), 0, tmp.getLength());
				SftpDatagram temp = SftpDatagram.analyse_datagram(real_receive);
				//debug
				if(temp != null && _DEBUG)
					deubg_println("RECV-ONE:"+temp);
				if(temp != null)
					conn.deal_datagram(temp);
			}catch(IOException e){
				System.err.println(e.getMessage());
			}
		}
		System.exit(0);
	}
	static class user_running implements Runnable{
		public void run(){
			/* main loop */
			BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
			while(conn.is_open){
				if(conn.state == IDLE){
					try{
						//prompt first
						System.out.print("sftp$ ");
						System.out.flush();
						String once = input.readLine();
						conn.deal_cmd(once);
					}catch(IOException e){
						System.err.println(e.getMessage());
					}
				}
				else{
					try{
						synchronized(Thread.currentThread()){
							Thread.currentThread().wait();
						}
					}catch(InterruptedException e){
					}
				}
			}
		}
	}
}
