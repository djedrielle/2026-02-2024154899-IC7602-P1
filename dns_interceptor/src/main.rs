use std::net::UdpSocket;

fn main() -> std::io::Result<()> {
    {
        let socket = UdpSocket::bind("0.0.0.0:53")?;

        let mut buf = [0; 512]; // Buffer para recibir la solicitud del cliente
        let (amt, src) = socket.recv_from(&mut buf)?; // Esperar la solicitud y guardarla en el buffer

        println!("{:02x?}", &buf[..amt]); // Imprimir la solicitud

        // Extraer valores de la solicitud enviada por el cliente
        // let id = u16::from_be_bytes([buf[0],buf[1]]); // Extraer id (mas adelante hay que incluirlo en la respuesta)
        let QR: bool = ((buf[2] >> 7) & 1) == 1; // Extraer QR (Booleano)
        let op_code: u8 = (buf[2] >> 3) & 0b0000_1111; // Extraer op_code (Numero entero en binario)

        if QR && op_code == 0 {
            // identificar el host que se esta tratando de resolver
            // consultar a DNS API (/api/exists) si existe un registro para este host
        } else {
            // codificar en base64
            // enviar via HTTPS a POST /api/dns_resolver

        }
        println!("{}", QR);
        println!("{}", op_code);

        
    } // the socket is closed here
    Ok(())
}