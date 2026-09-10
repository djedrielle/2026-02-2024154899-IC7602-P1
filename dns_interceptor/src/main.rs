use std::net::UdpSocket;

fn main() -> std::io::Result<()> {
    {
        let socket = UdpSocket::bind("0.0.0.0:53")?;
        let mut buf = [0; 512];
        let (amt, src) = socket.recv_from(&mut buf)?;

        println!("{:02?}", &buf[..amt]);

        // Redeclare `buf` as slice of the received data and send reverse data back to origin.
        let buf = &mut buf[..amt];
        buf.reverse();
        socket.send_to(buf, &src)?;
    } // the socket is closed here
    Ok(())
}