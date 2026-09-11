use std::net::UdpSocket;

fn main() -> std::io::Result<()> {
    {
        let socket = UdpSocket::bind("0.0.0.0:53")?;

        let mut buf = [0; 512]; // Buffer para recibir la solicitud del cliente
        let (amt, src) = socket.recv_from(&mut buf)?;

        println!("{:02x?}", &buf[..amt]);

        let QR: u8 = (buf[2] >> 7) & 1;
        let op_code: u8 = (buf[2] >> 3) & 0b0000_1111;

        println!("{}", QR);
        println!("{}", op_code);

        
    } // the socket is closed here
    Ok(())
}