use std::net::UdpSocket;

fn main() -> std::io::Result<()> {
    {
        let socket = UdpSocket::bind("0.0.0.0:53")?;

        let mut buffer_solicitud = [0; 512]; // Buffer para recibir la solicitud del cliente
        let (amt, src) = socket.recv_from(&mut buffer_solicitud)?; // Esperar la solicitud y guardarla en el buffer

        println!("{:02x?}", &buffer_solicitud[..amt]); // Imprimir la solicitud

        // Extraer valores de la solicitud enviada por el cliente
        // let id = u16::from_be_bytes([buffer_solicitud[0],buffer_solicitud[1]]); // Extraer id (mas adelante hay que incluirlo en la respuesta)
        let QR: bool = ((buffer_solicitud[2] >> 7) & 1) == 1; // Extraer QR (Booleano)
        let op_code: u8 = (buffer_solicitud[2] >> 3) & 0b0000_1111; // Extraer op_code (Numero entero en binario)

        if !QR && op_code == 0 {
            // identificar el host que se esta tratando de resolver
            let host = extraer_host(&buffer_solicitud[12..]); // [12..] para saltarnos el header
            println!("Host: {}", host);
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

fn extraer_host(buffer_solicitud: &[u8]) -> String {
    let mut host = String::new();
    let mut longitud_label = buffer_solicitud[0] as usize;
    let mut indice = 0;

    while buffer_solicitud[indice] != 0 {
        let inicio = indice + 1;
        let fin = indice + longitud_label;

        for &byte in &buffer_solicitud[inicio..=fin]{
            host.push(byte as char);
        }

        indice = fin + 1;
        longitud_label = buffer_solicitud[indice] as usize;

        if buffer_solicitud[indice] != 0 {
            host.push('.');
        }
    }
    host
}